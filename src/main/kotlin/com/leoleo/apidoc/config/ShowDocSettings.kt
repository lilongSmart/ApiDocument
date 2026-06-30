package com.leoleo.apidoc.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * ApiDoc 文档生成器的持久化配置
 */
@State(
    name = "ApiDocSettings",
    storages = [Storage("leoleo-apidoc-settings.xml")]
)
class ShowDocSettings : PersistentStateComponent<ShowDocSettings.State> {

    data class State(
        // 默认作者
        var author: String = "",
        // 产品版本
        var productVersion: String = "",
        // 是否从Git分支自动获取当前版本
        var autoVersionFromGit: Boolean = true,
        // Git分支版本前缀
        var versionPrefix: String = "V",
        // 版本号正则表达式（支持 3.3.6.a 这样带字母后缀的格式）
        var versionRegex: String = "(\\d+\\.\\d+\\.\\d+[\\w.]*)",
        // 是否显示接口调用位置
        var showCallLocation: Boolean = true,
        // 是否显示请求参数JSON
        var showRequestJson: Boolean = true,
        // 是否显示返回参数JSON
        var showResponseJson: Boolean = true,
        // 是否显示返回JSON中文注释
        var showJsonComment: Boolean = true,
        // 导出路径
        var exportPath: String = "",
        // 插件界面和生成文档语言：zh / en
        var language: String = "zh",
        // 实时预览字号
        var previewFontSizePt: Int = 10,
        // 排除的父类配置（类名 -> 排除的字段列表，空列表表示排除全部字段）
        var excludedParentClasses: MutableList<String> = mutableListOf(),
        // 排除的特定字段（格式：className#fieldName）
        var excludedFields: MutableList<String> = mutableListOf()
    ) {
        // 确保反序列化后字段不为null
        fun safeExcludedFields(): List<String> = excludedFields ?: emptyList()
        fun safeExcludedParentClasses(): List<String> = excludedParentClasses ?: emptyList()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): ShowDocSettings {
            return ApplicationManager.getApplication().getService(ShowDocSettings::class.java)
        }
    }
}
