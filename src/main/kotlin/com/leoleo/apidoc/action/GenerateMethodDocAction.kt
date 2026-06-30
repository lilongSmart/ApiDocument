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
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

/**
 * 为当前光标所在方法生成接口文档的Action
 */
class GenerateMethodDocAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        if (psiFile !is PsiJavaFile) {
            Messages.showWarningDialog(project, ApiDocI18n.text("warning.javaOnly"), "ApiDoc")
            return
        }

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
        val psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: run {
            Messages.showWarningDialog(project, ApiDocI18n.text("warning.caretInMethod"), "ApiDoc")
            return
        }

        val psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass::class.java)
        val classUrl = getClassRequestUrl(psiClass)

        // 获取配置
        val settings = ShowDocSettings.getInstance().state

        // 获取 context-path
        val contextPath = com.leoleo.apidoc.util.ContextPathUtil.getContextPath(project, psiFile.virtualFile)

        // 解析接口
        val parser = ControllerParser(settings.safeExcludedParentClasses(), contextPath, settings.safeExcludedFields())
        val apiInfo = parser.parseMethod(psiMethod, classUrl) ?: run {
            Messages.showWarningDialog(project, ApiDocI18n.text("warning.notApiMethod"), "ApiDoc")
            return
        }

        val currentVersion = if (settings.autoVersionFromGit) {
            GitVersionUtil.getCurrentVersion(project, settings.versionPrefix, settings.versionRegex)
        } else ""

        val generator = ShowDocGenerator(
            author = settings.author,
            currentVersion = currentVersion,
            productVersion = settings.productVersion,
            showCallLocation = settings.showCallLocation,
            showRequestJson = settings.showRequestJson,
            showResponseJson = settings.showResponseJson,
            showJsonComment = settings.showJsonComment
        )
        val document = generator.generate(apiInfo)

        ShowDocPreviewDialog(project, document, apiInfo.title).show()
    }

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = psiFile is PsiJavaFile && editor != null
        e.presentation.text = "ApiDoc - ${ApiDocI18n.text("action.generateMethod")}"
        e.presentation.description = ApiDocI18n.text("gutter.methodTooltip")
    }

    private fun getClassRequestUrl(psiClass: PsiClass?): String {
        if (psiClass == null) return ""
        val annotations = psiClass.modifierList?.annotations ?: return ""
        val mappingAnnotation = annotations.find { anno ->
            val name = anno.qualifiedName ?: ""
            name.contains("RequestMapping")
        } ?: return ""
        val valueAttr = mappingAnnotation.findAttributeValue("value")
            ?: mappingAnnotation.findAttributeValue("path")
            ?: return ""
        return when (valueAttr) {
            is com.intellij.psi.PsiLiteralExpression -> valueAttr.value?.toString() ?: ""
            is com.intellij.psi.PsiArrayInitializerMemberValue -> {
                val initializers = valueAttr.initializers
                if (initializers.isNotEmpty()) {
                    (initializers[0] as? com.intellij.psi.PsiLiteralExpression)?.value?.toString() ?: ""
                } else ""
            }
            else -> valueAttr.text.trim('"', '\'', '{', '}')
        }
    }
}
