package com.pico.spatial.sample.spatialvideo.ui

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports the actual Kotlin geometry and frames for an offline Blender inspection, not an asset.
 */
fun main(args: Array<String>) {
    fun points(values: List<LearningOrbPoint>) = JSONArray(values.map { listOf(it.x, it.y, it.z) })
    val meshes = JSONObject()
    for ((name, mesh) in
        listOf(
            "wood" to LearningCrystalOrbMeshes.pedestalWood(),
            "brass" to LearningCrystalOrbMeshes.pedestalBrass(),
            "swirl" to LearningCrystalOrbMeshes.waitingSwirl(),
            "runes" to LearningCrystalOrbMeshes.waitingRunes(),
        )) {
        meshes.put(
            name,
            JSONObject()
                .put("positions", points(mesh.positions))
                .put("normals", points(mesh.normals))
                .put("triangles", JSONArray(mesh.triangleIndices.chunked(3)))
        )
        println(
            "$name: ${mesh.positions.size} vertices, ${mesh.triangleIndices.size / 3} triangles"
        )
    }
    val frames = JSONArray()
    for (state in
        listOf(
            LearningVoiceControlState.Ready,
            LearningVoiceControlState.Analyzing,
            LearningVoiceControlState.Answering,
            LearningVoiceControlState.Speaking
        )) {
        for (time in listOf(0.0, 0.65)) {
            val frame = learningCrystalOrbFrame(state, false, false, time)
            frames.put(
                JSONObject()
                    .put("state", state.name)
                    .put("time", time)
                    .put(
                        "tint",
                        JSONArray(listOf(frame.tint.red, frame.tint.green, frame.tint.blue))
                    )
                    .put("glow", frame.glow)
                    .put("coreScale", frame.coreScale)
                    .put("shellOpacity", frame.shellOpacity)
                    .put("orbitSpeed", frame.orbitSpeedDegrees)
                    .put("waitingGlow", frame.waitingGlow)
                    .put("gatheringScale", frame.gatheringScale)
                    .put("swirlSpeed", frame.swirlSpeedDegrees)
                    .put("speakingGlow", frame.speakingGlow)
                    .put("speakingScale", frame.speakingScale)
            )
        }
    }
    val directory = File(args.single()).apply { mkdirs() }
    File(directory, "orb-geometry.json")
        .writeText(JSONObject().put("meshes", meshes).put("frames", frames).toString())
}
