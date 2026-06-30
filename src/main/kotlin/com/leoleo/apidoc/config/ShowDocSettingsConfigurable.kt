package com.leoleo.apidoc.config

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.leoleo.apidoc.i18n.ApiDocI18n
import com.leoleo.apidoc.i18n.ApiDocLanguage
import com.leoleo.apidoc.ui.ApiDocUi
import com.leoleo.apidoc.ui.RoundedBorder
import com.leoleo.apidoc.ui.RoundedButton
import com.leoleo.apidoc.ui.RoundedTitledPanel
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent

/**
 * ApiDoc 设置弹框
 * 独立对话框形式，不在Settings面板中
 */
class ShowDocSettingsDialog(private val project: Project) : DialogWrapper(project) {

    // 基础设置
    private lateinit var showCallLocationCheckBox: JCheckBox
    private lateinit var autoVersionCheckBox: JCheckBox
    private lateinit var showRequestJsonCheckBox: JCheckBox
    private lateinit var showResponseJsonCheckBox: JCheckBox
    private lateinit var showJsonCommentCheckBox: JCheckBox
    private lateinit var authorField: JTextField
    private lateinit var productVersionField: JTextField
    private lateinit var exportPathField: TextFieldWithBrowseButton
    private lateinit var languageComboBox: JComboBox<ApiDocLanguage>
    private lateinit var previewFontSizeSpinner: JSpinner

    // 排除父类 - 穿梭框
    private lateinit var searchField: JTextField
    private lateinit var availableListModel: DefaultListModel<String>
    private lateinit var availableList: JBList<String>
    private lateinit var selectedListModel: DefaultListModel<String>
    private lateinit var selectedList: JBList<String>

    // 缓存项目中所有类名
    private var allProjectClasses: List<String> = emptyList()

    // 排除的特定字段映射：className -> 排除的字段名列表（空列表表示排除全部）
    private val excludedFieldsMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    init {
        title = ApiDocI18n.text("settings.title")
        setOKButtonText(ApiDocI18n.text("common.confirm"))
        setCancelButtonText(ApiDocI18n.text("common.cancel"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 14)).apply {
            preferredSize = Dimension(880, 660)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            background = ApiDocUi.panelBg()
        }

        // ===== 基础设置面板 =====
        val basicPanel = createBasicSettingsPanel()
        panel.add(basicPanel, BorderLayout.NORTH)

        // ===== 排除父类面板（穿梭框） =====
        val excludePanel = createExcludePanel()
        panel.add(excludePanel, BorderLayout.CENTER)

        // 底部提示和重置按钮
        val bottomPanel = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(2, 2, 0, 2)
        }
        bottomPanel.add(ApiDocUi.mutedLabel(ApiDocI18n.text("settings.savedTip")), BorderLayout.WEST)
        val resetBtn = RoundedButton(ApiDocI18n.text("settings.resetAll"), ApiDocUi.danger)
        resetBtn.addActionListener {
            val confirm = JOptionPane.showConfirmDialog(
                contentPanel, ApiDocI18n.text("settings.resetConfirm"),
                ApiDocI18n.text("settings.resetTitle"), JOptionPane.YES_NO_OPTION
            )
            if (confirm == JOptionPane.YES_OPTION) {
                ShowDocSettings.getInstance().loadState(ShowDocSettings.State())
                loadSettings()
                filterAvailableClasses()
                selectedList.repaint()
            }
        }
        bottomPanel.add(resetBtn, BorderLayout.EAST)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 加载配置
        loadSettings()

        // 异步加载项目类列表
        loadProjectClasses()

        return panel
    }

    /**
     * 基础设置面板
     */
    private fun createBasicSettingsPanel(): JPanel {
        val content = JPanel(GridBagLayout())
        content.isOpaque = false
        val gbc = GridBagConstraints().apply {
            insets = Insets(7, 12, 7, 12)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        var row = 0

        // 第一行复选框
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0.5
        showCallLocationCheckBox = JCheckBox(ApiDocI18n.text("settings.showCallLocation")).apply { isOpaque = false }
        content.add(showCallLocationCheckBox, gbc)
        gbc.gridx = 1
        autoVersionCheckBox = JCheckBox(ApiDocI18n.text("settings.autoVersion")).apply { isOpaque = false }
        content.add(autoVersionCheckBox, gbc)

        // 第二行复选框
        row++
        gbc.gridx = 0; gbc.gridy = row
        showRequestJsonCheckBox = JCheckBox(ApiDocI18n.text("settings.showRequestJson")).apply { isOpaque = false }
        content.add(showRequestJsonCheckBox, gbc)
        gbc.gridx = 1
        showResponseJsonCheckBox = JCheckBox(ApiDocI18n.text("settings.showResponseJson")).apply { isOpaque = false }
        content.add(showResponseJsonCheckBox, gbc)

        // 第三行复选框
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        showJsonCommentCheckBox = JCheckBox(ApiDocI18n.text("settings.showJsonComment")).apply { isOpaque = false }
        content.add(showJsonCommentCheckBox, gbc)
        gbc.gridwidth = 1

        // 界面语言
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        content.add(JLabel(ApiDocI18n.text("settings.language")).apply { preferredSize = Dimension(90, preferredSize.height) }, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        languageComboBox = JComboBox(ApiDocLanguage.values()).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (component is JLabel && value is ApiDocLanguage) {
                        component.text = value.displayName
                    }
                    return component
                }
            }
        }
        content.add(languageComboBox, gbc)

        // 预览字号
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        content.add(JLabel(ApiDocI18n.text("settings.previewFontSize")).apply { preferredSize = Dimension(90, preferredSize.height) }, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        previewFontSizeSpinner = JSpinner(SpinnerNumberModel(10, 9, 14, 1)).apply {
            preferredSize = Dimension(90, preferredSize.height)
            toolTipText = ApiDocI18n.text("settings.previewFontSizeTip")
        }
        val fontPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        fontPanel.add(previewFontSizeSpinner)
        fontPanel.add(ApiDocUi.mutedLabel(" pt"))
        content.add(fontPanel, gbc)

        // 默认作者
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        content.add(JLabel(ApiDocI18n.text("settings.author")).apply { preferredSize = Dimension(90, preferredSize.height) }, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        authorField = JTextField(20)
        content.add(authorField, gbc)

        // 产品版本
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        content.add(JLabel(ApiDocI18n.text("settings.productVersion")).apply { preferredSize = Dimension(90, preferredSize.height) }, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        productVersionField = JTextField(20)
        content.add(productVersionField, gbc)

        // 导出路径
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        content.add(JLabel(ApiDocI18n.text("settings.exportPath")).apply { preferredSize = Dimension(90, preferredSize.height) }, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        exportPathField = TextFieldWithBrowseButton()
        exportPathField.addBrowseFolderListener(
            ApiDocI18n.text("settings.chooseExportTitle"), ApiDocI18n.text("settings.chooseExportDesc"),
            project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        content.add(exportPathField, gbc)

        val panel = RoundedTitledPanel(ApiDocI18n.text("settings.basic"), content)
        return panel
    }

    /**
     * 排除父类穿梭框面板
     */
    private fun createExcludePanel(): JPanel {
        val content = JPanel(BorderLayout(8, 8))
        content.isOpaque = false

        // 顶部搜索框
        val searchPanel = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            preferredSize = Dimension(0, 30)
            minimumSize = Dimension(0, 30)
        }
        // 放大镜图标
        val searchIcon = object : JLabel() {
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
        searchPanel.add(searchIcon, BorderLayout.WEST)
        searchField = JTextField().apply {
            toolTipText = ApiDocI18n.text("settings.searchClass")
            preferredSize = Dimension(0, 30)
            minimumSize = Dimension(0, 30)
            border = BorderFactory.createCompoundBorder(
                RoundedBorder(ApiDocUi.borderColor(), 8, 1),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)
            )
        }
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filterAvailableClasses()
            }
        })
        searchPanel.add(searchField, BorderLayout.CENTER)
        content.add(searchPanel, BorderLayout.NORTH)

        // 穿梭框主体
        val transferPanel = JPanel(GridBagLayout())
        transferPanel.isOpaque = false
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.BOTH
            insets = Insets(6, 6, 6, 6)
        }

        // 左侧：可选类列表
        val leftLabel = ApiDocUi.titleLabel(ApiDocI18n.text("settings.availableClasses"))
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.45; gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        transferPanel.add(leftLabel, gbc)

        availableListModel = DefaultListModel()
        availableList = JBList(availableListModel)
        availableList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        ApiDocUi.styleList(availableList)
        availableList.fixedCellHeight = 42
        availableList.cellRenderer = ClassNameListRenderer(keywordProvider = { searchField.text.trim() })
        val leftScroll = JBScrollPane(availableList)
        leftScroll.border = RoundedBorder(ApiDocUi.borderColor(), 8, 1)
        leftScroll.minimumSize = Dimension(220, 150)
        gbc.gridy = 1; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        transferPanel.add(leftScroll, gbc)

        // 中间：穿梭按钮
        val buttonPanel = JPanel(GridBagLayout())
        buttonPanel.isOpaque = false
        val btnGbc = GridBagConstraints().apply {
            insets = Insets(5, 4, 5, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        val addBtn = RoundedButton(ApiDocI18n.text("settings.add"), ApiDocUi.success)
        addBtn.toolTipText = ApiDocI18n.text("settings.addTip")
        addBtn.addActionListener { moveToSelected() }
        btnGbc.gridx = 0; btnGbc.gridy = 0
        buttonPanel.add(addBtn, btnGbc)

        val removeBtn = RoundedButton(ApiDocI18n.text("settings.remove"), ApiDocUi.danger)
        removeBtn.toolTipText = ApiDocI18n.text("settings.removeTip")
        removeBtn.addActionListener { moveToAvailable() }
        btnGbc.gridy = 1
        buttonPanel.add(removeBtn, btnGbc)

        val addAllBtn = RoundedButton(ApiDocI18n.text("settings.addAll"))
        addAllBtn.toolTipText = ApiDocI18n.text("settings.addAllTip")
        addAllBtn.addActionListener { moveAllToSelected() }
        btnGbc.gridy = 2
        buttonPanel.add(addAllBtn, btnGbc)

        val removeAllBtn = RoundedButton(ApiDocI18n.text("settings.removeAll"))
        removeAllBtn.toolTipText = ApiDocI18n.text("settings.removeAllTip")
        removeAllBtn.addActionListener { moveAllToAvailable() }
        btnGbc.gridy = 3
        buttonPanel.add(removeAllBtn, btnGbc)

        gbc.gridx = 1; gbc.gridy = 0; gbc.gridheight = 2; gbc.weightx = 0.16; gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.CENTER
        transferPanel.add(buttonPanel, gbc)
        gbc.gridheight = 1

        // 右侧：已排除类列表
        val rightLabel = ApiDocUi.titleLabel(ApiDocI18n.text("settings.excludedParents"))
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.45; gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        transferPanel.add(rightLabel, gbc)

        selectedListModel = DefaultListModel()
        selectedList = JBList(selectedListModel)
        selectedList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        ApiDocUi.styleList(selectedList)
        selectedList.fixedCellHeight = 46
        selectedList.cellRenderer = ClassNameListRenderer(
            keywordProvider = { searchField.text.trim() },
            suffixProvider = { className ->
                val fieldCount = excludedFieldsMap[className]?.size ?: 0
                if (fieldCount > 0) {
                    ApiDocI18n.text("settings.excludedSomeFields", fieldCount)
                } else {
                    ApiDocI18n.text("settings.excludedAllFields")
                }
            }
        )
        // 双击打开字段选择
        selectedList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val index = selectedList.locationToIndex(e.point)
                    if (index >= 0) {
                        val className = selectedListModel.getElementAt(index)
                        openFieldSelector(className)
                    }
                }
            }
        })
        val rightScroll = JBScrollPane(selectedList)
        rightScroll.border = RoundedBorder(ApiDocUi.borderColor(), 8, 1)
        rightScroll.minimumSize = Dimension(220, 150)
        gbc.gridy = 1; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        transferPanel.add(rightScroll, gbc)

        // 提示文本
        val tipLabel = ApiDocUi.mutedLabel(ApiDocI18n.text("settings.fieldTip"))
        content.add(tipLabel, BorderLayout.SOUTH)

        content.add(transferPanel, BorderLayout.CENTER)

        val panel = RoundedTitledPanel(ApiDocI18n.text("settings.excludePanel"), content)
        return panel
    }

    /**
     * 加载项目中的所有类（异步）
     */
    private fun loadProjectClasses() {
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val classes = mutableListOf<String>()
            com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                try {
                    val scope = GlobalSearchScope.projectScope(project)
                    AllClassesSearch.search(scope, project).forEach { psiClass ->
                        val qualifiedName = psiClass.qualifiedName
                        if (qualifiedName != null && !qualifiedName.startsWith("java.")
                            && !qualifiedName.startsWith("javax.")
                            && !qualifiedName.startsWith("kotlin.")) {
                            classes.add(qualifiedName)
                        }
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
            allProjectClasses = classes.sorted()

            // 回到UI线程更新列表
            SwingUtilities.invokeLater {
                filterAvailableClasses()
            }
        }
    }

    /**
     * 根据搜索关键词过滤可选类列表
     */
    private fun filterAvailableClasses() {
        val keyword = searchField.text.trim().lowercase()
        val excludedSet = getSelectedClasses().toSet()

        availableListModel.clear()
        val filtered = if (keyword.isEmpty()) {
            // 不输入时不展示，避免太多
            emptyList()
        } else {
            allProjectClasses.filter { className ->
                className !in excludedSet &&
                        (className.lowercase().contains(keyword) ||
                                className.substringAfterLast('.').lowercase().contains(keyword))
            }.take(100) // 最多显示100条
        }

        for (className in filtered) {
            availableListModel.addElement(className)
        }
    }

    /**
     * 将选中的类移动到右侧排除列表
     */
    private fun moveToSelected() {
        val selected = availableList.selectedValuesList
        for (item in selected) {
            if (!containsInModel(selectedListModel, item)) {
                selectedListModel.addElement(item)
            }
            availableListModel.removeElement(item)
        }
    }

    /**
     * 将选中的类从右侧移除
     */
    private fun moveToAvailable() {
        val selected = selectedList.selectedValuesList
        for (item in selected) {
            selectedListModel.removeElement(item)
            excludedFieldsMap.remove(item)
        }
        filterAvailableClasses()
    }

    /**
     * 全部添加到排除列表
     */
    private fun moveAllToSelected() {
        for (i in 0 until availableListModel.size()) {
            val item = availableListModel.getElementAt(i)
            if (!containsInModel(selectedListModel, item)) {
                selectedListModel.addElement(item)
            }
        }
        availableListModel.clear()
    }

    /**
     * 全部从排除列表移除
     */
    private fun moveAllToAvailable() {
        selectedListModel.clear()
        excludedFieldsMap.clear()
        filterAvailableClasses()
    }

    private fun containsInModel(model: DefaultListModel<String>, item: String): Boolean {
        for (i in 0 until model.size()) {
            if (model.getElementAt(i) == item) return true
        }
        return false
    }

    /**
     * 获取右侧已选中的排除类列表
     */
    private fun getSelectedClasses(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until selectedListModel.size()) {
            list.add(selectedListModel.getElementAt(i))
        }
        return list
    }

    /**
     * 加载已有配置到界面
     */
    private fun loadSettings() {
        val settings = ShowDocSettings.getInstance().state
        showCallLocationCheckBox.isSelected = settings.showCallLocation
        autoVersionCheckBox.isSelected = settings.autoVersionFromGit
        showRequestJsonCheckBox.isSelected = settings.showRequestJson
        showResponseJsonCheckBox.isSelected = settings.showResponseJson
        showJsonCommentCheckBox.isSelected = settings.showJsonComment
        authorField.text = settings.author
        productVersionField.text = settings.productVersion
        exportPathField.text = settings.exportPath
        languageComboBox.selectedItem = ApiDocLanguage.fromCode(settings.language)
        previewFontSizeSpinner.value = settings.previewFontSizePt.coerceIn(9, 14)

        selectedListModel.clear()
        excludedFieldsMap.clear()
        for (className in settings.excludedParentClasses) {
            selectedListModel.addElement(className)
        }
        // 加载特定字段排除配置
        for (entry in settings.excludedFields) {
            val parts = entry.split("#", limit = 2)
            if (parts.size == 2) {
                val className = parts[0]
                val fieldName = parts[1]
                excludedFieldsMap.getOrPut(className) { mutableListOf() }.add(fieldName)
            }
        }
    }

    /**
     * 保存配置
     */
    override fun doOKAction() {
        val settings = ShowDocSettings.getInstance()
        val currentState = settings.state
        val selectedClasses = getSelectedClasses()
        val selectedClassSet = selectedClasses.toSet()
        // 构建 excludedFields 列表（className#fieldName 格式）
        val excludedFields = mutableListOf<String>()
        for ((className, fields) in excludedFieldsMap) {
            if (className !in selectedClassSet) continue
            for (field in fields) {
                excludedFields.add("$className#$field")
            }
        }
        settings.loadState(
            ShowDocSettings.State(
                author = authorField.text,
                productVersion = productVersionField.text,
                autoVersionFromGit = autoVersionCheckBox.isSelected,
                versionPrefix = currentState.versionPrefix,
                versionRegex = currentState.versionRegex,
                showCallLocation = showCallLocationCheckBox.isSelected,
                showRequestJson = showRequestJsonCheckBox.isSelected,
                showResponseJson = showResponseJsonCheckBox.isSelected,
                showJsonComment = showJsonCommentCheckBox.isSelected,
                exportPath = exportPathField.text,
                language = (languageComboBox.selectedItem as? ApiDocLanguage ?: ApiDocLanguage.ZH).code,
                previewFontSizePt = (previewFontSizeSpinner.value as? Int ?: 10).coerceIn(9, 14),
                excludedParentClasses = selectedClasses.toMutableList(),
                excludedFields = excludedFields
            )
        )
        super.doOKAction()
    }

    /**
     * 打开字段选择对话框
     * 用穿梭框方式选择要排除的特定字段
     */
    private fun openFieldSelector(className: String) {
        // 异步获取类的所有字段
        val fields = mutableListOf<String>()
        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
            try {
                val scope = GlobalSearchScope.projectScope(project)
                val facade = JavaPsiFacade.getInstance(project)
                val psiClass = facade.findClass(className, scope)
                if (psiClass != null) {
                    for (field in psiClass.allFields) {
                        if (field.modifierList?.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) == true) continue
                        if (field.name == "serialVersionUID") continue
                        val fieldName = field.name
                        if (!fieldName.isNullOrBlank() && fieldName !in fields) {
                            fields.add(fieldName)
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略
            }
        }

        if (fields.isEmpty()) {
            JOptionPane.showMessageDialog(contentPanel, ApiDocI18n.text("settings.noFields"), ApiDocI18n.text("common.tip"), JOptionPane.WARNING_MESSAGE)
            return
        }

        // 弹出字段选择对话框
        val dialog = FieldSelectorDialog(project, className, fields, excludedFieldsMap[className] ?: emptyList())
        if (dialog.showAndGet()) {
            val selectedFields = dialog.getSelectedFields()
            if (selectedFields.isEmpty()) {
                // 没有选择特定字段，表示排除全部
                excludedFieldsMap.remove(className)
            } else {
                excludedFieldsMap[className] = selectedFields.toMutableList()
            }
            // 刷新列表显示
            selectedList.repaint()
        }
    }

    private class ClassNameListRenderer(
        private val keywordProvider: () -> String,
        private val suffixProvider: ((String) -> String)? = null
    ) : JPanel(BorderLayout(0, 1)), ListCellRenderer<String> {

        private val nameLabel = JLabel()
        private val packageLabel = JLabel()

        init {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            nameLabel.isOpaque = false
            packageLabel.isOpaque = false
            nameLabel.font = ApiDocUi.buttonFont().deriveFont(Font.PLAIN, 12f)
            packageLabel.font = ApiDocUi.buttonFont().deriveFont(Font.PLAIN, 11f)
            add(nameLabel, BorderLayout.NORTH)
            add(packageLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out String>,
            value: String,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val simpleName = value.substringAfterLast('.')
            val packageName = value.substringBeforeLast('.', "")
            val suffix = suffixProvider?.invoke(value)?.let { "  [$it]" } ?: ""
            val keyword = keywordProvider()

            background = if (isSelected) list.selectionBackground else list.background
            nameLabel.foreground = if (isSelected) list.selectionForeground else ApiDocUi.textColor()
            packageLabel.foreground = if (isSelected) list.selectionForeground else ApiDocUi.mutedText()
            nameLabel.text = toHtml(highlight(simpleName, keyword) + escapeHtml(suffix))
            packageLabel.text = toHtml(highlight(packageName, keyword).ifBlank { "&nbsp;" })
            toolTipText = value
            return this
        }

        private fun toHtml(content: String): String = "<html>$content</html>"

        private fun highlight(text: String, keyword: String): String {
            if (text.isBlank()) return ""
            val escaped = escapeHtml(text)
            if (keyword.isBlank()) return escaped
            val lowerText = text.lowercase()
            val lowerKeyword = keyword.lowercase()
            val start = lowerText.indexOf(lowerKeyword)
            if (start < 0) return escaped
            val end = start + keyword.length
            return escapeHtml(text.substring(0, start)) +
                "<span style='background:#ffe58a;color:#172033;'>${escapeHtml(text.substring(start, end))}</span>" +
                escapeHtml(text.substring(end))
        }

        private fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        }
    }
}
