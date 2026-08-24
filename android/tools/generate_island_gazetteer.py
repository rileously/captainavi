#!/usr/bin/env python3
"""Build the bundled Maldives OneMap island gazetteer asset."""

from __future__ import annotations

import json
import pathlib
import urllib.parse
import urllib.request


ENDPOINT = (
    "https://services7.arcgis.com/yvCbn3q8PPtPLZIM/arcgis/rest/services/"
    "island_20240509/FeatureServer/0/query"
)
PAGE_SIZE = 1000
MAX_RECORDS = 5000
OUTPUT = pathlib.Path(__file__).parents[1] / "app/src/main/assets/island_gazetteer_v1.json"


def fetch_page(offset: int) -> dict:
    query = urllib.parse.urlencode(
        {
            "where": "1=1",
            "outFields": "OBJECTID,atoll,islandName,islandNa_1,category,capital",
            "returnGeometry": "false",
            "returnCentroid": "true",
            "outSR": "4326",
            "orderByFields": "OBJECTID ASC",
            "resultOffset": offset,
            "resultRecordCount": PAGE_SIZE,
            "f": "json",
        }
    )
    request = urllib.request.Request(
        f"{ENDPOINT}?{query}",
        headers={"User-Agent": "CaptainAvi island asset generator"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main() -> None:
    islands: list[dict] = []
    offset = 0
    while offset < MAX_RECORDS:
        response = fetch_page(offset)
        features = response.get("features", [])
        for feature in features:
            attributes = feature.get("attributes", {})
            centroid = feature.get("centroid") or {}
            english_name = (attributes.get("islandName") or "").strip()
            longitude = centroid.get("x")
            latitude = centroid.get("y")
            if not english_name or not isinstance(latitude, (int, float)) or not isinstance(longitude, (int, float)):
                continue
            islands.append(
                {
                    "id": int(attributes["OBJECTID"]),
                    "englishName": english_name,
                    "dhivehiName": (attributes.get("islandNa_1") or "").strip(),
                    "atoll": (attributes.get("atoll") or "").strip(),
                    "latitude": float(latitude),
                    "longitude": float(longitude),
                    "category": (attributes.get("category") or "").strip(),
                    "isCapital": str(attributes.get("capital") or "").upper() == "Y",
                }
            )
        offset += len(features)
        if not response.get("exceededTransferLimit") or not features:
            break

    unique = {island["id"]: island for island in islands}
    sorted_islands = sorted(unique.values(), key=lambda island: (island["atoll"], island["englishName"]))
    if len(sorted_islands) < 1000:
        raise RuntimeError(f"Expected a complete registry, received only {len(sorted_islands)} islands")

    payload = {
        "version": 1,
        "snapshotDate": "2024-05-09",
        "source": "Maldives OneMap / Geomatics registry",
        "count": len(sorted_islands),
        "islands": sorted_islands,
    }
    OUTPUT.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {len(sorted_islands)} islands to {OUTPUT}")


if __name__ == "__main__":
    main()
