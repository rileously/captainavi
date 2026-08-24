#!/usr/bin/env python3
"""Build Captain Avi's compact offline reef asset from the official OneMap shapefile.

Dependencies: pyshp and pyproj
Usage: python tools/build_reef_asset.py <Reef.shp> <reef_boundaries_v1.bin>
"""

from __future__ import annotations

import argparse
import hashlib
import struct
from pathlib import Path

import shapefile
from pyproj import Transformer


MAGIC = b"CAVR"
FORMAT_VERSION = 1


def write_string(output, value: object) -> None:
    encoded = str(value or "").strip().encode("utf-8")
    if len(encoded) > 65_535:
        raise ValueError("Reef attribute is too long for the asset format")
    output.write(struct.pack(">H", len(encoded)))
    output.write(encoded)


def signed_area(points: list[tuple[float, float]]) -> float:
    return sum(
        points[index][0] * points[(index + 1) % len(points)][1]
        - points[(index + 1) % len(points)][0] * points[index][1]
        for index in range(len(points))
    ) / 2.0


def build(source: Path, destination: Path) -> None:
    reader = shapefile.Reader(str(source), encoding="utf-8")
    if reader.shapeType != shapefile.POLYGON:
        raise ValueError(f"Expected POLYGON data, got {reader.shapeTypeName}")

    fields = [field[0] for field in reader.fields[1:]]
    required = {"name", "Atoll", "FCODE"}
    if not required.issubset(fields):
        raise ValueError(f"Missing fields: {sorted(required - set(fields))}")

    # The supplied .prj is WGS 84 / UTM zone 43N. The XML contains a stale 32644
    # identifier, but that would place the data near 79E instead of the Maldives.
    transformer = Transformer.from_crs(32643, 4326, always_xy=True)
    destination.parent.mkdir(parents=True, exist_ok=True)

    point_count = 0
    ring_count = 0
    with destination.open("wb") as output:
        output.write(MAGIC)
        output.write(struct.pack(">HI", FORMAT_VERSION, len(reader)))

        for shape_record in reader.iterShapeRecords():
            attributes = dict(zip(fields, shape_record.record))
            write_string(output, attributes["FCODE"])
            write_string(output, attributes["name"])
            write_string(output, attributes["Atoll"])

            shape = shape_record.shape
            starts = list(shape.parts) + [len(shape.points)]
            rings: list[tuple[bool, list[tuple[int, int]]]] = []
            for start, end in zip(starts, starts[1:]):
                projected = list(shape.points[start:end])
                if len(projected) >= 2 and projected[0] == projected[-1]:
                    projected.pop()
                if len(projected) < 3:
                    continue

                # Esri shapefiles use clockwise outer rings and counter-clockwise holes.
                is_hole = signed_area(projected) > 0.0
                coordinates = []
                for easting, northing in projected:
                    longitude, latitude = transformer.transform(easting, northing)
                    coordinates.append((round(latitude * 1_000_000), round(longitude * 1_000_000)))
                rings.append((is_hole, coordinates))

            if len(rings) > 65_535:
                raise ValueError(f"Too many rings in {attributes['FCODE']}")
            output.write(struct.pack(">H", len(rings)))
            for is_hole, coordinates in rings:
                output.write(struct.pack(">BI", int(is_hole), len(coordinates)))
                for latitude_e6, longitude_e6 in coordinates:
                    output.write(struct.pack(">ii", latitude_e6, longitude_e6))
                point_count += len(coordinates)
                ring_count += 1

    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    print(
        f"Wrote {len(reader)} reefs, {ring_count} rings, {point_count} points, "
        f"{destination.stat().st_size} bytes, sha256={digest}"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    build(args.source, args.destination)


if __name__ == "__main__":
    main()
