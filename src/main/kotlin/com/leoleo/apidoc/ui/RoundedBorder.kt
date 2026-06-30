package com.leoleo.apidoc.ui

import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.AbstractBorder

object ApiDocUi {
    val accent: Color
        get() = UIManager.getColor("Component.focusColor")
            ?: UIManager.getColor("Link.activeForeground")
            ?: Color(0x4B, 0x8F, 0xD9)
    val primary = Color(0x4B, 0x7F, 0xC9)
    val success = Color(0x3F, 0x8F, 0x72)
    val warning = Color(0xC4, 0x7B, 0x3C)
    val danger = Color(0xBE, 0x5A, 0x5A)

    fun panelBg(): Color = UIManager.getColor("Panel.background") ?: Color(0x2B, 0x2B, 0x2B)
    fun contentBg(): Color = UIManager.getColor("TextArea.background") ?: Color(0x30, 0x30, 0x30)
    fun textColor(): Color = UIManager.getColor("Label.foreground") ?: Color(0xDD, 0xDD, 0xDD)
    fun mutedText(): Color = UIManager.getColor("Label.disabledForeground") ?: Color(0x8C, 0x8C, 0x8C)
    fun borderColor(): Color = UIManager.getColor("Component.borderColor") ?: Color(0x5A, 0x5A, 0x5A)
    fun buttonFont(): Font {
        val base = UIManager.getFont("Button.font") ?: UIManager.getFont("Label.font") ?: Font("Microsoft YaHei UI", Font.PLAIN, 13)
        val family = when {
            base.family.contains("Dialog", ignoreCase = true) -> "Microsoft YaHei UI"
            else -> base.family
        }
        return Font(family, Font.PLAIN, 12).deriveFont(12.5f)
    }

    fun titleLabel(text: String): JLabel = JLabel(text).apply {
        foreground = accent
        font = font.deriveFont(Font.BOLD, 13f)
        border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
    }

    fun mutedLabel(text: String): JLabel = JLabel(text).apply {
        foreground = mutedText()
        font = font.deriveFont(12f)
    }

    fun styleList(list: JList<*>) {
        list.visibleRowCount = 12
        list.fixedCellHeight = 30
        list.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    }
}

/**
 * 圆角边框
 */
class RoundedBorder(
    private val color: Color = ApiDocUi.borderColor(),
    private val radius: Int = 10,
    private val thickness: Int = 1
) : AbstractBorder() {

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        g2.stroke = BasicStroke(thickness.toFloat())
        g2.draw(RoundRectangle2D.Double(
            x + thickness / 2.0, y + thickness / 2.0,
            width - thickness.toDouble(), height - thickness.toDouble(),
            radius.toDouble(), radius.toDouble()
        ))
        g2.dispose()
    }

    override fun getBorderInsets(c: Component): Insets {
        val pad = radius / 3 + thickness + 2
        return Insets(pad, pad, pad, pad)
    }

    override fun getBorderInsets(c: Component, insets: Insets): Insets {
        val pad = radius / 3 + thickness + 2
        insets.set(pad, pad, pad, pad)
        return insets
    }
}

/**
 * 圆角带标题的边框面板
 */
class RoundedTitledPanel(title: String, content: JComponent) : JPanel(BorderLayout()) {
    private val arcSize = 10

    init {
        isOpaque = false
        border = BorderFactory.createCompoundBorder(
            RoundedBorder(ApiDocUi.borderColor(), arcSize, 1),
            BorderFactory.createEmptyBorder(12, 14, 14, 14)
        )

        val titleLabel = ApiDocUi.titleLabel(title).apply {
            foreground = ApiDocUi.accent
        }
        add(titleLabel, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = ApiDocUi.panelBg()
        g2.fillRoundRect(0, 0, width, height, arcSize, arcSize)
        g2.dispose()
    }
}

/**
 * 圆角按钮
 */
class RoundedButton(text: String, bgColor: Color? = null, fgColor: Color = Color.WHITE) : JButton(text) {
    private val bgCol: Color? = bgColor

    init {
        isFocusPainted = false
        isContentAreaFilled = false
        isBorderPainted = false
        foreground = if (bgColor != null) fgColor else ApiDocUi.textColor()
        font = ApiDocUi.buttonFont()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        margin = Insets(6, 15, 6, 15)
        val textWidth = getFontMetrics(font).stringWidth(text)
        val buttonSize = Dimension((textWidth + 36).coerceAtLeast(78), 32)
        preferredSize = buttonSize
        minimumSize = buttonSize
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val arc = 10

        val bg = if (bgCol != null) {
            when {
                model.isPressed -> bgCol.darker()
                model.isRollover -> brighter(bgCol, 0.15f)
                else -> bgCol
            }
        } else {
            when {
                model.isPressed -> ApiDocUi.borderColor().darker()
                model.isRollover -> brighter(ApiDocUi.contentBg(), 0.10f)
                else -> ApiDocUi.contentBg()
            }
        }
        g2.color = bg
        g2.fillRoundRect(0, 0, width, height, arc, arc)

        // 无背景色的按钮画边框
        if (bgCol == null) {
            g2.color = ApiDocUi.borderColor()
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        }

        g2.dispose()

        // 绘制文字（开启抗锯齿）
        val g3 = g.create() as Graphics2D
        g3.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val fm = g3.getFontMetrics(font)
        val textX = (width - fm.stringWidth(text)) / 2
        val textY = (height - fm.height) / 2 + fm.ascent
        g3.color = foreground
        g3.font = font
        g3.drawString(text, textX, textY)
        g3.dispose()
    }

    private fun brighter(color: Color, factor: Float): Color {
        val r = (color.red + (255 - color.red) * factor).toInt().coerceIn(0, 255)
        val gVal = (color.green + (255 - color.green) * factor).toInt().coerceIn(0, 255)
        val b = (color.blue + (255 - color.blue) * factor).toInt().coerceIn(0, 255)
        return Color(r, gVal, b)
    }
}

/**
 * 圆角输入框
 */
class RoundedTextField(columns: Int = 20) : JTextField(columns) {
    private val arcSize = 12

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = ApiDocUi.contentBg()
        g2.fillRoundRect(0, 0, width, height, arcSize, arcSize)
        g2.dispose()
        super.paintComponent(g)
    }

    override fun paintBorder(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = if (hasFocus()) ApiDocUi.primary else ApiDocUi.borderColor()
        g2.drawRoundRect(0, 0, width - 1, height - 1, arcSize, arcSize)
        g2.dispose()
    }
}
