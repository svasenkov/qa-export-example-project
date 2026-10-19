#!/bin/sh
# Source this file to prepare a local Sonar analysis environment:
#   . tools/quality/sonar-env.sh                                # self-hosted (default)
#   SONAR_PROVIDER=sonarcloud . tools/quality/sonar-env.sh      # sonarcloud.io
#
# Providers:
#   sonarqube  — self-hosted SonarQube (default https://sonar.qa.guru; override
#                SONAR_HOST_URL before sourcing for any other self-hosted host).
#   sonarcloud — https://sonarcloud.io; additionally requires SONAR_ORGANIZATION
#                (your org key) and usually SONAR_PROJECT_KEY overrides, since
#                the default project keys belong to the course organization.
#
# Exports SONAR_HOST_URL, SONAR_APPROVED_URL, SONAR_PROVIDER and SONAR_TOKEN
# without printing the token. Tokens live outside the repository in
# ${SONAR_TOKEN_FILE:-~/.config/sonar/<host>.token} (chmod 600).
# Students: generate a token at $SONAR_HOST_URL → My Account → Security,
# save it to the file, then source this script.
# Never put tokens into .env files inside the repository, commands you paste
# publicly, or CI logs — GitHub Actions uses the SONAR_TOKEN repo secret.

SONAR_PROVIDER=${SONAR_PROVIDER:-sonarqube}
case "$SONAR_PROVIDER" in
    sonarqube)
        SONAR_HOST_URL=${SONAR_HOST_URL:-https://sonar.qa.guru}
        SONAR_TOKEN_FILE=${SONAR_TOKEN_FILE:-"$HOME/.config/sonar/sonar.qa.guru.token"}
        ;;
    sonarcloud)
        SONAR_HOST_URL=${SONAR_HOST_URL:-https://sonarcloud.io}
        SONAR_TOKEN_FILE=${SONAR_TOKEN_FILE:-"$HOME/.config/sonar/sonarcloud.token"}
        if [ -z "${SONAR_ORGANIZATION:-}" ]; then
            echo "BLOCKED: sonarcloud requires SONAR_ORGANIZATION (your organization key)" >&2
            return 2 2>/dev/null || exit 2
        fi
        export SONAR_ORGANIZATION
        ;;
    *)
        echo "BLOCKED: unknown SONAR_PROVIDER '$SONAR_PROVIDER' (sonarqube|sonarcloud)" >&2
        return 2 2>/dev/null || exit 2
        ;;
esac
SONAR_APPROVED_URL=$SONAR_HOST_URL
export SONAR_HOST_URL SONAR_APPROVED_URL SONAR_PROVIDER

if [ ! -f "$SONAR_TOKEN_FILE" ]; then
    echo "BLOCKED: Sonar token file is missing: $SONAR_TOKEN_FILE" >&2
    echo "Get a token at $SONAR_HOST_URL (My Account -> Security) and save it there." >&2
    return 2 2>/dev/null || exit 2
fi
SONAR_TOKEN=$(cat "$SONAR_TOKEN_FILE")
export SONAR_TOKEN
echo "Sonar env ready for $SONAR_HOST_URL (provider $SONAR_PROVIDER, token from $SONAR_TOKEN_FILE)"
