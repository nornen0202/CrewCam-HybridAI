# Try CrewCam

Start with a Galaxy supported by the Android Firebase AI Logic on-device SDK. Install a configured, signed contest build. For this development snapshot, the registered S25 Ultra installation is the one with verified cloud access. A new debug installation needs its own App Check registration. That is a private console operation for the entry maintainer. The release build uses Play Integrity. [Supported devices](https://developers.google.com/ml-kit/genai) · [Firebase App Check](https://firebase.google.com/docs/app-check/android/play-integrity-provider)

## A one-minute sample

Open **Sample checks**. The test input is a drawing made on your phone. Choose **On-device sample** to request a strictly local response. Under **Model setup and other checks**, **Check preview model availability** shows the categories that Firebase can see. **Prepare local model** starts a download if AICore reports a downloadable model. The preview requires the official enrollment process and Wi-Fi on supported devices. A successful result identifies the source as **ON DEVICE**. [AICore preview](https://developers.google.com/ml-kit/genai/aicore-dev-preview)

Select **Local-first sample**. Read the source beneath the picture. **Cloud sample** requests cloud explicitly. **Stable-model fallback sample** selects the stable on-device model category with the same single image. If stable is unavailable on your phone, the SDK can automatically use the cloud. The response identifies the actual source. Three additional cloud fixtures check a medium-size ball, a close ball and an empty table. They have known intended results.

## Film a live subject

Close Sample checks, allow camera access and point the rear camera at a non-personal prop. Type a precise subject, then tap **Check my frame**. For a public or staged subject, tap **On this phone → Allow cloud fallback** if cloud use is appropriate. A result shows the framing, the SDK's actual source and the request time. **Record original → Stop recording → Play last original** opens the separately saved silent video.

## Pair the cameras

1. On camera A choose **Crew setup → Lead a crew → 2 phones**. On camera B choose **Crew setup → Join a crew → 2 phones**. Allow Nearby permissions and confirm the same pairing digits.
2. Aim both cameras at the same prop. Enter its description on A. Start **Check team framing** and have B confirm what it sees. Each phone makes its own inference request and chooses its own cloud policy.
3. Read the suggestions. Accept on each phone. Adjust only as requested, then mark **My shot is ready** on each phone. On A tap **Verify fresh views** and confirm the follow-up on B.
4. Record the moment on both phones, stop each original, then play each file. The app checks the framing before recording. It does not synchronize shutters. Previous single-phone results and generated checks are indexed separately in the validation report.

If a connection drops, pair again and start a new check. If the subject is uncertain, reframe so its extent is visible and retry. For an unavailable local model, check the preview categories and AICore first. A strict local request keeps the frame on the phone.
