package com.ganggreentempertatum.stickyburp

import burp.api.montoya.MontoyaApi
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

class KeyboardShortcutHandler(private val api: MontoyaApi, private val tab: StickyBurpTab) {
    private var keyEventListener: AWTEventListener? = null
    private var isCleanedUp = false

    fun register() {
        try {
            keyEventListener = AWTEventListener { event ->
                if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED) {
                    val isMac = System.getProperty("os.name").lowercase().contains("mac")
                    val modifiersEx = event.modifiersEx

                    val isCommandOrControl = if (isMac) {
                        (modifiersEx and InputEvent.META_DOWN_MASK) != 0
                    } else {
                        (modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
                    }

                    val isShiftDown = (modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0

                    when {
                        // CMD/CTRL + Shift + S handler
                        isCommandOrControl && isShiftDown && event.keyCode == KeyEvent.VK_S -> {
                            api.logging().logToOutput("""
                                Switch Tab Hotkey detected!
                                Platform: ${if (isMac) "Mac" else "Windows/Linux"}
                                Modifiers: ${getModifiersText(modifiersEx)}
                                Key: ${KeyEvent.getKeyText(event.keyCode)}
                            """.trimIndent())

                            event.consume()
                            SwingUtilities.invokeLater {
                                findAndSelectStickyBurpTab()
                            }
                        }

                        // CMD/CTRL + Shift + A handler
                        isCommandOrControl && isShiftDown && event.keyCode == KeyEvent.VK_A -> {
                            api.logging().logToOutput("""
                                Add Variable Hotkey detected!
                                Platform: ${if (isMac) "Mac" else "Windows/Linux"}
                                Modifiers: ${getModifiersText(modifiersEx)}
                                Key: ${KeyEvent.getKeyText(event.keyCode)}
                            """.trimIndent())

                            event.consume()
                            SwingUtilities.invokeLater {
                                tab.addNewVariable()
                            }
                        }
                    }
                }
            }

            Toolkit.getDefaultToolkit().addAWTEventListener(
                keyEventListener,
                AWTEvent.KEY_EVENT_MASK
            )
        } catch (e: Exception) {
            api.logging().logToError("Failed to register keyboard handler: ${e.message}")
        }
    }

    private fun getModifiersText(modifiers: Int): String {
        val text = StringBuilder()
        if ((modifiers and InputEvent.SHIFT_DOWN_MASK) != 0) text.append("Shift+")
        if ((modifiers and InputEvent.CTRL_DOWN_MASK) != 0) text.append("Ctrl+")
        if ((modifiers and InputEvent.META_DOWN_MASK) != 0) text.append("Meta+")
        if ((modifiers and InputEvent.ALT_DOWN_MASK) != 0) text.append("Alt+")
        return if (text.isEmpty()) "none" else text.substring(0, text.length - 1)
    }

    fun unregister() {
        if (!isCleanedUp) {
            keyEventListener?.let { listener ->
                Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            }
            keyEventListener = null
            isCleanedUp = true
        }
    }

    private fun findAndSelectStickyBurpTab() {
        getMainTabbedPane()?.let { pane ->
            selectStickyBurpTab(pane)
        }
    }

    private fun selectStickyBurpTab(pane: JTabbedPane) {
        for (i in 0 until pane.tabCount) {
            if (pane.getTitleAt(i).equals("StickyBurp", ignoreCase = true)) {
                pane.selectedIndex = i
                api.logging().logToOutput("Successfully switched to StickyBurp tab")
                return
            }
        }
        api.logging().logToOutput("Could not find StickyBurp tab")
    }

    private fun getMainTabbedPane(): JTabbedPane? {
        for (window in Window.getWindows()) {
            if (window is JFrame) {
                findRootTabbedPane(window)?.let { return it }
            }
        }
        return null
    }

    private fun findRootTabbedPane(container: Component): JTabbedPane? {
        if (container is JTabbedPane && isMainTabbedPane(container)) {
            return container
        }

        if (container is Container) {
            for (component in container.components) {
                findRootTabbedPane(component)?.let { return it }
            }
        }
        return null
    }

    private fun isMainTabbedPane(tabbedPane: JTabbedPane): Boolean {
        val tabTitles = (0 until tabbedPane.tabCount).map { tabbedPane.getTitleAt(it) }
        return tabTitles.any { it.equals("Repeater", ignoreCase = true) } &&
            tabTitles.any { it.equals("Proxy", ignoreCase = true) }
    }
}
