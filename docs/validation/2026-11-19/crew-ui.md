# Version 0.2 on one Galaxy S25 Ultra

On November 19 in Korea, a Samsung Galaxy S25 Ultra (SM-S938N, Android 17, API 37) ran the signed 0.2 review build. The installed APK and the reviewed copy both had SHA-256 `8491ff3406afa0dc84769f331f41ad9a87a0fd0d8b4e74478f734bdf43f2ed98` and 58,391,924 bytes. The Android implementation came from [revision 9f33318](https://github.com/nornen0202/CrewCam-HybridAI/commit/9f333187fefb769b5043ffe6e66350791fed0a5a). The judging invitation currently serves version 0.1. [Version 0.1 field and distribution check](README.md)

## The controlled rehearsal

The phone ran a generated first-birthday video with its camera and Nearby permissions all denied. Two virtual views of the light-blue ball started with the identical frame at 0.50 s (source frame 12). Both conditions then checked the identical source time of 1.50 s (frame 36). Each view made its own on-device Firebase request. The following values are individual SDK times in the order A / B.

| Build and branch | Initial views | Later views | Team result |
| --- | --- | --- | --- |
| Earlier 0.2, keep both | WIDE 7.557 s / WIDE 12.357 s | WIDE 8.331 s / WIDE 11.446 s | retry |
| Earlier 0.2, apply the cue | WIDE 7.448 s / WIDE 12.457 s | WIDE 9.623 s / MEDIUM 9.682 s | verified |
| Signed review build, keep both | WIDE 11.571 s / WIDE 9.697 s | WIDE 9.741 s / WIDE 9.741 s | retry |
| Signed review build, apply the cue | WIDE 7.916 s / WIDE 9.715 s | WIDE 9.782 s / MEDIUM 10.157 s | verified |
| Signed review build, select the ball by dragging on the opening still and apply the cue | WIDE 9.323 s / WIDE 9.886 s | WIDE 9.343 s / MEDIUM 10.014 s | verified |

Across the two matched comparisons, all 16 SDK observations reported ON_DEVICE and nano-v4-fast. At the later time A’s input stayed byte-for-byte identical across each pair. Only B’s crop changed. In the signed comparison B used the source rectangle (928, 644) to (1409, 915), or 481 × 271 pixels, while A kept all 1920 × 1080 pixels. The additional selection run located the opening reference again on later pixels and obtained the same wide and medium outcome. A separate initial face trial returned two 43.1% extents in the framing deadband. The app requested a retry without offering roles.

After the signed adjusted verdict, the single playback decoder supplied both virtual windows. The UI recorded 190 distinct player positions from 0 to 7,838 ms. A local 95.75-second edited demonstration includes the actual synchronized playback, English narration, an English subtitle track, Korean captions and a separate generated orange-ball cloud check. All 2,298 video frames and the audio decoded to completion. The film SHA-256 is `46b8afbbbb470445507a3172b90a714429ce68bafb13d956ef1df78a248235d6`.

The JSON includes the individual frame times, round and request identifiers, crop coordinates, pixel hashes, model, runtime source, receipt times and playback updates. Recheck both matched comparisons without uploading any images. [Deidentified records](crew-ui.json) · [Rehearsal instructions](../../REHEARSAL.md)

```sh
python scripts/compare_rehearsals.py --report docs/validation/2026-11-19/crew-ui.json
```

## Physical camera checks on that APK

The actual rear camera viewed a labeled lotion bottle. The phone stayed mounted while USB drove the ordinary buttons and the Android display orientation. Tapping a detected candidate and cancelling discarded the selection. After zooming in, a manual drag on a new frozen image selected the front bottle. The live Firebase request used a later, uncropped-by-the-selector camera frame.

| Camera test | Reported framing | Evidence from the phone |
| --- | --- | --- |
| Portrait reference | WIDE | 13.764 s, 480 × 406, matched point 601,525 per 1000 |
| Landscape reference, reselected after rotation | WIDE | 12.083 s, 606 × 480, matched point 539,403 per 1000 |

Rotating the display invalidated the earlier reference and disabled the check until reselection or clearing. On the lead, camera A showed its check photo, time and measured framing while B showed a waiting tile. Pairing and a team verdict remained pending. A separate earlier trial with adjacent bottles yielded a model box that missed the chosen detail. The app withheld a framing cue.

On the signed build, the camera A original from run prefix `0f7830da` saved 7,360,944 bytes. The ordinary playback button opened the Android player. The phone and PC copies had the same SHA-256 `676ae1e4687001c984725d9492bdf29e03c7e4075c69c06044a5b55e5fd460d5`. The 6.564-s original decoded through all 197 frames. The user-operated take on 0.1 is recorded in the earlier report.

## Model routes and engineering checks

This was an in-place, same-signer update on the S25 Ultra. The ordinary production controls reported the following results on the generated sample.

| Control | Visible result |
| --- | --- |
| Play Integrity App Check | App Check verified for this installation. |
| Strict local | WIDE  ·  ON DEVICE  ·  15.4 s Orange circle in the center nano-v4-fast |
| Forced cloud | WIDE  ·  IN CLOUD  ·  2.3 s orange ball with yellow circle gemini-3.5-flash-lite |
| Stable local model fallback | WIDE  ·  IN CLOUD  ·  2.8 s orange sphere with highlight spot gemini-3.5-flash-lite |

The configured release and a fresh public checkout both built. The public clone had neither Firebase account configuration nor the generated film. Both passed 23 JVM tests. Android Lint reported 0 errors and 16 warnings in the configured build. The suite covers pixel-reference matching, orientation geometry, virtual crops, independent camera roles and round progression.

An Android instrumentation check on the same physical S25 Ultra used two simulated members and generated colored images. It exercised metadata-only observations, optional JPEG, sender/frame binding, byte limits, revocation, stale-message rejection, disconnect cleanup, role acceptance and a later verified outcome. The physical phone count for these tests was one. At the end, Android camera and Nearby permissions and display rotation were restored to their recorded settings.

## What needs a second phone

The physical S25 Ultra and Fold8 must still pair through Nearby, accept a real cue, move one camera, verify later sensor frames from both phones and save two originals with the same run. Unassisted two-phone operation and comparison against free filming or verbal role assignment remain open. [Field trial plan](../../DEMO_AND_EVALUATION.md)
