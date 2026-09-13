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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrLocalDelegatedProperty
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
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
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val COMPOSABLE_FQN = FqName("androidx.compose.runtime.Composable")
private val LOUPE_IGNORE_FQN = FqName("com.wassimbeltaief.loupe.runtime.LoupeIgnore")
private val LOUPE_REDACT_FQN = FqName("com.wassimbeltaief.loupe.runtime.LoupeRedact")
private val LOUPE_RUNTIME_CLASS_ID = ClassId.topLevel(FqName("com.wassimbeltaief.loupe.runtime.LoupeRuntime"))
private val PAIR_CLASS_ID = ClassId(FqName("kotlin"), Name.identifier("Pair"))
private val SYSTEM_CLASS_ID = ClassId.fromString("java/lang/System")
private val LAMBDA_REF_CLASS_ID = ClassId(FqName("com.wassimbeltaief.loupe.runtime.model"), Name.identifier("LambdaRef"))
private val MODIFIER_FQN = FqName("androidx.compose.ui.Modifier")

/** Compose injects the Composer parameter under this name. */
private const val COMPOSER_PARAM_NAME = "\$composer"

/**
 * Instruments composables in the Kotlin IR.
 *
 * For every `@Composable` that passes [shouldInstrument], it inserts three calls:
 * 1. `LoupeRuntime.record(...)` at the start, with the parameters of the call.
 * 2. `LoupeRuntime.trackState(...)` after each local `MutableState`, so a state
 *    change such as `counter++` is visible in the drill-down.
 * 3. `LoupeRuntime.recordEnd(...)` in a `finally` block, to measure the duration.
 *
 * The user's own statements are never changed. They are only wrapped.
 *
 * Two caches matter here. The symbol lookups are `lazy`, so they are resolved
 * once per compilation. And `record()` is inserted inside the executed branch of
 * the Compose restart group, so skipped composables are not counted.
 */
internal class LoupeIrTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val packageFilter: List<String> = emptyList(),
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
            fn.owner.valueParameters.size == 6 &&
                fn.owner.valueParameters[0].name.asString() == "key"
        }.also { fn ->
            if (fn == null) warn("LoupeRuntime.record(key,file,line,params,instance,instanceTag) not found — no composables will be instrumented")
        }
    }

    // Duration measurement counterpart to record(). It is emitted in a finally block.
    private val recordEndFn by lazy {
        pluginContext.referenceFunctions(
            CallableId(LOUPE_RUNTIME_CLASS_ID, Name.identifier("recordEnd"))
        ).firstOrNull { fn ->
            fn.owner.valueParameters.size == 3 &&
                fn.owner.valueParameters[0].name.asString() == "key"
        }.also { fn ->
            if (fn == null) warn("LoupeRuntime.recordEnd(key,instance,instanceTag) not found — duration measurement disabled")
        }
    }

    // Local MutableState capture: emitted right after a state variable is declared
    private val trackStateFn by lazy {
        pluginContext.referenceFunctions(
            CallableId(LOUPE_RUNTIME_CLASS_ID, Name.identifier("trackState"))
        ).firstOrNull { fn ->
            fn.owner.valueParameters.size == 5 &&
                fn.owner.valueParameters[0].name.asString() == "key"
        }.also { fn ->
            if (fn == null) warn("LoupeRuntime.trackState(key,instance,name,value,instanceTag) not found — local state tracking disabled")
        }
    }

    // Extracts the first testTag string from a Modifier chain at runtime.
    private val extractTestTagFn by lazy {
        pluginContext.referenceFunctions(
            CallableId(LOUPE_RUNTIME_CLASS_ID, Name.identifier("extractTestTag"))
        ).firstOrNull { fn ->
            fn.owner.valueParameters.size == 1 &&
                fn.owner.valueParameters[0].name.asString() == "modifier"
        }
    }

    private val stateClass by lazy {
        pluginContext.referenceClass(ClassId.topLevel(FqName("androidx.compose.runtime.State")))
    }

    private val stateValueGetter by lazy {
        stateClass?.owner?.properties
            ?.firstOrNull { it.name.asString() == "value" }
            ?.getter
            .also { getter ->
                if (getter == null) warn("State.value not found — local state tracking disabled")
            }
    }

    // Per-instance identity: Compose's compound key hash, unique per call-site
    // instance (documented caveat: unkeyed loops can collide → aggregated counts)
    private val composerClass by lazy {
        pluginContext.referenceClass(ClassId.topLevel(FqName("androidx.compose.runtime.Composer")))
    }

    private val compoundKeyHashGetter by lazy {
        composerClass?.owner?.properties
            ?.firstOrNull { it.name.asString() == "compoundKeyHash" }
            ?.getter
            .also { getter ->
                if (getter == null) warn("Composer.compoundKeyHash not found — per-instance tracking disabled (falls back to aggregate)")
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

    private val lambdaRefClass by lazy {
        pluginContext.referenceClass(LAMBDA_REF_CLASS_ID).also { cls ->
            if (cls == null) warn("LambdaRef not found on classpath — lambda params will be recorded as raw Int")
        }
    }

    private val lambdaRefCtor by lazy {
        lambdaRefClass?.owner?.constructors?.singleOrNull { it.valueParameters.size == 1 }
    }

    private fun warn(msg: String) =
        messageCollector.report(CompilerMessageSeverity.WARNING, "[Loupe] $msg")

    // ── Visitor ──────────────────────────────────────────────────────────────

    /**
     * Visits every function in the module. Only eligible composables are changed;
     * everything else is left as it is.
     */
    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        declaration.transformChildrenVoid(this)

        if (!shouldInstrument(declaration)) return declaration

        val body = declaration.body as? IrBlockBody ?: return declaration
        val name = declaration.name.asString()

        val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
        val modifierParam = findModifierParam(declaration)
        val tagVar: IrVariable? = modifierParam?.let { buildTagVar(declaration, it, builder) }

        val recordCall = buildRecordCall(declaration, tagVar) ?: return declaration

        // This transform runs AFTER the Compose compiler's IR lowering, so the
        // injected calls appear after startRestartGroup in the bytecode.
        // A restartable composable has the shape
        //   [ startRestartGroup(...), ..., if (cond) { <real body> } else { skipToGroupEnd() }, ... ]
        // record() must only run when the body really executes, inside the true
        // branch, otherwise skipped invocations would be counted as recompositions.
        // When the shape is not found (non-restartable or unusual composables) the
        // whole body is used, so it counts invocations and never crashes.
        val container = findExecutedBodyContainer(body, name) ?: body.statements

        val originalStatements = container.toList()
        container.clear()
        if (tagVar != null) container += tagVar
        container += recordCall

        val file = File(declaration.file.fileEntry.name).name
        val instrumentedStatements =
            injectStateTracking(originalStatements, declaration, recordKey(declaration, file), builder, tagVar)

        val recordEndCall = buildRecordEndCall(declaration, tagVar)
        if (recordEndCall != null) {
            // Wrap the original body in try/finally, so the duration is recorded on
            // every exit path, including early returns and exceptions. The body is
            // Unit-typed, so the semantics do not change.
            val tryBlock = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.unitType).apply {
                statements += instrumentedStatements
            }
            val finallyBlock = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.unitType).apply {
                statements += recordEndCall
            }
            container += IrTryImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.unitType,
                tryResult = tryBlock,
                catches = emptyList(),
                finallyExpression = finallyBlock,
            )
        } else {
            container += instrumentedStatements
        }

        messageCollector.report(CompilerMessageSeverity.LOGGING, "[Loupe] instrumenting: $name")
        return declaration
    }

    /**
     * Finds the statement list that actually runs, inside the restart group.
     *
     * A restartable composable is lowered to something like
     * `if (...) { body } else { skipToGroupEnd() }`. Injecting into the `if`
     * branch means a skipped composable is not recorded. Returns null when the
     * shape is not found, for example for a non-restartable composable.
     */
    private fun findExecutedBodyContainer(body: IrBlockBody, name: String): MutableList<IrStatement>? {
        val skipCheck = body.statements.filterIsInstance<IrWhen>().firstOrNull { whenExpr ->
            whenExpr.branches.getOrNull(1)?.result?.containsSkipToGroupEnd() == true
        } ?: return null.also {
            messageCollector.report(CompilerMessageSeverity.LOGGING, "[Loupe] $name: no restart-group pattern — recording invocations")
        }
        return (skipCheck.branches.firstOrNull()?.result as? IrBlock)?.statements
    }

    // True when this expression calls skipToGroupEnd, directly or inside a block.
    private fun IrExpression.containsSkipToGroupEnd(): Boolean = when (this) {
        is IrCall -> symbol.owner.name.asString() == "skipToGroupEnd"
        is IrBlock -> statements.any { (it as? IrExpression)?.containsSkipToGroupEnd() == true }
        else -> false
    }

    /** Decides whether this function should be instrumented. See the class doc for the rules. */
    private fun shouldInstrument(declaration: IrSimpleFunction): Boolean {
        if (!declaration.hasAnnotation(COMPOSABLE_FQN)) return false
        if (declaration.isInline) return false        // inline has no discrete body post-inlining
        if (declaration.name.isSpecial) return false  // <anonymous> lambdas — no stable key
        if (declaration.body == null) return false    // abstract / expect
        if (declaration.hasAnnotation(LOUPE_IGNORE_FQN)) return false
        if (packageFilter.isNotEmpty()) {
            val pkg = declaration.fqNameWhenAvailable?.parent()?.asString() ?: return false
            if (packageFilter.none { pkg.startsWith(it) }) return false
        }
        return true
    }

    // ── Record call builder ──────────────────────────────────────────────────

    /** Builds the `LoupeRuntime.record(...)` call for one composable. */
    private fun buildRecordCall(declaration: IrSimpleFunction, tagVar: IrVariable? = null): IrExpression? {
        val rc = runtimeClass ?: return null
        val fn = recordFn ?: return null

        val file = File(declaration.file.fileEntry.name).name
        val key = recordKey(declaration, file)
        // Any negative offset (UNDEFINED_OFFSET = -1, SYNTHETIC_OFFSET = Int.MIN_VALUE/2, …) means no real source location.
        val line = if (declaration.startOffset >= 0) {
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
            call.putValueArgument(0, irString(key))
            call.putValueArgument(1, irString(file))
            call.putValueArgument(2, irInt(line))
            call.putValueArgument(3, paramsArray)
            call.putValueArgument(4, buildInstanceArg(declaration, builder))
            call.putValueArgument(5, if (tagVar != null) builder.irGet(tagVar) else irNull())
        }
    }

    /**
     * Builds the key of a composable as `FileName.Function`.
     *
     * The file prefix keeps two composables with the same name in different files
     * apart. It is dropped when the file and the function have the same name, so
     * `ProductCard.kt` stays `ProductCard` and the overlay stays readable.
     */
    private fun recordKey(declaration: IrSimpleFunction, file: String): String {
        val name = declaration.name.asString()
        val fileBase = file.removeSuffix(".kt")
        return if (fileBase == name) name else "$fileBase.$name"
    }

    // ── recordEnd(key) call — injected into the finally block ────────────────

    /** Builds the `LoupeRuntime.recordEnd(...)` call that closes the duration measurement. */
    private fun buildRecordEndCall(declaration: IrSimpleFunction, tagVar: IrVariable? = null): IrExpression? {
        val rc = runtimeClass ?: return null
        val fn = recordEndFn ?: return null
        val file = File(declaration.file.fileEntry.name).name
        val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
        return builder.irCall(fn).also { call ->
            call.dispatchReceiver = builder.irGetObjectValue(rc.owner.defaultType, rc)
            call.putValueArgument(0, irString(recordKey(declaration, file)))
            call.putValueArgument(1, buildInstanceArg(declaration, builder))
            call.putValueArgument(2, if (tagVar != null) builder.irGet(tagVar) else irNull())
        }
    }

    /**
     * Per-instance discriminator from `$composer.compoundKeyHash`. Falls back to 0
     * (aggregate) when the Compose runtime symbol is unavailable.
     */
    private fun buildInstanceArg(
        declaration: IrSimpleFunction,
        builder: DeclarationIrBuilder,
    ): IrExpression {
        val getter = compoundKeyHashGetter ?: return irInt(0)
        val composer = declaration.valueParameters
            .firstOrNull { it.name.asString() == COMPOSER_PARAM_NAME }
            ?: return irInt(0)
        return builder.irCall(getter.symbol).also { call ->
            call.dispatchReceiver = builder.irGet(composer)
        }
    }

    // ── Local MutableState capture ───────────────────────────────────────────

    /**
     * Emits `trackState(key, instance, name, value)` right after each local
     * `MutableState`/`State` declaration in the executed body, so reads like
     * `counter++` are attributed to the composable's own state rather than
     * surfacing only as a forced recomposition.
     */
    private fun injectStateTracking(
        statements: List<IrStatement>,
        declaration: IrSimpleFunction,
        key: String,
        builder: DeclarationIrBuilder,
        tagVar: IrVariable? = null,
    ): List<IrStatement> {
        if (trackStateFn == null || stateValueGetter == null) return statements
        val result = ArrayList<IrStatement>(statements.size * 2)
        for (statement in statements) {
            result += statement
            when (statement) {
                is IrVariable -> {
                    if (!statement.type.isStateType()) continue
                    val name = statement.name.asString().removeSuffix("\$delegate")
                    buildTrackStateCall(declaration, key, name, statement, builder, tagVar)?.let { result += it }
                }
                // `var counter by remember { mutableStateOf(0) }` lowers to a delegated
                // local property whose backing `delegate` variable holds the MutableState.
                is IrLocalDelegatedProperty -> {
                    val delegate = statement.delegate
                    if (!delegate.type.isStateType()) continue
                    buildTrackStateCall(declaration, key, statement.name.asString(), delegate, builder, tagVar)?.let { result += it }
                }
            }
        }
        return result
    }

    /** Builds the `trackState(key, instance, name, value, instanceTag)` call for one state holder. */
    private fun buildTrackStateCall(
        declaration: IrSimpleFunction,
        key: String,
        name: String,
        holder: IrVariable,
        builder: DeclarationIrBuilder,
        tagVar: IrVariable? = null,
    ): IrExpression? {
        val rc = runtimeClass ?: return null
        val fn = trackStateFn ?: return null
        val getter = stateValueGetter ?: return null
        val value = builder.irCall(getter.symbol).also { call ->
            call.dispatchReceiver = builder.irGet(holder)
        }
        return builder.irCall(fn).also { call ->
            call.dispatchReceiver = builder.irGetObjectValue(rc.owner.defaultType, rc)
            call.putValueArgument(0, irString(key))
            call.putValueArgument(1, buildInstanceArg(declaration, builder))
            call.putValueArgument(2, irString(name))
            call.putValueArgument(3, builder.irImplicitCast(value, anyNType))
            call.putValueArgument(4, if (tagVar != null) builder.irGet(tagVar) else irNull())
        }
    }

    /** True for `androidx.compose.runtime.MutableState` and `State`. */
    private fun IrType.isStateType(): Boolean {
        if (this !is IrSimpleType) return false
        val owner = classifier.owner as? IrClass ?: return false
        val fqn = owner.fqNameWhenAvailable?.asString() ?: return false
        return fqn == "androidx.compose.runtime.MutableState" || fqn == "androidx.compose.runtime.State"
    }

    // Called when buildParamsArray fails (pairCtor missing, etc.) but arrayOfFn is available.
    // Note: if arrayOfFn itself is null, this also returns null and the composable is dropped —
    // there is no way to build any array expression without it.
    private fun buildEmptyParamsArray(builder: DeclarationIrBuilder): IrExpression? {
        val aoFn = arrayOfFn ?: return null
        val pc = pairClass ?: return null  // can't produce a correctly-typed array without Pair
        val elementType = pc.typeWith(irBuiltIns.stringType, anyNType)
        val arrayType = irBuiltIns.arrayClass.typeWith(elementType)
        return builder.irCall(aoFn, arrayType).also { call ->
            call.putTypeArgument(0, elementType)
            call.putValueArgument(0, builder.irVararg(elementType, emptyList()))
        }
    }

    // ── Params array: arrayOf("name" to value, ...) ─────────────────────────

    /**
     * Builds the `arrayOf("name" to value, ...)` argument for `record()`.
     *
     * Redacted types are stored as "[redacted]", function types as their identity
     * hash, and everything else as itself. Compose's own `$composer`, `$changed`
     * and `$default` parameters are skipped.
     */
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
            // A redacted type is never captured. Function values are captured by
            // identity, because two lambdas are never equal by value.
            val rawValue: IrExpression = when {
                param.type.hasRedactAnnotation() -> irString("[redacted]")
                param.type.isFunctionLikeType() -> buildIdentityHashCode(param, builder) ?: builder.irGet(param)
                else -> builder.irGet(param)
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

    // ── LambdaRef(System.identityHashCode(param)) ────────────────────────────

    /** Wraps a function value in `LambdaRef(identityHashCode(value))`. */
    private fun buildIdentityHashCode(param: IrValueParameter, builder: DeclarationIrBuilder): IrExpression? {
        val fn = identityHashCodeFn ?: return null
        val hashCall = builder.irCall(fn.symbol).also { call ->
            call.putValueArgument(0, builder.irGet(param))
        }
        val ctor = lambdaRefCtor ?: return hashCall  // fall back to raw Int if LambdaRef unavailable
        return builder.irCallConstructor(ctor.symbol, emptyList()).also { call ->
            call.putValueArgument(0, hashCall)
        }
    }

    // ── Type check ───────────────────────────────────────────────────────────

    /** True when the parameter type is annotated `@LoupeRedact`. */
    private fun IrType.hasRedactAnnotation(): Boolean {
        val owner = (this as? IrSimpleType)?.classifier?.owner
        return (owner as? IrAnnotationContainer)?.hasAnnotation(LOUPE_REDACT_FQN) == true
    }

    /**
     * True for function types: lambdas, callable references and suspend lambdas.
     * A type parameter is also checked, through its upper bounds.
     */
    private fun IrType.isFunctionLikeType(): Boolean {
        if (this !is IrSimpleType) return false
        return when (val sym = classifier) {
            is IrClassSymbol -> {
                val fqn = sym.owner.fqNameWhenAvailable?.asString() ?: return false
                // kotlin.Function* covers lambdas; kotlin.reflect.KFunction* / KSuspendFunction* cover callable refs
                // kotlin.coroutines.SuspendFunction* covers suspend lambdas
                fqn.startsWith("kotlin.Function") ||
                    fqn.startsWith("kotlin.reflect.KFunction") ||
                    fqn.startsWith("kotlin.reflect.KSuspendFunction") ||
                    fqn.startsWith("kotlin.coroutines.SuspendFunction")
            }
            // <T : () -> Unit> — T's classifier is a type parameter; check its upper bounds
            is IrTypeParameterSymbol -> sym.owner.superTypes.any { it.isFunctionLikeType() }
            else -> false
        }
    }

    // ── Modifier / testTag helpers ───────────────────────────────────────────

    private fun findModifierParam(declaration: IrSimpleFunction): IrValueParameter? =
        declaration.valueParameters.firstOrNull { param ->
            !param.name.asString().startsWith("$") &&
                param.name.asString() == "modifier" &&
                isModifierType(param.type)
        }

    private fun isModifierType(type: IrType): Boolean {
        if (type !is IrSimpleType) return false
        val cls = (type.classifier as? IrClassSymbol)?.owner ?: return false
        return cls.fqNameWhenAvailable == MODIFIER_FQN
    }

    private fun buildTagVar(
        declaration: IrSimpleFunction,
        modifierParam: IrValueParameter,
        builder: DeclarationIrBuilder,
    ): IrVariable? {
        val fn = extractTestTagFn ?: return null
        val rc = runtimeClass ?: return null
        val call = builder.irCall(fn).also { c ->
            c.dispatchReceiver = builder.irGetObjectValue(rc.owner.defaultType, rc)
            c.putValueArgument(0, builder.irGet(modifierParam))
        }
        return IrVariableImpl(
            startOffset = UNDEFINED_OFFSET, endOffset = UNDEFINED_OFFSET,
            origin = IrDeclarationOrigin.DEFINED,
            symbol = IrVariableSymbolImpl(),
            name = Name.identifier("_loupeTag"),
            type = irBuiltIns.stringType.makeNullable(),
            isVar = false, isConst = false, isLateinit = false,
        ).also { it.parent = declaration; it.initializer = call }
    }

    // ── IR constant helpers ──────────────────────────────────────────────────

    /** Builds a string constant in the IR. */
    private fun irString(value: String): IrExpression =
        IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.stringType, value)

    /** Builds an int constant in the IR. */
    private fun irInt(value: Int): IrExpression =
        IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.intType, value)

    /** Builds a null literal of type `Nothing?` for nullable String parameters. */
    private fun irNull(): IrExpression =
        IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingNType)
}
