@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common.terminal

import studio.vibe.shared.core.common.terminal.TerminalSessionCreating
import studio.vibe.shared.core.common.terminal.TerminalSessionQuerying
import studio.vibe.shared.core.common.terminal.TerminalInputSending
import studio.vibe.shared.core.common.terminal.TerminalScrollbackAccessing

interface TerminalSessionManaging :
    TerminalSessionCreating,
    TerminalSessionQuerying,
    TerminalInputSending,
    TerminalScrollbackAccessing
