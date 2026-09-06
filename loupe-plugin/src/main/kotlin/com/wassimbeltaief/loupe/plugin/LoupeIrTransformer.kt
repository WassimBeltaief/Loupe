package com.wassimbeltaief.loupe.plugin

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

/**
 * Transforms every non-inline @Composable function in the IR tree by prepending
 * a LoupeRuntime.record(...) call as the first statement in its body.
 * The user's code is never modified — only a single call is inserted before it.
 */
internal class LoupeIrTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    internal val onComposableFound: ((String) -> Unit)? = null,
) : IrElementTransformerVoid() {

    private val irBuiltIns = pluginContext.irBuiltIns
    private val anyNType: IrType = irBuiltIns.anyType.makeNullable()

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        // Depth-first: transform nested composables before the enclosing one
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
        val runtimeClass = pluginContext.referenceClass(LOUPE_RUNTIME_CLASS_ID) ?: run {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "[Loupe] LoupeRuntime not found on classpath — skipping ${declaration.name}",
            )
            return null
        }

        val recordFn = pluginContext.referenceFunctions(
            CallableId(LOUPE_RUNTIME_CLASS_ID, Name.identifier("record"))
        ).singleOrNull { it.owner.valueParameters.size == 4 } ?: run {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "[Loupe] LoupeRuntime.record(4 params) not found — skipping ${declaration.name}",
            )
            return null
        }

        val file = declaration.file.fileEntry.name.substringAfterLast('/')
        val line = declaration.file.fileEntry.getLineNumber(declaration.startOffset) + 1
        val paramsArray = buildParamsArray(declaration) ?: return null

        val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
        return builder.irCall(recordFn).also { call ->
            call.dispatchReceiver = builder.irGetObjectValue(runtimeClass.owner.defaultType, runtimeClass)
            call.putValueArgument(0, irString(declaration.name.asString()))
            call.putValueArgument(1, irString(file))
            call.putValueArgument(2, irInt(line))
            call.putValueArgument(3, paramsArray)
        }
    }

    // ── Params array: arrayOf("name" to value, ...) ─────────────────────────

    private fun buildParamsArray(declaration: IrSimpleFunction): IrExpression? {
        val pairClass = pluginContext.referenceClass(PAIR_CLASS_ID) ?: return null
        val pairCtor = pairClass.owner.constructors
            .singleOrNull { it.valueParameters.size == 2 } ?: return null

        // arrayOf is vararg — identify by having exactly one vararg parameter
        val arrayOfFn = pluginContext.referenceFunctions(
            CallableId(FqName("kotlin"), Name.identifier("arrayOf"))
        ).singleOrNull { fn ->
            fn.owner.valueParameters.size == 1 &&
                fn.owner.valueParameters[0].varargElementType != null
        } ?: return null

        // Skip $composer, $changed, $default injected by the Compose compiler
        val userParams = declaration.valueParameters
            .filter { !it.name.asString().startsWith("$") }

        val pairType = pairClass.typeWith(irBuiltIns.stringType, anyNType)
        val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)

        val pairs = userParams.map { param ->
            val nameArg = irString(param.name.asString())

            // Lambdas: capture identity hash code — lambda instances are never equal across calls
            val rawValue: IrExpression = if (param.type.isFunctionLikeType()) {
                buildIdentityHashCode(param, builder) ?: return null
            } else {
                builder.irGet(param)
            }

            // Upcast to Any? so Pair<String, Any?> constructor argument resolves cleanly
            val valueArg = builder.irImplicitCast(rawValue, anyNType)

            builder.irCallConstructor(pairCtor.symbol, listOf(irBuiltIns.stringType, anyNType)).also { pair ->
                pair.putValueArgument(0, nameArg)
                pair.putValueArgument(1, valueArg)
            }
        }

        val arrayOfPairType = irBuiltIns.arrayClass.typeWith(pairType)
        return builder.irCall(arrayOfFn, arrayOfPairType).also { call ->
            call.putTypeArgument(0, pairType)
            call.putValueArgument(0, builder.irVararg(pairType, pairs))
        }
    }

    // ── System.identityHashCode(param) ───────────────────────────────────────

    private fun buildIdentityHashCode(
        param: org.jetbrains.kotlin.ir.declarations.IrValueParameter,
        builder: DeclarationIrBuilder,
    ): IrExpression? {
        val systemClass = pluginContext.referenceClass(SYSTEM_CLASS_ID) ?: return null
        val identityHashCodeFn = systemClass.owner.functions
            .singleOrNull { it.name.asString() == "identityHashCode" } ?: return null

        return builder.irCall(identityHashCodeFn.symbol).also { call ->
            call.putValueArgument(0, builder.irGet(param))
        }
    }

    // ── Type check ───────────────────────────────────────────────────────────

    private fun IrType.isFunctionLikeType(): Boolean {
        if (this !is IrSimpleType) return false
        val cls = classifier as? IrClassSymbol ?: return false
        val fqn = cls.owner.fqNameWhenAvailable?.asString() ?: return false
        return fqn.startsWith("kotlin.Function") ||
            fqn.startsWith("kotlin.coroutines.SuspendFunction") ||
            fqn.startsWith("kotlin.jvm.functions.Function")
    }

    // ── IR constant helpers ──────────────────────────────────────────────────

    private fun irString(value: String): IrExpression =
        IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.stringType, value)

    private fun irInt(value: Int): IrExpression =
        IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.intType, value)
}
