package com.pico.spatial.sample.spatialvideo.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.json.JSONArray
import org.json.JSONObject

/** Inspection-only snapshot of the meshes, dimensions and material inputs used in Stage. */
fun main(args: Array<String>) {
    val meshes = JSONObject()
    val directory = File(args.single()).apply { mkdirs() }
    fun points(values: List<LearningOrbPoint>) = JSONArray(values.map { listOf(it.x, it.y, it.z) })
    fun rgb(tint: LearningCrystalOrbTint) = JSONArray(listOf(tint.red, tint.green, tint.blue))
    var vertices = 0
    var triangles = 0
    fun record(name: String, mesh: LearningOrbMesh, instances: Int = 1) {
        meshes.put(
            name,
            JSONObject()
                .put("positions", points(mesh.positions))
                .put("normals", points(mesh.normals))
                .put("uv", JSONArray(mesh.uv.map { listOf(it.first, it.second) }))
                .put("triangles", JSONArray(mesh.triangleIndices.chunked(3)))
        )
        vertices += mesh.positions.size * instances
        triangles += mesh.triangleIndices.size / 3 * instances
    }
    record("GlassSurface", LearningCrystalOrbMeshes.shell(), instances = 2)
    for (stream in LearningCrystalOrbStream.entries) {
        val sweep = LearningCrystalOrbMeshes.sweep(stream)
        record("${stream.name}Filaments", sweep.filaments)
        record("${stream.name}Veils", sweep.veils)
        val image =
            BufferedImage(
                LearningCrystalOrbCaustics.width,
                LearningCrystalOrbCaustics.height,
                BufferedImage.TYPE_INT_ARGB
            )
        image.setRGB(
            0,
            0,
            image.width,
            image.height,
            LearningCrystalOrbCaustics.pixels(stream),
            0,
            image.width
        )
        ImageIO.write(image, "png", File(directory, "${stream.name}.png"))
    }
    println(
        "Stage: ${meshes.length()} unique meshes, ${meshes.length() + 1} model instances, $vertices instanced vertices, $triangles triangles"
    )

    val frames = JSONArray()
    fun snapshot(state: LearningVoiceControlState, time: Double, level: Float? = null) {
        val f = learningCrystalOrbFrame(state, false, false, time, level)
        // Constant state since t=0, equivalent to integrating the renderer's elapsed frame deltas.
        val phase = (time * f.swirlSpeedDegrees) % LearningCrystalOrbMotion.swirlRepeatDegrees
        frames.put(
            JSONObject()
                .put("state", state.name)
                .put("time", time)
                .put("level", level)
                .put("visualScale", f.visualScale)
                .put("shellTint", rgb(f.shellTint))
                .put("shellOpacity", f.shellOpacity)
                .put("innerTint", rgb(f.innerTint))
                .put("innerEmission", f.glow * 0.35f)
                .put("innerScale", f.innerScale)
                .put("innerOpacity", LearningCrystalOrbGeometry.innerGlassOpacity)
                .put(
                    "streams",
                    JSONArray(
                        LearningCrystalOrbStream.entries.map {
                            JSONObject()
                                .put("name", it.name)
                                .put("tint", rgb(it.tint))
                                .put("yaw", phase * it.speedRatio)
                                .put("scale", f.sweepScale)
                                .put("threadLight", f.threadLight)
                                .put("veilOpacity", f.veilOpacity)
                        }
                    )
                )
        )
    }
    snapshot(LearningVoiceControlState.Ready, 0.65)
    snapshot(LearningVoiceControlState.Analyzing, 0.65)
    snapshot(LearningVoiceControlState.Answering, 0.65)
    snapshot(LearningVoiceControlState.Speaking, 1.35, 0f)
    snapshot(LearningVoiceControlState.Speaking, 0.45, 1f)
    File(directory, "orb-geometry.json")
        .writeText(JSONObject().put("meshes", meshes).put("frames", frames).toString())
}
