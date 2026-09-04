FROM mcr.microsoft.com/devcontainers/java:dev-21-jdk-bookworm

USER root

ARG ANDROID_COMMANDLINE_TOOLS_VERSION=13114758
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates unzip wget \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMANDLINE_TOOLS_VERSION}_latest.zip" -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
    && chown -R vscode:vscode "${ANDROID_HOME}" \
    && rm -rf /var/lib/apt/lists/* /tmp/cmdline-tools.zip

USER vscode