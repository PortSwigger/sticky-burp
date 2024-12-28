package com.ganggreentempertatum.stickyburp

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi

class StickyBurpExtension : BurpExtension {
    override fun initialize(api: MontoyaApi) {
        val variables = mutableListOf<StickyVariable>()
        val tab = StickyBurpTab(variables, api.persistence())
        val contextMenu = StickyBurpContextMenu(tab, api.logging())

        val keyboardHandler = KeyboardShortcutHandler(api)
        keyboardHandler.register()

        api.userInterface().apply {
            registerSuiteTab("StickyBurp", tab)
            registerContextMenuItemsProvider(contextMenu)
        }

        val httpHandler = StickyBurpHttpHandler(tab)
        api.http().registerHttpHandler(httpHandler)

        api.extension().setName("StickyBurp")
        api.logging().logToOutput("StickyBurp extension loaded successfully!\n\n\nBrought to you with love by GangGreentempertatum <3\n")

        api.extension().registerUnloadingHandler { keyboardHandler.unregister() }
    }
}
