package studio.vibe.shared.feature.filetree.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class WatchToken(val id: Uuid = Uuid.random())
