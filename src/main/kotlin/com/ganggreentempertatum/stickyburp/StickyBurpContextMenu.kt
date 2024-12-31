@file:Suppress("CyclomaticComplexMethod")

package com.ganggreentempertatum.stickyburp

import burp.api.montoya.ui.Selection
import burp.api.montoya.core.ByteArray
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.contextmenu.InvocationType
import burp.api.montoya.core.ToolType
import burp.api.montoya.core.ToolSource
import burp.api.montoya.logging.Logging
import javax.swing.*
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.core.Range
import burp.api.montoya.http.HttpService

class StickyBurpContextMenu(private val tab: StickyBurpTab, private val logging: Logging) : ContextMenuItemsProvider {
    override fun provideMenuItems(event: ContextMenuEvent): List<JMenuItem> {
        // Try multiple methods to get selected content
        val selectedContent = when {
            // Standard message editor (what you currently use)
            event.messageEditorRequestResponse().isPresent -> {
                val editor = event.messageEditorRequestResponse().get()
                val selection = editor.selectionOffsets()
                if (!selection.isPresent) return emptyList()

                if (event.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST, InvocationType.MESSAGE_VIEWER_REQUEST)) {
                    val request = editor.requestResponse().request()
                    val range = selection.get()
                    request.toByteArray().subArray(range).toString()
                } else {
                    val response = editor.requestResponse().response()
                    val range = selection.get()
                    response.toByteArray().subArray(range).toString()
                }
            }

            // Text editor component
            event.textComponent().isPresent -> {
                val textComponent = event.textComponent().get()
                textComponent.selectedText()
            }

            // General selection
            event.selectionOffsets().isPresent -> {
                val selection = event.selectionOffsets().get()
                event.selectedData().subArray(selection).toString()
            }

            else -> null
        }

        if (selectedContent.isNullOrEmpty()) {
            logging.logToOutput("No text selected")
            return emptyList()
        }
        logging.logToOutput("Selected text: $selectedContent")

        val mainMenu = JMenu("StickyBurp")

        val addItem = JMenuItem("Add as New Sticky")
        addItem.addActionListener {
            val name = JOptionPane.showInputDialog("Enter name for this sticky:")
            if (name == null) return@addActionListener

            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) {
                JOptionPane.showMessageDialog(null,
                    "Sticky name cannot be empty",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }

            if (!trimmedName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                JOptionPane.showMessageDialog(null,
                    "Sticky name can only contain letters, numbers, underscores, and hyphens",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }

            if (tab.hasVariable(trimmedName)) {
                val update = JOptionPane.showConfirmDialog(
                    null,
                    "Sticky '$trimmedName' already exists. Update it?",
                    "Sticky Exists",
                    JOptionPane.YES_NO_OPTION
                )
                if (update != JOptionPane.YES_OPTION) return@addActionListener
            }

            val source = if (event.messageEditorRequestResponse().isPresent) {
                val reqRes = event.messageEditorRequestResponse().get().requestResponse()
                val tool = when (event.invocationType()) {
                    InvocationType.SITE_MAP_TREE,
                    InvocationType.SITE_MAP_TABLE -> "Target"
                    InvocationType.PROXY_HISTORY,
                    InvocationType.PROXY_INTERCEPT -> "Proxy"
                    InvocationType.MESSAGE_VIEWER_REQUEST,
                    InvocationType.MESSAGE_VIEWER_RESPONSE -> {
                        // Get tool type from the event using isFromTool
                        when {
                            event.isFromTool(ToolType.TARGET) -> "Target"
                            event.isFromTool(ToolType.LOGGER) -> "Logger"
                            else -> "Proxy"  // Default to Proxy for backwards compatibility
                        }
                    }
                    InvocationType.INTRUDER_PAYLOAD_POSITIONS,
                    InvocationType.INTRUDER_ATTACK_RESULTS -> "Intruder"
                    InvocationType.SCANNER_RESULTS -> "Scanner"
                    InvocationType.MESSAGE_EDITOR_REQUEST,
                    InvocationType.MESSAGE_EDITOR_RESPONSE -> "Repeater"
                    InvocationType.SEARCH_RESULTS -> "Search"
                    else -> "Other"
                }

                val context = when (event.invocationType()) {
                    InvocationType.PROXY_HISTORY -> "History"
                    InvocationType.PROXY_INTERCEPT -> "Intercept"
                    InvocationType.INTRUDER_PAYLOAD_POSITIONS -> "Payload Positions"
                    InvocationType.INTRUDER_ATTACK_RESULTS -> "Attack Results"
                    else -> ""
                }

                val source = buildString {
                    val request = reqRes.request()
                    val service = request.httpService()

                    append("HTTP ${request.method()} ${request.url()}")
                    append(" (${service.host()}:${service.port()})")
                    if (service.secure()) append(" [HTTPS]")

                    if (context.isNotEmpty()) {
                        append(" ($context)")
                    }

                    val notes = reqRes.annotations().notes()
                    if (notes != "") {
                        append(" - Note: $notes")
                    }
                }

                tab.addVariable(StickyVariable(
                    name = trimmedName,
                    value = selectedContent,
                    sourceTab = tool,
                    source = source,
                    timestamp = java.time.LocalDateTime.now().toString()
                ))
            } else {
                "Manual Selection"
            }
        }
        mainMenu.add(addItem)

        val replaceMenu = JMenu("Replace with Sticky")
        tab.getVariables().forEach { variable ->
            val replaceItem = JMenuItem("${variable.name} (${variable.value})")
            replaceItem.addActionListener {
                val selection = event.selectionOffsets().get()
                val reqRes = event.messageEditorRequestResponse().get().requestResponse()

                if (event.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST, InvocationType.MESSAGE_VIEWER_REQUEST)) {
                    val request = reqRes.request()
                    val newRequest = HttpRequest.httpRequest(
                        request.httpService(),
                        request.toString().replaceRange(
                            selection.startIndexInclusive(),
                            selection.endIndexExclusive(),
                            variable.value
                        )
                    )
                    event.messageEditorRequestResponse().get().setRequest(newRequest)
                } else {
                    val response = reqRes.response()
                    val newResponse = HttpResponse.httpResponse(
                        response.toString().replaceRange(
                            selection.startIndexInclusive(),
                            selection.endIndexExclusive(),
                            variable.value
                        )
                    )
                    event.messageEditorRequestResponse().get().setResponse(newResponse)
                }
            }
            replaceMenu.add(replaceItem)
        }
        mainMenu.add(replaceMenu)

        val updateMenu = JMenu("Update Existing Sticky")
        tab.getVariableNames().forEach { varName ->
            val updateItem = JMenuItem(varName)
            updateItem.addActionListener {
                val reqRes = event.messageEditorRequestResponse().get().requestResponse()
                val existingVar = tab.getVariables().find { it.name == varName }
                    ?: return@addActionListener

                val tool = when (event.invocationType()) {
                    InvocationType.SITE_MAP_TREE,
                    InvocationType.SITE_MAP_TABLE -> "Target"
                    InvocationType.PROXY_HISTORY,
                    InvocationType.PROXY_INTERCEPT -> "Proxy"
                    InvocationType.MESSAGE_VIEWER_REQUEST,
                    InvocationType.MESSAGE_VIEWER_RESPONSE -> {
                        // Get tool type from the event using isFromTool
                        when {
                            event.isFromTool(ToolType.TARGET) -> "Target"
                            event.isFromTool(ToolType.LOGGER) -> "Logger"
                            else -> "Proxy"  // Default to Proxy for backwards compatibility
                        }
                    }
                    InvocationType.INTRUDER_PAYLOAD_POSITIONS,
                    InvocationType.INTRUDER_ATTACK_RESULTS -> "Intruder"
                    InvocationType.SCANNER_RESULTS -> "Scanner"
                    InvocationType.MESSAGE_EDITOR_REQUEST,
                    InvocationType.MESSAGE_EDITOR_RESPONSE -> "Repeater"
                    InvocationType.SEARCH_RESULTS -> "Search"
                    else -> "Other"
                }

                val context = when (event.invocationType()) {
                    InvocationType.PROXY_HISTORY -> "History"
                    InvocationType.PROXY_INTERCEPT -> "Intercept"
                    InvocationType.INTRUDER_PAYLOAD_POSITIONS -> "Payload Positions"
                    InvocationType.INTRUDER_ATTACK_RESULTS -> "Attack Results"
                    else -> ""
                }

                val source = buildString {
                    val request = reqRes.request()
                    val service = request.httpService()

                    append("HTTP ${request.method()} ${request.url()}")
                    append(" (${service.host()}:${service.port()})")
                    if (service.secure()) append(" [HTTPS]")

                    if (context.isNotEmpty()) {
                        append(" ($context)")
                    }

                    val notes = reqRes.annotations().notes()
                    if (notes != "") {
                        append(" - Note: $notes")
                    }
                }

                tab.addVariable(existingVar.copy(
                    value = selectedContent,
                    sourceTab = tool,
                    source = source,
                    timestamp = java.time.LocalDateTime.now().toString()
                ))
            }
            updateMenu.add(updateItem)
        }

        if (tab.getVariableNames().isNotEmpty()) {
            mainMenu.addSeparator()
            mainMenu.add(updateMenu)
        }

        return listOf(mainMenu)
    }
}
