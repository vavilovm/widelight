# Picker decision

The user identified Instagram as a useful reference because its selector exposes Favorites. No physical Instagram session was available in this environment, so this is platform research, not an observed walkthrough of Instagram's current UI.

Android's first-party cloud photo picker documentation explicitly supports Favorites and cloud albums. That makes the system picker a better foundation than querying MediaStore, which misses cloud-only content, or using the separate Google Photos web Picker API, which does not directly display album/Favorites categories.

WideLight uses `PickMultipleVisualMedia(ImageOnly)`, accepts shared image URIs, and immediately imports selected content in a background dispatcher. Importing avoids relying on transient URI permissions for future print sessions. No Google OAuth credentials are required. The system owns cloud authorization and account selection. Older or unsupported pickers may fall back to documents; the UI provides a share-from-Photos alternative.

## UX built around it

1. Add photos without first connecting a printer.
2. Let the system surface the user's cloud library and Favorites.
3. Keep selected photos in a durable, visible queue.
4. Preview whole-photo fit by default, and identify center-fill as cropping.
5. Change rotation/composition independently for each photo.
6. Confirm the cost of one physical sheet before transfer.
7. Stop on printer errors without silently repeating an irreversible print command.

## Device validation still needed

Cloud album support is provider- and version-dependent. Test against the user's Android version and Google Photos account before promising exact parity with Instagram. Do not imply that WideLight can read or modify the user's Google Photos favorite flags: app-local favorites are separate.

Sources and setup instructions are linked in the project README.
