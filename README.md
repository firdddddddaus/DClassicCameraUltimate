# D'Classic Camera Ultimate
Android CameraX camera with 100 procedural classic color presets, live filtered preview, post-capture editing, strength slider, camera switching and JPEG export.

## Build on phone using GitHub Actions
1. Create a new GitHub repository (public or private).
2. Upload **all files and directories inside this ZIP**, preserving `.github/workflows/android.yml`.
3. Open **Actions > Build DClassic APK > Run workflow** (or push to main).
4. Open the completed run, download artifact **DClassicCamera-debug-APK**, extract it and install `app-debug.apk` on your phone.
5. Grant camera permission when prompted.

## Notes
- First build requires downloading Android dependencies; expect several minutes.
- Preview is a CPU-processed 640x480 CameraX ImageAnalysis stream, not a native 4K/60fps preview. On some phones this may be slower than the stock camera.
- 100 presets are generated from 6 families × 17 variations, with the first 100 exposed; these are not licensed manufacturer LUTs.
- The current version saves the edited image, not a separate original copy.
- CameraX chooses a supported capture resolution; Xiaomi stock camera's proprietary 50MP/Leica features are not guaranteed.
- Debug APK is suitable for personal testing, not Play Store release.
