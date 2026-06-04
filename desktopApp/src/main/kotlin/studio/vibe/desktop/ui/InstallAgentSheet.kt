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
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.contract.AIAgent
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun InstallAgentSheet(
    agent: AIAgent,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(500.dp, 420.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Install ${agent.displayName}",
        resizable = false,
    ) {
        InstallAgentSheetContent(agent = agent, onDismiss = onDismiss)
    }
}

@Composable
private fun InstallAgentSheetContent(
    agent: AIAgent,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalDSColors.current.surfaceOverlay),
    ) {
        InstallSheetHeader(agent = agent)
        HorizontalDivider(color = LocalDSColors.current.borderDefault)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(DSSpacing.lg),
        ) {
            InstallSteps(agent = agent)
        }
        HorizontalDivider(color = LocalDSColors.current.borderDefault)
        InstallSheetFooter(onDismiss = onDismiss)
    }
}

@Composable
private fun InstallSheetHeader(agent: AIAgent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DSSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(DSRadius.md))
                .background(agentColor(agent, LocalDSColors.current)),
        )
        Spacer(Modifier.width(DSSpacing.md))
        Column {
            Text(
                text = "Install ${agent.displayName}",
                style = DSFont.gitBranch,
                color = LocalDSColors.current.textPrimary,
            )
            Spacer(Modifier.height(DSSpacing.xxs))
            Text(
                text = agent.shortDescription,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
            )
        }
    }
}

@Composable
private fun InstallSteps(agent: AIAgent) {
    val hasPrereq = agent.prerequisite != null && agent.prerequisiteCheckCommand != null
    val installStepNumber = if (hasPrereq) 2 else 1
    val setupStepNumber = installStepNumber + 1

    if (hasPrereq) {
        StepWithCommand(
            number = 1,
            title = "Install ${agent.prerequisite}",
            description = "Verify the required version is installed:",
            command = agent.prerequisiteCheckCommand!!,
        )
        Spacer(Modifier.height(DSSpacing.xl))
    }

    StepWithCommand(
        number = installStepNumber,
        title = "Install ${agent.displayName}",
        description = "Run this command in your terminal:",
        command = agent.installHint,
    )

    agent.setupInstructions?.let { instructions ->
        Spacer(Modifier.height(DSSpacing.xl))
        SetupStep(number = setupStepNumber, instructions = instructions)
    }
}

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
            color = LocalDSColors.current.textSecondary,
        )
        Spacer(Modifier.height(DSSpacing.sm))
        CommandBlock(command = command)
    }
}

@Composable
private fun SetupStep(number: Int, instructions: String) {
    Column {
        StepHeader(number = number, title = "Setup")
        Spacer(Modifier.height(DSSpacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DSRadius.sm))
                .background(LocalDSColors.current.surfaceBase)
                .border(1.dp, LocalDSColors.current.borderDefault, RoundedCornerShape(DSRadius.sm))
                .padding(DSSpacing.sm),
        ) {
            Text(
                text = instructions,
                style = DSFont.monoPath,
                color = LocalDSColors.current.textSecondary,
            )
        }
    }
}

@Composable
private fun StepHeader(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(DSLayout.toolbarButtonHeight)
                .clip(CircleShape)
                .background(LocalDSColors.current.accentPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "$number", style = DSFont.smallButtonLabel, color = Color.White)
        }
        Spacer(Modifier.width(DSSpacing.sm))
        Text(text = title, style = DSFont.gitBranch, color = LocalDSColors.current.textPrimary)
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
            .background(LocalDSColors.current.surfaceBase)
            .border(1.dp, LocalDSColors.current.borderDefault, RoundedCornerShape(DSRadius.sm))
            .padding(DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command,
            style = DSFont.monoPath,
            color = LocalDSColors.current.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )

        Spacer(Modifier.width(DSSpacing.sm))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSRadius.sm))
                .background(LocalDSColors.current.surfaceRaised)
                .border(1.dp, LocalDSColors.current.borderDefault, RoundedCornerShape(DSRadius.sm))
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
                tint = if (copied) LocalDSColors.current.actionRun else LocalDSColors.current.textSecondary,
                modifier = Modifier.size(DSFont.iconMD.value.dp),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = if (copied) "Copied!" else "Copy",
                style = DSFont.sidebarItemSmall,
                color = if (copied) LocalDSColors.current.actionRun else LocalDSColors.current.textSecondary,
            )
        }
    }
}

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
            color = LocalDSColors.current.textMuted,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .width(72.dp)
                .height(DSLayout.gitButtonHeight)
                .clip(RoundedCornerShape(DSRadius.md))
                .background(LocalDSColors.current.buttonPrimaryBg)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Done", style = DSFont.buttonLabel, color = Color.White)
        }
    }
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}
