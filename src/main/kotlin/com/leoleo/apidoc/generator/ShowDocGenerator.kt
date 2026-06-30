package com.leoleo.apidoc.generator

import com.leoleo.apidoc.model.ApiInfo
import com.leoleo.apidoc.model.ParamInfo
import com.leoleo.apidoc.i18n.ApiDocI18n
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ShowDoc文档生成器
 * 将ApiInfo转换为ShowDoc格式的Markdown文档
 */
class ShowDocGenerator(
    private val author: String,
    private val currentVersion: String,
    private val productVersion: String,
    private val showCallLocation: Boolean = true,
    private val showRequestJson: Boolean = true,
    private val showResponseJson: Boolean = true,
    private val showJsonComment: Boolean = true
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 生成完整的ShowDoc文档
     */
    fun generate(apiInfo: ApiInfo): String {
        val sb = StringBuilder()

        // 标题
        sb.appendLine("# ${apiInfo.title}")
        sb.appendLine()

        // 信息表格
        sb.appendLine("|${ApiDocI18n.text("doc.author")}|${ApiDocI18n.text("doc.createdAt")}|${ApiDocI18n.text("doc.currentVersion")}|${ApiDocI18n.text("doc.productVersion")}|")
        sb.appendLine("|:----:|:----:|:----:|:----:|")
        sb.appendLine("|$author|${LocalDate.now().format(dateFormatter)}|$currentVersion|$productVersion|")
        sb.appendLine()

        // 接口调用位置
        if (showCallLocation && apiInfo.description.isNotBlank()) {
            sb.appendLine("**${ApiDocI18n.text("doc.callLocation")}**")
            sb.appendLine()
            sb.appendLine("- ${apiInfo.description}")
            sb.appendLine()
        }

        // 请求URL
        sb.appendLine("**${ApiDocI18n.text("doc.requestUrl")}** ")
        sb.appendLine()
        sb.appendLine("- `${apiInfo.url}`")
        sb.appendLine()

        // 请求方式
        sb.appendLine("**${ApiDocI18n.text("doc.requestMethod")}**")
        sb.appendLine()
        sb.appendLine("- ${apiInfo.method}")
        sb.appendLine("- ${apiInfo.contentType}")
        sb.appendLine()

        // 请求参数表格
        if (apiInfo.requestParams.isNotEmpty()) {
            sb.appendLine("### ${ApiDocI18n.text("doc.requestParams")}")
            sb.appendLine()
            sb.appendLine("|${ApiDocI18n.text("doc.paramName")}|${ApiDocI18n.text("doc.required")}|${ApiDocI18n.text("doc.type")}|${ApiDocI18n.text("doc.description")}|")
            sb.appendLine("|:----    |:---|:----- |-----   |")
            for (param in apiInfo.requestParams) {
                val required = if (param.required) ApiDocI18n.text("doc.yes") else ApiDocI18n.text("doc.no")
                val nameWithPrefix = "${param.prefix}${param.name}"
                sb.appendLine("|$nameWithPrefix|$required|${param.type}|${param.description}|")
            }
            sb.appendLine()

            // 请求参数JSON格式
            if (showRequestJson) {
                sb.appendLine("### ${ApiDocI18n.text("doc.requestJson")}")
                sb.appendLine()
                sb.appendLine("```json")
                sb.appendLine(generateRequestJson(apiInfo.requestParams))
                sb.appendLine("```")
                sb.appendLine()
            }
        }

        // 返回参数表格
        if (apiInfo.responseParams.isNotEmpty()) {
            sb.appendLine("### ${ApiDocI18n.text("doc.responseParams")}")
            sb.appendLine()
            sb.appendLine("|${ApiDocI18n.text("doc.paramName")}|${ApiDocI18n.text("doc.required")}|${ApiDocI18n.text("doc.type")}|${ApiDocI18n.text("doc.description")}|")
            sb.appendLine("|:----    |:---|:----- |-----   |")
            for (param in apiInfo.responseParams) {
                val required = if (param.required) ApiDocI18n.text("doc.yes") else ApiDocI18n.text("doc.no")
                val nameWithPrefix = "${param.prefix}${param.name}"
                sb.appendLine("|$nameWithPrefix|$required|${param.type}|${param.description}|")
            }
            sb.appendLine()

            // 返回参数JSON格式
            if (showResponseJson) {
                sb.appendLine("### ${ApiDocI18n.text("doc.responseJson")}")
                sb.appendLine()
                sb.appendLine("```json")
                sb.appendLine(generateResponseJson(apiInfo.responseParams))
                sb.appendLine("```")
            }
        }

        return sb.toString()
    }

    /**
     * 生成多个接口的文档
     */
    fun generateAll(apiInfoList: List<ApiInfo>): String {
        return apiInfoList.joinToString("\n\n---\n\n") { generate(it) }
    }

    /**
     * 生成请求参数的JSON格式
     */
    private fun generateRequestJson(params: List<ParamInfo>, indent: String = ""): String {
        val sb = StringBuilder()
        sb.appendLine("$indent{")

        val topLevelParams = params.filter { it.prefix.isEmpty() }
        for ((index, param) in topLevelParams.withIndex()) {
            val comma = if (index < topLevelParams.size - 1) "," else ""
            val comment = if (showJsonComment && param.description.isNotBlank()) " //${param.description}" else ""
            sb.appendLine("$indent  \"${param.name}\" : \"${param.type}\"$comma$comment")
        }

        sb.append("$indent}")
        return sb.toString()
    }

    /**
     * 生成返回参数的JSON格式（支持嵌套）
     */
    private fun generateResponseJson(params: List<ParamInfo>, indent: String = ""): String {
        val sb = StringBuilder()
        sb.appendLine("$indent{")

        var i = 0
        while (i < params.size) {
            val param = params[i]
            if (param.prefix.isNotEmpty()) {
                i++
                continue
            }

            // 查找该参数的子参数
            val children = mutableListOf<ParamInfo>()
            var j = i + 1
            while (j < params.size && params[j].prefix.isNotEmpty()) {
                children.add(params[j])
                j++
            }

            val isLast = findNextTopLevelIndex(params, i + 1) == -1
            val comma = if (!isLast) "," else ""
            val comment = if (showJsonComment && param.description.isNotBlank()) " //${param.description}" else ""

            if (children.isNotEmpty()) {
                sb.appendLine("$indent  \"${param.name}\" : {")
                for ((childIdx, child) in children.withIndex()) {
                    val childComma = if (childIdx < children.size - 1) "," else ""
                    val childComment = if (showJsonComment && child.description.isNotBlank()) " //${child.description}" else ""
                    sb.appendLine("$indent    \"${child.name}\" : \"${child.type}\"$childComma$childComment")
                }
                sb.appendLine("$indent  }$comma")
            } else {
                sb.appendLine("$indent  \"${param.name}\" : \"${param.type}\"$comma$comment")
            }
            i = if (j > i + 1) j else i + 1
        }

        sb.append("$indent}")
        return sb.toString()
    }

    private fun findNextTopLevelIndex(params: List<ParamInfo>, startIndex: Int): Int {
        for (i in startIndex until params.size) {
            if (params[i].prefix.isEmpty()) return i
        }
        return -1
    }
}
