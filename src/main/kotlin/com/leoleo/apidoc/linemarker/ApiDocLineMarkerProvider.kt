package com.leoleo.apidoc.linemarker

import com.leoleo.apidoc.config.ShowDocSettings
import com.leoleo.apidoc.generator.ShowDocGenerator
import com.leoleo.apidoc.i18n.ApiDocI18n
import com.leoleo.apidoc.parser.ControllerParser
import com.leoleo.apidoc.ui.ShowDocPreviewDialog
import com.leoleo.apidoc.util.GitVersionUtil
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

/**
 * ApiDocument 行标记提供器
 * 在Controller类名和接口方法旁的行号区域显示可点击图标
 * 点击图标即可生成对应的API文档
 */
class ApiDocLineMarkerProvider : LineMarkerProvider {

    companion object {
        // 类和方法统一使用插件图标（16x16 行标记专用）
        val CLASS_ICON: Icon = IconLoader.getIcon("/icons/apidoc.svg", ApiDocLineMarkerProvider::class.java)
        val METHOD_ICON: Icon = IconLoader.getIcon("/icons/apidoc.svg", ApiDocLineMarkerProvider::class.java)

        // Spring MVC 请求映射注解
        private val MAPPING_ANNOTATIONS = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
        )
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // 只在标识符上添加标记（避免重复）
        if (element !is PsiIdentifier) return null

        val parent = element.parent

        return when (parent) {
            is PsiClass -> handleClass(element, parent)
            is PsiMethod -> handleMethod(element, parent)
            else -> null
        }
    }

    /**
     * 处理Controller类 - 在类名旁显示图标
     */
    private fun handleClass(element: PsiIdentifier, psiClass: PsiClass): LineMarkerInfo<PsiIdentifier>? {
        if (!isController(psiClass)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            CLASS_ICON,
            { ApiDocI18n.text("gutter.classTooltip") },
            { _, elt ->
                // 点击图标时的处理
                val project = elt.project
                val clazz = PsiTreeUtil.getParentOfType(elt, PsiClass::class.java) ?: return@LineMarkerInfo
                generateClassDoc(project, clazz)
            },
            GutterIconRenderer.Alignment.LEFT,
            { ApiDocI18n.text("gutter.classText") }
        )
    }

    /**
     * 处理接口方法 - 在方法名旁显示图标
     */
    private fun handleMethod(element: PsiIdentifier, psiMethod: PsiMethod): LineMarkerInfo<PsiIdentifier>? {
        if (!hasRequestMapping(psiMethod)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            METHOD_ICON,
            { ApiDocI18n.text("gutter.methodTooltip") },
            { _, elt ->
                // 点击图标时的处理
                val project = elt.project
                val method = PsiTreeUtil.getParentOfType(elt, PsiMethod::class.java) ?: return@LineMarkerInfo
                val clazz = PsiTreeUtil.getParentOfType(method, PsiClass::class.java)
                generateMethodDoc(project, method, clazz)
            },
            GutterIconRenderer.Alignment.LEFT,
            { ApiDocI18n.text("gutter.methodText") }
        )
    }

    /**
     * 判断是否是Controller类
     */
    private fun isController(psiClass: PsiClass): Boolean {
        val annotations = psiClass.modifierList?.annotations ?: return false
        return annotations.any { anno ->
            val name = anno.qualifiedName ?: ""
            name.contains("Controller") || name.contains("RestController")
        }
    }

    /**
     * 判断方法是否有请求映射注解
     */
    private fun hasRequestMapping(psiMethod: PsiMethod): Boolean {
        return psiMethod.modifierList.annotations.any { anno ->
            MAPPING_ANNOTATIONS.any { anno.qualifiedName == it }
        }
    }

    /**
     * 生成整个Controller的文档
     */
    private fun generateClassDoc(project: com.intellij.openapi.project.Project, psiClass: PsiClass) {
        val settings = ShowDocSettings.getInstance().state
        val virtualFile = psiClass.containingFile?.virtualFile
        val contextPath = com.leoleo.apidoc.util.ContextPathUtil.getContextPath(project, virtualFile)
        val parser = ControllerParser(settings.safeExcludedParentClasses(), contextPath, settings.safeExcludedFields())
        val apiInfoList = parser.parseClass(psiClass)
        if (apiInfoList.isEmpty()) return

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

        val docItems = apiInfoList.map { apiInfo ->
            ShowDocPreviewDialog.DocItem(
                title = apiInfo.title,
                markdown = generator.generate(apiInfo)
            )
        }
        ShowDocPreviewDialog(project, docItems, psiClass.name ?: "API").show()
    }

    /**
     * 生成单个方法的文档
     */
    private fun generateMethodDoc(project: com.intellij.openapi.project.Project, psiMethod: PsiMethod, psiClass: PsiClass?) {
        val classUrl = getClassRequestUrl(psiClass)
        val settings = ShowDocSettings.getInstance().state
        val virtualFile = psiMethod.containingFile?.virtualFile
        val contextPath = com.leoleo.apidoc.util.ContextPathUtil.getContextPath(project, virtualFile)
        val parser = ControllerParser(settings.safeExcludedParentClasses(), contextPath, settings.safeExcludedFields())
        val apiInfo = parser.parseMethod(psiMethod, classUrl) ?: return

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

    /**
     * 获取类级别的RequestMapping URL
     */
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
            is PsiLiteralExpression -> valueAttr.value?.toString() ?: ""
            is PsiArrayInitializerMemberValue -> {
                val initializers = valueAttr.initializers
                if (initializers.isNotEmpty()) {
                    (initializers[0] as? PsiLiteralExpression)?.value?.toString() ?: ""
                } else ""
            }
            else -> valueAttr.text.trim('"', '\'', '{', '}')
        }
    }
}
