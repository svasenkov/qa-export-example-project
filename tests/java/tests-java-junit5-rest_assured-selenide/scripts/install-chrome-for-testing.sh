#!/usr/bin/env bash
# Pinned Chrome for Testing + chromedriver.
# Version comes from src/test/resources/chrome-for-testing.properties (override: CHROME_FOR_TESTING_VERSION).
# Layout matches helpers.LocalChromePin — no Node, no @puppeteer/browsers.
#
#   install-chrome-for-testing.sh                 install browser + driver
#   install-chrome-for-testing.sh --verify        check the installed build and print versions
#   install-chrome-for-testing.sh --print-version print the pinned version and exit
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIN_FILE="${MODULE_DIR}/src/test/resources/chrome-for-testing.properties"

MODE=install
case "${1:-}" in
  --print-version) MODE=print-version ;;
  --verify) MODE=verify ;;
  "") ;;
  *)
    echo "Unknown argument: $1 (expected --verify or --print-version)" >&2
    exit 2
    ;;
esac

read_pinned_version() {
  if [ ! -f "$PIN_FILE" ]; then
    echo "Pin file not found: ${PIN_FILE}" >&2
    exit 1
  fi
  local value
  value="$(grep -E '^version=' "$PIN_FILE" | tail -n 1 | cut -d= -f2- | tr -d '[:space:]')"
  if [ -z "$value" ]; then
    echo "No version= entry in ${PIN_FILE}" >&2
    exit 1
  fi
  printf '%s' "$value"
}

VERSION="${CHROME_FOR_TESTING_VERSION:-$(read_pinned_version)}"

if [ "$MODE" = print-version ]; then
  echo "$VERSION"
  exit 0
fi

CFT_PATH="${CHROME_FOR_TESTING_PATH:-${HOME}/.local/share/chrome-for-testing}"

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64|Linux-amd64|Linux-aarch64|Linux-arm64)
    PLATFORM=linux
    CFT_ARCH=linux64
    CHROME_ZIP=chrome-linux64
    DRIVER_ZIP=chromedriver-linux64
    CHROME_BIN="${CFT_PATH}/chrome/linux-${VERSION}/chrome-linux64/chrome"
    DRIVER_BIN="${CFT_PATH}/chromedriver/linux-${VERSION}/chromedriver-linux64/chromedriver"
    ;;
  Darwin-arm64)
    PLATFORM=mac_arm
    CFT_ARCH=mac-arm64
    CHROME_ZIP=chrome-mac-arm64
    DRIVER_ZIP=chromedriver-mac-arm64
    CHROME_BIN="${CFT_PATH}/chrome/mac_arm-${VERSION}/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
    DRIVER_BIN="${CFT_PATH}/chromedriver/mac_arm-${VERSION}/chromedriver-mac-arm64/chromedriver"
    ;;
  Darwin-x86_64)
    PLATFORM=mac
    CFT_ARCH=mac-x64
    CHROME_ZIP=chrome-mac-x64
    DRIVER_ZIP=chromedriver-mac-x64
    CHROME_BIN="${CFT_PATH}/chrome/mac-${VERSION}/chrome-mac-x64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
    DRIVER_BIN="${CFT_PATH}/chromedriver/mac-${VERSION}/chromedriver-mac-x64/chromedriver"
    ;;
  *)
    echo "Unsupported OS/arch: $(uname -s)-$(uname -m)" >&2
    exit 1
    ;;
esac

verify() {
  local incomplete=0
  for bin in "$CHROME_BIN" "$DRIVER_BIN"; do
    if [ ! -x "$bin" ]; then
      echo "Not installed: ${bin}" >&2
      incomplete=1
    fi
  done
  if [ "$incomplete" -ne 0 ]; then
    echo "Chrome for Testing ${VERSION} is missing or incomplete — run ${BASH_SOURCE[0]}" >&2
    exit 1
  fi
  "$CHROME_BIN" --version
  "$DRIVER_BIN" --version
}

if [ "$MODE" = verify ]; then
  verify
  exit 0
fi

BASE_URL="https://storage.googleapis.com/chrome-for-testing-public/${VERSION}/${CFT_ARCH}"

install_archive() {
  local kind="$1"
  local zip_name="$2"
  local dest="${CFT_PATH}/${kind}/${PLATFORM}-${VERSION}"
  local url="${BASE_URL}/${zip_name}.zip"
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  curl -fsSL "$url" -o "${tmp}/${zip_name}.zip"
  rm -rf "$dest"
  mkdir -p "$dest"
  unzip -q "${tmp}/${zip_name}.zip" -d "$dest"
}

install_archive chrome "$CHROME_ZIP"
install_archive chromedriver "$DRIVER_ZIP"

chmod +x "$CHROME_BIN" "$DRIVER_BIN"
echo "Chrome for Testing ${VERSION} → ${CFT_PATH} (${PLATFORM})"
verify
