@file:Suppress("unused")

package studio.vibe.shared.core.common

// This file previously contained all platform contract types.
// Each type has been extracted to its own file per M1 split requirement:
//   ProcessResult       → ProcessResult.kt
//   ProcessRunner       → ProcessRunning.kt
//   FileSystemWatcher   → FileSystemWatching.kt
//   CredentialStorage   → CredentialStorage.kt
//   PersistenceStore    → PersistenceStoring.kt
//   BinaryResolver      → BinaryResolving.kt
//   SettingsStorage     → SettingsStoring.kt
// All types are still in the same package — no import changes needed.
