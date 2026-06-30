package com.leoleo.apidoc.model

/**
 * API接口信息模型
 */
data class ApiInfo(
    // 接口标题（从方法注释或注解获取）
    val title: String,
    // 接口描述/调用位置
    val description: String = "",
    // 请求URL
    val url: String,
    // 请求方式（GET/POST/PUT/DELETE等）
    val method: String,
    // 请求数据格式（如 FormData、JSON）
    val contentType: String = "FormData",
    // 请求参数列表
    val requestParams: List<ParamInfo> = emptyList(),
    // 返回参数列表
    val responseParams: List<ParamInfo> = emptyList(),
    // 返回值类型名称
    val responseTypeName: String = ""
)

/**
 * 参数信息
 */
data class ParamInfo(
    // 参数名
    val name: String,
    // 是否必填
    val required: Boolean = false,
    // 参数类型
    val type: String,
    // 参数说明
    val description: String = "",
    // 前缀（用于嵌套对象表示层级，如 "--"）
    val prefix: String = "",
    // 子参数（用于嵌套对象）
    val children: List<ParamInfo> = emptyList()
)
