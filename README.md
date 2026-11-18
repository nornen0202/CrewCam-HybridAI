# CrewCam

Two people can both film the birthday cake and miss the room around it. CrewCam checks their framing before the candles are lit. Each phone looks at its own current view, and the lead camera suggests which operator can give the group a different shot. The crew accepts the plan, adjusts and checks fresh camera frames before recording the moment.

## Try it on Android

The Devpost invitation currently serves **0.1-hybrid (1)**. On a Galaxy S25 Ultra, that release passed Play Integrity following a fresh install, ran Gemini Nano 4, used the cloud and demonstrated automatic fallback. A tester downloaded the identical signed APK through the judging invitation. The phone's operator also recorded and played a new original. [0.1 measurements](docs/validation/2026-11-19/README.md) · [Judge instructions](docs/JUDGE_GUIDE.md)

**Version 0.2**, described below, adds photo selection and a one-phone rehearsal. In a controlled trial on the S25 Ultra, keeping two virtual views wide prompted a retry. Tightening virtual B produced a verified wide and medium pair on the later frame. The S25 Ultra and Fold8 physical pairing trial is pending. [Rehearsal and reproduction](docs/REHEARSAL.md)

1. Open **CrewCam Hybrid**. **Try the rehearsal** uses a local video and two virtual crops. **Sample checks** draws an orange ball on the phone. Both start without camera or Nearby permission. For a real scene tap **Enable camera**. [Rehearsal steps](docs/REHEARSAL.md)
2. Name a subject. **Select in photo** freezes the current camera view. Tap a detected candidate or drag around a distinctive detail, then **Use selection**. **Check my frame** matches that reference in a later full frame on this phone. The model locates the whole subject in that frame. Its box must agree with the matched point. An ambiguous reference, changed orientation or conflicting box prompts another selection. **Clear selection** returns to a name-only check.
3. For a crew, the leader opens **Crew setup → Lead a crew**. Each other phone chooses **Join a crew** and the same total count. Confirm the matching Nearby digits on both sides. Each camera has its own cloud preference and sees the subject before it shares its observation.
4. The leader names the subject and selects **Check team framing**. Each member confirms the subject and checks their frame. Accept or decline the suggestion on each phone, adjust the shot, then select **My shot is ready**. On the leader use **Verify fresh views**. Each other phone confirms its new view. A pass requires the fresh observations to match distinct assigned angles.
5. **Record original** and **Stop recording** save a silent MP4 on each phone. On Android 10 or newer, open Movies/CrewCam in the gallery or use **Play last original**. Recordings use each camera's local clock. No timecode synchronization is assumed.

**On this phone** is the default. **Allow cloud fallback** authorizes Google to process that phone’s reduced full frame, subject text and a matched point when selected. Nearby sends the framing, source and time to the paired lead. Each member can separately turn **Crew photo on** to include a JPEG at most 192 pixels on its long side with that check. Photos default off. Turning sharing off removes that camera’s current picture from the lead. Fresh rounds, new takes and disconnections clear prior check photos. A member confirms the subject in its own view. The source photo and selection coordinates from A are never used as coordinates on B.

The lead’s **Checked views** display the actual check time per phone. **Crew roles** shows current and assigned framing, the physical cue, acceptance, readiness, fresh verification and saved-original status. A lead waiting for other phones can check its own camera. With space to move, a camera operator may enable **I have space to step back** to permit a wider assignment at minimum zoom. A live trial still needs a fresh result after the move.

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
