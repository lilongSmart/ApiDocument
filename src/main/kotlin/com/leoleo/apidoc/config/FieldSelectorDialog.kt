package com.leoleo.apidoc.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.leoleo.apidoc.i18n.ApiDocI18n
import com.leoleo.apidoc.ui.ApiDocUi
import com.leoleo.apidoc.ui.RoundedBorder
import com.leoleo.apidoc.ui.RoundedButton
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent

/**
 * 字段选择对话框
 * 用穿梭框方式选择要排除的特定字段，支持搜索筛选
 */
class FieldSelectorDialog(
    project: Project,
    private val className: String,
    private val allFields: List<String>,
    private val currentExcluded: List<String>
) : DialogWrapper(project) {

    private lateinit var availableListModel: DefaultListModel<String>
    private lateinit var availableList: JBList<String>
    private lateinit var excludedListModel: DefaultListModel<String>
    private lateinit var excludedList: JBList<String>
    private lateinit var leftSearchField: JTextField
    private lateinit var rightSearchField: JTextField

    // 实际数据源
    private val availableFields = mutableListOf<String>()
    private val excludedFields = mutableListOf<String>()

    init {
        val simpleName = className.substringAfterLast('.')
        title = ApiDocI18n.text("field.title", simpleName)

        // 初始化数据
        for (field in allFields) {
            if (field in currentExcluded) {
                excludedFields.add(field)
            } else {
                availableFields.add(field)
            }
        }
        setOKButtonText(ApiDocI18n.text("common.confirm"))
        setCancelButtonText(ApiDocI18n.text("common.cancel"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 12)).apply {
            preferredSize = Dimension(720, 500)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            background = ApiDocUi.panelBg()
        }

        // 顶部说明
        val tipPanel = JPanel(BorderLayout()).apply { isOpaque = false }
        val tipLabel = ApiDocUi.titleLabel(ApiDocI18n.text("field.heading"))
        tipPanel.add(tipLabel)
        panel.add(tipPanel, BorderLayout.NORTH)

        // 穿梭框
        val transferPanel = JPanel(GridBagLayout())
        transferPanel.isOpaque = false
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.BOTH
            insets = Insets(5, 6, 5, 6)
        }

        // ===== 左侧：可用字段 =====
        val leftPanel = JPanel(BorderLayout(0, 8)).apply { isOpaque = false }

        // 左侧搜索框（带放大镜图标）
        val leftSearchPanel = createSearchPanel(ApiDocI18n.text("field.searchAvailable")) { filterAvailableList() }
        leftSearchField = leftSearchPanel.second
        leftPanel.add(leftSearchPanel.first, BorderLayout.NORTH)

        availableListModel = DefaultListModel()
        refreshAvailableList()
        availableList = JBList(availableListModel)
        availableList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        ApiDocUi.styleList(availableList)
        val leftScroll = JBScrollPane(availableList)
        leftScroll.border = RoundedBorder(ApiDocUi.borderColor(), 8, 1)
        leftPanel.add(leftScroll, BorderLayout.CENTER)

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.45; gbc.weighty = 1.0
        transferPanel.add(leftPanel, gbc)

        // ===== 中间按钮 =====
        val buttonPanel = JPanel(GridBagLayout())
        buttonPanel.isOpaque = false
        val btnGbc = GridBagConstraints().apply {
            insets = Insets(5, 4, 5, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        val addBtn = RoundedButton(ApiDocI18n.text("field.exclude"), ApiDocUi.success)
        addBtn.addActionListener { moveFieldsToExcluded() }
        btnGbc.gridx = 0; btnGbc.gridy = 0
        buttonPanel.add(addBtn, btnGbc)

        val removeBtn = RoundedButton(ApiDocI18n.text("field.restore"), ApiDocUi.danger)
        removeBtn.addActionListener { moveFieldsToAvailable() }
        btnGbc.gridy = 1
        buttonPanel.add(removeBtn, btnGbc)

        val addAllBtn = RoundedButton(ApiDocI18n.text("field.excludeAll"))
        addAllBtn.addActionListener { moveAllFieldsToExcluded() }
        btnGbc.gridy = 2
        buttonPanel.add(addAllBtn, btnGbc)

        val clearBtn = RoundedButton(ApiDocI18n.text("field.clear"))
        clearBtn.addActionListener { clearExcluded() }
        btnGbc.gridy = 3
        buttonPanel.add(clearBtn, btnGbc)

        val selectAllBtn = RoundedButton(ApiDocI18n.text("field.selectAll"))
        selectAllBtn.addActionListener { selectAll() }
        btnGbc.gridy = 4
        buttonPanel.add(selectAllBtn, btnGbc)

        val invertBtn = RoundedButton(ApiDocI18n.text("field.invert"))
        invertBtn.addActionListener { invertSelection() }
        btnGbc.gridy = 5
        buttonPanel.add(invertBtn, btnGbc)

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.16; gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.CENTER
        transferPanel.add(buttonPanel, gbc)

        // ===== 右侧：已排除字段 =====
        val rightPanel = JPanel(BorderLayout(0, 8)).apply { isOpaque = false }

        // 右侧搜索框（带放大镜图标）
        val rightSearchPanel = createSearchPanel(ApiDocI18n.text("field.searchExcluded")) { filterExcludedList() }
        rightSearchField = rightSearchPanel.second
        rightPanel.add(rightSearchPanel.first, BorderLayout.NORTH)

        excludedListModel = DefaultListModel()
        refreshExcludedList()
        excludedList = JBList(excludedListModel)
        excludedList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        ApiDocUi.styleList(excludedList)
        val rightScroll = JBScrollPane(excludedList)
        rightScroll.border = RoundedBorder(ApiDocUi.borderColor(), 8, 1)
        rightPanel.add(rightScroll, BorderLayout.CENTER)

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.45; gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        transferPanel.add(rightPanel, gbc)

        panel.add(transferPanel, BorderLayout.CENTER)

        // 底部提示
        val bottomTip = ApiDocUi.mutedLabel(ApiDocI18n.text("field.emptyMeansAll"))
        panel.add(bottomTip, BorderLayout.SOUTH)

        return panel
    }

    /**
     * 创建带放大镜图标的搜索面板
     */
    private fun createSearchPanel(placeholder: String, onSearch: () -> Unit): Pair<JPanel, JTextField> {
        val panel = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            preferredSize = Dimension(0, 30)
            minimumSize = Dimension(0, 30)
        }

        // 放大镜图标
        val iconLabel = object : JLabel() {
            init { preferredSize = Dimension(24, 30) }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(0x99, 0x99, 0x99)
                g2.stroke = BasicStroke(1.5f)
                val cx = width / 2 - 2
                val cy = height / 2 - 2
                g2.drawOval(cx - 5, cy - 5, 10, 10)
                g2.drawLine(cx + 4, cy + 4, cx + 8, cy + 8)
                g2.dispose()
            }
        }
        panel.add(iconLabel, BorderLayout.WEST)

        val searchField = JTextField().apply {
            toolTipText = placeholder
            preferredSize = Dimension(0, 30)
            minimumSize = Dimension(0, 30)
            border = BorderFactory.createCompoundBorder(
                RoundedBorder(ApiDocUi.borderColor(), 8, 1),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)
            )
        }
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { onSearch() }
        })
        panel.add(searchField, BorderLayout.CENTER)

        return Pair(panel, searchField)
    }

    private fun filterAvailableList() {
        val keyword = leftSearchField.text.trim().lowercase()
        availableListModel.clear()
        for (field in availableFields) {
            if (keyword.isEmpty() || field.lowercase().contains(keyword)) {
                availableListModel.addElement(field)
            }
        }
    }

    private fun filterExcludedList() {
        val keyword = rightSearchField.text.trim().lowercase()
        excludedListModel.clear()
        for (field in excludedFields) {
            if (keyword.isEmpty() || field.lowercase().contains(keyword)) {
                excludedListModel.addElement(field)
            }
        }
    }

    private fun refreshAvailableList() {
        availableListModel.clear()
        for (field in availableFields) { availableListModel.addElement(field) }
    }

    private fun refreshExcludedList() {
        excludedListModel.clear()
        for (field in excludedFields) { excludedListModel.addElement(field) }
    }

    private fun moveFieldsToExcluded() {
        for (field in availableList.selectedValuesList) {
            availableFields.remove(field)
            if (field !in excludedFields) {
                excludedFields.add(field)
            }
        }
        filterAvailableList()
        filterExcludedList()
    }

    private fun moveFieldsToAvailable() {
        for (field in excludedList.selectedValuesList) {
            excludedFields.remove(field)
            if (field !in availableFields) {
                availableFields.add(field)
            }
        }
        filterAvailableList()
        filterExcludedList()
    }

    private fun moveAllFieldsToExcluded() {
        for (field in availableFields) {
            if (field !in excludedFields) {
                excludedFields.add(field)
            }
        }
        availableFields.clear()
        filterAvailableList()
        filterExcludedList()
    }

    private fun clearExcluded() {
        for (field in excludedFields) {
            if (field !in availableFields) {
                availableFields.add(field)
            }
        }
        excludedFields.clear()
        filterAvailableList()
        filterExcludedList()
    }

    /**
     * 获取选中的要排除的字段列表
     */
    fun getSelectedFields(): List<String> = excludedFields.toList()

    /**
     * 全选左侧列表中的所有项
     */
    private fun selectAll() {
        if (availableListModel.isEmpty) return
        availableList.selectionModel.setSelectionInterval(0, availableListModel.size() - 1)
    }

    /**
     * 反选：左侧当前未选中的变为选中，已选中的变为未选中
     */
    private fun invertSelection() {
        val currentSelected = availableList.selectedIndices.toSet()
        availableList.clearSelection()
        for (i in 0 until availableListModel.size()) {
            if (i !in currentSelected) {
                availableList.addSelectionInterval(i, i)
            }
        }
    }
}
