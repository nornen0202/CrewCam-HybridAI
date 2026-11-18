# Galaxy S25 Ultra field verification

This page records the distributed **0.1** build. The **0.2** study covers the actual camera photo selector, the controlled rehearsal, playback, the physical solo check and the in-process crew tests. [Version 0.2 measurements](crew-ui.md) · [Version 0.2 run data](crew-ui.json)

On November 19, 2026 in Korea, one Samsung Galaxy S25 Ultra (SM-S938N, Android 17, API 37) ran the Android application. Firebase reported **ON_DEVICE** with **nano-v4-fast** for a live scene and an offline generated fixture. On the same installation, a separate request in **PREFER_ON_DEVICE** reached the cloud when its selected stable local model was unavailable. The production-signed APK then passed fresh-install Play Integrity and a protected cloud request.

The source revision is `3e6edc1014fc73b803df851e3bfab1489bc898c8`. The instrumented development APK SHA-256 is `c42c5a92bd4b775e1232256c378905bea8606f27fb3f77d385efe4d53c5577f2`. The signed release APK SHA-256 is `894103dee59343f92f7942e97e60829692ffab8903e544d30726b1a9ceb24579`. Results, sensor frame identifiers and source/model receipts are in the [deidentified run data](device.json).

## One device, four routes

The generated input was a 640 × 480 drawing with a small orange ball. Each row is a separate, completed request on the development APK. Times cover the SDK call. They are individual observations, with different warm-up and network conditions.

| User action | Requested model or route | Actual model and source | Framing | SDK time |
| --- | --- | --- | --- | ---: |
| On-device sample | Only on device, fast preview | nano-v4-fast · ON_DEVICE | Wide | 12.308 s |
| Local-first sample | Prefer on device, fast preview | nano-v4-fast · ON_DEVICE | Wide | 11.075 s |
| Cloud sample | Only in cloud | gemini-3.5-flash-lite · IN_CLOUD | Wide | 7.943 s |
| Stable-model fallback sample | Prefer on device, stable unavailable | gemini-3.5-flash-lite · IN_CLOUD | Wide | 7.974 s |
| On-device sample with internet disconnected | Only on device, fast preview | nano-v4-fast · ON_DEVICE | Wide | 11.959 s |

The offline trial verified that Android had no active default network immediately before and after the request. Wi-Fi and mobile data were off. Both original switch states and a default network were restored. USB supplied test control. AICore reported the fast preview as available, stable as unavailable and the full preview as downloadable. The free project used the Gemini Developer API. [Firebase inference modes](https://firebase.google.com/docs/ai-logic/hybrid/android/configuration-options) · [AICore preview](https://developers.google.com/ml-kit/genai/aicore-dev-preview)

## Framing and camera originals

Four separately generated cloud fixtures agreed with all four predefined labels in one trial each. The small, medium and large balls occupy 30%, 60% and approximately 90% of the frame height. The empty table contains no ball. The cloud returned wide, medium, close and uncertain, respectively. These four drawings provide a controlled sanity check. They do not estimate accuracy across families or events.

The normal camera screen then observed a real, staged drink bottle using ONLY_ON_DEVICE. Nano located it and CrewCam classified it as **medium** in **14.153 s**. Camera A saved a **6.830 s** silent MP4, **5,061,865 bytes**, under the same run ID, `e44af679-cede-47d2-bd6f-33af534c4611`. FFmpeg decoded **206 frames** to EOF. A different live request asked for an absent violet umbrella. Nano returned **uncertain / missing** in **9.861 s**. The app issued no framing assignment. Camera originals and uncropped screens are retained privately.

In an earlier build, a live cloud request timed out after 70 s. A fresh retry returned wide. In a later trial, the model located the visible bottle with a 41.7% extent between two framing bands. The app now distinguishes an uncertain size from an absent subject and offers corresponding guidance. A separate early recording decoded through all 662 frames over 22.023 s. These observations are tied to their earlier APKs in the data.

## Review findings that changed the implementation

- A cloud response misspelled a required JSON key. CrewCam now supplies a typed cloud response schema and validates each response.
- The Android JSON decoder silently accepted two distinct bounding boxes under the same key in early Nano outputs. The current parser rejects repeated top-level keys. The compact prompt requests each field once. A malformed local response can trigger one separately recorded retry under the operator's chosen privacy policy.
- With a working Nano preview, an early two-image request returned ON_DEVICE and failed the schema. The stable-category check now uses one image and an observed unavailable category. It demonstrates the SDK's actual routing decision on this phone.
- CameraX now uses public frame-analysis APIs, honors the preview viewport, and uses a new sensor timestamp for each request. Field observations come from the live analyzer. Recording success requires finalization, positive duration and actual file bytes.

## Signed install and build quality

A new source clone built its debug APK and passed the unit tests without Firebase client configuration. The configured build produced both variants. **14/14** unit tests passed. Android Lint reported **0 errors and 9 warnings**, covering API-level attributes, available library upgrades and backup configuration. The release APK passed v2 and v3 signature verification.

After preserving the developer app data and all four original takes on the PC, the developer installation was removed from the test phone. The release APK installed under the separate release key. Ordinary app buttons obtained a Play Integrity App Check token, a forced-cloud wide response (8.2 s), an on-device Nano 4 wide response (13.0 s), and a stable-category cloud fallback (4.0 s). These times are the displayed, rounded SDK measurements. The release uses the documented outside-Play device-integrity settings. [Firebase App Check](https://firebase.google.com/docs/app-check/android/play-integrity-provider) · [Android Play Integrity setup](https://developer.android.com/google/play/integrity/setup)

## Operator playback and judge distribution

The entrant used the signed release on the S25 Ultra and personally confirmed playback. USB stayed connected. We compared Movies/CrewCam with its pre-trial listing after the operator finished. The single new camera A file, run prefix `d16d2b89`, was **6.530 s**, **4,744,809 bytes** and decoded through **196 frames**. Its SHA-256 matched the file on the phone. The operator reported playback only. Framing, inference source and the full run ID for this take are unconfirmed. The separate instrumented bottle take above supplies those inference measurements.

Firebase App Distribution delivered release **0.1-hybrid (1)** to the judging group. The entrant joined the group, received the official invitation, and accepted it on the S25 Ultra. The APK downloaded from Firebase was **8,107,887 bytes** and matched the SHA-256 of the signed clean-install test above. Devpost stores the group invitation in its judge-only test-build field.

## Boundaries of the field result

The two-phone lead screen advertised over Nearby and waited for 0 of 1 peers with verification disabled. The crew policies cover two through eight participants in tests. Physical pairing, an accepted action and visible reposition, a fresh verdict from both cameras, two same-run originals, unaided two-phone operation and a matched comparison against verbal shot assignment still require simultaneous views from two physical phones. Historical Gemma footage belongs to the earlier app. The local English film uses real camera originals and screen recordings, holds genuine result screens for readability and identifies generated scenes.
