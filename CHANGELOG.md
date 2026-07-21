# Changelog

## 0.0.9 - 2026-07-XX

- add Canonicalize Urls feature & preference setting
  - when enabled, resolves share links from Reddit into canonical urls and strips tracking querystring paramters


## 0.0.8 - 2026-07-13

- Maintenance/Modernization updates
  - Used Signing Key for APK release
    - This requires uninstallation of the previous unsigned Espial Share releases to install
    - Espial Server Url and ApiKey (if used) will need to be re-set
  - Migrate to AGP 9, Gradle 9.6.1, compileSdk/targetSdk 37, JavaVersion 17, Update Android Studio project settings & gradle daemon config
  - Enable edge-to-edge display, fix view layout
  - Update versionCode, dependencies, fix warnings, minify & shrink resources

## 0.0.7 - 2024-02-04
- Update dependencies

## 0.0.6 - 2023-12-01
- Update dependencies and allow http Espial servers

## 0.0.4 - 2022-05-29
- Add "to read" preference, improve bookmark parsing

## 0.0.3 - 2022-05-28
- Implement ReadLater

## release-0.0.2 - 2021-09-22
- Add note from selection

## release-0.0.1 - 2021-09-19
- Initial release: SettingsActivity, AddActivity, Chrome share intent handling, parse Chrome selections
