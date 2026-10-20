package com.pico.spatial.sample.spatialvideo.data

import com.pico.spatial.core.ecs.resource.AssetBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

const val ACADEMIC_LECTURE_HALL_SCENE = "AcademicLectureHall"

val lectureHallAssetBundle =
    CoroutineScope(Dispatchers.IO).async(start = CoroutineStart.LAZY) {
        AssetBundle.load("asset://lecture-hall-editor-asset.bundle")
    }
