package dev.loupeapp.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName

private val COMPOSABLE_FQN = FqName("androidx.compose.runtime.Composable")

class LoupeIrGenerationExtension(
    private val messageCollector: MessageCollector,
    // Test hook: called with the function name whenever a @Composable is found.
    // Null in production — MessageCollector output is the only side effect.
    internal val onComposableFound: ((String) -> Unit)? = null,
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.acceptVoid(LoupeIrVisitor(messageCollector, onComposableFound))
    }
}

internal class LoupeIrVisitor(
    private val messageCollector: MessageCollector,
    private val onComposableFound: ((String) -> Unit)? = null,
) : IrElementVisitorVoid {

    // Recurse into every IR node by default
    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        if (declaration.hasAnnotation(COMPOSABLE_FQN)) {
            val name = declaration.name.asString()
            messageCollector.report(
                CompilerMessageSeverity.LOGGING,
                "[Loupe] found @Composable: $name"
            )
            onComposableFound?.invoke(name)
        }
        // Always recurse — composables can be nested inside objects/classes
        declaration.acceptChildrenVoid(this)
    }
}
