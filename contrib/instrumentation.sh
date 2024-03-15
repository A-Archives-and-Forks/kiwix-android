#!/usr/bin/env bash

adb logcat -c
# shellcheck disable=SC2035
adb logcat *:E -v color &
retry=0


PACKAGE_NAME="org.kiwix.kiwixmobile"

# Function to check if the application is installed
is_app_installed() {
  adb shell pm list packages | grep -q "${PACKAGE_NAME}"
}

if is_app_installed; then
  # Clear application data to properly run the test cases.
  adb shell pm clear "${PACKAGE_NAME}"
fi

while [ $retry -le 3 ]; do
  if ./gradlew jacocoInstrumentationTestReport; then
    echo "jacocoInstrumentationTestReport succeeded" >&2
    break
  else
    adb kill-server
    adb start-server
    adb logcat -c
    # shellcheck disable=SC2035
    adb logcat *:E -v color &

    PACKAGE_NAME="org.kiwix.kiwixmobile"

    # Function to check if the application is installed
    is_app_installed() {
      adb shell pm list packages | grep -q "${PACKAGE_NAME}"
    }

    if is_app_installed; then
      # Clear application data to properly run the test cases.
      adb shell pm clear "${PACKAGE_NAME}"
    fi
    ./gradlew clean
    retry=$(( retry + 1 ))
    if [ $retry -eq 3 ]; then
      adb exec-out screencap -p >screencap.png
      exit 1
    fi
  fi
done
