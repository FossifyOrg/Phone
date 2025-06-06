# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.5.0] - 2025-06-06

### Added

- Backspace button on call screen dialpad

### Changed

- SIM indicators now use system-defined colors
- Search query is now preserved when switching tabs ([#94])
- Updated translations

### Fixed

- Calling from the favorites grid view now works as expected ([#357])
- Fixed phone number text direction in RTL layout ([#307])
- Fixed incorrect colors on conference call screen ([#359])

## [1.4.0] - 2025-04-01

### Added

- Added support for caller location (state/country) in call history ([#39])
- Added option to open contact details when contact photo is tapped ([#35])

### Changed

- Other minor bug fixes and improvements
- Added more translations

### Removed

- Removed storage permission requirement

## [1.3.1] - 2025-01-14

### Changed

- Other minor bug fixes and improvements
- Added more translations

### Fixed

- Fixed an issue where call history wasn't refreshing ([#183])
- Fixed index letter sorting in the contacts list ([#186])
- Fixed dialpad search for some characters ([#139])
- Fixed an issue where call hangs up immediately after back press ([#237])

## [1.3.0] - 2024-10-30

### Changed

- Replaced checkboxes with switches
- Other minor bug fixes and improvements
- Added more translations

### Fixed

- Fixed issue with contacts not displaying on Android 14 and above

### Removed

- Removed support for Android 7 and older versions

## [1.2.0] - 2024-05-08

### Added

- Grouped call history entries by date ([#96])
- Added an option to format phone numbers in the call log ([#15])

### Changed

- Missed call notifications are now automatically dismissed when you view your call history ([#88])
- Moved some actions back into the popup menu to reduce visual clutter
- Updated menu design for better UI/UX
- Disabled call action buttons after a call ends for better UI/UX ([#181])
- Always show the date in the call details dialog ([#133])
- Updated call direction icons and colors in the call history for better clarity ([#81])
- Restructured the in-call UI to be more responsive to different screen sizes ([#147])
- Added some translations

### Fixed

- Fixed an issue where call history wasn't refreshing ([#146])
- Fixed a problem where search items would disappear ([#98])
- Fixed UI freeze that happened when loading call history
- Fixed a bug that caused search not to find older call logs ([#97])
- Fixed a crash that occurred when using the dialpad quick callback feature

## [1.1.1] - 2024-03-21

### Added

- Added quick dial-back feature ([#60])
- Added placeholder avatar for unknown numbers and contacts without photo
- Added a progress indicator to indicate call history retrieval
- Added bottom padding in lists to allow scrolling above the floating action button

### Changed

- The hang-up button is now always visible in the call UI ([#9])
- Enhanced the size of caller avatar and buttons in the call UI ([#118])
- Reorganized dialpad preferences into their own dedicated section ([#116])
- Added some translations

### Removed

- Removed call history limit ([#125])

## [1.1.0] - 2024-03-21

### Added

- Added quick dial-back feature ([#60])
- Added placeholder avatar for unknown numbers and contacts without photo
- Added a progress indicator to indicate call history retrieval
- Added bottom padding in lists to allow scrolling above the floating action button

### Changed

- The hang-up button is now always visible in the call UI ([#9])
- Enhanced the size of caller avatar and buttons in the call UI ([#118])
- Reorganized dialpad preferences into their own dedicated section ([#116])
- Added some translations

### Removed

- Removed call history limit ([#125])

## [1.0.0] - 2024-01-15

### Added

- Initial release

[Unreleased]: https://github.com/FossifyOrg/Phone/compare/1.5.0...HEAD
[1.5.0]: https://github.com/FossifyOrg/Phone/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/FossifyOrg/Phone/compare/1.3.1...1.4.0
[1.3.1]: https://github.com/FossifyOrg/Phone/compare/1.3.0...1.3.1
[1.3.0]: https://github.com/FossifyOrg/Phone/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/FossifyOrg/Phone/compare/1.1.1...1.2.0
[1.1.1]: https://github.com/FossifyOrg/Phone/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/FossifyOrg/Phone/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/FossifyOrg/Phone/releases/tag/1.0.0

[#9]: https://github.com/FossifyOrg/Phone/issues/9
[#15]: https://github.com/FossifyOrg/Contacts/issues/15
[#35]: https://github.com/FossifyOrg/Phone/issues/35
[#39]: https://github.com/FossifyOrg/Phone/issues/39
[#60]: https://github.com/FossifyOrg/Phone/issues/60
[#81]: https://github.com/FossifyOrg/Phone/issues/81
[#88]: https://github.com/FossifyOrg/Phone/issues/88
[#94]: https://github.com/FossifyOrg/Phone/issues/94
[#96]: https://github.com/FossifyOrg/Phone/issues/96
[#97]: https://github.com/FossifyOrg/Phone/issues/97
[#98]: https://github.com/FossifyOrg/Phone/issues/98
[#116]: https://github.com/FossifyOrg/Phone/issues/116
[#118]: https://github.com/FossifyOrg/Phone/issues/118
[#125]: https://github.com/FossifyOrg/Phone/issues/125
[#133]: https://github.com/FossifyOrg/Phone/issues/133
[#139]: https://github.com/FossifyOrg/Phone/issues/139
[#146]: https://github.com/FossifyOrg/Phone/issues/146
[#147]: https://github.com/FossifyOrg/Phone/issues/147
[#181]: https://github.com/FossifyOrg/Phone/issues/181
[#183]: https://github.com/FossifyOrg/Phone/issues/183
[#186]: https://github.com/FossifyOrg/Phone/issues/186
[#237]: https://github.com/FossifyOrg/Phone/issues/237
[#307]: https://github.com/FossifyOrg/Phone/issues/307
[#347]: https://github.com/FossifyOrg/Phone/issues/347
[#357]: https://github.com/FossifyOrg/Phone/issues/357
[#359]: https://github.com/FossifyOrg/Phone/issues/359
