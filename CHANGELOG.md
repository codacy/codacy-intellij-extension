<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# codacy-intellij-plugin Changelog

## [Unreleased]

## [0.1.1]

### Changed

- Fixed a bug where the plugin would not properly run local analysis on saved files.
- The plugin now groups issues for the same line into a single notification, improving readability and reducing clutter in the IDE.

## [0.1.0]

### Changed

- Improved exception handling and logging for better debugging and user experience.
- Changed notifications about cli not generating result files to info level logs, to reduce noise in the IDE.
- Fixed a bug where the plugin would not properly use cached resources

## [0.0.12]

### Changed

- Migrated to IntelliJ Platform Gradle Plugin v2