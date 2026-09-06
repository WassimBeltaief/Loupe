@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.wassimbeltaief.loupe.plugin

import java.io.File
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val COMPOSABLE_FQN = FqName("androidx.compose.runtime.Composable")
private val LOUPE_RUNTIME_CLASS_ID = ClassId.topLevel(FqName("com.wassimbeltaief.loupe.runtime.LoupeRuntime"))
private val PAIR_CLASS_ID = ClassId(FqName("kotlin"), Name.identifier("Pair"))
private val SYSTEM_CLASS_ID = ClassId.fromString("java/lang/System")

internal class LoupeIrTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    internal val onComposableFound: ((String) -> Unit)? = null,
) : IrElementTransformerVoid() {

    private val irBuiltIns = pluginContext.irBuiltIns
    private val anyNType: IrType = irBuiltIns.anyType.makeNullable()

    // ── Cached symbol lookups — resolved once per compilation, not per composable ──

    private val runtimeClass by lazy {
        pluginContext.referenceClass(LOUPE_RUNTIME_CLASS_ID).also { cls ->
            if (cls == null) warn("LoupeRuntime not found on classpath — no composables will be instrumented")
        }
    }

    // Identified by name + first param name "key", so a future overload won't silently break injection.
    private val recordFn by lazy {
        pluginContext.referenceFunctions(
            CallableId(LOUPE_RUNTIME_CLASS_ID, Name.identifier("record"))
        ).firstOrNull { fn ->
            fn.owner.valueParameters.size == 4 &&
                fn.owner.valueParameters[0].name.asString() == "key"
        }.also { fn ->
            if (fn == null) warn("LoupeRuntime.record(key,file,line,params) not found — no composables will be instrumented")
        }
    }

    private val pairClass by lazy {
        pluginContext.referenceClass(PAIR_CLASS_ID).also { cls ->
            if (cls == null) warn("kotlin.Pair not found — params recording disabled")
        }
    }

    private val pairCtor by lazy {
        pairClass?.owner?.constructors?.singleOrNull { it.valueParameters.size == 2 }.also { ctor ->
            if (ctor == null && pairClass != null) warn("Pair(A,B) constructor not found — params recording disabled")
        }
    }

    private val arrayOfFn by lazy {
        pluginContext.referenceFunctions(
            CallableId(FqName("kotlin"), Name.identifier("arrayOf"))
        ).firstOrNull { fn ->
            fn.owner.valueParameters.size == 1 &&
                fn.owner.valueParameters[0].varargElementType != null
        }.also { fn ->
            if (fn == null) warn("kotlin.arrayOf not found — params recording disabled")
        }
    }

    private val identityHashCodeFn by lazy {
        pluginContext.referenceClass(SYSTEM_CLASS_ID)
            ?.owner?.functions
            ?.singleOrNull { it.name.asString() == "identityHashCode" }
            .also { fn ->
                if (fn == null) warn("System.identityHashCode not found — lambda params will be captured by value")
            }
    }

    private fun warn(msg: String) =
        messageCollector.report(CompilerMessageSeverity.WARNING, "[Loupe] $msg")

    // ── Visitor ──────────────────────────────────────────────────────────────

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        declaration.transformChildrenVoid(this)

        if (!shouldInstrument(declaration)) return declaration

        val name = declaration.name.asString()
        onComposableFound?.invoke(name)
        messageCollector.report(CompilerMessageSeverity.LOGGING, "[Loupe] instrumenting: $name")

        val body = declaration.body as? IrBlockBody ?: return declaration
        val recordCall = buildRecordCall(declaration) ?: return declaration

        body.statements.add(0, recordCall)
        return declaration
    }

    private fun shouldInstrument(declaration: IrSimpleFunction): Boolean {
        if (!declaration.hasAnnotation(COMPOSABLE_FQN)) return false
        if (declaration.isInline) return false        // inline has no discrete body post-inlining
        if (declaration.name.isSpecial) return false  // <anonymous> lambdas — no stable key
        if (declaration.body == null) return false    // abstract / expect
        return true
    }

    // ── Record call builder ──────────────────────────────────────────────────

    private fun buildRecordCall(declaration: IrSimpleFunction): IrExpression? {
        val rc = runtimeClass ?: return null
        val fn = recordFn ?: return null

        val file = File(declaration.file.fileEntry.name).name
        val line = if (declaration.startOffset != UNDEFINED_OFFSET) {
            declaration.file.fileEntry.getLineNumber(declaration.startOffset) + 1
        } else {
            0
        }

        val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
        // Fall back to empty params array so recomposition count is still captured even if
        // Pair or arrayOf symbols can't be resolved (e.g. stripped test classpath).
        val paramsArray = buildParamsArray(declaration, builder) ?: buildEmptyParamsArray(builder) ?: return null

        return builder.irCall(fn).also { call ->
            call.dispatchReceiver = builder.irGetObjectValue(rc.owner.defaultType, rc)
            call.putValueArgument(0, irString(declaration.name.asString()))
            call.putValueArgument(1, irString(file))
            call.putValueArgument(2, irInt(line))
            call.putValueArgument(3, paramsArray)
        }
    }

    private fun buildEmptyParamsArray(builder: DeclarationIrBuilder): IrExpression? {
        val aoFn = arrayOfFn ?: return null
        val elementType = pairClass?.typeWith(irBuiltIns.stringType, anyNType) ?: anyNType
        val arrayType = irBuiltIns.arrayClass.typeWith(elementType)
        return builder.irCall(aoFn, arrayType).also { call ->
            call.putTypeArgument(0, elementType)
            call.putValueArgument(0, builder.irVararg(elementType, emptyList()))
        }
    }

    // ── Params array: arrayOf("name" to value, ...) ─────────────────────────

    private fun buildParamsArray(declaration: IrSimpleFunction, builder: DeclarationIrBuilder): IrExpression? {
        val pc = pairClass ?: return null
        val ctor = pairCtor ?: return null
        val aoFn = arrayOfFn ?: return null

        // Skip $composer, $changed, $default injected by the Compose compiler
        val userParams = declaration.valueParameters
            .filter { !it.name.asString().startsWith("$") }

        val pairType = pc.typeWith(irBuiltIns.stringType, anyNType)

        val pairs = userParams.map { param ->
            val nameArg = irString(param.name.asString())
            // Lambdas/callable refs: capture identity — instances are never equal across calls.
            // Fall back to value capture if System.identityHashCode is unavailable (stripped JDK).
            val rawValue: IrExpression = if (param.type.isFunctionLikeType()) {
                buildIdentityHashCode(param, builder) ?: builder.irGet(param)
            } else {
                builder.irGet(param)
            }
            val valueArg = builder.irImplicitCast(rawValue, anyNType)
            builder.irCallConstructor(ctor.symbol, listOf(irBuiltIns.stringType, anyNType)).also { pair ->
                pair.putValueArgument(0, nameArg)
                pair.putValueArgument(1, valueArg)
            }
        }

        val arrayOfPairType = irBuiltIns.arrayClass.typeWith(pairType)
        return builder.irCall(aoFn, arrayOfPairType).also { call ->
            call.putTypeArgument(0, pairType)
            call.putValueArgument(0, builder.irVararg(pairType, pairs))
        }
    }

    // ── System.identityHashCode(param) ───────────────────────────────────────

    private fun buildIdentityHashCode(param: IrValueParameter, builder: DeclarationIrBuilder): IrExpression? {
        val fn = identityHashCodeFn ?: return null
        return builder.irCall(fn.symbol).also { call ->
            call.putValueArgument(0, builder.irGet(param))
        }
    }

    // ── Type check ───────────────────────────────────────────────────────────

    private fun IrType.isFunctionLikeType(): Boolean {
        if (this !is IrSimpleType) return false
        val cls = classifier as? IrClassSymbol ?: return false
        val fqn = cls.owner.fqNameWhenAvailable?.asString() ?: return false
        // kotlin.Function* covers lambdas; kotlin.reflect.KFunction* / KSuspendFunction* cover callable refs
        // kotlin.coroutines.SuspendFunction* covers suspend lambdas
        return fqn.startsWith("kotlin.Function") ||
            fqn.startsWith("kotlin.reflect.KFunction") ||
            fqn.startsWith("kotlin.reflect.KSuspendFunction") ||
            fqn.startsWith("kotlin.coroutines.SuspendFunction")
    }

    // ── IR constant helpers ──────────────────────────────────────────────────

    private fun irString(value: String): IrExpression =
        IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.stringType, value)

    private fun irInt(value: Int): IrExpression =
        IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.intType, value)
}
