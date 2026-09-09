#!/usr/bin/env bash
set -euo pipefail

# Delete unused market_listing rows in small batches.
# Keeps the latest snapshot per set/platform/condition plus the last 7 days.
#
# Examples:
#   bash scripts/purge-stale-market-listings.sh --prod --dry-run
#   bash scripts/purge-stale-market-listings.sh --prod
#   bash scripts/purge-stale-market-listings.sh --prod --loops=10
#   bash scripts/purge-stale-market-listings.sh --prod --until-done --purge-empty-snapshots

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROJECT_ID="${PROJECT_ID:-the-timeless-vault}"
INSTANCE="${CLOUDSQL_INSTANCE:-${PROJECT_ID}:us-east1:timeless-vault}"
DATABASE="${POSTGRES_DB:-timeless_vault}"

ARGS=()
USE_PROD=false
for arg in "$@"; do
  case "$arg" in
    --prod) USE_PROD=true ;;
    *) ARGS+=("$arg") ;;
  esac
done

if [ "$USE_PROD" = true ]; then
  export POSTGRES_USER="${POSTGRES_USER:-$(gcloud secrets versions access latest --secret=ttv-db-user --project="$PROJECT_ID")}"
  export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(gcloud secrets versions access latest --secret=ttv-db-password --project="$PROJECT_ID")}"
  export POSTGRES_DB="$DATABASE"
  python3 "$ROOT/scripts/purge-stale-market-listings.py" \
    --prod \
    --instance="$INSTANCE" \
    --database="$DATABASE" \
    "${ARGS[@]}"
else
  if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
  fi
  python3 "$ROOT/scripts/purge-stale-market-listings.py" "${ARGS[@]}"
fi
