# CrewCam

## Inspiration

Two relatives can both point a phone at the birthday cake. When the candles go out, the family has two recordings of the cake and none of the people watching. The useful time to discover a missing angle is while someone can still move.

## What CrewCam does

Each operator checks the framing on their own phone. The lead receives observations over Nearby and suggests who should hold, widen or move closer. Each operator can accept or decline. After everyone marks ready, the lead asks for a fresh frame from every camera. CrewCam can verify the plan only when those later observations match the assigned views. Each phone saves its own original recording.

## How I built it

CameraX supplies a cropped, upright camera frame and its sensor timestamp. Firebase AI Logic asks Gemini to locate one named subject. Kotlin maps the visible extent to a wide, medium or close view. An ambiguous size earns an explanation and a chance to reframe. Each camera starts in on-device-only mode. Its operator can allow cloud fallback. The phones exchange framing metadata over Google Nearby Connections.

I started the Firebase entry on November 17, 2026. An earlier CrewCam prototype contributed framing thresholds, allocation rules and Nearby pairing. I built the per-camera Firebase integration, individual cloud consent, App Check, the fresh-frame crew workflow and source receipts for this entry.

## What I tested

On one Galaxy S25 Ultra, I turned off Wi-Fi and mobile data. Gemini Nano 4 Fast classified a generated wide shot in 11.959 seconds with no active default network. With connectivity restored, I selected an unavailable stable model in local-first mode. Firebase automatically returned a cloud result from the same input.

The rear camera also checked a real plastic bottle on the phone in 14.153 seconds. CrewCam reported a medium view. Its same-run original saved and decoded through all 206 frames. When I asked for a violet umbrella that was absent from the scene, the phone reported the subject missing. A fresh install of the signed release passed Play Integrity, local inference, cloud inference and automatic fallback.

## What I learned

A model can locate an object while its framing remains ambiguous. It can also return two coordinates for the same field. CrewCam rejects duplicate fields and keeps uncertain framing out of a crew verdict. A routing preference alone also cannot identify where an image was processed. The app records the source on each actual Firebase response.

## What comes next

The next field trial pairs the S25 Ultra and Fold8 for a take with an accepted cue, a visible physical adjustment, a new verdict from both cameras and two saved originals. That pair will also be compared with unassisted filming and a verbal wide/close assignment. The validation report distinguishes the one-phone measurements from these remaining trials.
