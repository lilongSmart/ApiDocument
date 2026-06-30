package com.leoleo.apidoc.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * 从Spring Boot配置文件中读取 server.servlet.context-path
 */
object ContextPathUtil {

    /**
     * 获取项目的 context-path
     * 优先读取当前模块的 resources 目录下的配置文件
     * @param currentFile 当前正在编辑的文件，用于定位所属模块
     */
    fun getContextPath(project: Project, currentFile: VirtualFile? = null): String {
        try {
            val scope = GlobalSearchScope.projectScope(project)

            // 收集所有配置文件
            val candidates = mutableListOf<VirtualFile>()

            val ymlFiles = FilenameIndex.getVirtualFilesByName("application.yml", scope)
            candidates.addAll(ymlFiles)
            val yamlFiles = FilenameIndex.getVirtualFilesByName("application.yaml", scope)
            candidates.addAll(yamlFiles)
            val propFiles = FilenameIndex.getVirtualFilesByName("application.properties", scope)
            candidates.addAll(propFiles)
            val bootstrapFiles = FilenameIndex.getVirtualFilesByName("bootstrap.yml", scope)
            candidates.addAll(bootstrapFiles)

            // 如果有当前文件，优先匹配同模块的配置
            if (currentFile != null) {
                val currentPath = currentFile.path
                // 找到当前文件的模块根目录（包含src的上级目录）
                val moduleRoot = findModuleRoot(currentPath)
                if (moduleRoot.isNotBlank()) {
                    // 优先在同模块下查找
                    val sameModuleFiles = candidates.filter { it.path.startsWith(moduleRoot) }
                    for (file in sameModuleFiles) {
                        val path = if (file.name.endsWith(".properties")) {
                            parsePropertiesContextPath(file)
                        } else {
                            parseYmlContextPath(file)
                        }
                        if (path.isNotBlank()) return path
                    }
                }
            }

            // 回退：遍历所有配置文件
            for (file in candidates) {
                val path = if (file.name.endsWith(".properties")) {
                    parsePropertiesContextPath(file)
                } else {
                    parseYmlContextPath(file)
                }
                if (path.isNotBlank()) return path
            }
        } catch (e: Exception) {
            // 忽略异常
        }
        return ""
    }

    /**
     * 根据文件路径推断模块根目录
     */
    private fun findModuleRoot(filePath: String): String {
        val normalized = filePath.replace("\\", "/")
        val srcIdx = normalized.indexOf("/src/")
        return if (srcIdx > 0) normalized.substring(0, srcIdx) else ""
    }

    /**
     * 从 yml 文件中解析 context-path
     * 支持格式：
     *   server:
     *     servlet:
     *       context-path: /law-cloud
     * 或
     *   server.servlet.context-path: /law-cloud
     */
    private fun parseYmlContextPath(file: VirtualFile): String {
        try {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            val lines = content.lines()

            // 方式1：行内格式 server.servlet.context-path: xxx
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("server.servlet.context-path:") ||
                    trimmed.startsWith("server.servlet.context-path =")) {
                    return extractValue(trimmed)
                }
            }

            // 方式2：层级格式解析
            var inServer = false
            var inServlet = false
            var serverIndent = -1
            var servletIndent = -1

            for (line in lines) {
                if (line.isBlank() || line.trim().startsWith("#")) continue

                val indent = line.length - line.trimStart().length
                val trimmed = line.trim()

                // 检测 server: 块
                if (trimmed == "server:" || trimmed.startsWith("server:")) {
                    inServer = true
                    inServlet = false
                    serverIndent = indent
                    continue
                }

                if (inServer && indent <= serverIndent && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    // 退出 server 块
                    if (indent <= serverIndent && trimmed != "server:") {
                        inServer = false
                        inServlet = false
                        continue
                    }
                }

                if (inServer) {
                    // 检测 servlet: 块
                    if (trimmed == "servlet:" || trimmed.startsWith("servlet:")) {
                        inServlet = true
                        servletIndent = indent
                        continue
                    }

                    if (inServlet && indent <= servletIndent && trimmed.isNotBlank()) {
                        if (!trimmed.startsWith("context-path")) {
                            inServlet = false
                            continue
                        }
                    }

                    // 在 servlet 块内找 context-path
                    if (inServlet && trimmed.startsWith("context-path:")) {
                        return extractValue(trimmed)
                    }

                    // 直接在 server 下的 context-path (旧版Spring Boot)
                    if (!inServlet && trimmed.startsWith("context-path:")) {
                        return extractValue(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }
        return ""
    }

    /**
     * 从 properties 文件中解析 context-path
     */
    private fun parsePropertiesContextPath(file: VirtualFile): String {
        try {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("server.servlet.context-path=") ||
                    trimmed.startsWith("server.servlet.context-path =")) {
                    val value = trimmed.substringAfter("=").trim()
                    return value.trim('"', '\'')
                }
                // 旧版格式
                if (trimmed.startsWith("server.context-path=") ||
                    trimmed.startsWith("server.context-path =")) {
                    val value = trimmed.substringAfter("=").trim()
                    return value.trim('"', '\'')
                }
            }
        } catch (e: Exception) {
            // 忽略
        }
        return ""
    }

    /**
     * 提取yml值
     */
    private fun extractValue(line: String): String {
        val value = line.substringAfter(":").trim()
        return value.trim('"', '\'')
    }
}
