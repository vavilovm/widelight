# WideLight

A focused Android photo-to-print app for the **original micro-USB Fujifilm Instax Link WIDE**. Unofficial, experimental, and not affiliated with Fujifilm.

## Photo selection

- Android's system photo picker: multiple selection, local photos, and cloud media provided by Google Photos.
- Albums and Favorites are provided by the system/cloud provider rather than an incomplete custom gallery.
- Google Photos → select inside an album or Favorites → Share → WideLight also works.
- Private, durable imported copies; no broad storage permission or Google login required.
- A persistent print queue with previews, rotate, fit/center-fill, remove, and app-local favorite bookmarks.
- A 1–6 photo composer with grid, columns, rows, and spotlight layouts.
- Per-photo rotate, fit/fill, zoom, and horizontal/vertical crop positioning; reorder photos within a layout.
- White, ink, cream, and teal frames, adjustable borders/gutters, and captions.
- Natural, Pop, B&W, and Warm color looks (independent of Fujifilm's color profiles).
- QR codes for links or short messages, rendered on a separate white panel with a quiet zone.
- One explicit confirmation per sheet. No automatic print retries.

### Enable cloud albums and Favorites

Tap **Add photos**, then open the system picker's menu/settings and select Google Photos as the cloud media app. Grant access if prompted. Android version, Google Photos account, system picker updates, and provider support affect availability. On some versions you can also configure this in Google Photos → Settings → Apps & devices → Google Photos access → Photo picker.

If an album is unavailable, select its images in Google Photos and share them to WideLight. WideLight's saved favorites are independent of Google Photos favorites.

The Android photo picker is different from Google's web Photos Picker API. The latter currently does not show album/Favorites categories directly. This app intentionally uses the Android integration.

## Create a collage or designed print

1. Add your photos to the queue, then tap **Create collage / designed print**. For a single photo, tap its **Design · frame, caption & QR** action.
2. Choose 1–6 photos from the queue or import more using the system picker. Selection order determines placement; use Earlier/Later to reorder.
3. Choose a layout and select each photo's numbered chip to rotate, fit, zoom, or adjust its crop position.
4. The preview stays pinned while controls scroll. Layout, ordering, rotation and crop are the primary controls. Frames, spacing, captions, color looks and QR codes are in a collapsed **More options** section at the bottom.
5. **Save print to queue** creates a separate, flattened, lossless 1260×840 PNG. Original photos stay unchanged. Print the saved design as one sheet.

The live draft survives activity rotation but is not restored after process death. Saved prints persist across restarts and stay usable after their source photos are removed. Saved designs are flattened rather than editable layout recipes; keep source photos to make another version. Captions support up to 80 characters. QR payloads support up to 160 UTF-8 bytes. QR codes embed the supplied text/link directly: WideLight does not host audio or other files. Try scanning a physical test print before making more copies.

## Printing

Pair the original Link WIDE's **ANDROID** endpoint in Android Bluetooth settings, close the official app, then choose the paired printer in WideLight. Printing uses Bluetooth Classic RFCOMM/SPP, not micro-USB. The printer reports image dimensions and JPEG limits; WideLight renders to those dimensions and adjusts JPEG quality to fit. Transfer replies and frame acknowledgements are validated, with connection/read deadlines. Errors stop the operation. A lost print acknowledgement can still mean a sheet was consumed: check the printer before retrying.

**Hardware validation is still required.** This is an initial implementation of the publicly documented reverse-engineered protocol, not a claim of tested compatibility with a physical printer. Only original WIDE dimensions are accepted. Other Instax models and WIDE2 are outside the current scope. Keep the app open during transfer. The preview shows the transferred image; physical film can trim edges.

## Build

Android Studio, JDK 17, Android SDK 35:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs these checks and uploads it. No signing credentials, Google Cloud project, server, or API keys are needed. Unit tests cover packet handling, every layout, crop/fit rendering, source-independent saved designs, persistence, and decoding the QR from the final compressed JPEG. Native graphics tests run with Robolectric on Android 14.

## Current scope

This version covers cloud-aware photo selection, a durable print queue, collage/design composition, and a direct print path. Copies/batch scheduling, Fujifilm rich/natural profiles, editable saved layout recipes, and background printing are not implemented. Photos are stored only in private app storage, excluded from backup, and deleted when removed or when the app is uninstalled. Import is limited to 50 images at a time and 30 MB per image; decoding is bounded to 2000 pixels on the longest edge.

## Verification on a device

1. Enable Google Photos cloud media and check cloud-only photos, Albums, and Favorites in Add photos.
2. Share multiple photos from a Google Photos album and verify previews survive app restart.
3. Compare portrait/landscape fit, fill, and rotate previews; remove an image and confirm it disappears after restart.
4. Create a six-photo collage; reorder, rotate, crop, change frames, add a caption and QR; save and restart. Remove the sources and check that the saved design is intact.
5. Pair a powered original Link WIDE, grant Nearby devices, then print one expendable test photo.
6. Confirm orientation, color, film margins, status, and transfer behavior before further use.
7. Check no-film/door-open errors, disconnect during transfer, and never blindly retry a lost print acknowledgement.

## References

- [Android cloud photo picker and Favorites](https://android-developers.googleblog.com/2024/02/cloud-photos-now-available-in-android-photo-picker.html)
- [Android photo picker integration](https://developer.android.com/training/data-storage/shared/photo-picker)
- [Google Photos picker setup](https://support.google.com/photos/answer/14442861)
- [Google Photos web picker limitations](https://developers.google.com/photos/picker/guides/picking-experience)
- [InstaxLink protocol research](https://github.com/paorin/InstaxLink) and [InstaxBLE research](https://github.com/javl/InstaxBLE)

Implementation here is original; protocol research projects are credited for documenting wire formats and transport behavior.

## Language and app size

The interface supports English and Russian and follows your device/app language. On Android 13+, choose a language in Android Settings → Apps → WideLight → Language. Built-in queue titles, controls, help, validation errors, and app/printer messages are translated; imported metadata remains unchanged.

Release builds use R8 code optimization and resource shrinking. The unused extended icon library is removed and packaged library translations are limited to the two supported languages. Debug builds remain unoptimized for development.

## Automatic GitHub releases

Every branch push tests, lints, and builds a signed, non-debuggable release APK once and publishes a GitHub release tagged `build-<workflow run number>`, with the APK and SHA-256 checksum attached. Default-branch releases become the latest release; other branches publish prereleases. Pull requests run checks without publishing. Re-running a workflow replaces the same build's assets rather than creating a duplicate release.

Download the newest default-branch build directly: [WideLight.apk](https://github.com/vavilovm/widelight/releases/latest/download/WideLight.apk), or browse [GitHub Releases](https://github.com/vavilovm/widelight/releases/latest). Android version codes increase with the workflow run number. The default signing identity is the intentionally public, dedicated development key in `.github/keys/development.p12`, so no secret setup is needed. These are development distributions, not Play Store releases. The earlier debug APK uses a different certificate; switching from it requires uninstalling it first, which removes its private photo library.

To use your own persistent signing identity, add these repository **Actions secrets**:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded keystore contents.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing-key alias.
- `ANDROID_KEY_PASSWORD`: signing-key password.

CI pins Ubuntu 24.04 and Node 24 actions, reuses the Gradle build cache, and transfers the already-built APK to the publishing job without rebuilding it. Artifact uploads skip redundant ZIP compression.

The workflow decodes the key into temporary runner storage, validates the accompanying settings, and removes the file after building. Keep the same key for future updates. Local `./gradlew assembleRelease` uses the development key unless `ANDROID_KEYSTORE_PATH` and the three signing environment variables are supplied.


### Crop, film, and updates

New photos fill the print by default. In the collage preview, tap a cell to select it,
pinch to zoom and drag to reframe. The gold outline and thirds grid identify the
active photo. Striped regions represent white areas in the printed photo; they
are preview guides and are never printed. Fit preserves the whole image and
requires explicit confirmation before printing; this warning survives saving a
collage to the queue. Intentional borders, captions and QR panels are excluded.

Choose a printer and use **Check printer / refresh film** to read its remaining
film. The count is the last reading (decreased after an accepted print), not a
live connection; refresh after changing film or using another app.

**Update app** downloads the APK from the latest stable GitHub release, verifies
its package, signing certificate and increasing Android version code, then opens
the system installer. Allow installation from WideLight when Android asks.
Updates retain the library. Releases currently use the stable public development
signing key because private signing secrets are not configured. Keep the same
key for compatible upgrades; changing keys requires reinstalling the app.
