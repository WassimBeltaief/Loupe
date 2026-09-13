package com.wassimbeltaief.loupe.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class LoupeIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val packageFilter: List<String> = emptyList(),
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(
            LoupeIrTransformer(pluginContext, messageCollector, packageFilter)
        )
    }
}
