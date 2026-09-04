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

- First build ( ./gradlew build ) gave error. The issue is that jcenter() was deprecated and removed from newer Gradle versions. We replaced it with mavenCentral() which is the modern standard.

- Retried `./gradlew build --stacktrace` on 2026-09-04. Gradle 4.10.2 downloaded successfully, but configuration stopped before dependency resolution because no Android SDK was available (`SDK location not found`).

- The checked-in project still had `jcenter()` and `minSdkVersion 15`, so both were corrected: repositories now use `mavenCentral()` and the minimum SDK is API 16 (Android 4.1).

- Next step: provide an Android SDK (including platforms;android-26 and build-tools;28.0.3) to the build environment and rerun `./gradlew build`.

