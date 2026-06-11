package studio.vibe.shared.feature.git.domain.contract

// Unified Git Service
interface GitServicing : GitStatusQuerying, GitStaging, GitCommitting,
    GitRemoteOperating, GitBranching, GitRepositoryInspection
