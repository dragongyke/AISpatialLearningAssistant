"""Convert the baked GLB to portable USD Preview Surface materials for PICO Editor."""
import argparse
import sys
from pathlib import Path

import bpy


def export(glb, out):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(glb.resolve()))
    objects = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    assert 16 <= len(objects) <= 48, "Unexpected library mesh set"
    for mat in bpy.data.materials:
        nodes, links = mat.node_tree.nodes, mat.node_tree.links
        images = [node.image for node in nodes if node.type == "TEX_IMAGE" and node.image]
        assert len(images) == 1, f"Expected one baked image: {mat.name}"
        image = images[0]
        nodes.clear()
        tex = nodes.new("ShaderNodeTexImage")
        tex.image = image
        uv = nodes.new("ShaderNodeUVMap")
        uv.uv_map = "UVMap"
        links.new(uv.outputs[0], tex.inputs[0])
        shader = nodes.new("ShaderNodeBsdfPrincipled")
        shader.inputs["Base Color"].default_value = (0, 0, 0, 1)
        shader.inputs["Roughness"].default_value = 1
        shader.inputs["Specular IOR Level"].default_value = 0
        shader.inputs["Emission Strength"].default_value = 1
        links.new(tex.outputs[0], shader.inputs["Emission Color"])
        output = nodes.new("ShaderNodeOutputMaterial")
        links.new(shader.outputs[0], output.inputs["Surface"])
    bpy.context.scene.unit_settings.system = "METRIC"
    bpy.context.scene.unit_settings.scale_length = 1
    out.mkdir(parents=True, exist_ok=True)
    kwargs = dict(selected_objects_only=False, export_animation=False, export_materials=True,
                  export_textures_mode="NEW", overwrite_textures=True, relative_paths=True,
                  generate_preview_surface=True, generate_materialx_network=False,
                  convert_orientation=True, export_global_forward_selection="NEGATIVE_Z",
                  export_global_up_selection="Y", root_prim_path="/AcademicLibrary",
                  export_lights=False, export_cameras=False, triangulate_meshes=True,
                  export_custom_properties=False)
    bpy.ops.wm.usd_export(filepath=str(out / "AcademicLibrary.usdc"), **kwargs)
    bpy.ops.wm.usd_export(filepath=str(out / "AcademicLibrary.usdz"), **kwargs)
    print(f"USD_READY {out} meshes={len(objects)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--glb", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    export(args.glb, args.output_dir.resolve())
