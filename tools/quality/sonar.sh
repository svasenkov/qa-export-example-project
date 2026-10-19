#!/bin/sh
# Explicit local preparation -> scanner -> final server gate. No auto-install.
set -eu
if [ -z "${SONAR_HOST_URL:-}" ] || [ "${SONAR_APPROVED_URL:-}" != "$SONAR_HOST_URL" ]; then
  echo 'BLOCKED: an exact Sonar URL must be explicitly authorized' >&2
  exit 2
fi
if [ "$#" -ne 4 ]; then
  echo 'usage: sh tools/quality/sonar.sh go|frontend MODULE MANIFEST REPORT' >&2
  exit 2
fi
kind=$1
module=$2
manifest=$3
report=$4
case "$kind" in go|frontend) ;; *) echo 'Unsupported scanner domain' >&2; exit 2;; esac
python3 "$(dirname "$0")/coverage.py" check "$kind" "$module" "$manifest" --report "$report"
if ! command -v sonar-scanner >/dev/null 2>&1; then
  echo 'BLOCKED: provision a pinned SonarScanner CLI and record its version' >&2
  exit 2
fi
sonar-scanner --version
absolute_report=$(python3 -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).resolve())' "$report")
cd "$module"
# Provider/fork overrides: SONAR_ORGANIZATION is required on sonarcloud.io;
# SONAR_PROJECT_KEY overrides the module default when the key is taken.
set -- -Dsonar.qualitygate.wait=true
[ -n "${SONAR_ORGANIZATION:-}" ] && set -- "$@" "-Dsonar.organization=$SONAR_ORGANIZATION"
[ -n "${SONAR_PROJECT_KEY:-}" ] && set -- "$@" "-Dsonar.projectKey=$SONAR_PROJECT_KEY"
case "$kind" in
  go) sonar-scanner "$@" "-Dsonar.go.coverage.reportPaths=$absolute_report" ;;
  frontend) sonar-scanner "$@" "-Dsonar.javascript.lcov.reportPaths=$(dirname "$absolute_report")/lcov.info" ;;
esac
