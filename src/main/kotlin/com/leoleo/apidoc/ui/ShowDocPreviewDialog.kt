package com.leoleo.apidoc.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.leoleo.apidoc.i18n.ApiDocI18n
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.event.ListSelectionEvent

/**
 * ApiDocument 接口文档编辑与预览对话框
 */
class ShowDocPreviewDialog(
    private val project: Project,
    private val documents: List<DocItem>,
    dialogTitle: String
) : DialogWrapper(project) {

    data class DocItem(val title: String, var markdown: String)

    constructor(project: Project, markdown: String, title: String) : this(
        project, listOf(DocItem(title, markdown)), title
    )

    private lateinit var listModel: DefaultListModel<String>
    private lateinit var interfaceList: JBList<String>
    private lateinit var markdownEditor: JTextArea
    private var previewPane: JEditorPane? = null
    private var previewBrowser: JBCefBrowser? = null
    private lateinit var themeComboBox: JComboBox<String>
    private var previewEnabled = true
    private var exportPath: String = ""
    private var currentDocumentIndex = -1
    private var previewFontSizePt = 10

    init {
        title = if (documents.size == 1) {
            ApiDocI18n.text("preview.title.single", dialogTitle)
        } else {
            ApiDocI18n.text("preview.title.multi", dialogTitle, documents.size)
        }
        val settings = com.leoleo.apidoc.config.ShowDocSettings.getInstance().state
        exportPath = settings.exportPath
        previewFontSizePt = settings.previewFontSizePt.coerceIn(9, 14)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 12)).apply {
            preferredSize = Dimension(1240, 780)
            border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
            background = ApiDocUi.panelBg()
        }

        // 顶部栏
        val headerPanel = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 2, 0, 0)
        }
        val titlePanel = JPanel(BorderLayout(0, 2)).apply { isOpaque = false }
        val titleLabel = JLabel(ApiDocI18n.text("preview.workspace")).apply {
            foreground = ApiDocUi.accent
            font = font.deriveFont(Font.BOLD, 16f)
        }
        val subtitleLabel = ApiDocUi.mutedLabel(ApiDocI18n.text("preview.subtitle", documents.size))
        titlePanel.add(titleLabel, BorderLayout.NORTH)
        titlePanel.add(subtitleLabel, BorderLayout.SOUTH)
        headerPanel.add(titlePanel, BorderLayout.WEST)

        // 右侧窗口控制按钮：最小化、全屏、关闭
        val controlPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        controlPanel.isOpaque = false

        // 最小化/折叠按钮
        val minimizeBtn = createWindowControlButton("minimize") {
            // 折叠到最小尺寸
            val window = SwingUtilities.getWindowAncestor(panel)
            if (window != null) {
                if (window is java.awt.Frame) {
                    window.extendedState = java.awt.Frame.ICONIFIED
                } else {
                    window.size = Dimension(400, 50)
                }
            }
        }
        controlPanel.add(minimizeBtn)

        // 全屏按钮
        val maximizeBtn = createWindowControlButton("maximize") {
            val window = SwingUtilities.getWindowAncestor(panel)
            if (window != null) {
                val screenSize = Toolkit.getDefaultToolkit().screenSize
                if (window.width >= screenSize.width - 50) {
                    // 已全屏，恢复默认大小
                    window.size = Dimension(1200, 700)
                    window.setLocationRelativeTo(null)
                } else {
                    // 全屏
                    window.size = screenSize
                    window.location = Point(0, 0)
                }
            }
        }
        controlPanel.add(maximizeBtn)

        // 关闭按钮
        val closeControlBtn = createWindowControlButton("close") {
            close(OK_EXIT_CODE)
        }
        controlPanel.add(closeControlBtn)

        val rightHeader = JPanel(BorderLayout())
        rightHeader.isOpaque = false
        val tipLabel = ApiDocUi.mutedLabel(ApiDocI18n.text("preview.currentEditTip"))
        rightHeader.add(tipLabel, BorderLayout.CENTER)
        rightHeader.add(controlPanel, BorderLayout.EAST)
        headerPanel.add(rightHeader, BorderLayout.EAST)
        panel.add(headerPanel, BorderLayout.NORTH)

        // 三栏内容
        val centerPanel = createMarkdownPanel()
        val rightPanel = createPreviewPanel()
        val leftPanel = createInterfaceListPanel()
        leftPanel.preferredSize = Dimension(200, 0)
        centerPanel.preferredSize = Dimension(500, 0)
        centerPanel.minimumSize = Dimension(360, 0)
        rightPanel.minimumSize = Dimension(420, 0)

        val rightSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerPanel, rightPanel)
        rightSplit.resizeWeight = 0.48
        rightSplit.dividerSize = 7
        rightSplit.border = null
        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit)
        mainSplit.resizeWeight = 0.16
        mainSplit.dividerSize = 7
        mainSplit.border = null

        panel.add(mainSplit, BorderLayout.CENTER)
        panel.add(createBottomPanel(), BorderLayout.SOUTH)

        SwingUtilities.invokeLater {
            mainSplit.setDividerLocation(200)
            rightSplit.setDividerLocation(500)
        }

        // 绑定事件
        interfaceList.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) onInterfaceSelected()
        }
        if (documents.isNotEmpty()) {
            interfaceList.selectedIndex = 0
            loadDocument(0)
        }
        return panel
    }

    private fun createInterfaceListPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createCompoundBorder(
                RoundedBorder(ApiDocUi.borderColor(), 10, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            )
            isOpaque = false
        }
        val header = JPanel(BorderLayout(8, 0)).apply { isOpaque = false }
        val tabLabel = ApiDocUi.titleLabel(ApiDocI18n.text("preview.interfaceList"))
        header.add(tabLabel, BorderLayout.WEST)
        val copyUrlListBtn = RoundedButton(ApiDocI18n.text("preview.copyUrlList")).apply {
            margin = Insets(4, 10, 4, 10)
            addActionListener { copyUrlNameList() }
        }
        header.add(copyUrlListBtn, BorderLayout.EAST)
        panel.add(header, BorderLayout.NORTH)

        listModel = DefaultListModel()
        documents.forEachIndexed { index, item -> listModel.addElement("${index + 1}. ${item.title}") }
        interfaceList = JBList(listModel).apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            ApiDocUi.styleList(this)
        }
        val scrollPane = JBScrollPane(interfaceList).apply {
            border = RoundedBorder(ApiDocUi.borderColor(), 8, 1)
        }
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun createMarkdownPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createCompoundBorder(
                RoundedBorder(ApiDocUi.borderColor(), 10, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            )
            isOpaque = false
        }
        val tabLabel = ApiDocUi.titleLabel(ApiDocI18n.text("preview.markdownSource"))
        panel.add(tabLabel, BorderLayout.NORTH)

        markdownEditor = JTextArea().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 14)
            lineWrap = true
            wrapStyleWord = true
            tabSize = 2
            margin = Insets(10, 12, 10, 12)
            border = BorderFactory.createEmptyBorder()
        }
        markdownEditor.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updatePreview()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updatePreview()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updatePreview()
        })
        val scrollPane = JBScrollPane(markdownEditor).apply {
            border = RoundedBorder(ApiDocUi.borderColor(), 8, 1)
        }
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun createPreviewPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createCompoundBorder(
                RoundedBorder(ApiDocUi.borderColor(), 10, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            )
            isOpaque = false
        }
        val previewHeader = JPanel(BorderLayout(10, 0)).apply { isOpaque = false }
        val previewLeft = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }

        val previewLabel = ApiDocUi.titleLabel(ApiDocI18n.text("preview.livePreview"))
        previewLeft.add(previewLabel)

        // Switch开关
        val switchBtn = object : JToggleButton() {
            init {
                isSelected = true
                preferredSize = Dimension(44, 22)
                isFocusPainted = false
                isBorderPainted = false
                isContentAreaFilled = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener {
                    previewEnabled = isSelected
                    if (previewEnabled) updatePreview()
                    repaint()
                }
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val bgColor = if (isSelected) ApiDocUi.success else ApiDocUi.borderColor()
                g2.color = bgColor
                g2.fillRoundRect(0, 2, width, height - 4, height - 4, height - 4)
                g2.color = Color.WHITE
                val circleSize = height - 8
                val x = if (isSelected) width - circleSize - 4 else 4
                g2.fillOval(x, 4, circleSize, circleSize)
                g2.dispose()
            }
        }
        previewLeft.add(switchBtn)
        previewHeader.add(previewLeft, BorderLayout.WEST)

        val themePanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }
        val themeLabel = ApiDocUi.mutedLabel(ApiDocI18n.text("preview.theme"))
        themePanel.add(themeLabel)
        themeComboBox = JComboBox(arrayOf("JetBrains Light", "GitHub Light", "One Dark", "Material Ocean", "Nord", "Dracula", "Solarized Light", "Tokyo Night")).apply {
            selectedIndex = 0
            preferredSize = Dimension(156, 30)
            addActionListener { updatePreview() }
        }
        themePanel.add(themeComboBox)
        previewHeader.add(themePanel, BorderLayout.EAST)

        panel.add(previewHeader, BorderLayout.NORTH)
        if (JBCefApp.isSupported()) {
            previewBrowser = JBCefBrowser()
            panel.add(previewBrowser!!.component, BorderLayout.CENTER)
        } else {
            previewPane = JEditorPane().apply {
                contentType = "text/html"
                isEditable = false
                font = Font("Microsoft YaHei", Font.PLAIN, 13)
            }
            val previewScroll = JBScrollPane(previewPane)
            previewScroll.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            previewScroll.border = RoundedBorder(ApiDocUi.borderColor(), 8, 1)
            panel.add(previewScroll, BorderLayout.CENTER)
        }
        return panel
    }

    private fun createBottomPanel(): JPanel {
        val panel = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ApiDocUi.borderColor()),
                BorderFactory.createEmptyBorder(10, 2, 0, 2)
            )
        }
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        val settingsBtn = RoundedButton(ApiDocI18n.text("common.settings"))
        settingsBtn.addActionListener {
            val dialog = com.leoleo.apidoc.config.ShowDocSettingsDialog(project)
            if (dialog.showAndGet()) {
                val settings = com.leoleo.apidoc.config.ShowDocSettings.getInstance().state
                exportPath = settings.exportPath
                previewFontSizePt = settings.previewFontSizePt.coerceIn(9, 14)
                updatePreview()
            }
        }
        leftPanel.add(settingsBtn)
        leftPanel.add(ApiDocUi.mutedLabel(ApiDocI18n.text("preview.ruleTip")))
        panel.add(leftPanel, BorderLayout.WEST)

        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }
        val copyAllBtn = RoundedButton(ApiDocI18n.text("preview.copyAll", documents.size), ApiDocUi.success)
        copyAllBtn.addActionListener { copyAll() }
        actionPanel.add(copyAllBtn)
        val exportAllBtn = RoundedButton(ApiDocI18n.text("preview.exportAll", documents.size), ApiDocUi.warning)
        exportAllBtn.addActionListener { exportAll() }
        actionPanel.add(exportAllBtn)
        val exportBtn = RoundedButton(ApiDocI18n.text("common.export"))
        exportBtn.addActionListener { exportCurrent() }
        actionPanel.add(exportBtn)
        val copyBtn = RoundedButton(ApiDocI18n.text("preview.copyToClipboard"), ApiDocUi.primary)
        copyBtn.addActionListener { copyCurrent() }
        actionPanel.add(copyBtn)
        val closeBtn = RoundedButton(ApiDocI18n.text("common.close"))
        closeBtn.addActionListener { close(OK_EXIT_CODE) }
        actionPanel.add(closeBtn)
        panel.add(actionPanel, BorderLayout.EAST)
        return panel
    }

    private fun onInterfaceSelected() {
        val index = interfaceList.selectedIndex
        if (index < 0 || index >= documents.size) return
        saveCurrentEdit()
        loadDocument(index)
    }

    private fun loadDocument(index: Int) {
        if (index < 0 || index >= documents.size) return
        currentDocumentIndex = index
        markdownEditor.text = documents[index].markdown
        markdownEditor.caretPosition = 0
        updatePreview()
    }

    private fun saveCurrentEdit() {
        val index = currentDocumentIndex
        if (index >= 0 && index < documents.size) {
            documents[index].markdown = markdownEditor.text
        }
    }

    private fun updatePreview() {
        if (!previewEnabled) return
        val html = markdownToHtml(markdownEditor.text)
        previewBrowser?.loadHTML(html)
        previewPane?.let {
            it.text = html
            it.caretPosition = 0
        }
    }

    private fun markdownToHtml(markdown: String): String {
        val sb = StringBuilder()
        val theme = getSelectedTheme()
        sb.append("<html><head><meta charset='UTF-8'><style>")
        sb.append(theme.css)
        sb.append(commonPreviewCss(theme))
        sb.append("</style>")
        sb.append(copyScript())
        sb.append("</head><body><div class='doc-card'>")

        var inCodeBlock = false
        var codeBlockLang = ""
        val codeBlockContent = StringBuilder()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    val code = codeBlockContent.toString()
                    sb.append(codeBlockToHtml(code, codeBlockLang == "json", theme))
                    inCodeBlock = false
                    codeBlockContent.clear()
                } else {
                    codeBlockLang = line.trimStart().removePrefix("```").trim().lowercase()
                    inCodeBlock = true
                }
                i++; continue
            }
            if (inCodeBlock) { codeBlockContent.appendLine(line); i++; continue }

            when {
                line.startsWith("### ") -> sb.append("<h3>${escapeHtml(line.substring(4))}</h3>")
                line.startsWith("## ") -> sb.append("<h2>${escapeHtml(line.substring(3))}</h2>")
                line.startsWith("# ") -> sb.append("<h1>${escapeHtml(line.substring(2))}</h1>")
                line.startsWith("|") -> {
                    val table = renderMarkdownTable(lines, i)
                    sb.append(table.first)
                    i = table.second
                    continue
                }
                line.trimStart().startsWith("- ") -> sb.append(renderListItem(line.trimStart().substring(2)))
                line.startsWith("**") -> sb.append("<p>${renderInline(line)}</p>")
                line.isBlank() -> sb.append("<br/>")
                else -> sb.append("<p>${renderInline(line)}</p>")
            }
            i++
        }
        sb.append("</div></body></html>")
        return sb.toString()
    }

    private fun renderMarkdownTable(lines: List<String>, startIndex: Int): Pair<String, Int> {
        var i = startIndex
        val headers = splitMarkdownTableRow(lines[i])
        val rows = mutableListOf<List<String>>()
        i++
        if (i < lines.size && lines[i].contains("----")) i++
        while (i < lines.size && lines[i].startsWith("|")) {
            rows.add(splitMarkdownTableRow(lines[i]))
            i++
        }

        val copyRows = mutableListOf<List<String>>()
        copyRows.add(normalizeTableRow(headers, headers.size).map { plainTableCell(it) })
        rows.forEach { row -> copyRows.add(normalizeTableRow(row, headers.size).map { plainTableCell(it) }) }
        val copyText = copyRows.joinToString("\n") { row ->
            row.joinToString("\t") { excelSafeCell(it.replace("\t", " ").replace("\r", " ").replace("\n", " ")) }
        }

        val sb = StringBuilder()
        sb.append("<div class='table-card'>")
        sb.append("<button class='copy-table-btn' onclick='copySiblingSource(this)'>${ApiDocI18n.text("common.copy")}</button>")
        sb.append("<textarea class='copy-source'>${escapeHtml(copyText)}</textarea>")
        sb.append("<table class='data-table' cellpadding='6' cellspacing='0' width='100%'>")
        sb.append("<tr>")
        val descriptionColumnIndex = headers.indexOfFirst { isDescriptionColumn(it) }
        headers.forEachIndexed { idx, h ->
            val cell = h.trim().takeIf { it.isNotBlank() }?.let { renderInline(it) } ?: "&nbsp;"
            if (idx == 0 || idx == descriptionColumnIndex) sb.append("<th style='text-align:left'>$cell</th>")
            else sb.append("<th>$cell</th>")
        }
        sb.append("</tr>")
        rows.forEach { row ->
            sb.append("<tr>")
            row.forEachIndexed { idx, c ->
                val cell = c.trim().takeIf { it.isNotBlank() }?.let { renderInline(it) } ?: "&nbsp;"
                if (idx == 0 || idx == descriptionColumnIndex) sb.append("<td style='text-align:left'>$cell</td>")
                else sb.append("<td>$cell</td>")
            }
            repeat((headers.size - row.size).coerceAtLeast(0)) { sb.append("<td>&nbsp;</td>") }
            sb.append("</tr>")
        }
        sb.append("</table></div>")
        return sb.toString() to i
    }

    private fun isDescriptionColumn(text: String): Boolean {
        val cell = plainTableCell(text).lowercase()
        return cell == "说明" || cell == "描述" || cell == "description" || cell == "desc"
    }

    private fun splitMarkdownTableRow(line: String): List<String> {
        val cols = line.split("|")
        return if (cols.size > 2) cols.subList(1, cols.size - 1) else cols.filter { it.isNotBlank() }
    }

    private fun normalizeTableRow(row: List<String>, size: Int): List<String> {
        if (row.size >= size) return row
        return row + List(size - row.size) { "" }
    }

    private fun plainTableCell(text: String): String {
        return text.trim()
            .replace(Regex("\\*\\*(.+?)\\*\\*")) { it.groupValues[1] }
            .replace(Regex("`(.+?)`")) { it.groupValues[1] }
            .replace(Regex("<[^>]+>"), "")
            .trim()
    }

    private fun excelSafeCell(text: String): String {
        if (text.isBlank()) return text
        val trimmed = text.trimStart()
        return if (trimmed.firstOrNull() in listOf('=', '+', '-', '@')) "'$text" else text
    }

    private fun renderListItem(content: String): String {
        val trimmed = content.trim()
        val codeUrl = Regex("^`([^`]+)`$").matchEntire(trimmed)?.groupValues?.getOrNull(1)
        if (codeUrl != null && isRequestUrl(codeUrl)) {
            return "<ul><li><span class='url-copy-row'>${renderInline(trimmed)}" +
                "<textarea class='copy-source'>${escapeHtml(codeUrl)}</textarea>" +
                "<button class='inline-copy-btn' onclick='copySiblingSource(this)'>${ApiDocI18n.text("common.copy")}</button>" +
                "</span></li></ul>"
        }
        return "<ul><li>${renderInline(content)}</li></ul>"
    }

    private fun isRequestUrl(text: String): Boolean {
        return text.startsWith("/") || text.startsWith("http://") || text.startsWith("https://")
    }

    private fun codeBlockToHtml(code: String, isJson: Boolean, theme: PreviewTheme): String {
        val sb = StringBuilder()
        val displayCode = if (isJson) formatJsonLikeCode(code) else code
        sb.append("<div class='code-card'>")
        if (isJson) {
            sb.append("<button class='copy-code-btn' onclick='copyCodeBlock(this)'>${ApiDocI18n.text("common.copy")}</button>")
            sb.append("<textarea class='copy-source'>${escapeHtml(displayCode)}</textarea>")
        }
        sb.append("<font face='Consolas, Monospaced' color='${theme.preCodeColor}'>")
        val lines = displayCode.removeSuffix("\n").lines()
        for (line in lines) {
            val renderedLine = if (isJson) {
                highlightJsonLine(line, theme)
            } else {
                preserveCodeSpaces(escapeHtml(line))
            }
            sb.append(renderedLine.ifBlank { "&nbsp;" })
            sb.append("<br/>")
        }
        sb.append("</font></div>")
        return sb.toString()
    }

    private fun commonPreviewCss(theme: PreviewTheme): String {
        val documentBg = extractBodyBackground(theme)
        val bodyFontSize = previewFontSizePt
        val h1FontSize = previewFontSizePt + 8
        val h2FontSize = previewFontSizePt + 4
        val h3FontSize = previewFontSizePt + 1
        val codeFontSize = (previewFontSizePt - 1).coerceAtLeast(10)
        return """
            body {
                box-sizing: border-box;
                margin: 0;
                padding: 14px;
                background-color: transparent;
                font-size: ${bodyFontSize}pt !important;
            }
            .doc-card {
                box-sizing: border-box;
                width: 100%;
                min-height: calc(100vh - 28px);
                padding: 22px 24px;
                background-color: $documentBg;
                border-radius: 12px;
                overflow: hidden;
                border: 1px solid rgba(120, 130, 145, 0.18);
                box-shadow: 0 8px 26px rgba(0, 0, 0, 0.10);
            }
            .doc-card h1 {
                font-size: ${h1FontSize}pt !important;
            }
            .doc-card h2 {
                font-size: ${h2FontSize}pt !important;
            }
            .doc-card h3 {
                font-size: ${h3FontSize}pt !important;
            }
            .doc-card p,
            .doc-card li,
            .doc-card td,
            .doc-card th {
                font-size: ${bodyFontSize}pt !important;
            }
            .code-card {
                position: relative;
                box-sizing: border-box;
                width: 100%;
                margin: 12px 0 18px 0;
                padding: 22px 24px;
                background-color: ${theme.preCodeBg};
                border-radius: 12px;
                overflow-x: auto;
                line-height: 1.72;
                text-align: left;
                border: 1px solid rgba(120, 130, 145, 0.22);
                font-size: ${codeFontSize}pt !important;
            }
            .copy-code-btn,
            .copy-table-btn,
            .inline-copy-btn {
                border: 1px solid rgba(140, 150, 170, 0.32);
                border-radius: 8px;
                cursor: pointer;
                font-family: 'Microsoft YaHei', sans-serif;
                font-size: 12px;
                line-height: 1.35;
                transition: background-color 0.15s ease, border-color 0.15s ease;
            }
            .copy-code-btn {
                position: absolute;
                top: 10px;
                right: 12px;
                padding: 4px 10px;
                background: rgba(255, 255, 255, 0.10);
                color: ${theme.preCodeColor};
            }
            .copy-code-btn:hover,
            .copy-table-btn:hover,
            .inline-copy-btn:hover {
                background: rgba(255, 255, 255, 0.18);
                border-color: rgba(140, 150, 170, 0.50);
            }
            .copy-source {
                display: none;
            }
            .url-copy-row {
                display: inline-flex;
                align-items: center;
                gap: 8px;
                flex-wrap: wrap;
            }
            .inline-copy-btn {
                padding: 3px 9px;
                background: rgba(95, 130, 190, 0.14);
                color: inherit;
            }
            .table-card {
                position: relative;
                box-sizing: border-box;
                width: 100%;
                margin: 12px 0 18px 0;
                padding-top: 36px;
                border-radius: 12px;
                overflow: hidden;
                border: 1px solid rgba(120, 130, 145, 0.28);
                background: rgba(120, 130, 145, 0.05);
            }
            .copy-table-btn {
                position: absolute;
                top: 8px;
                right: 10px;
                z-index: 2;
                padding: 4px 10px;
                background: rgba(95, 130, 190, 0.14);
                color: inherit;
            }
            .data-table {
                margin: 0;
                border-collapse: collapse;
                border-spacing: 0;
            }
            .data-table th,
            .data-table td {
                border-top: none;
            }
        """.trimIndent()
    }

    private fun copyScript(): String {
        val copiedText = escapeJs(ApiDocI18n.text("common.copied"))
        return """
            <script>
                function copyCodeBlock(button) {
                    var card = button.parentElement;
                    var source = card.querySelector('.copy-source');
                    if (!source) return;
                    copyTextWithFeedback(source.value, button);
                }
                function copySiblingSource(button) {
                    var parent = button.parentElement;
                    var source = parent ? parent.querySelector('.copy-source') : null;
                    if (!source) return;
                    copyTextWithFeedback(source.value, button);
                }
                function copyTextWithFeedback(text, button) {
                    function done() {
                        var oldText = button.innerText;
                        button.innerText = '$copiedText';
                        setTimeout(function(){ button.innerText = oldText; }, 1200);
                    }
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText(text).then(done).catch(function(){ fallbackCopy(text, done); });
                    } else {
                        fallbackCopy(text, done);
                    }
                }
                function fallbackCopy(text, done) {
                    var textarea = document.createElement('textarea');
                    textarea.value = text;
                    textarea.style.position = 'fixed';
                    textarea.style.left = '-9999px';
                    document.body.appendChild(textarea);
                    textarea.select();
                    try { document.execCommand('copy'); done(); } catch (e) {}
                    document.body.removeChild(textarea);
                }
            </script>
        """.trimIndent()
    }

    private fun escapeJs(text: String): String {
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
    }

    private fun extractBodyBackground(theme: PreviewTheme): String {
        return Regex("background-color:\\s*(#[0-9a-fA-F]{6})").find(theme.css)?.groupValues?.getOrNull(1)
            ?: "#ffffff"
    }

    private fun formatJsonLikeCode(code: String): String {
        val tokens = tokenizeJsonLikeCode(code)
        if (tokens.isEmpty()) return code

        val sb = StringBuilder()
        var indent = 0
        var currentLineHasContent = false

        fun appendIndentIfNeeded() {
            if (!currentLineHasContent) {
                repeat(indent.coerceAtLeast(0)) { sb.append("    ") }
                currentLineHasContent = true
            }
        }

        fun newLine() {
            while (sb.endsWith(" ")) {
                sb.setLength(sb.length - 1)
            }
            if (!sb.endsWith("\n")) sb.append('\n')
            currentLineHasContent = false
        }

        var tokenIndex = 0
        while (tokenIndex < tokens.size) {
            val token = tokens[tokenIndex]
            val nextToken = tokens.getOrNull(tokenIndex + 1)
            when {
                token == "{" || token == "[" -> {
                    appendIndentIfNeeded()
                    sb.append(token)
                    indent++
                    newLine()
                }
                token == "}" || token == "]" -> {
                    if (currentLineHasContent) newLine()
                    indent--
                    appendIndentIfNeeded()
                    sb.append(token)
                }
                token == "," -> {
                    appendIndentIfNeeded()
                    sb.append(",")
                    if (nextToken?.startsWith("//") == true) {
                        sb.append(" ")
                    } else {
                        newLine()
                    }
                }
                token == ":" -> {
                    appendIndentIfNeeded()
                    sb.append(" : ")
                }
                token.startsWith("//") -> {
                    appendIndentIfNeeded()
                    if (!sb.endsWith(" ")) sb.append(" ")
                    sb.append(token.trim())
                    newLine()
                }
                else -> {
                    appendIndentIfNeeded()
                    sb.append(token)
                }
            }
            tokenIndex++
        }

        return sb.toString().trimEnd()
    }

    private fun tokenizeJsonLikeCode(code: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var escape = false
        var i = 0

        fun flushCurrent() {
            val text = current.toString().trim()
            if (text.isNotEmpty()) tokens.add(text)
            current.clear()
        }

        while (i < code.length) {
            val ch = code[i]

            if (inString) {
                current.append(ch)
                when {
                    escape -> escape = false
                    ch == '\\' -> escape = true
                    ch == '"' -> {
                        inString = false
                        flushCurrent()
                    }
                }
                i++
                continue
            }

            if (ch == '/' && i + 1 < code.length && code[i + 1] == '/') {
                flushCurrent()
                val commentEnd = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                tokens.add(code.substring(i, commentEnd).trim())
                i = commentEnd
                continue
            }

            when {
                ch == '"' -> {
                    flushCurrent()
                    current.append(ch)
                    inString = true
                }
                ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ':' || ch == ',' -> {
                    flushCurrent()
                    tokens.add(ch.toString())
                }
                ch.isWhitespace() -> {
                    flushCurrent()
                }
                else -> current.append(ch)
            }
            i++
        }
        flushCurrent()
        return tokens
    }

    private fun highlightJson(json: String, theme: PreviewTheme): String {
        val sb = StringBuilder()
        for (line in json.lines()) {
            sb.appendLine(highlightJsonLine(line, theme))
        }
        return sb.toString()
    }

    private fun highlightJsonLine(line: String, theme: PreviewTheme): String {
        var highlighted = preserveCodeSpaces(escapeHtml(line))
        val commentIdx = highlighted.indexOf("//")
        var mainPart = highlighted
        var commentPart = ""
        if (commentIdx >= 0) {
            mainPart = highlighted.substring(0, commentIdx)
            commentPart = "<font color='${theme.jsonCommentColor}'><i>${highlighted.substring(commentIdx)}</i></font>"
        }

        val spacing = "(?:&nbsp;|\\s)*"
        mainPart = mainPart.replace(
            Regex("&quot;([^&]+?)&quot;($spacing:)"),
            "<font color='${theme.jsonKeyColor}'>&quot;\$1&quot;</font>\$2"
        )
        mainPart = mainPart.replace(
            Regex(":($spacing)&quot;(.*?)&quot;"),
            ":\$1<font color='${theme.jsonStringColor}'>&quot;\$2&quot;</font>"
        )
        mainPart = mainPart.replace(
            Regex(":($spacing)(\\d+)"),
            ":\$1<font color='${theme.jsonNumberColor}'>\$2</font>"
        )
        mainPart = mainPart.replace("{", "<font color='${theme.jsonBracketColor}'>{</font>")
        mainPart = mainPart.replace("}", "<font color='${theme.jsonBracketColor}'>}</font>")
        mainPart = mainPart.replace("[", "<font color='${theme.jsonBracketColor}'>[</font>")
        mainPart = mainPart.replace("]", "<font color='${theme.jsonBracketColor}'>]</font>")
        return "$mainPart$commentPart"
    }

    private fun preserveCodeSpaces(text: String): String {
        return text.replace(" ", "&nbsp;").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
    }

    private fun getSelectedTheme(): PreviewTheme {
        val index = if (::themeComboBox.isInitialized) themeComboBox.selectedIndex else 0
        return PreviewTheme.values()[index]
    }

    private fun renderInline(text: String): String {
        var r = escapeHtml(text)
        r = r.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>\$1</strong>")
        r = r.replace(Regex("`(.+?)`"), "<code>\$1</code>")
        return r
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun copyCurrent() {
        saveCurrentEdit()
        val indices = getTargetIndices()
        if (indices.isEmpty()) return
        val content = indices.joinToString("\n\n---\n\n") { documents[it].markdown }
        copyToClipboard(content)
        showTip(ApiDocI18n.text("preview.copiedCurrent", indices.size))
    }

    private fun copyAll() {
        saveCurrentEdit()
        copyToClipboard(documents.joinToString("\n\n---\n\n") { it.markdown })
        showTip(ApiDocI18n.text("preview.copiedAll", documents.size))
    }

    private fun copyUrlNameList() {
        saveCurrentEdit()
        val lines = documents.mapNotNull { doc ->
            val url = extractDocumentUrl(doc.markdown)
            if (url.isBlank()) null else "$url   ${doc.title}"
        }
        if (lines.isEmpty()) return
        copyToClipboard(lines.joinToString("\n"))
        showTip(ApiDocI18n.text("preview.copiedUrlList", lines.size))
    }

    private fun extractDocumentUrl(markdown: String): String {
        val urlLineRegex = Regex("^\\s*-\\s*`([^`]+)`\\s*$", RegexOption.MULTILINE)
        return urlLineRegex.findAll(markdown)
            .map { it.groupValues[1].trim() }
            .firstOrNull { isRequestUrl(it) }
            ?: ""
    }

    private fun exportCurrent() {
        saveCurrentEdit()
        val indices = getTargetIndices()
        if (indices.isEmpty()) return
        val dir = chooseExportDir() ?: return
        exportDocuments(dir, indices.map { documents[it] })
        showTip(ApiDocI18n.text("preview.exportedCurrent", indices.size))
    }

    private fun exportAll() {
        saveCurrentEdit()
        val dir = chooseExportDir() ?: return
        exportDocuments(dir, documents)
        showTip(ApiDocI18n.text("preview.exportedAll", documents.size))
    }

    private fun getTargetIndices(): List<Int> {
        val selected = interfaceList.selectedIndices.filter { it in documents.indices }
        if (selected.isNotEmpty()) return selected
        return if (currentDocumentIndex in documents.indices) listOf(currentDocumentIndex) else emptyList()
    }

    private fun exportDocuments(dir: File, items: List<DocItem>) {
        val usedNames = mutableSetOf<String>()
        for (doc in items) {
            val file = nextAvailableFile(dir, sanitizeFileName(doc.title).ifBlank { "api" }, usedNames)
            file.writeText(doc.markdown, Charsets.UTF_8)
        }
    }

    private fun nextAvailableFile(dir: File, baseName: String, usedNames: MutableSet<String>): File {
        var index = 0
        while (true) {
            val suffix = if (index == 0) "" else "_$index"
            val fileName = "$baseName$suffix.md"
            val file = File(dir, fileName)
            if (fileName !in usedNames && !file.exists()) {
                usedNames.add(fileName)
                return file
            }
            index++
        }
    }

    private fun chooseExportDir(): File? {
        val chooser = JFileChooser(if (exportPath.isNotBlank()) exportPath else System.getProperty("user.home"))
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        return if (chooser.showOpenDialog(contentPanel) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.also {
                exportPath = it.absolutePath
                com.leoleo.apidoc.config.ShowDocSettings.getInstance().state.exportPath = exportPath
            }
        } else {
            null
        }
    }
    private fun copyToClipboard(content: String) { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(content), null) }
    private fun showTip(msg: String) { JOptionPane.showMessageDialog(contentPanel, msg, "ApiDoc", JOptionPane.INFORMATION_MESSAGE) }
    private fun sanitizeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    override fun createActions(): Array<Action> = emptyArray()

    /**
     * 创建窗口控制按钮（最小化/全屏/关闭）
     */
    private fun createWindowControlButton(type: String, action: () -> Unit): JButton {
        return object : JButton() {
            init {
                preferredSize = Dimension(36, 28)
                isFocusPainted = false
                isBorderPainted = false
                isContentAreaFilled = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = when (type) {
                    "minimize" -> ApiDocI18n.text("preview.minimize")
                    "maximize" -> ApiDocI18n.text("preview.maximize")
                    "close" -> ApiDocI18n.text("common.close")
                    else -> ""
                }
                addActionListener { action() }
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                // hover 背景
                if (model.isRollover) {
                    g2.color = if (type == "close") Color(0xE8, 0x1C, 0x1C, 80) else Color(0x55, 0x55, 0x55, 80)
                    g2.fillRoundRect(2, 2, width - 4, height - 4, 6, 6)
                }

                // 绘制图标
                g2.color = if (model.isRollover && type == "close") Color(0xFF, 0x55, 0x55) else Color(0xAA, 0xAA, 0xAA)
                g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val cx = width / 2
                val cy = height / 2

                when (type) {
                    "minimize" -> {
                        // 横线 —
                        g2.drawLine(cx - 6, cy, cx + 6, cy)
                    }
                    "maximize" -> {
                        // 方框 □
                        g2.drawRect(cx - 5, cy - 5, 10, 10)
                    }
                    "close" -> {
                        // 叉号 ×
                        g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5)
                        g2.drawLine(cx + 5, cy - 5, cx - 5, cy + 5)
                    }
                }
                g2.dispose()
            }
        }
    }

    enum class PreviewTheme(val css: String, val jsonKeyColor: String, val jsonStringColor: String, val jsonCommentColor: String, val jsonNumberColor: String, val jsonBracketColor: String, val preCodeBg: String, val preCodeColor: String) {
        DARK_IDEA("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #233043; background-color: #f6f8fb; }
            h1 { color: #2f68c5; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #d5def0; padding-bottom: 8px; }
            h2 { color: #2f68c5; font-size: 19pt; font-weight: bold; }
            h3 { color: #2f68c5; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #edf2fb; color: #2c3a4d; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #cbd7ea; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #e3e9f3; color: #334155; }
            code { background-color: #dfe9fa; color: #2f68c5; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #ba5d27; font-weight: bold; }
        """.trimIndent(), "#8ab4f8", "#b6e3a1", "#8b949e", "#f2cc60", "#c9d1d9", "#10151d", "#c9d1d9"),

        DARK_BLUE("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #24292f; background-color: #ffffff; }
            h1 { color: #0969da; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #d8dee4; padding-bottom: 8px; }
            h2 { color: #0969da; font-size: 19pt; font-weight: bold; }
            h3 { color: #1a7f37; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #f6f8fa; color: #24292f; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #d8dee4; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #d8dee4; color: #24292f; }
            code { background-color: #eff4ff; color: #0969da; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #cf222e; font-weight: bold; }
        """.trimIndent(), "#79c0ff", "#a5d6ff", "#8b949e", "#f2cc60", "#c9d1d9", "#0d1117", "#c9d1d9"),

        DARK_GREEN("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #c9d1d9; background-color: #1f2430; }
            h1 { color: #61afef; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #3b4252; padding-bottom: 8px; }
            h2 { color: #61afef; font-size: 19pt; font-weight: bold; }
            h3 { color: #56b6c2; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #2b3242; color: #dbe5f1; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #3b4252; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #343c4d; color: #c9d1d9; }
            code { background-color: #2b3242; color: #e5c07b; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #e06c75; font-weight: bold; }
        """.trimIndent(), "#61afef", "#98c379", "#7f848e", "#d19a66", "#c678dd", "#161b22", "#c9d1d9"),

        LIGHT_CLASSIC("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #cdd6f4; background-color: #1e293b; }
            h1 { color: #89ddff; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #334155; padding-bottom: 8px; }
            h2 { color: #89ddff; font-size: 19pt; font-weight: bold; }
            h3 { color: #c3e88d; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #273449; color: #e2e8f0; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #3b4a61; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #334155; color: #cdd6f4; }
            code { background-color: #273449; color: #f78c6c; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #ffcb6b; font-weight: bold; }
        """.trimIndent(), "#82aaff", "#c3e88d", "#69758c", "#f78c6c", "#89ddff", "#111827", "#d7deee"),

        GITHUB("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #d8dee9; background-color: #2e3440; }
            h1 { color: #88c0d0; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #4c566a; padding-bottom: 8px; }
            h2 { color: #81a1c1; font-size: 19pt; font-weight: bold; }
            h3 { color: #a3be8c; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #3b4252; color: #eceff4; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #4c566a; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #434c5e; color: #d8dee9; }
            code { background-color: #3b4252; color: #ebcb8b; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #bf616a; font-weight: bold; }
        """.trimIndent(), "#88c0d0", "#a3be8c", "#616e88", "#b48ead", "#d08770", "#242933", "#d8dee9"),

        MONOKAI("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #f8f8f2; background-color: #282a36; }
            h1 { color: #bd93f9; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #44475a; padding-bottom: 8px; }
            h2 { color: #8be9fd; font-size: 19pt; font-weight: bold; }
            h3 { color: #50fa7b; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #44475a; color: #f8f8f2; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #6272a4; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #44475a; color: #f8f8f2; }
            code { background-color: #44475a; color: #f1fa8c; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #ff79c6; font-weight: bold; }
        """.trimIndent(), "#8be9fd", "#f1fa8c", "#6272a4", "#bd93f9", "#ff79c6", "#1e1f29", "#f8f8f2"),

        NORD("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #586e75; background-color: #fdf6e3; }
            h1 { color: #268bd2; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #eee8d5; padding-bottom: 8px; }
            h2 { color: #268bd2; font-size: 19pt; font-weight: bold; }
            h3 { color: #859900; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #eee8d5; color: #586e75; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #d6cfb8; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #eee8d5; color: #586e75; }
            code { background-color: #eee8d5; color: #cb4b16; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #dc322f; font-weight: bold; }
        """.trimIndent(), "#268bd2", "#2aa198", "#93a1a1", "#cb4b16", "#6c71c4", "#002b36", "#eee8d5"),

        DRACULA("""
            body { font-family: 'Microsoft YaHei', sans-serif; color: #c8d3f5; background-color: #1a1b26; }
            h1 { color: #7aa2f7; font-size: 24pt; font-weight: bold; border-bottom: 2px solid #2f334d; padding-bottom: 8px; }
            h2 { color: #7aa2f7; font-size: 19pt; font-weight: bold; }
            h3 { color: #9ece6a; font-size: 15pt; font-weight: bold; }
            table { width: 100%; border-spacing: 0; }
            th { background-color: #24283b; color: #c8d3f5; font-weight: bold; padding: 10px; text-align: center; border-bottom: 1px solid #3b4261; }
            td { padding: 10px; text-align: center; border-bottom: 1px solid #2f334d; color: #c8d3f5; }
            code { background-color: #24283b; color: #e0af68; border-radius: 6px; padding: 2px 6px; }
            ul { padding-left: 24px; }
            strong { color: #f7768e; font-weight: bold; }
        """.trimIndent(), "#7dcfff", "#9ece6a", "#565f89", "#e0af68", "#bb9af7", "#11121a", "#c8d3f5")
    }
}
