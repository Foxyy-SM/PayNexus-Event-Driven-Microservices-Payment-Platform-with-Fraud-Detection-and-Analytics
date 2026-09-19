#!/bin/sh
set -eu

: "${KEYCLOAK_PAYMENT_CLIENT_SECRET:?KEYCLOAK_PAYMENT_CLIENT_SECRET is required}"

case "${KEYCLOAK_PAYMENT_CLIENT_SECRET}" in
  *[^A-Za-z0-9._~-]*)
    echo "KEYCLOAK_PAYMENT_CLIENT_SECRET may only contain letters, digits, ., _, ~, and -" >&2
    exit 1
    ;;
esac

mkdir -p /opt/keycloak/data/import
sed "s/\${KEYCLOAK_PAYMENT_CLIENT_SECRET}/${KEYCLOAK_PAYMENT_CLIENT_SECRET}/g" \
  /tmp/realm-source.json > /opt/keycloak/data/import/realm-export.json

exec /opt/keycloak/bin/kc.sh "$@"
