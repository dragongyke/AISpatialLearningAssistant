"""Build the approved collegiate library, bake its lighting, and export a portable GLB.

Blender coordinates: X right, Y forward into the library, Z up; origin is the viewer.
The glTF exporter converts this to PICO's Y-up / -Z-forward convention.
"""
import argparse
import json
import math
import random
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Vector

RNG = random.Random(9212026)
GROUPS = {}
MATERIALS = {}
BOOK_COUNT = 0
FLOOR_TILES = {}


class MeshBatch:
    def __init__(self, name):
        self.name = name
        self.vertices, self.faces, self.material_ids, self.uvs = [], [], [], []

    def polyhedron(self, vertices, faces, material, grain="vertical"):
        offset = len(self.vertices)
        self.vertices.extend(vertices)
        for face in faces:
            self.faces.append(tuple(offset + i for i in face))
            self.material_ids.append(list(MATERIALS).index(material))
            points = [Vector(vertices[i]) for i in face]
            normal = (points[1] - points[0]).cross(points[2] - points[0])
            axis = max(range(3), key=lambda i: abs(normal[i]))
            uvs = []
            for p in points:
                if axis == 2:
                    uvs.append((p.y / .55, p.x / 2.2) if material.startswith("FloorOak")
                               else (p.x / 0.9, p.y / 2.5))
                elif axis == 1:
                    uvs.append((p.x / 0.9, p.z / 2.5))
                else:
                    uvs.append((p.y / 0.9, p.z / 2.5))
            self.uvs.extend(uvs)

    def finish(self):
        mesh = bpy.data.meshes.new(self.name)
        mesh.from_pydata(self.vertices, [], self.faces)
        mesh.update()
        for mat in MATERIALS.values():
            mesh.materials.append(mat)
        for poly, mat_id in zip(mesh.polygons, self.material_ids):
            poly.material_index = mat_id
        uv = mesh.uv_layers.new(name="SurfaceUV")
        for item, coords in zip(uv.data, self.uvs):
            item.uv = coords
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
        bm.to_mesh(mesh)
        bm.free()
        obj = bpy.data.objects.new(self.name, mesh)
        bpy.data.collections["LibraryGeometry"].objects.link(obj)
        return obj


def batch(name):
    if name not in GROUPS:
        GROUPS[name] = MeshBatch(name)
    return GROUPS[name]


def box(group, name, center, size, mat, yaw=0):
    x, y, z = center
    sx, sy, sz = (s / 2 for s in size)
    c, s = math.cos(yaw), math.sin(yaw)
    verts = [(x + c * a - s * b, y + s * a + c * b, z + d)
             for a, b, d in [(-sx, -sy, -sz), (sx, -sy, -sz), (sx, sy, -sz),
                             (-sx, sy, -sz), (-sx, -sy, sz), (sx, -sy, sz),
                             (sx, sy, sz), (-sx, sy, sz)]]
    batch(group).polyhedron(verts, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4),
                                    (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)], mat)


def beam(group, name, start, end, radius, mat, sides=8):
    a, b = Vector(start), Vector(end)
    axis = (b - a).normalized()
    guide = Vector((0, 0, 1)) if abs(axis.z) < 0.9 else Vector((1, 0, 0))
    u, v = axis.cross(guide).normalized(), None
    v = axis.cross(u).normalized()
    verts = [tuple(p + radius * (u * math.cos(i * math.tau / sides) +
                                v * math.sin(i * math.tau / sides)))
             for p in (a, b) for i in range(sides)]
    faces = [tuple(reversed(range(sides))), tuple(range(sides, sides * 2))]
    faces += [(i, (i + 1) % sides, (i + 1) % sides + sides, i + sides)
              for i in range(sides)]
    batch(group).polyhedron(verts, faces, mat)


def arch(group, cx, y, spring, radius, thickness, depth, mat, rz=None, steps=40):
    rz = radius if rz is None else rz
    verts = []
    for i in range(steps + 1):
        t = i * math.pi / steps
        for yy, r, zrad in [(y - depth / 2, radius, rz),
                            (y - depth / 2, radius + thickness, rz + thickness),
                            (y + depth / 2, radius, rz),
                            (y + depth / 2, radius + thickness, rz + thickness)]:
            verts.append((cx + r * math.cos(t), yy, spring + zrad * math.sin(t)))
    faces = [(0, 2, 3, 1), (4 * steps, 4 * steps + 1, 4 * steps + 3, 4 * steps + 2)]
    for i in range(steps):
        a, b = i * 4, (i + 1) * 4
        faces.extend([(a, b, b + 1, a + 1), (a + 2, a + 3, b + 3, b + 2),
                      (a, a + 2, b + 2, b), (a + 1, b + 1, b + 3, a + 3)])
    batch(group).polyhedron(verts, faces, mat)


def spandrel(cx, y, spring, radius, top, depth, mat):
    for side in (-1, 1):
        verts = []
        steps = 24
        for i in range(steps + 1):
            x = radius * i / steps
            z = spring + math.sqrt(max(0, radius * radius - x * x))
            verts.extend([(cx + side * x, y - depth / 2, z),
                          (cx + side * x, y - depth / 2, top),
                          (cx + side * x, y + depth / 2, z),
                          (cx + side * x, y + depth / 2, top)])
        faces = []
        for i in range(steps):
            a, b = i * 4, (i + 1) * 4
            faces.extend([(a, b, b + 1, a + 1), (a + 2, a + 3, b + 3, b + 2),
                          (a, a + 2, b + 2, b), (a + 1, b + 1, b + 3, a + 3)])
        faces.extend([(0, 1, 3, 2), (steps * 4, steps * 4 + 2, steps * 4 + 3, steps * 4 + 1)])
        batch("Architecture").polyhedron(verts, faces, mat)


def material(name, color, roughness=0.65, wood=None, emission=0):
    mat = bpy.data.materials.new(name)
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    shader = nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (*color, 1)
    shader.inputs["Roughness"].default_value = roughness
    if emission:
        shader.inputs["Emission Color"].default_value = (*color, 1)
        shader.inputs["Emission Strength"].default_value = emission
    if wood:
        uv = nodes.new("ShaderNodeUVMap")
        uv.uv_map = "SurfaceUV"
        tex = nodes.new("ShaderNodeTexImage")
        tex.image = wood
        links.new(uv.outputs[0], tex.inputs[0])
        mix = nodes.new("ShaderNodeMixRGB")
        mix.blend_type = "MULTIPLY"
        mix.inputs[0].default_value = 1
        mix.inputs[2].default_value = (*color, 1)
        links.new(tex.outputs[0], mix.inputs[1])
        links.new(mix.outputs[0], shader.inputs["Base Color"])
        bump = nodes.new("ShaderNodeBump")
        bump.inputs["Strength"].default_value = 0.16
        bump.inputs["Distance"].default_value = 0.008
        links.new(tex.outputs[0], bump.inputs["Height"])
        links.new(bump.outputs[0], shader.inputs["Normal"])
    elif name in ("Stone", "Plaster"):
        noise = nodes.new("ShaderNodeTexNoise")
        noise.inputs["Scale"].default_value = 85
        bump = nodes.new("ShaderNodeBump")
        bump.inputs["Strength"].default_value = 0.12
        bump.inputs["Distance"].default_value = 0.012
        links.new(noise.outputs["Fac"], bump.inputs["Height"])
        links.new(bump.outputs[0], shader.inputs["Normal"])
    elif name == "Paper" or name.startswith("Book"):
        texcoord = nodes.new("ShaderNodeTexCoord")
        noise = nodes.new("ShaderNodeTexNoise")
        noise.inputs["Scale"].default_value = 190 if name == "Paper" else 135
        links.new(texcoord.outputs["Object"], noise.inputs["Vector"])
        bump = nodes.new("ShaderNodeBump")
        bump.inputs["Strength"].default_value = .16
        bump.inputs["Distance"].default_value = .0012
        links.new(noise.outputs["Fac"], bump.inputs["Height"])
        links.new(bump.outputs[0], shader.inputs["Normal"])
    MATERIALS[name] = mat
    return mat


def make_materials(wood_path):
    wood = bpy.data.images.load(str(wood_path.resolve()))
    wood.name = "AuthoredOakAlbedo"
    wood.pack()
    material("Stone", (0.57, 0.49, 0.37))
    material("Plaster", (0.63, 0.57, 0.47), 0.82)
    material("Oak", (0.23, 0.17, 0.12), 0.5, wood)
    material("OakTrim", (0.34, 0.24, 0.16), 0.43, wood)
    material("DarkOak", (0.21, 0.17, 0.13), 0.55, wood)
    material("FloorOak", (0.56, 0.49, 0.40), 0.52, wood)
    material("FloorOakLight", (0.62, 0.54, 0.44), 0.52, wood)
    material("FloorOakDark", (0.50, 0.43, 0.35), 0.55, wood)
    material("Bronze", (0.32, 0.22, 0.09), 0.35)
    material("Lead", (0.075, 0.08, 0.075), 0.6)
    material("Oxblood", (0.24, 0.018, 0.026), 0.8)
    material("CreamCloth", (0.65, 0.56, 0.4), 0.9)
    material("InkCloth", (0.015, 0.026, 0.03), 0.9)
    material("Paper", (0.48, 0.40, 0.25), 0.9)
    material("Leather", (.038, .073, .053), .68)
    material("Gilt", (0.53, 0.34, 0.13), 0.55)
    for index, color in enumerate([(0.16, 0.024, 0.022), (0.09, 0.12, 0.075),
                                   (0.045, 0.066, 0.082), (0.32, 0.23, 0.12),
                                   (0.07, 0.042, 0.028), (0.36, 0.31, 0.23),
                                   (0.12, 0.08, 0.034), (0.18, 0.105, 0.065)]):
        material(f"Book{index}", color, 0.84)
    material("WindowLight", (0.66, 0.76, 0.79), emission=0.65)
    material("LampGlass", (1, 0.67, 0.30), emission=3)


def shelf_unit(x, y, z, width, height, yaw=0, group="Joinery", books_group="Books"):
    global BOOK_COUNT
    c, s = math.cos(yaw), math.sin(yaw)

    def p(xx, yy, zz):
        return (x + c * xx - s * yy, y + s * xx + c * yy, z + zz)

    def part(label, center, dims, mat="Oak"):
        box(group, label, p(*center), dims, mat, yaw)

    part("Back", (0, .27, height / 2), (width, .08, height), "DarkOak")
    for xx in (-width / 2, width / 2):
        part("Upright", (xx, 0, height / 2), (.085, .62, height), "OakTrim")
        part("BeadedEdge", (xx, -.33, height / 2), (.025, .026, height), "OakTrim")
    part("Cornice", (0, -.005, height + .06), (width + .20, .75, .12), "OakTrim")
    part("CorniceLip", (0, -.035, height + .145), (width + .27, .79, .05), "OakTrim")
    rows = max(2, int(height / .49))
    step = (height - .1) / rows
    for row in range(rows + 1):
        zz = row * step + .04
        part("Shelf", (0, 0, zz), (width, .63, .045), "OakTrim")
        part("ShelfLip", (0, -.325, zz + .01), (width, .028, .055), "OakTrim")
        if row == rows:
            continue
        xx = -width / 2 + .08
        while xx < width / 2 - .12:
            thick = RNG.uniform(.045, .098)
            high = step * RNG.uniform(.63, .9)
            dep = RNG.uniform(.24, .34)
            col = f"Book{RNG.randrange(8)}"
            by = -.24 + dep / 2
            bx, bz = xx + thick / 2, zz + .03 + high / 2
            # Inset page block, overhanging boards and a rounded, raised spine.
            box(books_group, "PageBlock", p(bx, by + .006, bz),
                (thick - .011, dep - .021, high - .014), "Paper", yaw)
            for dx in (-thick / 2 + .0025, thick / 2 - .0025):
                box(books_group, "CoverBoard", p(bx + dx, by, bz),
                    (.005, dep, high), col, yaw)
            spine = []
            for dz in (-high / 2, high / 2):
                for k in range(5):
                    t = math.pi * k / 4
                    spine.append(p(bx + math.cos(t) * (thick / 2),
                                   -.24 - .012 * math.sin(t), bz + dz))
            # Closed volume keeps recalc_face_normals from turning a curved spine inward.
            faces = [(0, 1, 2, 3, 4), (9, 8, 7, 6, 5), (4, 9, 5, 0)]
            faces += [(k, k + 5, k + 6, k + 1) for k in range(4)]
            batch(books_group).polyhedron(spine, faces, col)
            if RNG.random() < .6:
                for dz in (.045, high - .035):
                    box(books_group, "SpineBand", p(bx, -.253, zz + .03 + dz),
                        (thick * .74, .006, .008), "Gilt", yaw)
                box(books_group, "SpineLabel", p(bx, -.254, bz + high * .13),
                    (thick * .63, .003, high * .13), "DarkOak", yaw)
                for dz in (-.012, .008):
                    box(books_group, "GiltTitleRule", p(bx, -.256, bz + high * .13 + dz),
                        (thick * .45, .002, .003), "Gilt", yaw)
            xx += thick + RNG.uniform(.006, .018)
            BOOK_COUNT += 1


def cabinet(x, y, width, height=.65, yaw=0, group="Joinery"):
    box(group, "Cabinet", (x, y, height / 2), (width, .70, height), "Oak", yaw)
    c, s = math.cos(yaw), math.sin(yaw)
    for dx in (-width * .245, width * .245):
        xx, yy = x + c * dx + s * .36, y + s * dx - c * .36
        box(group, "RecessedDoor", (xx, yy, height / 2),
            (width * .43, .035, height * .72), "DarkOak", yaw)
        for dh in (-height * .36, height * .36):
            box(group, "DoorMoulding", (xx, yy - c * .025, height / 2 + dh),
                (width * .44, .04, .024), "OakTrim", yaw)
        for dw in (-width * .215, width * .215):
            box(group, "DoorMoulding", (xx + c * dw, yy + s * dw, height / 2),
                (.028, .055, height * .73), "OakTrim", yaw)
    box(group, "Plinth", (x, y - .015, .08), (width + .10, .80, .16), "OakTrim", yaw)


def column(x, y, height=7.1):
    box("Architecture", "Pier", (x, y, height / 2), (.39, .49, height), "Stone")
    box("Architecture", "PierFace", (x, y - .28, height / 2), (.25, .06, height - .3), "Stone")
    for z, w, d, h in [(.14, .67, .71, .28), (.34, .56, .62, .11),
                         (3.84, .61, .71, .14), (4.01, .70, .77, .12),
                         (6.3, .58, .69, .13), (6.46, .71, .80, .17)]:
        box("Architecture", "PierMoulding", (x, y, z), (w, d, h), "Stone")


def baluster(x, y, base, yaw=0):
    profile = [(0, .052), (.07, .052), (.10, .029), (.19, .024), (.28, .048),
               (.36, .039), (.45, .020), (.56, .025), (.68, .045), (.74, .048)]
    verts = [(x + r * math.cos(i * math.tau / 8), y + r * math.sin(i * math.tau / 8), base + z)
             for z, r in profile for i in range(8)]
    faces = [tuple(reversed(range(8))), tuple(range((len(profile) - 1) * 8, len(profile) * 8))]
    for j in range(len(profile) - 1):
        for i in range(8):
            faces.append((j * 8 + i, j * 8 + (i + 1) % 8,
                          (j + 1) * 8 + (i + 1) % 8, (j + 1) * 8 + i))
    batch("Joinery").polyhedron(verts, faces, "OakTrim")


def railing(start, end, base):
    a, b = Vector((*start, base)), Vector((*end, base))
    length = (b - a).length
    count = int(length / .22)
    for i in range(count + 1):
        p = a.lerp(b, i / count)
        baluster(p.x, p.y, base)
    beam("Joinery", "Handrail", (a.x, a.y, base + .82), (b.x, b.y, base + .82), .075, "OakTrim")
    beam("Joinery", "BottomRail", (a.x, a.y, base + .04), (b.x, b.y, base + .04), .065, "OakTrim")


def window_front(cx, y, bottom, width, height):
    radius = width / 2
    spring = bottom + height - radius
    # Opaque luminous glazing is outside the architectural opening; bake lights are inside.
    verts = [(cx - radius, y, bottom), (cx + radius, y, bottom)]
    verts += [(cx + radius * math.cos(i * math.pi / 32), y,
               spring + radius * math.sin(i * math.pi / 32)) for i in range(33)]
    batch("Glazing").polyhedron(verts, [tuple(range(len(verts)))], "WindowLight")
    arch("Architecture", cx, y - .08, spring, radius, .14, .22, "Stone")
    for x in (cx - radius, cx + radius):
        box("Architecture", "WindowJamb", (x, y - .08, (bottom + spring) / 2),
            (.13, .22, spring - bottom), "Stone")
    box("Architecture", "WindowSill", (cx, y - .12, bottom), (width + .35, .5, .14), "Stone")
    for x in (-radius * .48, 0, radius * .48):
        top = spring + math.sqrt(max(0, radius * radius - x * x))
        beam("Details", "Mullion", (cx + x, y - .14, bottom),
             (cx + x, y - .14, top), .031, "Stone")
    for z in [bottom + i * .9 for i in range(1, int(height / .9))]:
        half = radius if z <= spring else math.sqrt(max(0, radius * radius - (z - spring)**2))
        if half < .01:
            continue
        beam("Details", "LeadHorizontal", (cx - half, y - .16, z), (cx + half, y - .16, z), .008, "Lead", 4)
    for slope in (-1, 1):
        for j in range(-14, 22):
            intercept = j * .32
            points = []
            for k in range(81):
                xx = -radius + width * k / 80
                zz = bottom + slope * xx + intercept
                limit = spring + math.sqrt(max(0, radius * radius - xx * xx))
                if bottom <= zz <= limit:
                    points.append((cx + xx, y - .16, zz))
            if len(points) > 1:
                beam("Details", "DiamondLead", points[0], points[-1], .009, "Lead", 4)


def rotate_new_geometry(snapshot, center, angle):
    c, s = math.cos(angle), math.sin(angle)
    for name, group in GROUPS.items():
        for i in range(snapshot.get(name, 0), len(group.vertices)):
            x, y, z = group.vertices[i]
            group.vertices[i] = (center[0] + c * x - s * y,
                                 center[1] + s * x + c * y, z)


def sconce(x, y, z):
    box("Details", "SconceMount", (x, y, z), (.16, .11, .40), "Bronze")
    box("Details", "SconceGlass", (x, y - .13, z + .1), (.11, .13, .23), "LampGlass")
    for zz in (z - .04, z + .23):
        box("Details", "SconceCap", (x, y - .13, zz), (.18, .18, .045), "Bronze")
    light("Sconce", (x, y - .4, z + .2), (x, y - 1.5, z + .3), 28, .32, (1, .68, .34))


def light(name, pos, target, power, size, color=(1, .86, .68), size_y=None):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = power
    data.color = color
    data.shape = "RECTANGLE"
    data.size = size
    data.size_y = size if size_y is None else size_y
    obj = bpy.data.objects.new(name, data)
    bpy.data.collections["Lighting"].objects.link(obj)
    obj.location = pos
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()
    return obj


def make_floor():
    box("Floor", "FloorSlab", (0, 2.8, -.13), (13.2, 20.4, .24), "DarkOak")
    # Planar local lightmaps retain 300+ texels/m near the viewer without an 8K atlas.
    # The original floor extents and height are unchanged; only visible gallery floor is tiled.
    tile_depth = (12.667 + 7.567) / 6
    for row in range(92):
        y0, y1 = -7.567 + row * .22, -7.567 + row * .22 + .214
        x = -6.5
        while x < 6.499:
            length = min(2.2 - (row % 3) * .72 if x == -6.5 else 2.2, 6.5 - x)
            x0, x1 = x + .003, x + length - .003
            mat = RNG.choice(["FloorOak"] * 5 + ["FloorOakLight", "FloorOakDark"])
            for iy in range(6):
                ty = -7.567 + iy * tile_depth
                bottom, top = max(y0, ty), min(y1, ty + tile_depth)
                if top <= bottom:
                    continue
                for ix in range(4):
                    tx = -6.5 + ix * 3.25
                    left, right = max(x0, tx), min(x1, tx + 3.25)
                    if iy >= 4:
                        left, right = max(left, -2.24), min(right, 2.24)
                    if right <= left:
                        continue
                    name = f"FloorTile_{iy:02}_{ix:02}"
                    FLOOR_TILES[name] = (tx, ty, 3.25, tile_depth)
                    # No artificial bevel or seam where a lightmap boundary cuts a plank.
                    il = left + (.002 if left == x0 else 0)
                    ir = right - (.002 if right == x1 else 0)
                    ib = bottom + (.002 if bottom == y0 else 0)
                    it = top - (.002 if top == y1 else 0)
                    verts = [(left, bottom, -.006), (right, bottom, -.006),
                             (right, top, -.006), (left, top, -.006),
                             (il, ib, -.001), (ir, ib, -.001), (ir, it, -.001), (il, it, -.001)]
                    faces = [(4, 5, 6, 7)]
                    for a, b in ((0, 1), (1, 2), (2, 3), (3, 0)):
                        if Vector(verts[a][:2]) != Vector(verts[a + 4][:2]) or Vector(verts[b][:2]) != Vector(verts[b + 4][:2]):
                            faces.append((a, b, b + 4, a + 4))
                    batch(name).polyhedron(verts, faces, mat)
            x += length


def learning_wall():
    # This is the room's wall, not a baked blackboard. The runtime owns all board geometry.
    box("LearningBay", "EmptyStoneBay", (0, -7.42, 2.125), (4.4, .25, 3.85), "Plaster")
    box("Architecture", "RearWallAboveBay", (0, -7.42, 6.725), (13.2, .25, 5.35), "Plaster")
    box("Architecture", "RearWallBelowBay", (0, -7.42, .1), (13.2, .25, .2), "Plaster")
    for side in (-1, 1):
        box("Architecture", "RearWallFlank", (side * 4.4, -7.42, 2.125),
            (4.4, .25, 3.85), "Plaster")
        box("LearningSurround", "StonePilaster", (side * 2.39, -7.25, 2.12),
            (.30, .09, 3.92), "Stone")
        box("LearningSurround", "OakPilasterRecess", (side * 2.39, -7.192, 2.12),
            (.19, .025, 3.53), "DarkOak")
        for dx in (-.114, .114):
            box("LearningSurround", "PilasterBead", (side * 2.39 + dx, -7.174, 2.12),
                (.022, .027, 3.64), "OakTrim")
        box("LearningSurround", "PilasterCapital", (side * 2.39, -7.18, 4.05),
            (.40, .20, .12), "Stone")
        box("LearningSurround", "PilasterBase", (side * 2.39, -7.2, .13),
            (.40, .18, .24), "Stone")
        # Mount and lamp stay outside x +/-2.2 and behind the clear y=-7 boundary.
        xx = side * 2.98
        box("LearningSurround", "LampBackplate", (xx, -7.245, 2.9), (.19, .09, .42), "Bronze")
        box("LearningSurround", "LampGlass", (xx, -7.13, 2.93), (.14, .14, .27), "LampGlass")
        for zz in (2.77, 3.09):
            box("LearningSurround", "LampCap", (xx, -7.13, zz), (.22, .18, .05), "Bronze")
        light("LearningWallLamp", (xx, -7.02, 3.08), (xx, -6.6, 3.8), 12, .28, (1, .74, .46))
    for zz, width, depth, height in [(4.12, 5.22, .22, .12), (4.24, 5.37, .27, .08),
                                       (4.31, 5.48, .30, .055)]:
        box("LearningSurround", "ArchitecturalCrown", (0, -7.23, zz),
            (width, depth, height), "OakTrim")
    box("LearningSurround", "StoneSkirting", (0, -7.26, .085), (4.4, .12, .17), "Stone")


def reading_station(x, y, name):
    box(name, "Desktop", (x, y, .754), (1.5, 1.12, .062), "OakTrim")
    box(name, "LeatherWritingInset", (x, y, .787), (1.24, .88, .004), "Leather")
    for dx in (-.645, .645):
        for dy in (-.435, .435):
            box(name, "LegCapital", (x + dx, y + dy, .645), (.10, .10, .16), "OakTrim")
            beam(name, "TaperedLeg", (x + dx, y + dy, .05),
                 (x + dx, y + dy, .62), .035, "Oak", 8)
            box(name, "LegFoot", (x + dx, y + dy, .045), (.08, .08, .09), "DarkOak")
        box(name, "SideApron", (x + dx, y, .655), (.06, .91, .13), "Oak")
    for dy in (-.46, .46):
        box(name, "FrontApron", (x, y + dy, .655), (1.34, .07, .13), "Oak")
        box(name, "ApronBead", (x, y + dy, .595), (1.36, .075, .022), "OakTrim")
    for side in (-1, 1):
        cy = y + side * .94
        box(name, "ChairSeat", (x, cy, .455), (.57, .53, .075), "OakTrim")
        box(name, "ChairLeather", (x, cy - side * .012, .5), (.46, .42, .035), "Leather")
        for dx in (-.235, .235):
            for dy in (-.20, .20):
                beam(name, "ChairLeg", (x + dx * 1.08, cy + dy * 1.08, .04),
                     (x + dx, cy + dy, .43), .027, "Oak", 6)
            beam(name, "ChairBackPost", (x + dx, cy + side * .2, .42),
                 (x + dx, cy + side * .29, 1.13), .030, "OakTrim", 8)
            beam(name, "ChairStretcher", (x + dx, cy - .2, .20),
                 (x + dx, cy + .2, .20), .018, "Oak", 6)
        for zz, yy, hh in [(1.10, .28, .095), (.66, .23, .05)]:
            box(name, "ChairBackRail", (x, cy + side * yy, zz), (.55, .052, hh), "OakTrim")
        for dx in (-.145, 0, .145):
            beam(name, "ChairBackSplat", (x + dx, cy + side * .225, .69),
                 (x + dx, cy + side * .275, 1.06), .023, "Oak", 6)


def make_room():
    # Original coordinates are retained. The app moves/rotates the hall, never this model.
    make_floor()
    for side in (-1, 1):
        for index, y in enumerate((-2.5, .3)):
            reading_station(side * 4.5, y, f"ReadingStation_{'L' if side < 0 else 'R'}{index}")

    # Solid side walls interrupted by real high-window openings.
    for side in (-1, 1):
        x = side * 6.5
        box("Architecture", "LowerSideWall", (x, -.8, 1.45), (.25, 13.2, 2.9), "Plaster")
        box("Architecture", "UpperSideWall", (x, -.8, 6.65), (.25, 13.2, 1.5), "Plaster")
        for y in (-6.8, -3.2, .4, 4.5):
            box("Architecture", "WindowPier", (x, y, 4.5), (.35, .62, 3.25), "Stone")
        for yy in (-5, -1.4, 2.4):
            snapshot = {name: len(group.vertices) for name, group in GROUPS.items()}
            window_front(0, 0, 2.95, 2.85, 3.0)
            rotate_new_geometry(snapshot, (x, yy), -side * math.pi / 2)
            light("WindowSoftbox", (side * 6.25, yy, 4.8), (0, yy + 2.8, .6),
                  370 if side == -1 else 110, 2.5, (1, .86, .68), 2.7)
        # Modest wainscot with real recessed panels, not a solid blank front.
        box("Perimeter", "SideWainscot", (side * 6.32, -.8, 1.35), (.10, 13, 2.7), "Oak")
        for y in [-6.7 + i * 1.15 for i in range(12)]:
            box("Perimeter", "SidePanelInset", (side * 6.25, y, 1.6), (.035, .96, 1.65), "DarkOak")
            for zz in (.74, 2.46):
                box("Perimeter", "SidePanelFrame", (side * 6.21, y, zz), (.07, 1.03, .05), "OakTrim")
            for dy in (-.51, .51):
                box("Perimeter", "SidePanelFrame", (side * 6.21, y + dy, 1.6), (.07, .04, 1.75), "OakTrim")
        for zz in (.18, 2.73, 2.83):
            box("Perimeter", "WainscotCornice", (side * 6.16, -.8, zz), (.20, 13.1, .09), "OakTrim")
        for yy in (-3.9, 1.2):
            box("Perimeter", "BenchSeat", (side * 5.95, yy, .52), (.67, 3.1, .095), "OakTrim")
            for dy in (-1.25, 0, 1.25):
                box("Perimeter", "BenchLeg", (side * 5.95, yy + dy, .25), (.45, .12, .50), "Oak")
        # Side upper galleries connect physically to the front gallery.
        box("Joinery", "SideGalleryDeck", (side * 5.85, 2.75, 4.54), (1.25, 5.2, .20), "Oak")
        railing((side * 5.18, .2), (side * 5.18, 5.1), 4.67)

    # Barrel vault and moulded transverse ribs.
    arch("Architecture", 0, -.2, 6.45, 6.45, .20, 14.2, "Plaster", rz=2.5, steps=64)
    for y in (-7.3, -3.6, .4, 5.42):
        arch("Architecture", 0, y, 6.43, 6.32, .13, .23, "Stone", rz=2.48, steps=56)
    learning_wall()
    for xx in (-4.65, 4.65):
        shelf_unit(xx, -7.06, .66, 1.9, 3.4, math.pi, "Perimeter", "LearningBooks")
        cabinet(xx, -7.06, 1.94, yaw=math.pi, group="Perimeter")

    # Front library elevation: passage, book bays, piers, balcony, and high arch.
    for x in (-4.03, 4.03):
        box("Architecture", "FrontBacking", (x, 5.83, 2.2), (4.84, .36, 4.4), "Plaster")
    spandrel(0, 5.65, 2.55, 1.56, 4.50, .55, "Stone")
    for thickness, offset, dep in [(.12, 0, .68), (.09, .14, .79), (.055, .25, .87)]:
        arch("Architecture", 0, 5.40, 2.55, 1.56 + offset, thickness, dep, "Stone")
    for xx in (-1.66, 1.66):
        box("Architecture", "PortalJamb", (xx, 5.41, 1.27), (.20, .73, 2.54), "Stone")
        box("Architecture", "PortalBase", (xx, 5.33, .13), (.33, .91, .26), "Stone")
    for xx in (-5.0, -3.0, 3.0, 5.0):
        cabinet(xx, 5.33, 1.71)
        shelf_unit(xx, 5.33, .67, 1.64, 3.56)
    for xx in (-6.23, -2.01, 2.01, 6.23):
        column(xx, 5.24)
    box("Joinery", "FrontGalleryDeck", (0, 5.87, 4.55), (12.65, 1.55, .23), "Oak")
    for zz, yy, h in [(4.44, 5.01, .09), (4.56, 4.98, .11), (4.69, 4.96, .07)]:
        box("Joinery", "BalconyCornice", (0, yy, zz), (12.7, .22, h), "OakTrim")
    railing((-6.20, 5.04), (6.20, 5.04), 4.76)
    box("Architecture", "HighFrontWall", (0, 6.75, 7.0), (13.0, .30, 4.9), "Plaster")
    for xx in (-5.0, -3.1, 3.1, 5.0):
        shelf_unit(xx, 6.32, 4.68, 1.65, 1.90)
    # Decorative relieving arch and rosette match the approved concept.
    arch("Architecture", 0, 6.44, 6.25, 2.16, .15, .18, "Stone", rz=1.64)
    arch("Architecture", 0, 6.43, 6.25, 2.35, .07, .23, "Stone", rz=1.83)
    for offset in (0, math.pi):
        # Full roundel assembled from paired half rings.
        before = {name: len(group.vertices) for name, group in GROUPS.items()}
        arch("Details", 0, 6.46, 7.12, .42, .08, .10, "Stone", steps=36)
        if offset:
            group = GROUPS["Details"]
            for i in range(before.get("Details", 0), len(group.vertices)):
                x, y, z = group.vertices[i]
                group.vertices[i] = (-x, y, 14.24 - z)
    for i in range(8):
        t = i * math.tau / 8
        beam("Details", "RosettePetal", (.12 * math.cos(t), 6.35, 7.12 + .12 * math.sin(t)),
             (.35 * math.cos(t), 6.35, 7.12 + .35 * math.sin(t)), .045, "Stone", 8)
    # Sewn banner colors are geometry, so they survive every export format.
    for xx in (-4.65, 4.65):
        box("Details", "HangingBanner", (xx, 5.8, 7.1), (.76, .025, 1.7), "Oxblood")
        verts = [(xx - .38, 5.779, 6.75), (xx + .38, 5.779, 7.45),
                 (xx + .38, 5.779, 7.9), (xx - .38, 5.779, 7.2)]
        batch("Details").polyhedron(verts, [(0, 1, 2, 3)], "CreamCloth")
        batch("Details").polyhedron([(xx + .38, 5.771, 6.26), (xx + .38, 5.771, 7.0),
                                      (xx - .05, 5.771, 6.83)], [(0, 1, 2)], "InkCloth")
        beam("Details", "BannerRod", (xx - .47, 5.8, 7.99), (xx + .47, 5.8, 7.99), .021, "Bronze")

    # Secondary gallery behind the central arch; the far window is real geometry.
    for side in (-1, 1):
        box("Architecture", "GalleryWall", (side * 2.12, 9.5, 2.2), (.24, 6.5, 4.4), "Plaster")
        for yy in (7.35, 9.5, 11.65):
            shelf_unit(side * 1.79, yy, .40, 1.72, 2.75,
                       -side * math.pi / 2, "Perimeter", "RearBooks")
    arch("Architecture", 0, 9.2, 2.55, 2.10, .18, 7.4, "Plaster", rz=1.75)
    for yy in (6.5, 9.2, 12.6):
        arch("Architecture", 0, yy, 2.35, 1.99, .10, .16, "Stone", rz=1.48)
    box("Architecture", "FarWindowLowWall", (0, 12.85, .46), (4.4, .22, .92), "Stone")
    for xx in (-1.7, 1.7):
        box("Architecture", "FarWindowSideWall", (xx, 12.85, 2.65), (1.2, .22, 3.8), "Plaster")
    box("Architecture", "FarWindowTopWall", (0, 12.85, 4.13), (4.4, .22, .96), "Plaster")
    spandrel(0, 12.84, 2.67, 1.0, 3.7, .22, "Plaster")
    window_front(0, 12.83, .92, 2.0, 2.75)
    light("FarWindowDaylight", (0, 12.58, 2.3), (0, 6, 1), 220, 1.8, (1, .91, .77), 2.5)
    for xx in (-6.07, 6.07):
        sconce(xx, 4.9, 3.55)
    light("HallBounce", (0, -1.6, 6.8), (0, 4, 2), 160, 8, (1, .83, .63))
    light("GalleryBounce", (0, 8.6, 3.4), (0, 5.2, 1.5), 35, 2, (1, .81, .59))
    sun_data = bpy.data.lights.new("AfternoonSun", "SUN")
    sun_data.energy = 3.0
    sun_data.angle = .035
    sun_data.color = (1, .83, .62)
    sun = bpy.data.objects.new("AfternoonSun", sun_data)
    bpy.data.collections["Lighting"].objects.link(sun)
    sun.rotation_euler = Vector((1, .25, -.58)).to_track_quat("-Z", "Y").to_euler()


def configure_device(scene):
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "OPTIX"
    prefs.refresh_devices()
    for device in prefs.devices:
        device.use = device.type == "OPTIX"
    scene.cycles.device = "GPU" if any(d.use for d in prefs.devices) else "CPU"


def configure(scene):
    scene.unit_settings.system = "METRIC"
    scene.unit_settings.scale_length = 1
    scene.render.engine = "CYCLES"
    configure_device(scene)
    scene.cycles.samples = 40
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 7
    scene.cycles.diffuse_bounces = 5
    scene.render.resolution_x = 1536
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
    scene.view_settings.exposure = -.2
    scene.world = bpy.data.worlds.new("DaylightAmbient")
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.49, .58, .70, 1)
    scene.world.node_tree.nodes["Background"].inputs[1].default_value = .08


def camera(name, pos, target, lens=24):
    data = bpy.data.cameras.new(name)
    data.lens = lens
    data.clip_end = 100
    obj = bpy.data.objects.new(name, data)
    bpy.data.collections["Presentation"].objects.link(obj)
    obj.location = pos
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()
    return obj


def render(scene, out, name, cam):
    scene.camera = cam
    scene.render.filepath = str(out / name)
    bpy.ops.render.render(write_still=True)


def bake_export(scene, out, resolution, samples, rebake_books=False, export_existing_bakes=False):
    objects = list(bpy.data.collections["LibraryGeometry"].objects)
    scene.cycles.samples = samples
    scene.render.bake.use_pass_direct = True
    scene.render.bake.use_pass_indirect = True
    scene.render.bake.use_pass_color = True
    scene.render.bake.margin = 4
    scene.render.bake.use_clear = True
    scene.render.bake.use_selected_to_active = False
    textures = out / "baked"
    textures.mkdir(exist_ok=True)
    baked = []
    texture_stats = []
    cached_runtime = None
    if rebake_books:
        # Reuse the original UVs as well as pixels; unrelated room bakes stay byte-identical.
        with bpy.data.libraries.load(str(out / "academic-library-runtime.blend"), link=False) as (available, requested):
            requested.meshes = available.meshes
        cached_runtime = {mesh.name.removesuffix(".001"): mesh for mesh in requested.meshes}
    # Keep original materials on EVERY object until all bakes finish, to preserve GI.
    for obj in objects:
        print(f"BAKE_START {obj.name}", flush=True)
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        uv = obj.data.uv_layers.new(name="BakedUV")
        obj.data.uv_layers.active = uv
        uv.active_render = True
        path = textures / f"{obj.name}.png"
        if rebake_books and "Books" not in obj.name:
            cached = cached_runtime[obj.data.name]
            assert len(cached.vertices) == len(obj.data.vertices) and len(cached.loops) == len(obj.data.loops), \
                f"Cannot reuse a bake after geometry changes: {obj.name}"
            assert all((a.co - b.co).length < 1e-7 for a, b in zip(cached.vertices, obj.data.vertices)), \
                f"Cannot reuse a bake after moving geometry: {obj.name}"
            for target, original in zip(uv.data, cached.uv_layers[0].data):
                target.uv = original.uv
            runtime_image = bpy.data.images.load(str(path), check_existing=False)
            runtime_image.name = f"{obj.name}_Baked"
            runtime_image.pack()
            baked.append((obj, runtime_image))
            texture_stats.append({"mesh": obj.name, "size": list(runtime_image.size), "reused": True})
            print(f"BAKE_REUSED {obj.name}", flush=True)
            continue
        if "floor_bake_bounds" in obj:
            tx, ty, width, depth = obj["floor_bake_bounds"]
            for loop in obj.data.loops:
                p = obj.data.vertices[loop.vertex_index].co
                uv.data[loop.index].uv = (.006 + .988 * (p.x - tx) / width,
                                          .006 + .988 * (p.y - ty) / depth)
            size = max(512, resolution // (4 if ty < 5 else 8))
        else:
            bpy.ops.object.mode_set(mode="EDIT")
            bpy.ops.mesh.select_all(action="SELECT")
            bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=.0015,
                                     area_weight=1.0, correct_aspect=True, scale_to_bounds=True)
            bpy.ops.object.mode_set(mode="OBJECT")
            if obj.name in ("Joinery", "Books", "Architecture", "Perimeter"):
                size = resolution
            elif obj.name in ("Floor", "Glazing"):
                size = 512
            elif obj.name.startswith("ReadingStation_") or obj.name in ("Details", "LearningBay"):
                size = max(1024, resolution // 4)
            else:
                size = max(1024, resolution // 2)
        if export_existing_bakes:
            runtime_image = bpy.data.images.load(str(path), check_existing=False)
            assert list(runtime_image.size) == [size, size], f"Cached bake size changed: {obj.name}"
            runtime_image.name = f"{obj.name}_Baked"
            runtime_image.pack()
            baked.append((obj, runtime_image))
            texture_stats.append({"mesh": obj.name, "size": [size, size], "reused": True})
            continue
        image = bpy.data.images.new(f"{obj.name}_HDR", width=size, height=size,
                                    alpha=False, float_buffer=True)
        for mat in obj.data.materials:
            node = mat.node_tree.nodes.get("BakeTarget") or mat.node_tree.nodes.new("ShaderNodeTexImage")
            node.name = "BakeTarget"
            node.image = image
            mat.node_tree.nodes.active = node
        bpy.ops.object.bake(type="COMBINED", pass_filter={"COLOR", "DIRECT", "INDIRECT", "DIFFUSE", "EMIT"})
        # Tone-map HDR once at authoring time; runtime textures are display-referred unlit.
        image.save_render(filepath=str(path), scene=scene)
        runtime_image = bpy.data.images.load(str(path), check_existing=False)
        runtime_image.name = f"{obj.name}_Baked"
        runtime_image.pack()
        baked.append((obj, runtime_image))
        texture_stats.append({"mesh": obj.name, "size": [size, size]})
        bpy.data.images.remove(image)
        print(f"BAKE_DONE {obj.name} {size}", flush=True)
    if cached_runtime:
        for mesh in cached_runtime.values():
            bpy.data.meshes.remove(mesh)
        for library in list(bpy.data.libraries):
            if Path(bpy.path.abspath(library.filepath)).resolve() == (out / "academic-library-runtime.blend").resolve():
                bpy.data.libraries.remove(library)
    for obj, image in baked:
        mat = bpy.data.materials.new(f"{obj.name}_BakedUnlit")
        nodes, links = mat.node_tree.nodes, mat.node_tree.links
        nodes.clear()
        tex = nodes.new("ShaderNodeTexImage")
        tex.image = image
        output = nodes.new("ShaderNodeOutputMaterial")
        # Blender's glTF exporter recognizes a color-to-output graph as KHR_materials_unlit.
        links.new(tex.outputs[0], output.inputs["Surface"])
        obj.data.materials.clear()
        obj.data.materials.append(mat)
        for polygon in obj.data.polygons:
            polygon.material_index = 0
        obj.data.uv_layers.remove(obj.data.uv_layers["SurfaceUV"])
        obj.data.uv_layers[0].name = "UVMap"
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]
    bpy.ops.export_scene.gltf(filepath=str(out / "academic-library.glb"), export_format="GLB",
                              use_selection=True, export_cameras=False, export_lights=False,
                              export_yup=True, export_texcoords=True, export_normals=True)
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(out / "academic-library-runtime.blend"), compress=True)
    render(scene, out, "runtime-front.png", bpy.data.objects["FrontCamera"])
    render(scene, out, "runtime-learning.png", bpy.data.objects["LearningCamera"])
    for obj in objects:
        obj.data.calc_loop_triangles()
    stats = {"book_count": scene.get("book_count", BOOK_COUNT), "mesh_count": len(objects),
             "triangles": sum(len(o.data.loop_triangles) for o in objects),
             "baked_textures": len(baked), "bake_resolution": resolution,
             "texture_sizes": texture_stats,
             "geometry": [{"name": o.name, "vertices": len(o.data.vertices),
                           "triangles": len(o.data.loop_triangles)} for o in objects],
             "coordinate_contract": {
                 "blender_to_pico": "(x, y, z) -> (x, z, -y); meters; no model rotation or offset",
                 "bay_blender": {"x": [-2.2, 2.2], "y": -7.295, "z": [.2, 4.05]},
                 "runtime_reserved_blender": {"x": [-1.85, 1.85], "y": -7.295, "z": [.25, 4]},
                 "viewer_blender": [0, -4.395, 1.65],
                 "clear_zone_blender": {"x": [-3.5, 3.5], "y": [-7, -3], "z": [.03, 4]},
                 "desk_centers_blender": [[x, y, .789] for x in (-4.5, 4.5) for y in (-2.5, .3)],
                 "runtime_board_in_model": False}}
    (out / "build-info.json").write_text(json.dumps(stats, indent=2), encoding="utf-8")


def main(args):
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if args.bake_existing:
        bpy.ops.wm.open_mainfile(filepath=str(out / "academic-library.blend"))
        configure_device(bpy.context.scene)
        bake_export(bpy.context.scene, out, args.bake_resolution, args.samples, args.rebake_books, args.export_existing_bakes)
        return
    bpy.ops.wm.read_factory_settings(use_empty=True)
    for name in ("LibraryGeometry", "Lighting", "Presentation"):
        bpy.context.scene.collection.children.link(bpy.data.collections.new(name))
    scene = bpy.context.scene
    configure(scene)
    make_materials(args.wood)
    make_room()
    for group in GROUPS.values():
        obj = group.finish()
        if obj.name in FLOOR_TILES:
            obj["floor_bake_bounds"] = FLOOR_TILES[obj.name]
        if obj.name == "Glazing":
            obj.visible_shadow = False
        if obj.name in ("Architecture", "Joinery", "Perimeter", "LearningSurround") or obj.name.startswith("ReadingStation_"):
            bpy.context.view_layer.objects.active = obj
            bevel = obj.modifiers.new("CraftedEdgeHighlights", "BEVEL")
            bevel.width = .006 if obj.name.startswith("ReadingStation_") else .012
            bevel.segments = 2
            bevel.limit_method = "ANGLE"
            bpy.ops.object.modifier_apply(modifier=bevel.name)
        obj.data.calc_loop_triangles()
    triangles = sum(len(o.data.loop_triangles) for o in bpy.data.collections["LibraryGeometry"].objects)
    assert triangles < 400000, f"Source exceeds triangle budget: {triangles}"
    front = camera("FrontCamera", (0, -5.8, 2.6), (0, 5.4, 3.55), 23)
    camera("ViewerCamera", (0, 0, 1.65), (0, 6, 2.8), 20)
    camera("ReverseCamera", (1.0, 3.2, 2.0), (0, -6, 3.1), 20)
    camera("LearningCamera", (0, -4.395, 1.8), (0, -7.295, 2.1), 15)
    camera("DetailCamera", (-3.0, -4.35, 1.55), (-4.65, -7.0, 1.35), 38)
    scene.camera = front
    bpy.ops.object.select_all(action="DESELECT")
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == "VIEW_3D":
                area.spaces.active.region_3d.view_perspective = "CAMERA"
                area.spaces.active.shading.type = "MATERIAL"
    scene["asset_brief"] = "Collegiate library: preserved arch/gallery; empty rear learning wall; four side reading stations. No baked blackboard/UI."
    scene["book_count"] = BOOK_COUNT
    bpy.ops.wm.save_as_mainfile(filepath=str(out / "academic-library.blend"))
    render(scene, out, "preview-front.png", front)
    render(scene, out, "preview-reverse.png", bpy.data.objects["ReverseCamera"])
    render(scene, out, "preview-learning.png", bpy.data.objects["LearningCamera"])
    render(scene, out, "preview-detail.png", bpy.data.objects["DetailCamera"])
    print(f"SOURCE_READY books={BOOK_COUNT} triangles={triangles}", flush=True)
    if args.bake:
        bake_export(scene, out, args.bake_resolution, args.samples, args.rebake_books, args.export_existing_bakes)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--wood", type=Path)
    parser.add_argument("--bake", action="store_true")
    parser.add_argument("--bake-existing", action="store_true")
    parser.add_argument("--rebake-books", action="store_true",
                        help="Rebake only book atlases, validating and reusing unchanged room UVs/textures")
    parser.add_argument("--export-existing-bakes", action="store_true",
                        help="Reassemble an interrupted export from completed bake images, without baking")
    parser.add_argument("--bake-resolution", type=int, default=4096)
    parser.add_argument("--samples", type=int, default=32)
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    if not args.bake_existing and not args.wood:
        parser.error("--wood is required when creating the source scene")
    main(args)
