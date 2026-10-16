# WideLight

A focused Android photo-to-print app for the **original micro-USB Fujifilm Instax Link WIDE**. Unofficial, experimental, and not affiliated with Fujifilm.

## Photo selection

- Android's system photo picker: multiple selection, local photos, and cloud media provided by Google Photos.
- Albums and Favorites are provided by the system/cloud provider rather than an incomplete custom gallery.
- Google Photos → select inside an album or Favorites → Share → WideLight also works.
- Private, durable imported copies; no broad storage permission or Google login required.
- A persistent print queue with previews, rotate, fit/center-fill, remove, and app-local favorite bookmarks.
- One explicit confirmation per sheet. No automatic print retries.

### Enable cloud albums and Favorites

Tap **Add photos**, then open the system picker's menu/settings and select Google Photos as the cloud media app. Grant access if prompted. Android version, Google Photos account, system picker updates, and provider support affect availability. On some versions you can also configure this in Google Photos → Settings → Apps & devices → Google Photos access → Photo picker.

If an album is unavailable, select its images in Google Photos and share them to WideLight. WideLight's saved favorites are independent of Google Photos favorites.

The Android photo picker is different from Google's web Photos Picker API. The latter currently does not show album/Favorites categories directly. This app intentionally uses the Android integration.

## Printing

Pair the original Link WIDE's **ANDROID** endpoint in Android Bluetooth settings, close the official app, then choose the paired printer in WideLight. Printing uses Bluetooth Classic RFCOMM/SPP, not micro-USB. The printer reports image dimensions and JPEG limits; WideLight renders to those dimensions and adjusts JPEG quality to fit. Transfer replies and frame acknowledgements are validated, with connection/read deadlines. Errors stop the operation. A lost print acknowledgement can still mean a sheet was consumed: check the printer before retrying.

**Hardware validation is still required.** This is an initial implementation of the publicly documented reverse-engineered protocol, not a claim of tested compatibility with a physical printer. Only original WIDE dimensions are accepted. Other Instax models and WIDE2 are outside the current scope. Keep the app open during transfer. The preview shows the transferred image; physical film can trim edges.

## Build

Android Studio, JDK 17, Android SDK 35:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs these checks and uploads it. No signing credentials, Google Cloud project, server, or API keys are needed.

## Current scope

This first version covers photo selection, an individual-photo queue, simple composition, and a direct print path. Manual pan/zoom crop, copies/batch scheduling, rich/natural color profiles, collage, frames, QR codes, and background printing are not implemented. Photos are stored only in private app storage, excluded from backup, and deleted when removed or when the app is uninstalled. Import is limited to 50 images at a time and 30 MB per image; decoding is bounded to 2000 pixels on the longest edge.

## Verification on a device

1. Enable Google Photos cloud media and check cloud-only photos, Albums, and Favorites in Add photos.
2. Share multiple photos from a Google Photos album and verify previews survive app restart.
3. Compare portrait/landscape fit, fill, and rotate previews; remove an image and confirm it disappears after restart.
4. Pair a powered original Link WIDE, grant Nearby devices, then print one expendable test photo.
5. Confirm orientation, color, film margins, status, and transfer behavior before further use.
6. Check no-film/door-open errors, disconnect during transfer, and never blindly retry a lost print acknowledgement.

## References

- [Android cloud photo picker and Favorites](https://android-developers.googleblog.com/2024/02/cloud-photos-now-available-in-android-photo-picker.html)
- [Android photo picker integration](https://developer.android.com/training/data-storage/shared/photo-picker)
- [Google Photos picker setup](https://support.google.com/photos/answer/14442861)
- [Google Photos web picker limitations](https://developers.google.com/photos/picker/guides/picking-experience)
- [InstaxLink protocol research](https://github.com/paorin/InstaxLink) and [InstaxBLE research](https://github.com/javl/InstaxBLE)

Implementation here is original; protocol research projects are credited for documenting wire formats and transport behavior.
