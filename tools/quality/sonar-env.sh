#!/bin/sh
# Source this file to prepare a local Sonar analysis environment:
#   . tools/quality/sonar-env.sh
#
# Exports SONAR_HOST_URL, SONAR_APPROVED_URL and SONAR_TOKEN without printing
# the token. The token itself lives outside the repository in
# ${SONAR_TOKEN_FILE:-~/.config/sonar/sonar.qa.guru.token} (chmod 600).
# Students: create a token at https://sonar.qa.guru → My Account → Security →
# Generate Token, save it to that file, then source this script.
# Never put the token into .env files inside the repository, commands you paste
# publicly, or CI logs — GitHub Actions uses the SONAR_TOKEN repo secret.

SONAR_HOST_URL=https://sonar.qa.guru
SONAR_APPROVED_URL=https://sonar.qa.guru
SONAR_TOKEN_FILE=${SONAR_TOKEN_FILE:-"$HOME/.config/sonar/sonar.qa.guru.token"}

if [ ! -f "$SONAR_TOKEN_FILE" ]; then
    echo "BLOCKED: Sonar token file is missing: $SONAR_TOKEN_FILE" >&2
    echo "Get a token at $SONAR_HOST_URL (My Account -> Security) and save it there." >&2
    return 2 2>/dev/null || exit 2
fi
SONAR_TOKEN=$(cat "$SONAR_TOKEN_FILE")
export SONAR_HOST_URL SONAR_APPROVED_URL SONAR_TOKEN
echo "Sonar env ready for $SONAR_HOST_URL (token from $SONAR_TOKEN_FILE)"
