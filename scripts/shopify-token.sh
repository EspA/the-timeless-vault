#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
EXAMPLE="$ROOT/.env.example"

if [ ! -f "$ENV_FILE" ]; then
  cp "$EXAMPLE" "$ENV_FILE"
  echo "Created $ENV_FILE from .env.example"
fi

read_env() {
  local key="$1"
  local line
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  printf '%s' "${line#*=}"
}

upsert_env() {
  local key="$1"
  local value="$2"
  local tmp
  tmp="$(mktemp)"
  if grep -qE "^${key}=" "$ENV_FILE"; then
    awk -v key="$key" -v value="$value" '
      BEGIN { updated = 0 }
      $0 ~ "^" key "=" {
        if (!updated) { print key "=" value; updated = 1 }
        next
      }
      { print }
      END { if (!updated) print key "=" value }
    ' "$ENV_FILE" > "$tmp"
  else
    cat "$ENV_FILE" > "$tmp"
    printf '\n%s=%s\n' "$key" "$value" >> "$tmp"
  fi
  mv "$tmp" "$ENV_FILE"
}

current_shop="$(read_env SHOPIFY_SHOP_DOMAIN)"
current_shop="${current_shop:-thetimelessvault.myshopify.com}"
current_id="$(read_env SHOPIFY_CLIENT_ID)"

echo "Shopify Admin token helper"
echo "The token lasts 24 hours. Re-run this script when it expires."
echo

read -r -p "Shop domain [${current_shop}]: " shop
shop="${shop:-$current_shop}"
shop="${shop#https://}"
shop="${shop%/}"
if [[ "$shop" != *.myshopify.com ]]; then
  shop="${shop}.myshopify.com"
fi

if [ -n "$current_id" ]; then
  read -r -p "Client ID [${current_id}]: " client_id
  client_id="${client_id:-$current_id}"
else
  read -r -p "Client ID: " client_id
fi

if [ -z "$client_id" ]; then
  echo "Client ID is required." >&2
  exit 1
fi

read -r -s -p "Client secret: " client_secret
echo
if [ -z "$client_secret" ]; then
  echo "Client secret is required." >&2
  exit 1
fi

echo "Requesting access token from ${shop}..."

response="$(curl -sS -X POST "https://${shop}/admin/oauth/access_token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=${client_id}" \
  -d "client_secret=${client_secret}")" || {
  echo "Token request failed." >&2
  exit 1
}

token="$(python3 -c '
import json, sys
raw = sys.stdin.read()
try:
    data = json.loads(raw)
except json.JSONDecodeError:
    print("", end="")
    sys.exit(2)
if "access_token" not in data:
    print(raw, file=sys.stderr)
    sys.exit(1)
print(data["access_token"], end="")
' <<< "$response")" || {
  echo "Shopify did not return an access token. Response:" >&2
  echo "$response" >&2
  exit 1
}

expires="$(python3 -c '
import json, sys
data = json.loads(sys.stdin.read())
print(data.get("expires_in", 86400))
' <<< "$response")"

upsert_env SHOPIFY_SHOP_DOMAIN "$shop"
upsert_env SHOPIFY_CLIENT_ID "$client_id"
upsert_env SHOPIFY_CLIENT_SECRET "$client_secret"
upsert_env SHOPIFY_ADMIN_TOKEN "$token"

hours=$((expires / 3600))
echo "Updated $ENV_FILE"
echo "SHOPIFY_SHOP_DOMAIN=$shop"
echo "SHOPIFY_ADMIN_TOKEN saved (expires in about ${hours} hours)."
echo "Restart the API so it picks up the new token."
