package com.pico.spatial.sample.spatialvideo.ui

import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.sample.spatialvideo.STAGE_ID
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseCatalog
import com.pico.spatial.sample.spatialvideo.learning.LearningSessionViewModel
import com.pico.spatial.ui.platform.containers.OpenStageResult
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.openStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class StageGeometryDeviceTest {
    @Test
    fun stageBodiesAreAlignedAndHandlesAreBelowBodiesAndAboveFloor() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result =
            withContext(Dispatchers.Main) {
                val learning =
                    GlobalContext.get()
                        .getScope(VIDEO_SESSION_SCOPE_ID)
                        .get<LearningSessionViewModel>()
                learning.selectCourseVideo(LearningCourseCatalog.videos.first())
                context.openStage(STAGE_ID, StageStyle.Mixed)
            }
        assertTrue("Stage opening denied: $result", result is OpenStageResult.Allowed)
        val reports = mutableMapOf<String, JSONObject>()
        withTimeout(30_000) {
            while (reports.size < LearningPanel.entries.size) {
                val descriptor =
                    instrumentation.uiAutomation.executeShellCommand(
                        "logcat -d -v raw -s LearningGeometry:I *:S"
                    )
                val log =
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
                        it.readText()
                    }
                log.lineSequence()
                    .filter { it.startsWith("{") }
                    .forEach { line ->
                        val report = JSONObject(line)
                        if (report.optInt("pid") == Process.myPid()) {
                            reports[report.getString("panel")] = report
                        }
                    }
                if (reports.size < LearningPanel.entries.size) delay(500)
            }
        }
        for ((panel, report) in reports) {
            assertEquals(
                "$panel handle size must match toolbar",
                reports.getValue(LearningPanel.Toolbar.name).getDouble("handleWidth"),
                report.getDouble("handleWidth"),
                0.001,
            )
            if (panel == LearningPanel.Video.name || panel == LearningPanel.VoiceControl.name) {
                assertTrue(
                    "$panel attachment clips the hovered handle",
                    report.getDouble("handleSurfaceWidth") > report.getDouble("handleWidth") * 1.22,
                )
            }
            if (panel == LearningPanel.Video.name) {
                assertTrue(
                    "Video handle overlaps subtitles",
                    report.getDouble("handleTopY") < report.getDouble("subtitleBottomY"),
                )
            }
            assertTrue(
                "$panel drag hitbox overlaps its body: $report",
                report.getDouble("handleTopY") < report.getDouble("bottomY")
            )
            assertTrue(
                "$panel footer clips the floor: $report",
                report.getDouble("handleBottomY") >= 0.119
            )
            if (panel == LearningPanel.Toolbar.name) {
                assertTrue(
                    "Toolbar must remain below video: $report",
                    report.getDouble("topY") < report.getDouble("videoY")
                )
            } else if (panel == LearningPanel.Outline.name || panel == LearningPanel.AiCoach.name) {
                assertEquals(
                    "$panel must align with video",
                    report.getDouble("videoY"),
                    report.getDouble("centerY"),
                    0.005
                )
            }
        }
    }
}
