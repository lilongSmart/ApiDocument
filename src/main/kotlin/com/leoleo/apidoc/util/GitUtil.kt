package com.leoleo.apidoc.util

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager

/**
 * Git工具类
 * 用于从当前项目的Git分支名中提取版本号
 */
object GitVersionUtil {

    /**
     * 从当前Git分支名中提取版本号
     * 例如分支名为 "release/3.3.6"，提取出 "3.3.6"
     *
     * @param project 当前项目
     * @param prefix 版本前缀，如 "V"
     * @param regex 版本正则表达式
     * @return 带前缀的版本号，如 "V3.3.6"，提取失败返回空字符串
     */
    fun getCurrentVersion(project: Project, prefix: String, regex: String): String {
        try {
            val repositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = repositoryManager.repositories
            if (repositories.isEmpty()) return ""

            val repository = repositories.first()
            val branchName = repository.currentBranch?.name ?: return ""

            // 使用正则从分支名中提取版本号
            val pattern = Regex(regex)
            val matchResult = pattern.find(branchName)
            return if (matchResult != null) {
                "$prefix${matchResult.groupValues[1]}"
            } else {
                // 如果正则匹配失败，直接使用分支名
                "$prefix$branchName"
            }
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * 获取当前分支名
     */
    fun getCurrentBranchName(project: Project): String {
        try {
            val repositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = repositoryManager.repositories
            if (repositories.isEmpty()) return ""

            val repository = repositories.first()
            return repository.currentBranch?.name ?: ""
        } catch (e: Exception) {
            return ""
        }
    }
}
