package com.leoleo.apidoc.parser

import com.leoleo.apidoc.model.ApiInfo
import com.leoleo.apidoc.model.ParamInfo
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil

/**
 * Controller解析器
 * 负责从PsiClass/PsiMethod中提取API接口信息
 * @param excludedParentClasses 需要排除的父类全限定名列表
 * @param contextPath 项目的context-path前缀
 * @param excludedFields 需要排除的特定字段（className#fieldName 格式）
 */
class ControllerParser(
    private val excludedParentClasses: List<String> = emptyList(),
    private val contextPath: String = "",
    private val excludedFields: List<String> = emptyList()
) {

    companion object {
        // Spring MVC 请求映射注解
        private val REQUEST_MAPPING_ANNOTATIONS = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
        )

        // 注解对应的请求方式
        private val ANNOTATION_METHOD_MAP = mapOf(
            "GetMapping" to "GET",
            "PostMapping" to "POST",
            "PutMapping" to "PUT",
            "DeleteMapping" to "DELETE",
            "PatchMapping" to "PATCH",
            "RequestMapping" to "GET"
        )

        // 参数注解
        private const val REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam"
        private const val REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody"
        private const val PATH_VARIABLE = "org.springframework.web.bind.annotation.PathVariable"
    }

    /**
     * 解析整个Controller类，获取所有接口信息
     */
    fun parseClass(psiClass: PsiClass): List<ApiInfo> {
        val classUrl = getClassRequestUrl(psiClass)
        val apiList = mutableListOf<ApiInfo>()

        for (method in psiClass.methods) {
            val apiInfo = parseMethod(method, classUrl)
            if (apiInfo != null) {
                apiList.add(apiInfo)
            }
        }
        return apiList
    }

    /**
     * 解析单个方法，获取接口信息
     */
    fun parseMethod(psiMethod: PsiMethod, classUrl: String = ""): ApiInfo? {
        // 查找方法的请求映射注解
        val mappingAnnotation = findMappingAnnotation(psiMethod) ?: return null
        val annotationName = mappingAnnotation.qualifiedName?.substringAfterLast('.') ?: return null

        // 获取请求URL
        val methodUrl = getAnnotationValue(mappingAnnotation)
        val fullUrl = combineUrl(classUrl, methodUrl)

        // 获取请求方式
        val httpMethod = getHttpMethod(mappingAnnotation, annotationName)

        // 获取内容类型
        val contentType = getContentType(psiMethod)

        // 获取接口标题（从JavaDoc获取）
        val title = getMethodTitle(psiMethod)

        // 获取接口调用位置（类注释 -> 方法注释）
        val description = getCallLocation(psiMethod)

        // 解析请求参数
        val requestParams = parseRequestParams(psiMethod)

        // 解析返回参数
        val responseParams = parseResponseParams(psiMethod)
        val responseTypeName = getResponseTypeName(psiMethod)

        return ApiInfo(
            title = title,
            description = description,
            url = fullUrl,
            method = httpMethod,
            contentType = contentType,
            requestParams = requestParams,
            responseParams = responseParams,
            responseTypeName = responseTypeName
        )
    }

    /**
     * 获取类级别的RequestMapping URL
     */
    private fun getClassRequestUrl(psiClass: PsiClass): String {
        val annotation = psiClass.modifierList?.annotations?.find { anno ->
            REQUEST_MAPPING_ANNOTATIONS.any { anno.qualifiedName == it }
        } ?: return ""
        return getAnnotationValue(annotation)
    }

    /**
     * 查找方法上的请求映射注解
     */
    private fun findMappingAnnotation(psiMethod: PsiMethod): PsiAnnotation? {
        return psiMethod.modifierList.annotations.find { anno ->
            REQUEST_MAPPING_ANNOTATIONS.any { anno.qualifiedName == it }
        }
    }

    /**
     * 获取注解中的value/path值
     */
    private fun getAnnotationValue(annotation: PsiAnnotation): String {
        // 尝试获取 value 属性
        val valueAttr = annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("path")
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

    /**
     * 获取HTTP请求方式
     */
    private fun getHttpMethod(annotation: PsiAnnotation, annotationName: String): String {
        // 如果是RequestMapping，需要看method属性
        if (annotationName == "RequestMapping") {
            val methodAttr = annotation.findAttributeValue("method")
            if (methodAttr != null) {
                val text = methodAttr.text
                return when {
                    text.contains("GET") -> "GET"
                    text.contains("POST") -> "POST"
                    text.contains("PUT") -> "PUT"
                    text.contains("DELETE") -> "DELETE"
                    text.contains("PATCH") -> "PATCH"
                    else -> "GET"
                }
            }
        }
        return ANNOTATION_METHOD_MAP[annotationName] ?: "GET"
    }

    /**
     * 获取请求内容类型
     */
    private fun getContentType(psiMethod: PsiMethod): String {
        // 检查参数中是否有@RequestBody
        for (param in psiMethod.parameterList.parameters) {
            if (param.modifierList?.findAnnotation(REQUEST_BODY) != null) {
                return "JSON"
            }
        }
        return "FormData"
    }

    /**
     * 获取方法标题（从JavaDoc注释中获取）
     */
    private fun getMethodTitle(psiMethod: PsiMethod): String {
        val docComment = psiMethod.docComment ?: return psiMethod.name
        val description = getDocDescription(docComment)
        return description ?: psiMethod.name
    }

    /**
     * 获取接口调用位置
     * 格式：类注释 -> 方法注释
     * 例如：即时咨询 -> 即时咨询抢单V2
     */
    private fun getCallLocation(psiMethod: PsiMethod): String {
        val psiClass = psiMethod.containingClass
        val classComment = getClassComment(psiClass)
        val methodComment = getMethodTitle(psiMethod)

        return if (classComment.isNotBlank() && methodComment.isNotBlank()) {
            "$classComment -> $methodComment"
        } else if (classComment.isNotBlank()) {
            classComment
        } else {
            methodComment
        }
    }

    /**
     * 获取类的JavaDoc注释第一行描述
     */
    private fun getClassComment(psiClass: PsiClass?): String {
        if (psiClass == null) return ""
        val docComment = psiClass.docComment ?: return ""
        return getDocDescription(docComment) ?: ""
    }

    private fun getDocDescription(docComment: PsiDocComment): String? {
        return docComment.descriptionElements
            .asSequence()
            .map { cleanDocText(it.text) }
            .flatMap { it.lines().asSequence() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !isJavadocTagLine(it) }
    }

    private fun cleanDocText(text: String): String {
        return text
            .replace(Regex("(?i)<\\s*/?\\s*p\\s*>"), "\n")
            .replace(Regex("(?i)<\\s*br\\s*/?\\s*>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
    }

    private fun isJavadocTagLine(line: String): Boolean {
        return line.startsWith("@")
    }

    /**
     * 解析请求参数
     */
    private fun parseRequestParams(psiMethod: PsiMethod): List<ParamInfo> {
        val params = mutableListOf<ParamInfo>()

        for (parameter in psiMethod.parameterList.parameters) {
            // 跳过 HttpServletRequest、HttpServletResponse 等框架参数
            val typeName = parameter.type.canonicalText
            if (isFrameworkType(typeName)) continue

            val requestBodyAnnotation = parameter.modifierList?.findAnnotation(REQUEST_BODY)
            val paramAnnotation = parameter.modifierList?.findAnnotation(REQUEST_PARAM)
            val pathVariableAnnotation = parameter.modifierList?.findAnnotation(PATH_VARIABLE)

            when {
                requestBodyAnnotation != null -> {
                    // @RequestBody 参数，递归解析对象字段（包括父类字段）
                    val bodyParams = parseTypeFields(parameter.type, "")
                    params.addAll(bodyParams)
                }
                paramAnnotation != null || pathVariableAnnotation != null -> {
                    // @RequestParam 或 @PathVariable，作为简单参数
                    val name = getParamName(parameter, paramAnnotation ?: pathVariableAnnotation)
                    val required = getParamRequired(paramAnnotation)
                    val type = getSimpleTypeName(parameter.type)
                    val description = getParamDescription(psiMethod, parameter.name ?: "")
                    params.add(
                        ParamInfo(
                            name = name,
                            required = required,
                            type = type,
                            description = description
                        )
                    )
                }
                else -> {
                    // 没有注解的参数：如果是复杂对象类型，展开其字段（包括父类字段）
                    if (isComplexType(parameter.type)) {
                        val entityParams = parseTypeFields(parameter.type, "")
                        params.addAll(entityParams)
                    } else {
                        // 基础类型参数，直接列出
                        val name = parameter.name ?: ""
                        val type = getSimpleTypeName(parameter.type)
                        val description = getParamDescription(psiMethod, name)
                        params.add(
                            ParamInfo(
                                name = name,
                                required = false,
                                type = type,
                                description = description
                            )
                        )
                    }
                }
            }
        }
        return params
    }

    /**
     * 解析返回参数
     */
    private fun parseResponseParams(psiMethod: PsiMethod): List<ParamInfo> {
        val returnType = psiMethod.returnType ?: return emptyList()
        return parseTypeFields(returnType, "")
    }

    /**
     * 获取返回值类型名称
     */
    private fun getResponseTypeName(psiMethod: PsiMethod): String {
        return psiMethod.returnType?.presentableText ?: "void"
    }

    /**
     * 递归解析类型的字段（包括父类字段）
     */
    private fun parseTypeFields(type: PsiType, prefix: String, depth: Int = 0): List<ParamInfo> {
        if (depth > 3) return emptyList() // 防止无限递归

        val params = mutableListOf<ParamInfo>()
        val resolvedClass = when (type) {
            is PsiClassType -> {
                // 处理泛型类型，如 Result<T>
                val resolved = type.resolve()
                if (resolved != null && isGenericWrapper(resolved)) {
                    // 如果是泛型包装类（如Result<Data>），解析泛型参数
                    val typeArgs = type.parameters
                    if (typeArgs.isNotEmpty()) {
                        val wrapperFields = parseWrapperFields(resolved, prefix)
                        params.addAll(wrapperFields)
                        val innerFields = parseTypeFields(typeArgs[0], "$prefix--", depth + 1)
                        params.addAll(innerFields)
                        return params
                    }
                }
                resolved
            }
            else -> null
        }

        if (resolvedClass == null) return params

        // 跳过Java基础类型
        if (isBasicType(resolvedClass.qualifiedName ?: "")) return params

        val excludedFieldsByClass = buildExcludedFieldsByClass()

        // allFields 包含当前类和所有父类的字段
        for (field in resolvedClass.allFields) {
            // 跳过 static 和 transient 字段
            if (field.modifierList?.hasModifierProperty(PsiModifier.STATIC) == true) continue
            if (field.modifierList?.hasModifierProperty(PsiModifier.TRANSIENT) == true) continue

            // 跳过 Object 类和序列化相关字段
            val containingClass = field.containingClass
            val containingClassName = containingClass?.qualifiedName ?: ""
            if (containingClassName == "java.lang.Object") continue
            if (field.name == "serialVersionUID") continue

            val fieldName = field.name ?: ""

            // === 排除逻辑 ===
            // 检查字段所属类是否在排除列表中
            val matchedExcludeClass = findMatchedExcludeClass(containingClass)

            if (matchedExcludeClass != null) {
                // 查找该类对应的排除字段集合
                val classExcludedFields = getExcludedFieldsForClass(matchedExcludeClass, containingClass, excludedFieldsByClass)
                if (classExcludedFields == null || classExcludedFields.isEmpty()) {
                    // 没有配置特定字段 -> 排除该类全部字段
                    continue
                }
                // 有特定字段配置 -> 只排除指定的字段
                if (fieldName in classExcludedFields) continue
            }

            val fieldType = field.type
            val simpleType = getSimpleTypeName(fieldType)
            val description = getFieldDescription(field)

            params.add(
                ParamInfo(
                    name = field.name ?: "",
                    required = false,
                    type = simpleType,
                    description = description,
                    prefix = prefix
                )
            )

            // 如果是复杂对象类型，递归解析
            if (isComplexType(fieldType) && !isCollectionType(fieldType)) {
                val childFields = parseTypeFields(fieldType, "$prefix--", depth + 1)
                params.addAll(childFields)
            } else if (isCollectionType(fieldType) && fieldType is PsiClassType) {
                val typeArgs = fieldType.parameters
                if (typeArgs.isNotEmpty() && isComplexType(typeArgs[0])) {
                    val childFields = parseTypeFields(typeArgs[0], "$prefix--", depth + 1)
                    params.addAll(childFields)
                }
            }
        }
        return params
    }

    private fun buildExcludedFieldsByClass(): Map<String, Set<String>> {
        val excludedFieldsByClass = mutableMapOf<String, MutableSet<String>>()
        for (entry in excludedFields) {
            val separatorIdx = entry.indexOf('#')
            if (separatorIdx > 0 && separatorIdx < entry.length - 1) {
                val className = entry.substring(0, separatorIdx)
                val fieldName = entry.substring(separatorIdx + 1)
                excludedFieldsByClass.getOrPut(className) { mutableSetOf() }.add(fieldName)
            }
        }
        return excludedFieldsByClass
    }

    private fun findMatchedExcludeClass(containingClass: PsiClass?): String? {
        val qualifiedName = containingClass?.qualifiedName ?: return null
        val simpleName = containingClass.name ?: return null
        return excludedParentClasses.find { excludedClass ->
            excludedClass == qualifiedName ||
                    qualifiedName.endsWith(".$excludedClass") ||
                    excludedClass.substringAfterLast('.') == simpleName
        }
    }

    private fun getExcludedFieldsForClass(
        matchedExcludeClass: String,
        containingClass: PsiClass?,
        excludedFieldsByClass: Map<String, Set<String>>
    ): Set<String>? {
        val qualifiedName = containingClass?.qualifiedName
        val simpleName = containingClass?.name
        return excludedFieldsByClass[matchedExcludeClass]
            ?: qualifiedName?.let { excludedFieldsByClass[it] }
            ?: simpleName?.let { excludedFieldsByClass[it] }
    }

    /**
     * 解析包装类的基础字段（如 code, message）
     */
    private fun parseWrapperFields(psiClass: PsiClass, prefix: String): List<ParamInfo> {
        val params = mutableListOf<ParamInfo>()
        // 获取包装类自身声明的类型参数名称（如 "T", "E" 等）
        val typeParamNames = psiClass.typeParameters.map { it.name }.toSet()

        for (field in psiClass.fields) {
            if (field.modifierList?.hasModifierProperty(PsiModifier.STATIC) == true) continue
            val fieldType = field.type
            // 跳过使用了类级别泛型参数的字段（将由泛型参数处理）
            val fieldTypeName = field.type.presentableText
            if (fieldTypeName in typeParamNames) continue
            // 跳过泛型容器类型中引用了类型参数的（如 List<T>）
            if (fieldType is PsiClassType) {
                val hasTypeParamRef = fieldType.parameters.any { param ->
                    param.presentableText in typeParamNames
                }
                if (hasTypeParamRef) continue
            }

            params.add(
                ParamInfo(
                    name = field.name ?: "",
                    required = false,
                    type = getSimpleTypeName(fieldType),
                    description = getFieldDescription(field),
                    prefix = prefix
                )
            )
        }
        return params
    }

    /**
     * 判断是否是泛型包装类（如Result<T>, Response<T>）
     */
    private fun isGenericWrapper(psiClass: PsiClass): Boolean {
        return psiClass.typeParameters.isNotEmpty()
    }

    /**
     * 获取参数名称
     */
    private fun getParamName(parameter: PsiParameter, annotation: PsiAnnotation?): String {
        if (annotation != null) {
            val valueAttr = annotation.findAttributeValue("value")
                ?: annotation.findAttributeValue("name")
            if (valueAttr != null) {
                val name = valueAttr.text.trim('"')
                if (name.isNotBlank()) return name
            }
        }
        return parameter.name ?: ""
    }

    /**
     * 获取参数是否必填
     */
    private fun getParamRequired(annotation: PsiAnnotation?): Boolean {
        if (annotation == null) return false
        val requiredAttr = annotation.findAttributeValue("required")
        return requiredAttr?.text != "false"
    }

    /**
     * 从JavaDoc获取参数描述
     */
    private fun getParamDescription(psiMethod: PsiMethod, paramName: String): String {
        val docComment = psiMethod.docComment ?: return ""
        val paramTags = docComment.findTagsByName("param")
        for (tag in paramTags) {
            val elements = tag.dataElements
            if (elements.isNotEmpty()) {
                val tagParamName = elements[0].text.trim()
                if (tagParamName == paramName && elements.size > 1) {
                    return elements.drop(1).joinToString("") { it.text }.trim()
                }
            }
        }
        return ""
    }

    /**
     * 从字段注释获取描述
     */
    private fun getFieldDescription(field: PsiField): String {
        // 优先从JavaDoc获取
        val docComment = field.docComment
        if (docComment != null) {
            val desc = getDocDescription(docComment)
            if (!desc.isNullOrBlank()) return desc
        }

        // 从行尾注释获取
        val comment = PsiTreeUtil.getNextSiblingOfType(field, PsiComment::class.java)
        if (comment != null) {
            return comment.text.removePrefix("//").removePrefix("/*").removeSuffix("*/").trim()
        }

        // 从注解获取（如 @ApiModelProperty）
        val apiModelProp = field.modifierList?.findAnnotation("io.swagger.annotations.ApiModelProperty")
        if (apiModelProp != null) {
            val value = apiModelProp.findAttributeValue("value")
            if (value != null) return value.text.trim('"')
        }

        return ""
    }

    /**
     * 获取简化的类型名
     */
    private fun getSimpleTypeName(type: PsiType): String {
        return when {
            type.presentableText.contains("Integer") || type.presentableText == "int" -> "Integer"
            type.presentableText.contains("Long") || type.presentableText == "long" -> "Long"
            type.presentableText.contains("String") -> "String"
            type.presentableText.contains("Boolean") || type.presentableText == "boolean" -> "Boolean"
            type.presentableText.contains("Double") || type.presentableText == "double" -> "Double"
            type.presentableText.contains("Float") || type.presentableText == "float" -> "Float"
            type.presentableText.contains("List") -> "List"
            type.presentableText.contains("Map") -> "Map"
            type.presentableText.contains("Date") -> "Date"
            else -> type.presentableText
        }
    }

    /**
     * 判断是否是基础类型
     */
    private fun isBasicType(qualifiedName: String): Boolean {
        val basicTypes = listOf(
            "java.lang.String", "java.lang.Integer", "java.lang.Long",
            "java.lang.Boolean", "java.lang.Double", "java.lang.Float",
            "java.lang.Byte", "java.lang.Short", "java.lang.Character",
            "java.math.BigDecimal", "java.math.BigInteger",
            "java.util.Date", "java.time.LocalDate", "java.time.LocalDateTime"
        )
        return qualifiedName in basicTypes
    }

    /**
     * 判断是否是复杂类型（需要递归解析的）
     */
    private fun isComplexType(type: PsiType): Boolean {
        if (type !is PsiClassType) return false
        val resolved = type.resolve() ?: return false
        val qualifiedName = resolved.qualifiedName ?: return false
        return !isBasicType(qualifiedName) && !qualifiedName.startsWith("java.")
    }

    /**
     * 判断是否是集合类型
     */
    private fun isCollectionType(type: PsiType): Boolean {
        return type.presentableText.contains("List") ||
                type.presentableText.contains("Set") ||
                type.presentableText.contains("Collection")
    }

    /**
     * 检查某个类的继承链中是否包含指定的类名
     */
    private fun isClassInHierarchy(psiClass: PsiClass, targetClassName: String): Boolean {
        var current: PsiClass? = psiClass.superClass
        val targetSimple = targetClassName.substringAfterLast('.')
        while (current != null) {
            val currentName = current.qualifiedName ?: ""
            if (currentName == targetClassName ||
                currentName.endsWith(".$targetClassName") ||
                current.name == targetSimple) {
                return true
            }
            current = current.superClass
        }
        return false
    }

    /**
     * 判断是否是框架类型（HttpServletRequest等），跳过不解析
     */
    private fun isFrameworkType(qualifiedName: String): Boolean {
        val frameworkPrefixes = listOf(
            "javax.servlet.",
            "jakarta.servlet.",
            "org.springframework.web.",
            "org.springframework.ui.",
            "org.springframework.validation.",
            "javax.validation.",
            "jakarta.validation.",
            "org.springframework.http.",
            "java.security."
        )
        return frameworkPrefixes.any { qualifiedName.startsWith(it) }
    }

    /**
     * 合并URL路径（contextPath + classUrl + methodUrl）
     */
    private fun combineUrl(classUrl: String, methodUrl: String): String {
        val ctx = contextPath.trimEnd('/')
        val base = classUrl.trimEnd('/')
        val path = methodUrl.trimStart('/')

        val classAndMethod = if (base.isEmpty()) {
            "/$path"
        } else if (path.isEmpty()) {
            if (base.startsWith("/")) base else "/$base"
        } else {
            val prefix = if (base.startsWith("/")) base else "/$base"
            "$prefix/$path"
        }

        return if (ctx.isNotBlank()) {
            val ctxPrefix = if (ctx.startsWith("/")) ctx else "/$ctx"
            "$ctxPrefix$classAndMethod"
        } else {
            classAndMethod
        }
    }
}
