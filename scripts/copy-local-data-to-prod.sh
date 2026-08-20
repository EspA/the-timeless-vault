#!/usr/bin/env bash
set -euo pipefail

# One-time copy of inventory/watch/alert/log data from local Postgres to Cloud SQL.
# Does not copy app_user (prod Google login) or flyway_schema_history.
#
# Usage: PROJECT_ID=the-timeless-vault bash scripts/copy-local-data-to-prod.sh

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
PROJECT_ID="${PROJECT_ID:?Set PROJECT_ID}"
INSTANCE="${INSTANCE:-timeless-vault}"
DATABASE="${DATABASE:-timeless_vault}"
BUCKET="${BUCKET:-${PROJECT_ID}-photos}"
LOCAL_PHOTOS="${LOCAL_PHOTOS:-$ROOT/apps/api/data/photos}"

read_env() {
  local key="$1"
  local line
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  local value="${line#*=}"
  value="${value%\"}"
  value="${value#\"}"
  printf '%s' "$value"
}

DB_USER="$(read_env POSTGRES_USER)"
DB_USER="${DB_USER:-vault}"
DB_PASSWORD="$(read_env POSTGRES_PASSWORD)"
DB_NAME="$(read_env POSTGRES_DB)"
DB_NAME="${DB_NAME:-timeless_vault}"

TABLES=(
  catalog_item
  inventory_item
  photo
  channel_listing
  price_guard
  publish_job
  set_watch
  watch_rule
  market_snapshot
  market_listing
  market_scan_log
  alert_event
  listing_log
)

DUMP="$(mktemp /tmp/ttv-local-to-prod.XXXXXX.sql)"
cleanup() { rm -f "$DUMP"; }
trap cleanup EXIT

TABLE_LIST="$(printf '%s, ' "${TABLES[@]}")"
TABLE_LIST="${TABLE_LIST%, }"
DUMP_ARGS=()
for table in "${TABLES[@]}"; do
  DUMP_ARGS+=(-t "$table")
done

echo "Dumping local tables..."
{
  echo "BEGIN;"
  echo "TRUNCATE TABLE ${TABLE_LIST} RESTART IDENTITY CASCADE;"
  docker exec -e PGPASSWORD="$DB_PASSWORD" the-timeless-vault-postgres-1 \
    pg_dump -U "$DB_USER" -d "$DB_NAME" \
      --data-only --no-owner --no-privileges \
      "${DUMP_ARGS[@]}"
  echo "COMMIT;"
} > "$DUMP"

GCS_URI="gs://${BUCKET}/_admin/local-to-prod.sql"
echo "Uploading dump to ${GCS_URI}..."
gcloud storage cp "$DUMP" "$GCS_URI" --project="$PROJECT_ID"

SQL_SA="$(gcloud sql instances describe "$INSTANCE" --project="$PROJECT_ID" --format='value(serviceAccountEmailAddress)')"
gsutil iam ch "serviceAccount:${SQL_SA}:objectViewer" "gs://${BUCKET}" || true

echo "Importing into Cloud SQL..."
gcloud sql import sql "$INSTANCE" "$GCS_URI" \
  --project="$PROJECT_ID" \
  --database="$DATABASE" \
  --user="$DB_USER" \
  --quiet

if [ -d "$LOCAL_PHOTOS" ]; then
  echo "Uploading local photos that exist on disk..."
  gcloud storage rsync --recursive "$LOCAL_PHOTOS" "gs://${BUCKET}" --project="$PROJECT_ID"
else
  echo "No local photo directory at ${LOCAL_PHOTOS}; skipping GCS upload."
fi

echo "Done. Application data is in Cloud SQL."
echo "Remove the dump when finished: gcloud storage rm ${GCS_URI}"
