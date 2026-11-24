Basic Instructions:

1. Install the APK
2. Allow the app to record the video and to save file to the memory of the device
3. Select the right device in the main screen
4. Restart the app
5. Once restarted open peloton and start a workout
6. Using "nc -u -l 8003" on a PC or Mac you should see something like "100;70;30;19.9" where the first number is the power, the second the cadence, the 3rd the resistance and the last one the speed

Horizontal floating metrics (beta):
- Ensure QZ and Companion are on the same network, and QZ is serving its floating web page.
- In Companion select "Horizontal floating metrics (QZ)"; vertical remains the default.
- Companion displays QZ's existing /floating/hfloating.htm page instead of /floating/floating.htm.
  No change to QZ, the UDP discovery protocol, or the metric feed is required.
- Width, height, left and top are stored separately for vertical and horizontal layouts.
  The horizontal layout initially fills the screen width and appears as a short bar at the bottom.
- If a QZ version returns HTTP 404 for the horizontal page, Companion falls back to the vertical page.
- Companion injects app/src/main/assets/companion_horizontal.js after the QZ horizontal page loads.
  The horizontal overlay is read-only: quick-action buttons, the metric selector and control
  panels are intentionally hidden so they do not cover Peloton workouts.
- The initial bar shows Power, Cadence, Heart Rate, Resistance and Peloton Resistance.
  To customize the bar on Android TV, enable "Horizontal floating metrics (QZ)" in Companion,
  then choose "Choose horizontal metrics". Use the Nvidia Shield remote to select metrics
  in the native Android dialog and Save. These preferences are stored by Companion, never
  written to QZ tile_* settings; they update the currently running overlay.
- An existing Companion WebView-local selection is retained until a native selection is saved.
  The page can still safely store preferences without changing QZ when used with a pointer.
- A minimal Android bridge supports local overlay close and the existing panel APIs without
  changing QZ or its WebSocket feed. The window remains non-focusable so Peloton receives
  the remote's playback and navigation keys.
- Node regression tests: `node --test tools/test-companion-horizontal.cjs`.
  Java unit tests also cover invalid/duplicate metric IDs and round-trip remote selections.
  Real Nvidia Shield + Peloton behavior still needs confirmation.

Google Play 16 KB release verification:
- The Android GitHub Actions workflow builds a new release AAB, checks its bundle
  PAGE_ALIGNMENT_16K flag when native 64-bit libraries are present, checks the ELF
  PT_LOAD alignment of native arm64/x86_64 libraries, and validates the release APK
  with zipalign -P 16.
- A passing CI build is not a substitute for a 16 KB emulator/device test or the
  Play Console app-bundle compatibility check. The workflow does not publish to Play.
