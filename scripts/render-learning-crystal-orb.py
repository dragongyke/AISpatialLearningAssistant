"""Render exported Kotlin orb geometry. Materials approximate SDK6, not a device capture."""
import argparse
import json
import math
from pathlib import Path
import sys

import bpy
from mathutils import Euler, Matrix, Vector


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
    out = args.output_dir
    payload = json.loads((out / 'orb-geometry.json').read_text())
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 40
    scene.cycles.use_denoising = True
    scene.render.resolution_x = 2000
    scene.render.resolution_y = 820
    scene.render.resolution_percentage = 100
    scene.world.color = (0.10, 0.10, 0.10)
    scene.view_settings.view_transform = 'AgX'
    conversion = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))

    def native(vector):
        return (vector[0], -vector[2], vector[1])

    def material(name, color, roughness=0.4, metal=0, emission=0, opacity=1):
        result = bpy.data.materials.new(name)
        result.use_nodes = True
        nodes = result.node_tree.nodes
        shader = nodes.get('Principled BSDF')
        shader.inputs['Base Color'].default_value = (*color, 1)
        shader.inputs['Metallic'].default_value = metal
        shader.inputs['Roughness'].default_value = roughness
        shader.inputs['Emission Color'].default_value = (*color, 1)
        shader.inputs['Emission Strength'].default_value = emission
        if opacity < 1:
            mix = nodes.new('ShaderNodeMixShader')
            transparent = nodes.new('ShaderNodeBsdfTransparent')
            mix.inputs[0].default_value = opacity
            links = result.node_tree.links
            links.new(transparent.outputs[0], mix.inputs[1])
            links.new(shader.outputs[0], mix.inputs[2])
            links.new(mix.outputs[0], nodes.get('Material Output').inputs['Surface'])
        return result

    wood = material('Dark walnut', (0.075, 0.030, 0.018), 0.46)
    brass = material('Aged brass', (0.42, 0.27, 0.10), 0.42, 0.72)
    patina = material('Patinated brass', (0.19, 0.22, 0.15), 0.52, 0.58)

    def pose(obj, parent, position=(0, 0, 0), rotation=(0, 0, 0), scale=1):
        obj.parent = parent
        rotation_matrix = Euler(tuple(math.radians(v) for v in rotation), 'XYZ').to_matrix().to_4x4()
        obj.matrix_basis = conversion @ rotation_matrix @ conversion.inverted()
        obj.location = native(position)
        obj.scale = (scale, scale, scale)
        return obj

    def mesh(name, parent, mat, rotation=(0, 0, 0), scale=1):
        data = payload['meshes'][name]
        geometry = bpy.data.meshes.new(name)
        geometry.from_pydata([native(p) for p in data['positions']], [], data['triangles'])
        geometry.update()
        for poly in geometry.polygons:
            poly.use_smooth = True
        geometry.normals_split_custom_set_from_vertices([native(n) for n in data['normals']])
        obj = bpy.data.objects.new(name, geometry)
        scene.collection.objects.link(obj)
        obj.data.materials.append(mat)
        return pose(obj, parent, rotation=rotation, scale=scale)

    def sphere(name, radius, parent, mat, position=(0, 0, 0), scale=1):
        bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=radius)
        obj = bpy.context.object
        obj.name = name
        obj.data.materials.append(mat)
        for poly in obj.data.polygons:
            poly.use_smooth = True
        return pose(obj, parent, position=position, scale=scale)

    def torus(name, outer, inner, parent, mat, rotation=(0, 0, 0), scale=1):
        bpy.ops.mesh.primitive_torus_add(major_radius=(outer + inner) / 2,
            minor_radius=(outer - inner) / 2, major_segments=80, minor_segments=8)
        obj = bpy.context.object
        obj.name = name
        obj.data.materials.append(mat)
        for poly in obj.data.polygons:
            poly.use_smooth = True
        return pose(obj, parent, rotation=rotation, scale=scale)

    def empty(name, parent=None, **kwargs):
        obj = bpy.data.objects.new(name, None)
        scene.collection.objects.link(obj)
        return pose(obj, parent, **kwargs)

    def orb(frame, offset):
        root = empty(frame['state'])
        root.location.x = offset
        tint = frame['tint']
        core_mat = material('Heart', [v * 0.3 for v in tint], 0.16, 0.18, emission=frame['glow'] / 0.3)
        shell_mat = material('Crystal', [0.64 + tint[0] * 0.25, 0.68 + tint[1] * 0.22, 0.76 + tint[2] * 0.2],
            0.08, 0.08, opacity=frame['shellOpacity'])
        star_mat = material('Starlight', tint, emission=1)
        sphere('Crystal shell', 0.12, root, shell_mat)
        angle = frame['time'] * frame['orbitSpeed']
        core = empty('Core', root, rotation=(12, -angle * 0.6, 18), scale=frame['coreScale'])
        sphere('Heart', 0.036, core, core_mat)
        for sign in (-1, 1):
            bpy.ops.mesh.primitive_cone_add(vertices=48, radius1=0.027, radius2=0, depth=0.048)
            obj = bpy.context.object
            obj.data.materials.append(core_mat)
            pose(obj, core, position=(0, sign * 0.024, 0), rotation=(180 if sign < 0 else 0, 0, 0))
        halo_mat = material('Inner light', tint, emission=frame['glow'], opacity=0.06 + frame['glow'] * 0.12)
        sphere('Inner light', 0.078, root, halo_mat, scale=0.98 + (frame['coreScale'] - 1) * 0.4)
        inner = empty('Inner orbit', root, rotation=(62, angle * 0.8, 26))
        torus('Filament', 0.087, 0.085, inner, star_mat)
        torus('Meridian', 0.065, 0.0638, inner, patina, rotation=(72, 25, 12))
        for index, (radius, rotation) in enumerate(((0.145, (54, angle, 18)), (0.153, (-38, -angle * 0.72, 66)))):
            orbit = empty('Orbit', root, rotation=rotation)
            torus('Armillary ring', radius, radius - 0.002, orbit, brass if index == 0 else patina)
            for star in range(2):
                phase = star * math.pi + index * math.pi / 2
                center = ((radius - 0.001) * math.cos(phase), 0, (radius - 0.001) * math.sin(phase))
                for dimensions in ((2.6, 0.6, 0.6), (0.6, 2.6, 0.6), (0.6, 0.6, 2.6)):
                    obj = sphere('Star ray', 0.0028, orbit, star_mat, position=center)
                    obj.scale = (dimensions[0], dimensions[2], dimensions[1])
        mesh('wood', root, wood)
        mesh('brass', root, brass)
        if frame['waitingGlow'] > 0:
            mat = material('Waiting signal', tint, emission=1, opacity=frame['waitingGlow'])
            swirl_angle = frame['time'] * frame['swirlSpeed']
            mesh('swirl', root, mat, rotation=(18, swirl_angle, -12), scale=frame['gatheringScale'])
            mesh('runes', root, mat, rotation=(18, -swirl_angle * 0.35, 12))
        if frame['speakingGlow'] > 0:
            mat = material('Speaking signal', tint, emission=1, opacity=frame['speakingGlow'])
            speaking = empty('Speaking', root, rotation=(16, 0, 0), scale=frame['speakingScale'])
            torus('Voice resonance', 0.134, 0.1315, speaking, mat)
            torus('Voice echo', 0.134, 0.1315, speaking, mat, rotation=(24, 0, 0), scale=0.86)
        light = bpy.data.lights.new('Local warm fill', 'POINT')
        light.energy = 1.2
        light.color = (1, 0.87, 0.69)
        light.shadow_soft_size = 0.025
        obj = bpy.data.objects.new('Local warm fill', light)
        scene.collection.objects.link(obj)
        pose(obj, root, position=(0.08, 0.055, 0.135))
        bpy.ops.object.text_add(location=(offset, -0.23, -0.15), rotation=(math.radians(67), 0, 0))
        label = bpy.context.object
        label.data.body = frame['state'].upper()
        label.data.align_x = 'CENTER'
        label.data.size = 0.023
        label.data.materials.append(material('Label', (0.72, 0.74, 0.78), emission=0.4))

    for index, frame in enumerate(payload['frames'][1::2]):
        orb(frame, (index - 1.5) * 0.39)

    bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, -0.155))
    floor = bpy.context.object
    floor.data.materials.append(material('Neutral backdrop', (0.065, 0.068, 0.075), 0.8))
    for location, energy, size in (((-0.8, -0.9, 1.1), 45, 1.1), ((0.9, 0.5, 0.7), 35, 0.8)):
        light = bpy.data.lights.new('Studio inspection', 'AREA')
        light.energy, light.shape, light.size = energy, 'DISK', size
        obj = bpy.data.objects.new('Studio inspection', light)
        scene.collection.objects.link(obj)
        obj.location = location
        obj.rotation_euler = (Vector((0, 0, 0)) - obj.location).to_track_quat('-Z', 'Y').to_euler()
    bpy.ops.object.camera_add(location=(0.52, -2.8, 0.95))
    camera = bpy.context.object
    camera.rotation_euler = (Vector((0, 0, -0.02)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.type = 'ORTHO'
    camera.data.ortho_scale = 1.62
    scene.camera = camera
    scene.render.filepath = str(out / 'orb-states.png')
    bpy.ops.wm.save_as_mainfile(filepath=str(out / 'orb-inspection.blend'))
    bpy.ops.render.render(write_still=True)


if __name__ == '__main__':
    main()
