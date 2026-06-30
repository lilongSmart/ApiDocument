package com.leoleo.apidoc.action

import com.leoleo.apidoc.config.ShowDocSettings
import com.leoleo.apidoc.generator.ShowDocGenerator
import com.leoleo.apidoc.i18n.ApiDocI18n
import com.leoleo.apidoc.parser.ControllerParser
import com.leoleo.apidoc.ui.ShowDocPreviewDialog
import com.leoleo.apidoc.util.GitVersionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile

/**
 * 从整个Controller类生成接口文档的Action
 */
class GenerateShowDocAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        if (psiFile !is PsiJavaFile) {
            Messages.showWarningDialog(project, ApiDocI18n.text("warning.javaOnly"), "ApiDoc")
            return
        }

        val psiClass = psiFile.classes.firstOrNull() ?: run {
            Messages.showWarningDialog(project, ApiDocI18n.text("warning.noJavaClass"), "ApiDoc")
            return
        }

        if (!isController(psiClass)) {
            Messages.showWarningDialog(project, ApiDocI18n.text("warning.notController"), "ApiDoc")
            return
        }

        // 获取配置
        val settings = ShowDocSettings.getInstance().state

        // 获取 context-path
        val contextPath = com.leoleo.apidoc.util.ContextPathUtil.getContextPath(project, psiFile.virtualFile)

        // 解析接口（传入排除的父类列表和contextPath）
        val parser = ControllerParser(settings.safeExcludedParentClasses(), contextPath, settings.safeExcludedFields())
        val apiInfoList = parser.parseClass(psiClass)

        if (apiInfoList.isEmpty()) {
            Messages.showWarningDialog(project, ApiDocI18n.text("warning.noApiMethods"), "ApiDoc")
            return
        }

        val currentVersion = if (settings.autoVersionFromGit) {
            GitVersionUtil.getCurrentVersion(project, settings.versionPrefix, settings.versionRegex)
        } else ""

        // 生成文档列表
        val generator = ShowDocGenerator(
            author = settings.author,
            currentVersion = currentVersion,
            productVersion = settings.productVersion,
            showCallLocation = settings.showCallLocation,
            showRequestJson = settings.showRequestJson,
            showResponseJson = settings.showResponseJson,
            showJsonComment = settings.showJsonComment
        )

        val docItems = apiInfoList.map { apiInfo ->
            ShowDocPreviewDialog.DocItem(
                title = apiInfo.title,
                markdown = generator.generate(apiInfo)
            )
        }

        ShowDocPreviewDialog(project, docItems, psiClass.name ?: "API").show()
    }

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = psiFile is PsiJavaFile
        e.presentation.text = "ApiDoc - ${ApiDocI18n.text("action.generateController")}"
        e.presentation.description = ApiDocI18n.text("gutter.classTooltip")
    }

    private fun isController(psiClass: PsiClass): Boolean {
        val annotations = psiClass.modifierList?.annotations ?: return false
        return annotations.any { anno ->
            val name = anno.qualifiedName ?: ""
            name.contains("Controller") || name.contains("RestController")
        }
    }
}
