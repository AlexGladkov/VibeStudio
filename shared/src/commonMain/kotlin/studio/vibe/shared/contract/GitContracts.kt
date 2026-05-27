package studio.vibe.shared.contract

import studio.vibe.shared.model.*
import kotlin.time.Duration

// Git Status Querying
interface GitStatusQuerying {
    suspend fun status(at: FilePath): GitStatus
    suspend fun diff(file: String, staged: Boolean, at: FilePath): List<GitDiffHunk>
    suspend fun fullStagedDiff(at: FilePath): String
    suspend fun headDiff(at: FilePath): String
    suspend fun log(limit: Int = 50, at: FilePath): List<GitCommitInfo>
    suspend fun aheadBehind(at: FilePath): Pair<Int, Int>
    suspend fun diffStats(at: FilePath): Map<String, GitDiffStat>
}

// Git Staging
interface GitStaging {
    suspend fun stage(files: List<String>, at: FilePath)
    suspend fun unstage(files: List<String>, at: FilePath)
}

// Git Committing
interface GitCommitting {
    suspend fun commit(message: String, at: FilePath): String
}

// Git Remote Operating
interface GitRemoteOperating {
    suspend fun push(remote: String = "origin", at: FilePath)
    suspend fun pull(remote: String = "origin", at: FilePath)
    suspend fun fetch(remote: String = "origin", at: FilePath)
    suspend fun pushBranch(branch: String, remote: String = "origin", at: FilePath)
    suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String = "origin", at: FilePath)
    suspend fun addRemote(name: String, url: String, at: FilePath)
    suspend fun remoteURL(name: String = "origin", at: FilePath): String?
    suspend fun defaultRemote(forBranch: String?, at: FilePath): String
}

// Git Branching
interface GitBranching {
    suspend fun branches(at: FilePath): List<GitBranch>
    suspend fun checkout(branch: String, at: FilePath)
    suspend fun createBranch(name: String, from: String? = null, at: FilePath)
}

// Git Repository Inspection
interface GitRepositoryInspection {
    suspend fun isRepository(at: FilePath): Boolean
    suspend fun repositoryRoot(forPath: FilePath): FilePath
    suspend fun initRepository(at: FilePath)
}

// Unified Git Service
interface GitServicing : GitStatusQuerying, GitStaging, GitCommitting,
    GitRemoteOperating, GitBranching, GitRepositoryInspection
