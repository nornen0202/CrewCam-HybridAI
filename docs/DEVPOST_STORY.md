# CrewCam

## Inspiration

Two relatives can both point a phone at the birthday cake. When the candles go out, the family has two recordings of the cake and none of the people watching. The useful time to discover a missing angle is while someone can still move.

## What CrewCam does

Each camera operator names or confirms the subject and checks the current view. The lead phone collects the framing observations over Nearby and suggests who should hold, widen or move closer. Each operator can accept or decline. After everyone marks ready, CrewCam asks for a new camera frame from each phone. The lead can verify the plan only when these later observations match the assigned views. Each phone can save its own original recording.

## How we built it

CameraX supplies a cropped, upright frame and its sensor timestamp. Firebase AI Logic asks Gemini to locate the visible subject. Kotlin maps that extent to a wide, medium or close view, with uncertainty bands between categories. This lets the app explain a cue using the actual observation. Each camera starts in on-device-only mode. Its operator can allow Firebase to fall back to the cloud. The crew exchanges framing metadata over Google Nearby Connections. The camera image stays with its operator unless that operator allows cloud processing.

I started this Firebase entry on November 17, 2026. An earlier CrewCam prototype supplied the framing policy, allocation rules and Nearby transport. During this hackathon I created a separate Android app with per-camera Firebase inference, App Check, cloud consent, a fresh-frame verification workflow and per-request source receipts. I also built a generated orange-ball scene so reviewers can try the routing without filming anyone.

## What I have tested

On a Galaxy S25 Ultra I obtained an App Check token and exercised forced cloud and automatic fallback through Firebase AI Logic. In a live test with a plastic bottle, the first request timed out. A retry on a newly captured frame returned a wide view from the cloud. A separate 22.023-second camera original decoded through all 662 frames. The generated image checks and the live camera run have separate receipts.

## What I learned and what comes next

A route preference cannot tell a camera operator where an image was actually processed. CrewCam uses the source attached to the completed SDK response. A plausible framing category is also insufficient to verify movement. Each camera needs a later sensor frame and the crew still needs the requested mix of views.

The next filming milestone is a full S25 Ultra and Fold8 take with a visible physical adjustment, fresh verification and both originals. I will compare it with unassisted filming and with a verbal wide/close assignment. The validation report records local model availability and the exact hardware used.
