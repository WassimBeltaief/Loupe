package com.wassimbeltaief.loupe.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Runs the Loupe IR transformer over each compiled module.
 *
 * @param messageCollector used to report plugin warnings
 * @param packageFilter packages to instrument. Empty means no filter.
 */
class LoupeIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val packageFilter: List<String> = emptyList(),
) : IrGenerationExtension {

    /** Compiler callback. It visits the whole module and instruments what it finds. */
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(
            LoupeIrTransformer(pluginContext, messageCollector, packageFilter)
        )
    }
}
