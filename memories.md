# Objective:
  Have a working pipeline to generate Android 4.1 apps

Chatting in the github web interface we selected as Proof-Of-Concept to backport version 1.7 of my fork of Forecastie app ( https://github.com/psanio/forecastie)

We selected this version since:

- minSdkVersion: 15 (Android 4.0.3) — closest to your target of 4.1 (API 16)
- compileSdkVersion: 26 (Android 8.0) — still modern enough to build reliably
- No AndroidX — uses legacy Support Library v26.1.0, which has better backwards compatibility
- Simpler dependencies — no Lifecycle components, no ConstraintLayout (added in v1.10+) 

# What we have done so far:

- Change minSdkVersion to 16 in build.gradle

- Create a .devcontainer/devcontainer.json file in your android-4.1-backport branch. This will give you a Codespace with:

-- Java 21 + Android SDK ( This uses a lightweight Java 21 base image.)
-- Gradle for building
-- Git & GitHub CLI pre-installed
-- VS Code extensions for Java/Gradle development

- The checked-in project uses `mavenCentral()` and `minSdkVersion 16` (Android 4.1).
- The dev container installs Android SDK platform 35, build-tools 35.0.0, and platform-tools under `/opt/android-sdk`.
- The container provides Java 21 and the repository Gradle wrapper uses Gradle 8.9. Use `./gradlew`, not a system `gradle` command.
- The post-create check in `.devcontainer/devcontainer.json` runs `java -version && ./gradlew --version && sdkmanager --list`.
- The unavailable `com.roughike:bottom-bar:1.4.0.1` dependency was removed. `MapActivity` now uses the existing Android Support Design `BottomNavigationView` for the same rain, wind, and temperature layer actions.
- Added AGP 8/Android 12 compatibility: enabled generated `BuildConfig`, declared the app `colorAccent` attribute, removed the manifest `package` attribute, and specified component `android:exported` values.
- `./gradlew assembleDebug` passed on 2026-09-04. The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`; only Java 8 source/target deprecation warnings remain under Java 21.
- On an old Android phone, OpenWeather requests reached the host but failed with an OpenSSL `sslv3 alert handshake failure`; separate runs also showed intermittent `UnknownHostException` DNS failures.
- `GenericRequestTask` now enables TLS 1.2 on HTTPS sockets while retaining normal certificate validation, trims the saved API key, and logs safe network exception details. DNS/routing failures still require fixing the phone or network configuration.


