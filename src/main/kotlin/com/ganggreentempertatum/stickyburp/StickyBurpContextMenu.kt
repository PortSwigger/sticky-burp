@file:Suppress("CyclomaticComplexMethod")

package com.ganggreentempertatum.stickyburp

import burp.api.montoya.ui.Selection
import burp.api.montoya.core.ByteArray
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.contextmenu.InvocationType
import burp.api.montoya.logging.Logging
import javax.swing.*
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.core.Range
import burp.api.montoya.http.HttpService

class StickyBurpContextMenu(private val tab: StickyBurpTab, private val logging: Logging) : ContextMenuItemsProvider {
    override fun provideMenuItems(event: ContextMenuEvent): List<JMenuItem> {
        val messageEditor = event.messageEditorRequestResponse()
        if (!messageEditor.isPresent) return emptyList()

        val editor = messageEditor.get()
        val selection = editor.selectionOffsets()
        if (!selection.isPresent) return emptyList()

        val selectedText = if (event.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST, InvocationType.MESSAGE_VIEWER_REQUEST)) {
            val request = editor.requestResponse().request()
            val range = selection.get()
            request.toByteArray().subArray(range).toString()
        } else {
            val response = editor.requestResponse().response()
            val range = selection.get()
            response.toByteArray().subArray(range).toString()
        }

        if (selectedText.isEmpty()) {
            logging.logToOutput("No text selected")
            return emptyList()
        }
        logging.logToOutput("Selected text: $selectedText")

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

            if (!trimmedName.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                JOptionPane.showMessageDialog(null,
                    "Sticky name can only contain letters, numbers, and underscores",
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

            val source = if (messageEditor.isPresent) {
                val reqRes = messageEditor.get().requestResponse()
                val tool = when (event.invocationType()) {
                    InvocationType.PROXY_HISTORY,
                    InvocationType.PROXY_INTERCEPT,
                    InvocationType.MESSAGE_VIEWER_REQUEST,
                    InvocationType.MESSAGE_VIEWER_RESPONSE -> "Proxy"
                    InvocationType.INTRUDER_PAYLOAD_POSITIONS,
                    InvocationType.INTRUDER_ATTACK_RESULTS -> "Intruder"
                    InvocationType.SCANNER_RESULTS -> "Scanner"
                    InvocationType.MESSAGE_EDITOR_REQUEST,
                    InvocationType.MESSAGE_EDITOR_RESPONSE -> "Repeater"
                    InvocationType.SITE_MAP_TREE,
                    InvocationType.SITE_MAP_TABLE -> "Site Map"
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
                    append("HTTP ${reqRes.request().method()} ${reqRes.request().url()}")
                    if (context.isNotEmpty()) {
                        append(" ($context)")
                    }
                }

                tab.addVariable(StickyVariable(
                    name = trimmedName,
                    value = selectedText,
                    sourceTab = tool,
                    source = source
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
                val range = selection.get()
                val reqRes = editor.requestResponse()

                if (event.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST, InvocationType.MESSAGE_VIEWER_REQUEST)) {
                    val request = reqRes.request()
                    val newRequest = HttpRequest.httpRequest(
                        request.httpService(),
                        request.toString().replaceRange(
                            range.startIndexInclusive(),
                            range.endIndexExclusive(),
                            variable.value
                        )
                    )
                    editor.setRequest(newRequest)
                } else {
                    val response = reqRes.response()
                    val newResponse = HttpResponse.httpResponse(
                        response.toString().replaceRange(
                            range.startIndexInclusive(),
                            range.endIndexExclusive(),
                            variable.value
                        )
                    )
                    editor.setResponse(newResponse)
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
                val tool = when (event.invocationType()) {
                    InvocationType.PROXY_HISTORY,
                    InvocationType.PROXY_INTERCEPT,
                    InvocationType.MESSAGE_VIEWER_REQUEST,
                    InvocationType.MESSAGE_VIEWER_RESPONSE -> "Proxy"
                    InvocationType.INTRUDER_PAYLOAD_POSITIONS,
                    InvocationType.INTRUDER_ATTACK_RESULTS -> "Intruder"
                    InvocationType.SCANNER_RESULTS -> "Scanner"
                    InvocationType.MESSAGE_EDITOR_REQUEST,
                    InvocationType.MESSAGE_EDITOR_RESPONSE -> "Repeater"
                    InvocationType.SITE_MAP_TREE,
                    InvocationType.SITE_MAP_TABLE -> "Site Map"
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
                    append("HTTP ${reqRes.request().method()} ${reqRes.request().url()}")
                    if (context.isNotEmpty()) {
                        append(" ($context)")
                    }
                }

                tab.addVariable(StickyVariable(
                    name = varName,
                    value = selectedText,
                    sourceTab = tool,
                    source = source
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
