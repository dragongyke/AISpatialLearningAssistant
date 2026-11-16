"""Inspect the exported Stage meshes/poses in Blender. Lighting and SDK6 blending are approximations."""
import argparse
import json
import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector


def native(p):
    return (p[0], -p[2], p[1])


def surface(name, rgb, roughness, metal, emission=0, opacity=1, texture=None):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    tree = mat.node_tree
    nodes, link = tree.nodes, tree.links.new
    shader = nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (*rgb, 1)
    shader.inputs["Metallic"].default_value = metal
    shader.inputs["Roughness"].default_value = roughness
    shader.inputs["Emission Color"].default_value = (*rgb, 1)
    shader.inputs["Emission Strength"].default_value = emission
    if opacity < 1:
        transparent = nodes.new("ShaderNodeBsdfTransparent")
        mix = nodes.new("ShaderNodeMixShader")
        mix.inputs[0].default_value = opacity
        link(transparent.outputs[0], mix.inputs[1])
        if texture:
            emit = nodes.new("ShaderNodeEmission")
            tex = nodes.new("ShaderNodeTexImage")
            tex.image = texture
            link(tex.outputs["Color"], emit.inputs["Color"])
            add = nodes.new("ShaderNodeAddShader")
            link(transparent.outputs[0], add.inputs[0])
            link(emit.outputs[0], add.inputs[1])
            link(add.outputs[0], mix.inputs[2])
        else:
            link(shader.outputs[0], mix.inputs[2])
        # Both Stage transparent materials cull back faces and do not write depth.
        front = nodes.new("ShaderNodeMixShader")
        link(nodes.new("ShaderNodeNewGeometry").outputs["Backfacing"], front.inputs[0])
        link(mix.outputs[0], front.inputs[1])
        link(transparent.outputs[0], front.inputs[2])
        link(front.outputs[0], nodes.get("Material Output").inputs["Surface"])
    return mat


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True)
    out = parser.parse_args(sys.argv[sys.argv.index("--") + 1:]).output_dir
    payload = json.loads((out / "orb-geometry.json").read_text())
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 32
    scene.cycles.use_denoising = True
    scene.cycles.transparent_max_bounces = 24
    scene.world.use_nodes = True
    scene.world.node_tree.nodes.get("Background").inputs[0].default_value = (0.035, 0.025, 0.021, 1)
    scene.view_settings.view_transform = "Standard"
    scene.render.resolution_percentage = 100
    bpy.context.preferences.filepaths.save_version = 0

    geometry = {}
    maps = {}
    for stream in payload["frames"][0]["streams"]:
        name = stream["name"]
        maps[name] = bpy.data.images.load(str(out / (name + ".png")))
        maps[name].pack()
    for name, data in payload["meshes"].items():
        geo = bpy.data.meshes.new(name)
        geo.from_pydata([native(p) for p in data["positions"]], [], data["triangles"])
        geo.update()
        if data["uv"]:
            uv = geo.uv_layers.new(name="Current")
            for loop in geo.loops:
                u, v = data["uv"][loop.vertex_index]
                uv.data[loop.index].uv = (u, 1 - v)
        for poly in geo.polygons:
            poly.use_smooth = True
        geo.normals_split_custom_set_from_vertices([native(n) for n in data["normals"]])
        geo.materials.append(None)
        geometry[name] = geo

    def node(name, parent=None, data=None):
        obj = bpy.data.objects.new(name, data)
        scene.collection.objects.link(obj)
        obj.parent = parent
        return obj

    def instance(name, geometry_name, mat, parent, scale=1):
        obj = node(name, parent, geometry[geometry_name])
        obj.material_slots[0].link = "OBJECT"
        obj.material_slots[0].material = mat
        obj.scale = (scale,) * 3
        return obj

    def orb(frame, offset):
        root = node(frame["state"])
        root.location.x = offset
        mat = surface("Polished shell", frame["shellTint"], 0.08, 0.08, opacity=frame["shellOpacity"])
        glass_shader = mat.node_tree.nodes.get("Principled BSDF")
        glass_shader.inputs["Emission Color"].default_value = (0.028, 0.046, 0.085, 1)
        glass_shader.inputs["Emission Strength"].default_value = 1
        instance("Shell", "GlassSurface", mat, root)
        details = node("Details", root)
        details.scale = (frame["visualScale"],) * 3
        mat = surface("Inner glass", frame["innerTint"], 0.24, 0.15, frame["innerEmission"], frame["innerOpacity"])
        instance("InnerGlass", "GlassSurface", mat, details, frame["innerScale"])
        for stream in frame["streams"]:
            name = stream["name"]
            current = node(name, details)
            current.scale = (stream["scale"],) * 3
            current.rotation_euler.z = math.radians(stream["yaw"])
            rgb = stream["tint"]
            mat = surface(name + " light", [v * 0.34 for v in rgb], 0.22, 0.18, stream["threadLight"] / 0.34)
            instance(name + "Filaments", name + "Filaments", mat, current)
            mat = surface(name + " silk", rgb, 1, 0, opacity=stream["veilOpacity"], texture=maps[name])
            instance(name + "Veils", name + "Veils", mat, current)
        fill = bpy.data.lights.new("LocalFill", "POINT")
        fill.energy, fill.color, fill.shadow_soft_size = 0.45, (1, 0.87, 0.69), 0.008
        fill.use_shadow = False
        node("LocalFill", root, fill).location = native((0.08, 0.055, 0.135))
        return root

    roots = []
    for i, frame in enumerate(payload["frames"]):
        roots.append(orb(frame, (i - 2) * 0.305))

    def area(location, watts, width, height):
        lamp = bpy.data.lights.new("Studio reflection", "AREA")
        lamp.energy, lamp.shape, lamp.size, lamp.size_y = watts, "RECTANGLE", width, height
        obj = node("Studio reflection", data=lamp)
        obj.location = location
        obj.rotation_euler = (-obj.location).to_track_quat("-Z", "Y").to_euler()

    area((-0.7, -0.7, 1.2), 12, 1.4, 0.08)
    area((1, 0.4, 0.85), 10, 0.09, 1.1)
    bpy.ops.object.camera_add()
    camera = bpy.context.object
    camera.data.type = "ORTHO"
    scene.camera = camera

    def shot(target, span, width, height, filename):
        target = Vector(target)
        camera.location = target + Vector((0, -2, 0.21))
        camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
        camera.data.ortho_scale = span
        scene.render.resolution_x, scene.render.resolution_y = width, height
        scene.render.filepath = str(out / filename)

    shot((roots[0].location.x, 0, 0), 0.29, 760, 760, "orb-idle.png")
    bpy.ops.render.render(write_still=True)

    label_mat = surface("Labels", (0.65, 0.74, 0.84), 0.8, 0, emission=1)
    for root, label in zip(roots, ("READY", "ANALYZING", "ANSWERING", "SPEAKING / LOW", "SPEAKING / PEAK")):
        data = bpy.data.curves.new(label, "FONT")
        data.body, data.align_x, data.size = label, "CENTER", 0.015
        obj = node(label, data=data)
        obj.location = (root.location.x, 0, -0.16)
        obj.rotation_euler.x = math.radians(84)
        data.materials.append(label_mat)

    shot((0, 0, -0.014), 1.55, 1984, 480, "orb-states.png")
    bpy.ops.wm.save_as_mainfile(filepath=str(out / "orb-inspection.blend"))
    bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    main()
