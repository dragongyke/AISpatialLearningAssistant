package com.pico.spatial.sample.spatialvideo.ui

import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.sample.spatialvideo.STAGE_ID
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseCatalog
import com.pico.spatial.sample.spatialvideo.learning.LearningSessionViewModel
import com.pico.spatial.ui.platform.containers.OpenStageResult
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.openStage
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StageDragDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun eachVisibleHandleAcceptsRepeatedUiDrags() = runBlocking {
        withContext(Dispatchers.Main) {
            org.koin.core.context.GlobalContext.get()
                .getScope(VIDEO_SESSION_SCOPE_ID)
                .get<LearningSessionViewModel>()
                .selectCourseVideo(LearningCourseCatalog.videos.first())
            assertTrue(
                instrumentation.targetContext.openStage(STAGE_ID, StageStyle.Mixed)
                    is OpenStageResult.Allowed
            )
        }
        val attachmentPanels = LearningPanel.entries.filter { it != LearningPanel.VoiceControl }
        var handles = emptyMap<String, JSONObject>()
        withTimeout(30_000) {
            while (
                handles.size < attachmentPanels.size ||
                    reports("LearningGeometry").size < attachmentPanels.size
            ) {
                handles = reports("LearningHandleInput").associateBy { it.getString("panel") }
                delay(300)
            }
        }
        for (panel in attachmentPanels) {
            val info = handles.getValue(panel.name)
            val view =
                withContext(Dispatchers.Main) {
                    WindowInspector.getGlobalWindowViews()
                        .asSequence()
                        .flatMap { descendants(it) }
                        .firstOrNull { System.identityHashCode(it) == info.getInt("view") }
                }
            assertNotNull("Missing attachment view for $panel", view)
            repeat(3) { attempt ->
                val before = reports("LearningDrag").size
                val downTime = SystemClock.uptimeMillis()
                val x = info.getDouble("x").toFloat()
                val y = info.getDouble("y").toFloat()
                val direction =
                    (if (panel == LearningPanel.Outline) -1f else 1f) *
                        (if (attempt % 2 == 0) 1f else -1f)
                dispatch(view!!, downTime, MotionEvent.ACTION_DOWN, x, y)
                repeat(10) { step ->
                    delay(35)
                    dispatch(
                        view,
                        downTime,
                        MotionEvent.ACTION_MOVE,
                        x + direction * (step + 1) * 60f,
                        y
                    )
                }
                dispatch(view, downTime, MotionEvent.ACTION_UP, x + direction * 600f, y)
                delay(150)
                val moved = reports("LearningDrag").drop(before)
                assertTrue("$panel drag ${attempt + 1} produced no panel movement", moved.size >= 2)
                assertTrue(
                    "$panel drag moved another panel",
                    moved.all { it.getString("panel") == panel.name }
                )
                assertTrue(
                    "$panel drag ${attempt + 1} did not change position",
                    moved
                        .map { Triple(it.getDouble("x"), it.getDouble("y"), it.getDouble("z")) }
                        .distinct()
                        .size > 1
                )
                // Cancel an active pointer before the next attempt, rather than only testing UP.
                if (attempt < 2) {
                    val cancelTime = SystemClock.uptimeMillis()
                    dispatch(view, cancelTime, MotionEvent.ACTION_DOWN, x, y)
                    dispatch(view, cancelTime, MotionEvent.ACTION_CANCEL, x, y)
                    delay(100)
                }
            }
        }
    }

    private suspend fun dispatch(view: View, downTime: Long, action: Int, x: Float, y: Float) =
        withContext(Dispatchers.Main) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                view.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }

    private fun reports(tag: String): List<JSONObject> {
        val fd = instrumentation.uiAutomation.executeShellCommand("logcat -d -v raw -s $tag:I *:S")
        val log =
            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        return log.lineSequence()
            .filter { it.startsWith("{") }
            .map { JSONObject(it) }
            .filter { it.optInt("pid") == Process.myPid() }
            .toList()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup)
            for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
