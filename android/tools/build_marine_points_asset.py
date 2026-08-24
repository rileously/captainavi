#!/usr/bin/env python3
"""Build CaptainAvi's offline Maldives fishing and dive-point catalog.

Sources:
  * Active FADs published by the Maldives Fisheries Information System.
  * Dive-site nodes from a Geofabrik Maldives OpenStreetMap PBF extract.
  * Maldives dive sites from the OpenDiveMap public API.
  * Maldives OneMap island centroids, used only to add a nearby island/atoll.

The script deliberately excludes dive centres, shops, snorkeling-only points,
unnamed points, and OpenStreetMap records explicitly marked unverified.

Example:
  python android/tools/build_marine_points_asset.py \
      --osm-pbf tmp/marine_points/maldives-latest.osm.pbf \
      --osm-snapshot-date 2026-08-19 \
      --output android/app/src/main/assets/marine_activity_points_v1.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import unicodedata
import urllib.parse
import urllib.request
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any, Iterable

import osmium


FAD_URL = "https://keyolhu.mv/home/fadlist"
OPEN_DIVE_URL = "https://api.opendivemap.com/v1/sites?country=MV&limit=1000"
ONEMAP_URL = (
    "https://services7.arcgis.com/yvCbn3q8PPtPLZIM/arcgis/rest/services/"
    "island_20240509/FeatureServer/0/query"
)
OSM_ATTRIBUTION = "OpenStreetMap contributors (ODbL)"
OPEN_DIVE_ATTRIBUTION = "OpenDiveMap contributors (ODbL)"
FISHERIES_ATTRIBUTION = (
    "Maldives Fisheries Information System / Ministry of Fisheries and Ocean Resources"
)
ONEMAP_ATTRIBUTION = "Maldives OneMap / Geomatics Department"


@dataclass(frozen=True)
class Island:
    name: str
    atoll: str
    latitude: float
    longitude: float


def fetch_bytes(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "CaptainAvi-data-builder/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def stable_id(prefix: str, *parts: object) -> str:
    digest = hashlib.sha256("|".join(map(str, parts)).encode("utf-8")).hexdigest()[:14]
    return f"{prefix}-{digest}"


def title_name(value: str) -> str:
    return " ".join(part.capitalize() for part in value.strip().split())


def normalize_name(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value.casefold())
    ascii_like = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]+", " ", ascii_like).strip()


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius_km = 6371.0088
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lon2 - lon1)
    a = (
        math.sin(d_phi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    )
    return radius_km * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def load_islands() -> list[Island]:
    islands: list[Island] = []
    offset = 0
    while offset < 5000:
        query = urllib.parse.urlencode(
            {
                "where": "1=1",
                "outFields": "OBJECTID,atoll,islandName",
                "returnGeometry": "false",
                "returnCentroid": "true",
                "outSR": "4326",
                "orderByFields": "OBJECTID ASC",
                "resultOffset": offset,
                "resultRecordCount": 1000,
                "f": "json",
            }
        )
        payload = json.loads(fetch_bytes(f"{ONEMAP_URL}?{query}"))
        features = payload.get("features", [])
        for feature in features:
            attributes = feature.get("attributes", {})
            centroid = feature.get("centroid") or {}
            name = str(attributes.get("islandName") or "").strip()
            atoll = str(attributes.get("atoll") or "").strip()
            if name and "x" in centroid and "y" in centroid:
                islands.append(Island(name, atoll, float(centroid["y"]), float(centroid["x"])))
        offset += len(features)
        if not payload.get("exceededTransferLimit") or not features:
            break
    if not islands:
        raise RuntimeError("OneMap returned no island centroids")
    return islands


def nearby_fields(latitude: float, longitude: float, islands: list[Island]) -> tuple[str, str]:
    nearest = min(
        islands,
        key=lambda island: haversine_km(latitude, longitude, island.latitude, island.longitude),
    )
    distance = haversine_km(latitude, longitude, nearest.latitude, nearest.longitude)
    if distance > 80:
        return "", ""
    return nearest.atoll, nearest.name


def load_active_fads() -> list[dict[str, Any]]:
    html = fetch_bytes(FAD_URL).decode("utf-8")
    match = re.search(r"var\s+json\s*=\s*(\[.*?\]);", html, re.DOTALL)
    if not match:
        raise RuntimeError("FAD JSON was not found in the Fisheries page")
    records = json.loads(match.group(1))
    points: list[dict[str, Any]] = []
    for record in records:
        if str(record.get("state", "")).casefold() != "active":
            continue
        latitude = float(record["latitudeMap"])
        longitude = float(record["longitudeMap"])
        sport = "sport" in str(record.get("fadType", "")).casefold()
        point_type = "SPORT_FAD" if sport else "TUNA_FAD"
        type_label = "Sport FAD" if sport else "Tuna FAD"
        island = title_name(str(record.get("island") or ""))
        atoll = str(record.get("atoll") or "").strip()
        reference = str(record.get("fadNumber") or "").strip()
        name = f"{island} {type_label}" if island else type_label
        points.append(
            {
                "id": stable_id("fad", point_type, f"{latitude:.7f}", f"{longitude:.7f}"),
                "name": name,
                "type": point_type,
                "latitude": latitude,
                "longitude": longitude,
                "atoll": atoll,
                "nearby": island,
                "detail": str(record.get("location") or "").strip(),
                "reference": reference,
                "source": "FISHERIES",
            }
        )
    # The live Fisheries page currently repeats one coordinate as an older
    # code-less record and a newer record with a station code. Prefer the
    # richer record rather than showing two markers at the same FAD.
    unique: dict[str, dict[str, Any]] = {}
    for point in points:
        previous = unique.get(point["id"])
        if previous is None or (not previous["reference"] and point["reference"]):
            unique[point["id"]] = point
    return sorted(unique.values(), key=lambda point: (point["atoll"], point["name"], point["id"]))


def depth_detail(tags: dict[str, str]) -> list[str]:
    details: list[str] = []
    max_depth = tags.get("scuba_diving:maxdepth", "").strip()
    typical_depth = tags.get("scuba_diving:depth", "").strip()
    if max_depth:
        details.append(f"Mapped max depth {max_depth} m")
    elif typical_depth:
        details.append(f"Mapped depth {typical_depth} m")
    entry = tags.get("scuba_diving:entry", "").strip().casefold()
    if not entry and tags.get("scuba_diving:entry:boat") == "yes":
        entry = "boat"
    if entry in {"boat", "shore"}:
        details.append(f"{entry.capitalize()} entry")
    if tags.get("historic") == "wreck" or tags.get("seamark:type") == "wreck":
        details.insert(0, "Wreck dive")
    return details


class DiveSiteHandler(osmium.SimpleHandler):
    def __init__(self, islands: list[Island]):
        super().__init__()
        self.islands = islands
        self.points: list[dict[str, Any]] = []

    def node(self, node: osmium.osm.Node) -> None:
        tags = dict(node.tags)
        name = (tags.get("name") or tags.get("name:en") or "").strip()
        is_scuba = tags.get("sport") == "scuba_diving" or "scuba_diving:divespot" in tags
        dive_type = tags.get("scuba_diving:type", "").casefold()
        is_service = (
            tags.get("amenity") == "dive_centre"
            or tags.get("shop") == "scuba_diving"
            or tags.get("club") == "scuba_diving"
            or "centre" in dive_type
            or "center" in dive_type
        )
        is_physical_site = (
            tags.get("natural") == "reef"
            or tags.get("historic") == "wreck"
            or tags.get("seamark:type") == "wreck"
            or tags.get("tourism") == "attraction"
            or tags.get("scuba_diving:divespot") in {"yes", "true", "1"}
        )
        is_snorkeling_only = (
            dive_type == "snorkeling" or tags.get("scuba_diving:type:snorkeling") == "yes"
        )
        is_unverified = "no" in tags.get("verified", "").casefold()
        poor_name = normalize_name(name) in {"", "thila", "house reef", "entrence base", "entrance base"}
        if (
            not is_scuba
            or not name
            or is_service
            or not is_physical_site
            or is_snorkeling_only
            or is_unverified
            or poor_name
        ):
            return

        latitude = node.location.lat
        longitude = node.location.lon
        atoll, nearby = nearby_fields(latitude, longitude, self.islands)
        self.points.append(
            {
                "id": f"osm-node-{node.id}",
                "name": name,
                "type": "DIVE_SITE",
                "latitude": latitude,
                "longitude": longitude,
                "atoll": atoll,
                "nearby": nearby,
                "detail": " · ".join(depth_detail(tags)),
                "reference": f"OSM node {node.id}",
                "source": "OPENSTREETMAP",
            }
        )


def load_osm_dive_sites(pbf_path: Path, islands: list[Island]) -> list[dict[str, Any]]:
    handler = DiveSiteHandler(islands)
    handler.apply_file(str(pbf_path), locations=True)
    return sorted(handler.points, key=lambda point: point["name"].casefold())


def is_duplicate_dive(candidate: dict[str, Any], existing: Iterable[dict[str, Any]]) -> bool:
    candidate_name = normalize_name(candidate["name"])
    for other in existing:
        distance = haversine_km(
            candidate["latitude"],
            candidate["longitude"],
            other["latitude"],
            other["longitude"],
        )
        if distance <= 0.1:
            return True
        other_name = normalize_name(other["name"])
        if candidate_name == other_name and distance <= 5:
            return True
        if distance <= 2 and SequenceMatcher(None, candidate_name, other_name).ratio() >= 0.82:
            return True
    return False


def load_open_dive_sites(
    islands: list[Island], existing: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    payload = json.loads(fetch_bytes(OPEN_DIVE_URL))
    points: list[dict[str, Any]] = []
    for feature in payload.get("features", []):
        properties = feature.get("properties", {})
        coordinates = (feature.get("geometry") or {}).get("coordinates", [])
        name = str(properties.get("name") or "").strip()
        if len(coordinates) < 2 or not name:
            continue
        longitude, latitude = float(coordinates[0]), float(coordinates[1])
        details: list[str] = []
        max_depth = properties.get("max_depth")
        if max_depth is not None:
            details.append(f"Mapped max depth {max_depth} m")
        entry = str(properties.get("entry") or "").strip().casefold()
        if entry in {"boat", "shore"}:
            details.append(f"{entry.capitalize()} entry")
        topologies = [str(value).replace("_", " ").title() for value in properties.get("topologies") or []]
        if topologies:
            details.append(", ".join(topologies))
        atoll, nearby = nearby_fields(latitude, longitude, islands)
        point = {
            "id": f"odm-{properties.get('id')}",
            "name": name,
            "type": "DIVE_SITE",
            "latitude": latitude,
            "longitude": longitude,
            "atoll": atoll,
            "nearby": nearby,
            "detail": " · ".join(details),
            "reference": f"OpenDiveMap {properties.get('id')}",
            "source": "OPENDIVEMAP",
        }
        if not is_duplicate_dive(point, [*existing, *points]):
            points.append(point)
    return sorted(points, key=lambda point: point["name"].casefold())


def validate(points: list[dict[str, Any]]) -> None:
    ids = [point["id"] for point in points]
    if len(ids) != len(set(ids)):
        raise RuntimeError("Duplicate point IDs generated")
    for point in points:
        if not -1.5 <= point["latitude"] <= 8.0:
            raise RuntimeError(f"Latitude outside Maldives bounds: {point}")
        if not 72.0 <= point["longitude"] <= 75.5:
            raise RuntimeError(f"Longitude outside Maldives bounds: {point}")
        if not point["name"].strip():
            raise RuntimeError(f"Blank point name: {point}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--osm-pbf", required=True, type=Path)
    parser.add_argument("--osm-snapshot-date", required=True)
    parser.add_argument("--snapshot-date", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    islands = load_islands()
    fads = load_active_fads()
    osm_dives = load_osm_dive_sites(args.osm_pbf, islands)
    open_dives = load_open_dive_sites(islands, osm_dives)
    points = [*fads, *osm_dives, *open_dives]
    validate(points)

    payload = {
        "version": 1,
        "snapshotDate": args.snapshot_date,
        "osmSnapshotDate": args.osm_snapshot_date,
        "counts": {
            "activeFads": len(fads),
            "osmDiveSites": len(osm_dives),
            "openDiveMapSites": len(open_dives),
            "diveSites": len(osm_dives) + len(open_dives),
            "total": len(points),
        },
        "sources": {
            "fisheries": FISHERIES_ATTRIBUTION,
            "openStreetMap": OSM_ATTRIBUTION,
            "openDiveMap": OPEN_DIVE_ATTRIBUTION,
            "oneMap": ONEMAP_ATTRIBUTION,
        },
        "points": points,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(json.dumps(payload["counts"], indent=2))
    print(f"Wrote {args.output} ({args.output.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
