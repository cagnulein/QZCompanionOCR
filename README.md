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
- Advanced controls and metric selection in the horizontal HTML may be limited (the page belongs to QZ).

Google Play 16 KB release verification:
- The Android GitHub Actions workflow builds a new release AAB, checks its bundle
  PAGE_ALIGNMENT_16K flag when native 64-bit libraries are present, checks the ELF
  PT_LOAD alignment of native arm64/x86_64 libraries, and validates the release APK
  with zipalign -P 16.
- A passing CI build is not a substitute for a 16 KB emulator/device test or the
  Play Console app-bundle compatibility check. The workflow does not publish to Play.
