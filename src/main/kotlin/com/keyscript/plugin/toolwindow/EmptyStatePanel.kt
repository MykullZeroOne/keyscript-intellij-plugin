package com.keyscript.plugin.toolwindow

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Creates a centered empty state panel with optional action button.
 * Follows IntelliJ HIG for empty state design.
 */
class EmptyStatePanel(
    message: String,
    detail: String? = null,
    actionText: String? = null,
    action: (() -> Unit)? = null
) : JPanel(GridBagLayout()) {
    init {
        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false

            add(JBLabel(message).apply {
                foreground = UIUtil.getLabelDisabledForeground()
                font = JBUI.Fonts.label(14f)
                horizontalAlignment = SwingConstants.CENTER
                alignmentX = CENTER_ALIGNMENT
            })

            if (detail != null) {
                add(Box.createVerticalStrut(4))
                add(JBLabel(detail).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = JBUI.Fonts.smallFont()
                    horizontalAlignment = SwingConstants.CENTER
                    alignmentX = CENTER_ALIGNMENT
                })
            }

            if (actionText != null && action != null) {
                add(Box.createVerticalStrut(12))
                add(JButton(actionText).apply {
                    alignmentX = CENTER_ALIGNMENT
                    addActionListener { action() }
                })
            }
        }

        add(center, GridBagConstraints())
        border = JBUI.Borders.empty(20)
    }
}
