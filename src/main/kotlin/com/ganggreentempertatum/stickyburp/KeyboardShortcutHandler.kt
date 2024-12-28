package com.ganggreentempertatum.stickyburp

import burp.api.montoya.MontoyaApi
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

class KeyboardShortcutHandler(
    private val api: MontoyaApi
) {
    private var keyEventListener: AWTEventListener? = null
    private var isCleanedUp = false

    fun register() {
        try {
            keyEventListener = AWTEventListener { event ->
                if (event is KeyEvent) {
                    handleKeyEvent(event)
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

    private fun handleKeyEvent(event: KeyEvent) {
        if (isCtrlShiftSPressed(event)) {
            api.logging().logToOutput("!!! CTRL+SHIFT+S Detected !!!")
            event.consume()
            SwingUtilities.invokeLater {
                findAndSelectStickyBurpTab()
            }
        }
    }

    private fun isCtrlShiftSPressed(event: KeyEvent): Boolean =
        !event.isConsumed &&
            event.id == KeyEvent.KEY_PRESSED &&
            event.keyCode == KeyEvent.VK_S &&
            event.isControlDown &&
            event.isShiftDown &&
            !event.isAltDown

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

    private fun getMainTabbedPane(): JTabbedPane? {
        for (window in Window.getWindows()) {
            if (window is JFrame) {
                findRootTabbedPane(window)?.let { return it }
            }
        }
        return null
    }

    private fun selectStickyBurpTab(pane: JTabbedPane) {
        for (i in 0 until pane.tabCount) {
            if (pane.getTitleAt(i).equals("StickyBurp", ignoreCase = true)) {
                pane.selectedIndex = i
                return
            }
        }
    }

    private fun findRootTabbedPane(container: Component): JTabbedPane? {
        if (container is JTabbedPane && isMainTabbedPane(container)) {
            return container
        }

        if (container is java.awt.Container) {
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
