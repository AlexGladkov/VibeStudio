package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.AIAssistant
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// ── InstallAgentSheet ─────────────────────────────────────────────────────────

/**
 * Step-by-step installation guide for a specific AI CLI agent.
 *
 * Displays numbered installation steps (prerequisites → install → setup).
 * Each step with a shell command includes a copy-to-clipboard button
 * that shows a "Copied!" confirmation for 2 seconds.
 *
 * @param assistant The agent requiring installation.
 * @param onDismiss Called when the user closes the dialog.
 */
@Composable
fun InstallAgentSheet(
    assistant: AIAssistant,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(500.dp, 420.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Install ${assistant.displayName}",
        resizable = false,
    ) {
        InstallAgentSheetContent(
            assistant = assistant,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun InstallAgentSheetContent(
    assistant: AIAssistant,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColor.surfaceOverlay),
    ) {
        // Header
        InstallSheetHeader(assistant = assistant)

        HorizontalDivider(color = DSColor.borderDefault)

        // Scrollable step list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(DSSpacing.lg),
        ) {
            InstallSteps(assistant = assistant)
        }

        HorizontalDivider(color = DSColor.borderDefault)

        // Footer
        InstallSheetFooter(onDismiss = onDismiss)
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun InstallSheetHeader(assistant: AIAssistant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DSSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Agent color badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(DSRadius.md))
                .background(agentColor(assistant)),
        )
        Spacer(Modifier.width(DSSpacing.md))
        Column {
            Text(
                text = "Install ${assistant.displayName}",
                style = DSFont.gitBranch,
                color = DSColor.textPrimary,
            )
            Spacer(Modifier.height(DSSpacing.xxs))
            Text(
                text = assistant.shortDescription,
                style = DSFont.sidebarItemSmall,
                color = DSColor.textMuted,
            )
        }
    }
}

// ── Steps ─────────────────────────────────────────────────────────────────────

@Composable
private fun InstallSteps(assistant: AIAssistant) {
    val hasPrereq = assistant.prerequisite != null && assistant.prerequisiteCheckCommand != null
    val installStepNumber = if (hasPrereq) 2 else 1
    val setupStepNumber = installStepNumber + 1

    // Step 1 — Prerequisite (optional)
    if (hasPrereq) {
        StepWithCommand(
            number = 1,
            title = "Install ${assistant.prerequisite}",
            description = "Verify the required version is installed:",
            command = assistant.prerequisiteCheckCommand!!,
        )
        Spacer(Modifier.height(DSSpacing.xl))
    }

    // Install step
    StepWithCommand(
        number = installStepNumber,
        title = "Install ${assistant.displayName}",
        description = "Run this command in your terminal:",
        command = assistant.installHint,
    )

    // Setup step (optional)
    assistant.setupInstructions?.let { instructions ->
        Spacer(Modifier.height(DSSpacing.xl))
        SetupStep(
            number = setupStepNumber,
            instructions = instructions,
        )
    }
}

// ── Step components ───────────────────────────────────────────────────────────

@Composable
private fun StepWithCommand(
    number: Int,
    title: String,
    description: String,
    command: String,
) {
    Column {
        StepHeader(number = number, title = title)
        Spacer(Modifier.height(DSSpacing.sm))
        Text(
            text = description,
            style = DSFont.sidebarItemSmall,
            color = DSColor.textSecondary,
        )
        Spacer(Modifier.height(DSSpacing.sm))
        CommandBlock(command = command)
    }
}

@Composable
private fun SetupStep(
    number: Int,
    instructions: String,
) {
    Column {
        StepHeader(number = number, title = "Setup")
        Spacer(Modifier.height(DSSpacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DSRadius.sm))
                .background(DSColor.surfaceBase)
                .border(1.dp, DSColor.borderDefault, RoundedCornerShape(DSRadius.sm))
                .padding(DSSpacing.sm),
        ) {
            Text(
                text = instructions,
                style = DSFont.monoPath,
                color = DSColor.textSecondary,
            )
        }
    }
}

@Composable
private fun StepHeader(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Numbered circle badge
        Box(
            modifier = Modifier
                .size(DSLayout.toolbarButtonHeight)
                .clip(CircleShape)
                .background(DSColor.accentPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                style = DSFont.smallButtonLabel,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(DSSpacing.sm))
        Text(
            text = title,
            style = DSFont.gitBranch,
            color = DSColor.textPrimary,
        )
    }
}

@Composable
private fun CommandBlock(command: String) {
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(DSColor.surfaceBase)
            .border(1.dp, DSColor.borderDefault, RoundedCornerShape(DSRadius.sm))
            .padding(DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command,
            style = DSFont.monoPath,
            color = DSColor.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // Copy button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSRadius.sm))
                .background(DSColor.surfaceRaised)
                .border(1.dp, DSColor.borderDefault, RoundedCornerShape(DSRadius.sm))
                .clickable {
                    copyToClipboard(command)
                    copied = true
                    scope.launch {
                        delay(1_500)
                        copied = false
                    }
                }
                .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy",
                tint = if (copied) DSColor.actionRun else DSColor.textSecondary,
                modifier = Modifier.size(DSFont.iconMD.value.dp),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = if (copied) "Copied!" else "Copy",
                style = DSFont.sidebarItemSmall,
                color = if (copied) DSColor.actionRun else DSColor.textSecondary,
            )
        }
    }
}

// ── Footer ────────────────────────────────────────────────────────────────────

@Composable
private fun InstallSheetFooter(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DSSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Restart VibeStudio after installation",
            style = DSFont.sidebarItemSmall,
            color = DSColor.textMuted,
            modifier = Modifier.weight(1f),
        )

        // Done button
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(DSLayout.gitButtonHeight)
                .clip(RoundedCornerShape(DSRadius.md))
                .background(DSColor.buttonPrimaryBg)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Done",
                style = DSFont.buttonLabel,
                color = Color.White,
            )
        }
    }
}

// ── Clipboard helper ──────────────────────────────────────────────────────────

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}
