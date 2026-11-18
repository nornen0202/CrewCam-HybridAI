"""Compare one saved Keep run and one Apply run of the same two-view video rehearsal."""
import argparse
import json
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inspect(run, branch):
    require(run["branch"] == branch, f"Run {run['run']} belongs to a different branch")
    require(run["physical_phones"] == 1 and run["virtual_views"] == 2, "Unexpected input topology")
    actual = {event["request"]: event for event in run["events"] if event["event"] == "inference"}
    views = {}
    for item in run["inputs"]:
        key = (item["stage"], item["camera"])
        require(key not in views, f"Repeated observation {key}")
        receipt = actual[item["request"]]
        fields = ("round", "camera", "framing", "source", "sdk_ms")
        require(receipt["run"] == run["run"] and all(receipt[k] == item[k] for k in fields),
                f"Observation and SDK receipt differ for {key}")
        require(receipt["frame"] == item["frame_id"], f"Frame mismatch for {key}")
        require(receipt["mode"] == "ONLY_ON_DEVICE" and receipt["local_model"] == item["model"]
                and receipt["source"] == "ON_DEVICE", f"Local provenance missing for {key}")
        views[key] = item
    expected = {(stage, camera) for stage in ("initial", "fresh") for camera in ("A", "B")}
    require(views.keys() == expected, "Both stages need an observed A and B")
    for stage in ("initial", "fresh"):
        a, b = (views[stage, c] for c in ("A", "B"))
        require(a["round"] == b["round"] and source_frame(a) == source_frame(b),
                f"A and B do not share a round and source frame at {stage}")
    first, fresh = (views[stage, "A"] for stage in ("initial", "fresh"))
    require(first["round"] != fresh["round"] and fresh["source_pts_us"] > first["source_pts_us"]
            and fresh["source_frame_index"] > first["source_frame_index"], "Expected a later round and frame")
    return run, views


def source_frame(view):
    return view["source_pts_us"], view["source_frame_index"]


def pixels(view):
    return view["crop_xyxy_px"], view["input_width"], view["input_height"], view["input_argb_sha256"]


def compare(keep_record, apply_record):
    keep, old = inspect(keep_record, "keep original framing")
    apply, new = inspect(apply_record, "apply suggested framing")
    require(keep["run"] != apply["run"], "Choose two different runs")
    for key in ("source_sha256", "subject"):
        require(keep[key] == apply[key], f"The runs have different {key}")
    for key in old:
        require(source_frame(old[key]) == source_frame(new[key]), f"Input times differ for {key}")
    start = pixels(old["initial", "A"])
    require(all(pixels(views["initial", c]) == start for views in (old, new) for c in ("A", "B")),
            "Four identical starting images are required for this comparison")
    require(all(old["fresh", c]["crop_xyxy_px"] == start[0] for c in ("A", "B")),
            "The Keep branch changed its framing")
    require(pixels(old["fresh", "A"]) == pixels(old["fresh", "B"]), "Keep has different fresh images")
    changed = [c for c in ("A", "B") if pixels(old["fresh", c]) != pixels(new["fresh", c])]
    result = {"source_sha256": keep["source_sha256"], "subject": keep["subject"],
              "changed_views_at_same_fresh_frame": changed, "runs": []}
    for run, views in [(keep, old), (apply, new)]:
        result["runs"].append({
            "run": run["run"], "branch": run["branch"], "outcome": run["result"],
            "observations": [{k: x[k] for k in ("stage", "camera", "source_pts_us", "source_frame_index",
                             "crop_xyxy_px", "input_argb_sha256", "framing", "model", "source", "sdk_ms")}
                             for x in views.values()],
            "playback": [e for e in run["events"] if e["event"] == "paired_virtual_playback"],
        })
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipts", nargs="*", type=Path, help="Two phone JSON files, Keep then Apply")
    parser.add_argument("--report", type=Path, help="Recheck all matched pairs in a published evidence file")
    args = parser.parse_args()
    try:
        if args.report:
            require(not args.receipts, "Specify either a report or two exported phone files")
            report = json.loads(args.report.read_text(encoding="utf-8"))
            runs = {r["run"]: r for r in report["rehearsals"]}
            results = [compare(runs[p["keep"]], runs[p["apply"]]) for p in report["matched_pairs"]]
        else:
            require(len(args.receipts) == 2, "Supply the Keep and Apply phone JSON files")
            results = compare(*(json.loads(p.read_text(encoding="utf-8")) for p in args.receipts))
        print(json.dumps(results, indent=2))
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f"Could not compare these receipts: {error}\n")
