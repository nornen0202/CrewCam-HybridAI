# CrewCam

Two people can both film the birthday cake and miss the room around it. CrewCam checks their framing before the candles are lit. Each phone looks at its own current view, and the lead camera suggests which operator can give the group a different shot. The crew accepts the plan, adjusts and checks fresh camera frames before recording the moment.

## Try it on Android

On a Galaxy S25 Ultra, Gemini Nano 4 checked a live bottle and an offline sample. A fresh installation of the signed release passed Play Integrity, local inference, forced cloud and automatic cloud fallback. The operator also played back a new recording on that release. The two-phone field trial is pending. Judges can install the signed build through the Firebase App Distribution invitation supplied in Devpost. [Measurements and limitations](docs/validation/2026-11-19/README.md) · [Judge instructions](docs/JUDGE_GUIDE.md)

1. Open **CrewCam Hybrid** and allow camera access. To inspect the model without pointing the camera at anyone, open **Sample checks**. The orange ball is generated on the phone. Start with **Check model**, prepare the local model if needed, and select **On-device sample**.
2. For one live view, describe an object in the preview and tap **Check my frame**. The app shows the observed framing, source and elapsed inference time. If the object cannot be located, adjust the view and try again.
3. For a crew, the leader opens **Crew setup → Lead a crew**. Each other phone chooses **Join a crew** and the same total count. Confirm the matching Nearby digits on both sides. Each camera has its own cloud preference and sees the subject before it shares its observation.
4. The leader names the subject and selects **Check team framing**. Each member confirms the subject and checks their frame. Accept or decline the suggestion on each phone, adjust the shot, then select **My shot is ready**. On the leader use **Verify fresh views**. Each other phone confirms its new view. A pass requires the fresh observations to match distinct assigned angles.
5. **Record original** and **Stop recording** save a silent MP4 on each phone. On Android 10 or newer, open Movies/CrewCam in the gallery or use **Play last original**. Recordings use each camera's local clock. No timecode synchronization is assumed.

**On this phone** is the default. **Allow cloud fallback** gives the SDK permission to process that phone’s reduced frame and subject text using the configured Gemini service. Nearby sends the observation to the lead, without transferring camera images. Use public staged objects for cloud trials on the free service. A crew can mix phone-local and cloud observations. The reported source comes from each SDK response.

## Build and configure

Use JDK 17 or 21, Android SDK platform 35 with build tools 35.0.0, and the checked-in Gradle 8.9 wrapper. Set JAVA_HOME and ANDROID_HOME to your installed paths. The entry uses AGP 8.7.3 and CameraX 1.4.2. Kotlin 2.4.0 is needed for the metadata in ML Kit's transitive schema library. The original 055 repository keeps its own toolchain.

Register `com.galaxycrew.crewcam.hybrid` in a Firebase project. Enable Firebase AI Logic with the Gemini Developer API and App Check. Save its Android client configuration as `app/google-services.json`, which Git ignores. A source-only checkout can build and exercise policy tests without that file. Configured inference requires a real Firebase app. [Firebase Android setup](https://firebase.google.com/docs/ai-logic/hybrid/android/get-started)

```sh
bash scripts/build-local.sh :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Windows developers can stage the same inputs on a local SSD using `scripts/build-windows.ps1 -Sdk <sdk-path> -Java <jdk-path>`. The script prints the build output directory. The debug APK path under either build is `app/build/outputs/apk/debug/app-debug.apk`. Register the debug provider's per-install token privately in the project's App Check panel. Release builds use Play Integrity. Keep tokens and signing keys out of version control. [Debug provider](https://firebase.google.com/docs/app-check/android/debug-provider) · [Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider)

## Verify the implementation

The actual call to Firebase AI Logic is in [HybridInference.kt](app/src/main/java/com/galaxycrew/crewcam/HybridInference.kt). CameraX supplies a new sensor timestamp. [TeamRound.kt](app/src/main/java/com/galaxycrew/crewcam/TeamRound.kt) checks each camera's own frame sequence, accepted role and observed framing. A new run invalidates outstanding assignments.

On a debug installation, `scripts/sample_check.py` matches the installed APK digest and exports each outcome. Add `--require-nano4` to the local sample to check the actual model name. The stable-model sample pins the stable category and calls the SDK with one image in local-first mode. If stable is unavailable, the SDK can fall back even on a phone with a working preview model. The result displays what happened on this installation.

An ambiguous model response earns no framing cue. Repeated JSON fields cause a schema failure. A malformed local answer can trigger one new inference on the same frame. Strictly local mode retries on the device. When the operator has allowed cloud fallback, the application can retry in the cloud. The request journal links both attempts. This recovery is separate from the SDK routing a request automatically.

[Entry plan and official sources](docs/ENTRY_PLAN.md) · [Evaluation and demo shot list](docs/DEMO_AND_EVALUATION.md) · [Inherited work and new development](docs/PROVENANCE.md) · [License](LICENSE)
