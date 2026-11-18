# Rehearse with one Android phone

**Try the rehearsal** opens from the camera screen and works without camera or Nearby permission. The entry build includes a locally supplied, generated first-birthday scene. A source checkout also accepts an MP4 through Android’s document picker. The supplied scene has SHA-256 `60d091cc8a30f0664fa750264d7e402c80ce98a8c04e51fb5c6b058a90cc93a9`. Builders with access to that clip can place it at `app/src/main/assets/rehearsal/first-birthday.mp4` before building. It is excluded from Git.

Each rehearsal uses one physical device, two virtual views and one local video. The phone decodes and pauses the source. An operator can name the subject or select a reference in the opening still. Camera A and B initially receive the full frame nearest 0.50 seconds. Each view makes a separate strictly on-device Firebase AI Logic request. Both measurements enter the same framing and role-assignment policy as the live crew. A virtual camera can center a digital crop on the subject's observed position.

After the first checks, choose **Keep current views** or **Apply suggested framing**. Keeping leaves both crop windows alone. Applying makes the suggested digital crop. **Check new frames** decodes the same later source time (1.50 seconds) in either condition and sends the actual pixels of each virtual view to the SDK. The returned boxes and the existing crew state machine determine the verdict. Reset to repeat the other condition from the same start.

**Run details** shows the source time, crop coordinates, actual Firebase source and model, inference duration and a prefix of the input-pixel digest. The phone also writes a per-run JSON file under its application-specific external `files/rehearsals` directory. It contains the source digest, source frame index and presentation time, crop in source-image pixels, dimensions and SHA-256 of the exact ARGB model input. A failed or interrupted round leaves its observed requests in the record. A physical camera trial has separate sensor timestamps, Nearby transport and camera originals.

Select **Play both virtual views** after the verdict. A single Android player decodes the source while the renderer draws both crop windows on each update. Watch the shared source clock while the room stays wide in A and the chosen detail fills more of B. The receipt records the first and last displayed player positions and how many distinct positions were drawn.

The comparison tool checks the source times, pixel hashes and real SDK responses together. To reproduce the two published comparisons from the source checkout, run the following command.

```sh
python scripts/compare_rehearsals.py --report docs/validation/2026-11-19/crew-ui.json
```

To compare your own Keep and Apply experiments, export both JSON files from the phone's app-specific rehearsal folder. Pass their paths to the same script in that order. The report lists which later camera inputs changed, both measured verdicts and their respective inference receipts.

For the bundled scene, the default subject is the light-blue ball in front of the baby. In the first matched experiment, all eight requests for the two branches returned `ON_DEVICE` with `nano-v4-fast`. Both unadjusted views stayed wide in the keep condition, and the later frame prompted a retry. In the adjusted condition A kept the entire 1920 × 1080 source, B used the rectangle (928, 644) to (1409, 915), and the later check verified wide and medium. A separate trial on the baby's face produced two uncertain observations at 43.1% extent, inside the framing policy's gap. The app withheld a role assignment for that trial.
