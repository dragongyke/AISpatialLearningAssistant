package com.pico.spatial.sample.spatialvideo.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.learning.LearningAiConfigurationStatus
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun rememberLearningStageEntry(
    windowCoordinator: LearningWindowCoordinator,
): (() -> Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val gate =
        remember(windowCoordinator) {
            LearningStageEntryGate(
                LearningAiConfigurationStatus.from(),
                windowCoordinator::enterStage
            )
        }
    val showNotice by gate.showNotice.collectAsStateWithLifecycle()
    DisposableEffect(gate) { onDispose { gate.dismiss() } }
    if (showNotice) {
        LearningAiSetupNotice(
            configuration = gate.configuration,
            onContinue = { scope.launch(Dispatchers.Main.immediate) { gate.continueEntry() } },
            onDismiss = gate::dismiss,
        )
    }
    return remember(gate, scope) {
        { prepareSession ->
            scope.launch(Dispatchers.Main.immediate) { gate.request(prepareSession) }
            Unit
        }
    }
}

@Composable
internal fun LearningAiSetupNotice(
    configuration: LearningAiConfigurationStatus,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val missing =
        stringResource(
            when {
                !configuration.modelConfigured && !configuration.speechConfigured ->
                    R.string.catalog_ai_setup_missing_both
                !configuration.modelConfigured -> R.string.catalog_ai_setup_missing_model
                else -> R.string.catalog_ai_setup_missing_speech
            }
        )
    AlertDialog(
        modifier = Modifier.widthIn(max = 620.dp),
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        orientation = Orientation.Vertical,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.catalog_ai_setup_title),
                modifier = Modifier.semantics { heading() },
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        content = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.catalog_ai_setup_limitations, missing),
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
                Text(
                    text = stringResource(R.string.catalog_ai_setup_instructions),
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
                Text(
                    text = "github.com/dragongyke/AISpatialLearningAssistant",
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
            }
        },
        buttons = {
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                onClick = onContinue,
            ) {
                Text(text = stringResource(R.string.catalog_ai_setup_continue), fontSize = 20.sp)
            }
        },
    )
}
