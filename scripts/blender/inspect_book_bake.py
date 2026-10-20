"""Diagnose source-facing normals and atlas pixels on the visible library spines."""
import argparse
import json
import sys
from pathlib import Path

import bpy
import numpy as np


def check_readability(assets):
    means = []
    for filename in ("preview-detail.png", "glb-detail.png"):
        image = bpy.data.images.load(str(assets / filename), check_existing=False)
        pixels = np.empty(len(image.pixels), dtype=np.float32)
        image.pixels.foreach_get(pixels)
        rgb = pixels.reshape(image.size[1], image.size[0], 4)[256:768, 400:1150, :3]
        means.append(float((rgb @ np.array([.2126, .7152, .0722])).mean()))
        bpy.data.images.remove(image)
    ratio = means[1] / means[0]
    print(f"BOOK_READABILITY source={means[0]} baked={means[1]} ratio={ratio}")
    assert ratio > .5, f"Book bake lost source readability: luminance ratio={ratio}"
    return ratio


def inspect(out, assets, source_only=False):
    report = {}
    source_indices = []
    variants = ("academic-library.blend",) if source_only else ("academic-library.blend", "academic-library-runtime.blend")
    for variant in variants:
        bpy.ops.wm.open_mainfile(filepath=str(assets / variant))
        obj = bpy.data.objects["Books"]
        if variant == "academic-library.blend":
            source_indices = [p.index for p in obj.data.polygons
                              if 5.075 < p.center.y < 5.089 and abs(p.normal.y) > .5
                              and obj.data.materials[p.material_index].name.startswith("Book")]
        faces = [obj.data.polygons[i] for i in source_indices]
        records = []
        for x in (-5, -3, 3, 5):
            chosen = [p for p in faces if x - .7 < p.center.x < x + .7 and p.center.z < 4.3]
            records.append({"bay_x": x, "face_count": len(chosen),
                            "inward_normals": sum(p.normal.y > 0 for p in chosen),
                            "outward_normals": sum(p.normal.y < 0 for p in chosen)})
        report[variant] = records
    (out / "book-bake-diagnostic.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    assert all(r["inward_normals"] == 0 for records in report.values() for r in records), \
        "Book spine normals point inside the book, producing black diffuse bakes"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--asset-dir", type=Path, required=True)
    parser.add_argument("--source-only", action="store_true")
    parser.add_argument("--readability-only", action="store_true")
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    if args.readability_only:
        check_readability(args.asset_dir)
    else:
        inspect(args.output_dir, args.asset_dir, args.source_only)
