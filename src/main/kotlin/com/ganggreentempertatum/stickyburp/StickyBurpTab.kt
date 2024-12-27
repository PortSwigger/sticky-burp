package com.ganggreentempertatum.stickyburp

import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Component
import javax.swing.*
import javax.swing.table.DefaultTableModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import burp.api.montoya.persistence.Persistence
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Color
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

class StickyBurpTab(
    private val variables: MutableList<StickyVariable>,
    private val persistence: Persistence
) : JPanel() {
    private val tableModel: DefaultTableModel = object : DefaultTableModel(
        arrayOf("Name", "Value", "Source Tab", "Source", "Notes"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = column == 4
    }

    private fun extractColor(source: String): Color? {
        val colorRegex = """\[color:(-?\d+)]""".toRegex()
        return colorRegex.find(source)?.groupValues?.get(1)?.toIntOrNull()?.let { Color(it) }
    }

    private val alternateRowColor = Color(245, 245, 245)

    private val table: JTable = JTable(tableModel).also { t ->
        t.autoCreateRowSorter = true

        val sorter = TableRowSorter(tableModel)
        t.rowSorter = sorter

        sorter.setComparator(2, Comparator<String> { s1, s2 ->
            val clean1 = s1.replace("""\s*\[color:-?\d+]""".toRegex(), "")
            val clean2 = s2.replace("""\s*\[color:-?\d+]""".toRegex(), "")
            clean1.compareTo(clean2)
        })

        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        t.putClientProperty("terminateEditOnFocusLost", true)

        val popupMenu = JPopupMenu()
        val copyItem = JMenuItem("Copy Cell Value")
        val deleteItem = JMenuItem("Delete Sticky")

        copyItem.addActionListener {
            val row = t.selectedRow
            val col = t.selectedColumn
            if (row >= 0 && col >= 0) {
                val value = t.getValueAt(row, col)?.toString() ?: ""
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(value), null)
            }
        }

        deleteItem.addActionListener {
            val row = t.selectedRow
            if (row >= 0) {
                deleteVariable(row)
            }
        }

        popupMenu.add(copyItem)
        popupMenu.add(deleteItem)

        t.setDefaultRenderer(Object::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

                if (!isSelected) {
                    val modelRow = table.convertRowIndexToModel(row)
                    if (modelRow < variables.size) {
                        val variable = variables[modelRow]
                        c.background = extractColor(variable.source)
                            ?: when {
                                variable.source.contains("Proxy") -> Color(255, 200, 200)
                                variable.source.contains("Repeater") -> Color(200, 255, 200)
                                modelRow % 2 == 0 -> Color.WHITE
                                else -> alternateRowColor
                            }
                    }
                }
                return c
            }
        })

        val colorMenu = JMenuItem("Set Color")
        colorMenu.addActionListener {
            val row = t.selectedRow
            if (row >= 0) {
                val color = JColorChooser.showDialog(this, "Choose Color", t.background)
                if (color != null) {
                    val variable = variables[row]
                    val sourceWithoutColor = variable.source.replace("""\s*\[color:-?\d+]""".toRegex(), "")
                    variables[row] = variable.copy(source = "$sourceWithoutColor [color:${color.rgb}]")
                    updateTableRow(row, variables[row])
                    saveVariables()
                    t.repaint()
                }
            }
        }
        popupMenu.add(colorMenu)

        t.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { handlePopup(e) }
            override fun mouseReleased(e: MouseEvent) { handlePopup(e) }
            private fun handlePopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val point = e.point
                    val row = t.rowAtPoint(point)
                    val col = t.columnAtPoint(point)
                    if (row >= 0 && col >= 0) {
                        t.setRowSelectionInterval(row, row)
                        t.setColumnSelectionInterval(col, col)
                        popupMenu.show(e.component, e.x, e.y)
                    }
                }
            }
        })

        t.getColumnModel().getColumn(4).cellEditor = DefaultCellEditor(JTextField())

        t.addPropertyChangeListener { evt ->
            if ("tableCellEditor" == evt.propertyName) {
                val row = t.editingRow
                val col = t.editingColumn
                if (row != -1 && col == 4) {
                    val notes = t.getValueAt(row, col)?.toString() ?: ""
                    val variable = variables[row]
                    variables[row] = variable.copy(notes = notes)
                    saveVariables()
                }
            }
        }
    }

    init {
        loadVariables()

        layout = BorderLayout()
        add(JScrollPane(table), BorderLayout.CENTER)

        val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val addButton = JButton("New Sticky")
        val updateButton = JButton("Update Selected Sticky")
        val deleteButton = JButton("Delete Selected Sticky")

        addButton.addActionListener { addNewVariable() }
        updateButton.addActionListener { updateSelectedVariable() }
        deleteButton.addActionListener {
            val selectedRow = table.selectedRow
            if (selectedRow >= 0) {
                deleteVariable(selectedRow)
            } else {
                JOptionPane.showMessageDialog(this, "Please select a sticky to delete")
            }
        }

        controlPanel.add(addButton)
        controlPanel.add(updateButton)
        controlPanel.add(deleteButton)
        add(controlPanel, BorderLayout.SOUTH)
    }

    private fun loadVariables() {
        val savedVars = persistence.extensionData().getString("stickyburp.variables")
        if (!savedVars.isNullOrEmpty()) {
            try {
                val loadedVars = Json.decodeFromString<List<StickyVariable>>(savedVars)
                variables.clear()
                loadedVars.forEach { addVariable(it) }
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("SwallowedException")
    private fun saveVariables() {
        try {
            val varsJson = Json.encodeToString(variables)
            persistence.extensionData().setString("stickyburp.variables", varsJson)
        } catch (_: Exception) {
        }
    }

    fun addVariable(variable: StickyVariable) {
        val existingIndex = variables.indexOfFirst { it.name == variable.name }
        if (existingIndex != -1) {
            variables[existingIndex] = variable
            updateTableRow(existingIndex, variable)
            saveVariables()
            return
        }

        tableModel.addRow(arrayOf(
            variable.name,
            variable.value,
            variable.sourceTab,
            variable.source,
            variable.notes
        ))
        variables.add(variable)
        saveVariables()
    }

    fun hasVariable(name: String): Boolean {
        return variables.any { it.name == name }
    }

    fun getVariableNames(): List<String> {
        return variables.map { it.name }
    }

    fun getVariables(): List<StickyVariable> {
        return variables.toList()
    }

    private fun updateTableRow(index: Int, variable: StickyVariable) {
        tableModel.setValueAt(variable.name, index, 0)
        tableModel.setValueAt(variable.value, index, 1)
        tableModel.setValueAt(variable.sourceTab, index, 2)
        tableModel.setValueAt(variable.source, index, 3)
        tableModel.setValueAt(variable.notes, index, 4)
    }

    private fun updateSelectedVariable() {
        val selectedRow = table.selectedRow
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a sticky to update")
            return
        }

        val currentVariable = variables[selectedRow]
        val value = JOptionPane.showInputDialog(
            this,
            "Enter new value for '${currentVariable.name}':",
            currentVariable.value
        )
        if (value == null) return

        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Sticky value cannot be empty", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return
        }

        val updatedVariable = currentVariable.copy(
            value = trimmedValue,
            source = "Manual Update",
            sourceTab = currentVariable.sourceTab
        )
        variables[selectedRow] = updatedVariable
        updateTableRow(selectedRow, updatedVariable)
        saveVariables()
    }

    private fun addNewVariable() {
        val name = JOptionPane.showInputDialog("Enter sticky name:")
        if (name == null) return

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Sticky name cannot be empty", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return
        }

        if (!trimmedName.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            JOptionPane.showMessageDialog(this,
                "Variable name can only contain letters, numbers, and underscores",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE)
            return
        }

        if (variables.any { it.name == trimmedName }) {
            JOptionPane.showMessageDialog(this, "A sticky with this name already exists!")
            return
        }

        val value = JOptionPane.showInputDialog("Enter sticky value:")
        if (value == null) return

        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Sticky value cannot be empty", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return
        }

        addVariable(StickyVariable(trimmedName, trimmedValue, "Manual Entry"))
    }

    private fun deleteVariable(row: Int) {
        val variable = variables[row]
        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete the sticky '${variable.name}'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (confirm == JOptionPane.YES_OPTION) {
            variables.removeAt(row)
            tableModel.removeRow(row)
            saveVariables()
        }
    }
}