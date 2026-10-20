"""Run inside Blender to validate the shipped library GLB, not just its source."""
import argparse
import json
import struct
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Vector


def bounds(obj):
    points = [obj.matrix_world @ Vector(p) for p in obj.bound_box]
    return ([min(p[i] for p in points) for i in range(3)],
            [max(p[i] for p in points) for i in range(3)])


def learning_contract(meshes):
    """Check actual surfaces, not generator metadata, for integration regressions."""
    errors = []
    scene = bpy.context.scene
    depsgraph = bpy.context.evaluated_depsgraph_get()
    bay = next((o for o in meshes if o.name == "LearningBay"), None)
    if bay is None or not np.allclose(bounds(bay), [[-2.2, -7.545, .2], [2.2, -7.295, 4.05]], atol=.002):
        errors.append("Empty wall bay does not preserve its full 4.4 x 3.85 m envelope")
    for x in (-2.0, -1.85, -1.0, 0.0, 1.0, 1.85, 2.0):
        for z in (.25, 1.2, 2.1, 3.0, 4.0):
            hit = scene.ray_cast(depsgraph, Vector((x, -4.395, z)),
                                 Vector((0, -1, 0)), distance=3.0)
            if not hit[0] or abs(hit[1].y + 7.295) > .002 or hit[2].y < .98:
                errors.append(f"Mounting bay is not flat/empty at x={x}, z={z}: "
                              f"hit={tuple(hit[1]) if hit[0] else None}")
    # Polygon bounds catch objects between sample rays as well as large crossing faces.
    blockers = []
    for obj in meshes:
        verts = [obj.matrix_world @ v.co for v in obj.data.vertices]
        for poly in obj.data.polygons:
            points = [verts[i] for i in poly.vertices]
            lo = [min(p[i] for p in points) for i in range(3)]
            hi = [max(p[i] for p in points) for i in range(3)]
            if all(hi[i] > (-3.5, -7.0, .03)[i] and
                   lo[i] < (3.5, -3.0, 4.0)[i] for i in range(3)):
                blockers.append(obj.name)
                break
    if blockers:
        errors.append(f"Central learning zone is obstructed by {blockers}")
    stations = [o for o in meshes if o.name.startswith("ReadingStation_")]
    if len(stations) != 4:
        errors.append(f"Expected four side reading stations, found {len(stations)}")
    for obj in stations:
        lo, hi = bounds(obj)
        if not (lo[0] >= 3.5 or hi[0] <= -3.5):
            errors.append(f"Reading station enters central aisle: {obj.name}, {lo}, {hi}")
        if not (1.0 < hi[2] < 1.4 and hi[2] - lo[2] > 1.0):
            errors.append(f"Reading station lacks full-height chairs: {obj.name}")
    for x in (-4.5, 4.5):
        for y in (-2.5, .3):
            hit = scene.ray_cast(depsgraph, Vector((x, y, 1.5)),
                                 Vector((0, 0, -1)), distance=1)
            if not hit[0] or not .74 < hit[1].z < .82:
                errors.append(f"Missing library desktop at {x}, {y}")
    tiles = [o for o in meshes if o.name.startswith("FloorTile_")]
    if len(tiles) < 12:
        errors.append(f"Floor is still one global atlas; local tiles found: {len(tiles)}")
    for obj in tiles:
        lo, hi = bounds(obj)
        if hi[0] - lo[0] > 3.4 or hi[1] - lo[1] > 3.5:
            errors.append(f"Floor tile has insufficient local coverage: {obj.name}")
    return errors


def source_contract():
    errors = []
    books = [o for o in bpy.context.scene.objects if o.type == "MESH" and "Books" in o.name]
    page_faces = sum(sum(o.data.materials[p.material_index].name == "Paper"
                         for p in o.data.polygons) for o in books)
    if page_faces < 3000:
        errors.append(f"Books lack modeled page blocks and separate covers: paper faces={page_faces}")
    front_books = bpy.data.objects.get("Books")
    if front_books:
        spines = [p for p in front_books.data.polygons
                  if 5.075 < p.center.y < 5.089 and abs(p.normal.y) > .5
                  and front_books.data.materials[p.material_index].name.startswith("Book")]
        if any(p.normal.y > 0 for p in spines):
            errors.append("Book spine normals point inward; diffuse bake will blacken visible covers")
    images = [im for im in bpy.data.images if im.type == "IMAGE"]
    if not images or not all(im.packed_file for im in images):
        errors.append("Editable source has unpacked image dependencies")
    return errors


def verify(out, asset_dir=None, contracts_only=False, usd=False):
    asset_dir = asset_dir or out
    out.mkdir(parents=True, exist_ok=True)
    source = asset_dir / "academic-library.blend"
    exported = asset_dir / "academic-library.glb"
    assert source.is_file(), "Editable Blender source is missing"
    assert exported.is_file(), "Runtime GLB is missing"
    binary = exported.read_bytes()
    magic, version, length = struct.unpack_from("<4sII", binary)
    assert magic == b"glTF" and version == 2 and length == len(binary)
    json_size, chunk_type = struct.unpack_from("<I4s", binary, 12)
    assert chunk_type == b"JSON"
    document = json.loads(binary[20:20 + json_size])
    assert all("uri" not in buf for buf in document["buffers"]), "GLB has external buffers"
    assert all("bufferView" in im and "uri" not in im for im in document["images"]), "GLB has external images"
    assert all("KHR_materials_unlit" in mat.get("extensions", {})
               for mat in document["materials"]), "Baked materials must actually be unlit"
    bpy.ops.wm.open_mainfile(filepath=str(source))
    assert bpy.context.scene.unit_settings.system == "METRIC"
    errors = source_contract()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(exported))
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    errors.extend(learning_contract(meshes))
    (out / "contract-checks.json").write_text(json.dumps({"errors": errors}, indent=2), encoding="utf-8")
    assert not errors, "\n".join(errors)
    if contracts_only:
        return
    assert 16 <= len(meshes) <= 48, f"Unexpected runtime mesh count: {len(meshes)}"
    triangles = 0
    points = []
    for obj in meshes:
        obj.data.calc_loop_triangles()
        triangles += len(obj.data.loop_triangles)
        assert obj.data.uv_layers, f"Missing UVs: {obj.name}"
        assert obj.data.materials, f"Missing material: {obj.name}"
        points.extend(obj.matrix_world @ Vector(p) for p in obj.bound_box)
    assert 10000 < triangles < 400000, f"Triangle budget exceeded: {triangles}"
    lower = [min(p[i] for p in points) for i in range(3)]
    upper = [max(p[i] for p in points) for i in range(3)]
    size = [upper[i] - lower[i] for i in range(3)]
    assert 11 < size[0] < 17, f"Incorrect width in meters: {size}"
    assert 15 < size[1] < 26, f"Incorrect depth in meters: {size}"
    assert 7 < size[2] < 12, f"Incorrect height in meters: {size}"
    assert -0.3 < lower[2] < 0.05, f"Floor is not at ground level: {lower}"
    assert np.allclose(lower, [-6.675, -7.567, -.25], atol=.025), f"Model origin/orientation moved: {lower}"
    assert np.allclose(upper, [6.675, 13, 9.45], atol=.025), f"Model bounds changed: {upper}"
    images = [im for im in bpy.data.images if im.type == "IMAGE"]
    assert len(images) >= 6, "Missing baked material textures"
    texture_stats = []
    for im in images:
        pixels = np.empty(len(im.pixels), dtype=np.float32)
        im.pixels.foreach_get(pixels)
        assert pixels.size and np.isfinite(pixels).all(), f"Invalid texture: {im.name}"
        rgb = pixels.reshape(-1, 4)[:, :3]
        assert rgb.max() > 0.02, f"Black/empty texture: {im.name}"
        assert float(rgb.std()) > 0.005, f"Texture has no visible variation: {im.name}"
        assert im.packed_file, f"Texture not embedded in GLB: {im.name}"
        assert max(im.size) <= 4096, f"Unnecessary oversized texture: {im.name}"
        texture_stats.append({"name": im.name, "size": list(im.size)})
    assert sum(im.size[0] * im.size[1] for im in images) <= 100 * 1024 * 1024, "Texture pixel budget exceeded"
    floor_density = []
    for obj in meshes:
        if not obj.name.startswith("FloorTile_"):
            continue
        image = next(n.image for n in obj.data.materials[0].node_tree.nodes
                     if n.type == "TEX_IMAGE" and n.image)
        density = []
        uv = obj.data.uv_layers.active.data
        for tri in obj.data.loop_triangles:
            if tri.normal.z < .99:
                continue
            a, b, c = [Vector(uv[i].uv) for i in tri.loops]
            area_uv = abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2
            density.append((area_uv * image.size[0] * image.size[1] / tri.area) ** .5)
        lo, hi = bounds(obj)
        if lo[1] < -.5:
            assert min(density) >= 275, f"Near floor texel density too low: {obj.name}, {min(density)}"
        floor_density.append({"mesh": obj.name, "minimum_texels_per_meter": min(density)})
    # The relocated viewer and rear learning zone are the primary interaction volume.
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for origin, direction, distance in [
        ((0, -4.395, 1.65), (0, -1, 0), 2.6),
        ((-3.4, -4.395, 1.4), (1, 0, 0), 6.8),
        ((0, -3, .05), (0, -1, 0), 4.0),
        ((0, 0, 1.65), (0, 1, 0), 9.0),
    ]:
        hit = bpy.context.scene.ray_cast(depsgraph, Vector(origin), Vector(direction), distance=distance)
        assert not hit[0], f"Environment blocks the learning volume: {origin}, {hit[4]}"
    report = {"mesh_count": len(meshes), "triangles": triangles,
              "bounds_min_blender_z_up": lower, "bounds_max_blender_z_up": upper,
              "size_meters": size, "textures": texture_stats,
              "glb_bytes": exported.stat().st_size, "clearance_checks": 4,
              "floor_texel_density": floor_density,
              "texture_megapixels": sum(im.size[0] * im.size[1] for im in images) / 1024 ** 2,
              "mounting_plane_blender": {"x": [-2.2, 2.2], "y": -7.295, "z": [.2, 4.05]},
              "clear_zone_blender": {"x": [-3.5, 3.5], "y": [-7, -3], "z": [.03, 4.0]},
              "viewer_blender": [0, -4.395, 1.65]}
    with bpy.data.libraries.load(str(source), link=False) as (available, requested):
        requested.objects = [name for name in available.objects
                             if name in ("FrontCamera", "ViewerCamera", "ReverseCamera", "LearningCamera", "DetailCamera")]
    for obj in requested.objects:
        bpy.context.scene.collection.objects.link(obj)
    for name, position, target, lens in [
        ("FloorDetailCamera", (0, -3.7, 1.15), (0, -5.1, -.001), 38),
        ("FurnitureCamera", (-2.75, -4.15, 1.65), (-4.5, -1.7, .65), 28),
    ]:
        data = bpy.data.cameras.new(name)
        data.lens = lens
        obj = bpy.data.objects.new(name, data)
        bpy.context.scene.collection.objects.link(obj)
        obj.location = position
        obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 8
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = 1536, 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0
    for camera_name, filename in [("FrontCamera", "glb-front.png"),
                                   ("ViewerCamera", "glb-viewer.png"),
                                   ("ReverseCamera", "glb-reverse.png"),
                                   ("LearningCamera", "glb-learning.png"),
                                   ("DetailCamera", "glb-detail.png"),
                                   ("FloorDetailCamera", "glb-floor-detail.png"),
                                   ("FurnitureCamera", "glb-furniture.png")]:
        scene.camera = bpy.data.objects[camera_name]
        scene.render.filepath = str(out / filename)
        bpy.ops.render.render(write_still=True)
    original = bpy.data.images.load(str(asset_dir / "runtime-front.png"))
    roundtrip = bpy.data.images.load(str(out / "glb-front.png"))
    a = np.array(original.pixels[:], dtype=np.float32)
    b = np.array(roundtrip.pixels[:], dtype=np.float32)
    assert a.shape == b.shape
    error = float(np.abs(a - b).mean())
    assert error < .025, f"GLB material round-trip changed the image: MAE={error}"
    report["roundtrip_render_mean_absolute_error"] = error
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from inspect_book_bake import check_readability
    report["book_bake_source_luminance_ratio"] = check_readability(asset_dir if asset_dir == out else out)
    if usd:
        from pxr import Usd, UsdGeom, UsdShade
        usd_path = asset_dir / "usd" / "AcademicLibrary.usdc"
        stage = Usd.Stage.Open(str(usd_path))
        assert UsdGeom.GetStageUpAxis(stage) == "Y", "USD is not Y-up"
        assert UsdGeom.GetStageMetersPerUnit(stage) == 1, "USD changed meter units"
        bbox_cache = UsdGeom.BBoxCache(Usd.TimeCode.Default(), [UsdGeom.Tokens.default_])
        world_box = bbox_cache.ComputeWorldBound(stage.GetPrimAtPath("/AcademicLibrary")).ComputeAlignedRange()
        usd_bounds = [list(world_box.GetMin()), list(world_box.GetMax())]
        assert np.allclose(usd_bounds, [[-6.675, -.25, -13], [6.675, 9.45, 7.567]], atol=.025), \
            f"USD raw Y-up bounds changed orientation/scale: {usd_bounds}"
        bay_mesh = next(p for p in stage.Traverse() if p.IsA(UsdGeom.Mesh) and "LearningBay" in str(p.GetPath()))
        bay_box = bbox_cache.ComputeWorldBound(bay_mesh).ComputeAlignedRange()
        assert np.allclose([list(bay_box.GetMin()), list(bay_box.GetMax())],
                           [[-2.2, .2, 7.295], [2.2, 4.05, 7.545]], atol=.002), "USD bay is not at PICO +Z7.295"
        files = []
        for prim in stage.Traverse():
            if prim.IsA(UsdShade.Shader):
                shader = UsdShade.Shader(prim)
                if shader.GetIdAttr().Get() == "UsdUVTexture":
                    path = shader.GetInput("file").Get()
                    assert path and not Path(path.path).is_absolute(), "USD texture path is not relative"
                    assert Path(path.resolvedPath).is_file(), f"USD texture missing: {path}"
                    files.append(path.path)
        assert len(set(files)) == len(images), "USD lost baked textures"
        bpy.ops.wm.read_factory_settings(use_empty=True)
        bpy.ops.wm.usd_import(filepath=str(usd_path))
        usd_meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
        assert len(usd_meshes) == len(meshes), "USD lost meshes"
        usd_errors = learning_contract(usd_meshes)
        assert not usd_errors, "USD changed the coordinate/clearance contract: " + "; ".join(usd_errors)
        for obj in usd_meshes:
            obj.data.calc_loop_triangles()
        assert sum(len(o.data.loop_triangles) for o in usd_meshes) == triangles, "USD changed triangle count"
        with bpy.data.libraries.load(str(source), link=False) as (available, requested):
            requested.objects = ["LearningCamera"]
        for obj in requested.objects:
            bpy.context.scene.collection.objects.link(obj)
        scene = bpy.context.scene
        scene.camera = requested.objects[0]
        scene.render.engine = "CYCLES"
        scene.cycles.samples = 8
        scene.render.resolution_x, scene.render.resolution_y = 1536, 1024
        scene.render.resolution_percentage = 100
        scene.view_settings.view_transform = "Standard"
        scene.view_settings.look = "None"
        scene.view_settings.exposure = 0
        scene.render.filepath = str(out / "usd-learning.png")
        bpy.ops.render.render(write_still=True)
        glb_image = bpy.data.images.load(str(out / "glb-learning.png"))
        usd_image = bpy.data.images.load(str(out / "usd-learning.png"))
        usd_error = float(np.abs(np.array(glb_image.pixels[:]) - np.array(usd_image.pixels[:])).mean())
        assert usd_error < .025, f"USD changed the baked appearance: MAE={usd_error}"
        report["usd"] = {"textures": sorted(set(files)), "bounds_y_up": usd_bounds,
                         "roundtrip_render_mean_absolute_error": usd_error}
    (out / "verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--asset-dir", type=Path)
    parser.add_argument("--contracts-only", action="store_true")
    parser.add_argument("--usd", action="store_true")
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    verify(args.output_dir, args.asset_dir, args.contracts_only, args.usd)
