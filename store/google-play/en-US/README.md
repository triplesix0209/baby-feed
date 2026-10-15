# Baby Feed — Google Play listing pack

All public copy and artwork text in this pack is English (en-US). Prepared from the current native Android implementation on October 15, 2026.

## Text fields

Paste `title.txt`, `short-description.txt`, and `full-description.txt` into the corresponding Main store listing fields. `release-notes.txt` contains the first-release notes; paste its content inside the en-US locale section in the release editor.

The title uses a descriptive suffix for discovery; the installed application name remains Baby Feed. No app source branding was changed.

## Graphics

- `graphics/app-icon.png`: 512 × 512, RGBA PNG, based on the existing launcher vector. Upload under App icon.
- `graphics/feature-graphic.png`: 1024 × 500, RGB PNG. Upload under Feature graphic.
- `phone/`: English promotional screenshots, 1080 × 1920, RGB PNG. Upload in numerical order under Phone screenshots.
- `source/`: original device captures, retained as provenance. These are not the upload files.
- `editable/`: editable SVG artwork.
- `alt-text.json`: accessible descriptions for the graphics and screenshots.
- `preview.jpg`: overview for review, not a store upload.

Screenshots use fictitious records in the disposable application `triplesix.babyfeed.storepreview`, with the same native screens as the current app. Sample amounts and intervals are examples, not feeding guidance. The family app's records and settings are not used in the artwork.

To regenerate SVG-based artwork and framed screenshots, run `node store/google-play/render-assets.cjs`. The script uses the bundled Sharp package; it does not recreate app UI. Device captures are rendered by Android.

## Verification

All six phone images were visually reviewed. `validation.json` records dimensions, channels, file sizes and text lengths: title 25 characters, short description 67, full description 2355. Android status and navigation bars are cropped from the original captures; the app UI is otherwise unchanged.

The report's three summary cards now use equal heights, and their headings resize to fit a single line. Daily units are localized in English and Vietnamese. The normal debug build and Android lint passed after these corrections.

## Before submitting the release

This is a store listing asset pack, not a signed production app bundle.

- Create a signed release AAB and configure Play App Signing. Keep the upload key outside Git.
- Register the Play app signing certificate SHA-1 for package `triplesix.babyfeed` in the Android OAuth client. Installing from Play uses that certificate, which can differ from the upload certificate.
- Complete Google OAuth production verification as applicable. The implementation requests the restricted `https://www.googleapis.com/auth/drive` scope; Google Play developer account approval does not approve this OAuth scope.
- Supply the real support email and a publicly hosted privacy policy URL. Do not submit placeholder contact details.
- Complete Data safety, Health apps, target audience, content rating, app access and any alarm/full-screen permission declarations from the final build and actual data practices. The app serves parents and caregivers; do not select a child audience solely because its records concern a baby.
- Confirm the currently applicable target API requirement and testing/access requirements in Play Console before uploading. The source currently targets API 35 and has versionCode 12 / versionName 1.0.
- Test the installed Play build's alarms, notification permissions, shared Drive access and offline recovery on real devices.

## Official references

- [Preview assets and specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Store listing text fields](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
- [Google Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)

The prepared uploads follow the current documented asset dimensions. Console declarations and release eligibility must be completed for the production build.
