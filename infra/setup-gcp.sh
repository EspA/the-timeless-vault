#!/usr/bin/env bash
set -euo pipefail

# One-time GCP bootstrap for The Timeless Vault.
# Usage: PROJECT_ID=the-timeless-vault REGION=us-east1 bash infra/setup-gcp.sh

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"

PROJECT_ID="${PROJECT_ID:?Set PROJECT_ID}"
REGION="${REGION:-us-east1}"
REPO="${REPO:-timeless-vault}"
BUCKET="${BUCKET:-${PROJECT_ID}-photos}"
INSTANCE="${INSTANCE:-timeless-vault}"
DATABASE="${DATABASE:-timeless_vault}"

gcloud config set project "$PROJECT_ID"

gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  cloudscheduler.googleapis.com \
  cloudbuild.googleapis.com \
  storage.googleapis.com \
  iam.googleapis.com

gcloud artifacts repositories describe "$REPO" --location="$REGION" >/dev/null 2>&1 || \
  gcloud artifacts repositories create "$REPO" \
    --repository-format=docker \
    --location="$REGION" \
    --description="The Timeless Vault"

if ! gcloud sql instances describe "$INSTANCE" >/dev/null 2>&1; then
  echo "Creating Cloud SQL instance ${INSTANCE} (this can take several minutes)..."
  gcloud sql instances create "$INSTANCE" \
    --database-version=POSTGRES_16 \
    --tier=db-f1-micro \
    --edition=ENTERPRISE \
    --region="$REGION" \
    --storage-size=10GB \
    --availability-type=ZONAL \
    --quiet
fi

gcloud sql databases describe "$DATABASE" --instance="$INSTANCE" >/dev/null 2>&1 || \
  gcloud sql databases create "$DATABASE" --instance="$INSTANCE"

if gsutil ls -b "gs://${BUCKET}" >/dev/null 2>&1; then
  echo "Bucket gs://${BUCKET} already exists."
else
  gcloud storage buckets create "gs://${BUCKET}" --project="$PROJECT_ID" --location="$REGION"
fi
gsutil iam ch allUsers:objectViewer "gs://${BUCKET}" || true

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
COMPUTE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
CLOUDBUILD_SA="${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com"

grant_project_role() {
  local member="$1"
  local role="$2"
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${member}" \
    --role="$role" \
    --condition=None \
    --quiet >/dev/null
}

grant_project_role "$COMPUTE_SA" "roles/cloudsql.client"
grant_project_role "$COMPUTE_SA" "roles/secretmanager.secretAccessor"
grant_project_role "$COMPUTE_SA" "roles/storage.objectAdmin"
grant_project_role "$CLOUDBUILD_SA" "roles/run.admin"
grant_project_role "$CLOUDBUILD_SA" "roles/artifactregistry.writer"
grant_project_role "$CLOUDBUILD_SA" "roles/iam.serviceAccountUser"
grant_project_role "$CLOUDBUILD_SA" "roles/secretmanager.secretAccessor"

gcloud iam service-accounts add-iam-policy-binding "$COMPUTE_SA" \
  --member="serviceAccount:${CLOUDBUILD_SA}" \
  --role="roles/iam.serviceAccountUser" \
  --quiet >/dev/null || true

read_env() {
  local key="$1"
  local line
  if [ ! -f "$ENV_FILE" ]; then
    printf '%s' ""
    return
  fi
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  local value="${line#*=}"
  value="${value%\"}"
  value="${value#\"}"
  printf '%s' "$value"
}

upsert_secret() {
  local name="$1"
  local value="$2"
  if [ -z "$value" ]; then
    value=" "
  fi
  if gcloud secrets describe "$name" >/dev/null 2>&1; then
    printf '%s' "$value" | gcloud secrets versions add "$name" --data-file=- >/dev/null
    echo "Updated secret ${name}."
  else
    printf '%s' "$value" | gcloud secrets create "$name" --data-file=- >/dev/null
    echo "Created secret ${name}."
  fi
}

DB_USER="$(read_env POSTGRES_USER)"
DB_USER="${DB_USER:-vault}"
DB_PASSWORD="$(read_env POSTGRES_PASSWORD)"
if [ -z "$DB_PASSWORD" ]; then
  echo "POSTGRES_PASSWORD is required in ${ENV_FILE}" >&2
  exit 1
fi

if gcloud sql users list --instance="$INSTANCE" --format='value(name)' | grep -qx "$DB_USER"; then
  gcloud sql users set-password "$DB_USER" --instance="$INSTANCE" --password="$DB_PASSWORD" --quiet
else
  gcloud sql users create "$DB_USER" --instance="$INSTANCE" --password="$DB_PASSWORD" --quiet
fi

DATABASE_URL="jdbc:postgresql:///${DATABASE}?cloudSqlInstance=${PROJECT_ID}:${REGION}:${INSTANCE}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"

MAIL_HOST="$(read_env MAIL_HOST)"
MAIL_PORT="$(read_env MAIL_PORT)"
MAIL_USERNAME="$(read_env MAIL_USERNAME)"
MAIL_PASSWORD="$(read_env MAIL_PASSWORD)"
MAIL_FROM="$(read_env MAIL_FROM)"
MAIL_SMTP_AUTH="$(read_env MAIL_SMTP_AUTH)"
MAIL_SMTP_STARTTLS="$(read_env MAIL_SMTP_STARTTLS)"
if [ -z "$MAIL_SMTP_AUTH" ] && [ -n "$MAIL_USERNAME" ]; then
  MAIL_SMTP_AUTH="true"
fi
if [ -z "$MAIL_SMTP_STARTTLS" ] && [ -n "$MAIL_USERNAME" ]; then
  MAIL_SMTP_STARTTLS="true"
fi
if [ -z "$MAIL_PORT" ] && [ -n "$MAIL_USERNAME" ]; then
  MAIL_PORT="587"
fi
if [ -z "$MAIL_PORT" ]; then
  MAIL_PORT="587"
fi
if [ -z "$MAIL_SMTP_AUTH" ]; then
  MAIL_SMTP_AUTH="false"
fi
if [ -z "$MAIL_SMTP_STARTTLS" ]; then
  MAIL_SMTP_STARTTLS="false"
fi

upsert_secret ttv-database-url "$DATABASE_URL"
upsert_secret ttv-db-user "$DB_USER"
upsert_secret ttv-db-password "$DB_PASSWORD"
upsert_secret ttv-google-client-id "$(read_env GOOGLE_CLIENT_ID)"
upsert_secret ttv-google-client-secret "$(read_env GOOGLE_CLIENT_SECRET)"
upsert_secret ttv-allowed-emails "$(read_env AUTH_ALLOWED_EMAILS)"
upsert_secret ttv-brickeconomy-key "$(read_env BRICKECONOMY_API_KEY)"
upsert_secret ttv-shopify-domain "$(read_env SHOPIFY_SHOP_DOMAIN)"
upsert_secret ttv-shopify-client-id "$(read_env SHOPIFY_CLIENT_ID)"
upsert_secret ttv-shopify-client-secret "$(read_env SHOPIFY_CLIENT_SECRET)"
upsert_secret ttv-shopify-token "$(read_env SHOPIFY_ADMIN_TOKEN)"
upsert_secret ttv-bl-consumer-key "$(read_env BRICKLINK_CONSUMER_KEY)"
upsert_secret ttv-bl-consumer-secret "$(read_env BRICKLINK_CONSUMER_SECRET)"
upsert_secret ttv-bl-token "$(read_env BRICKLINK_TOKEN)"
upsert_secret ttv-bl-token-secret "$(read_env BRICKLINK_TOKEN_SECRET)"
upsert_secret ttv-ebay-client-id "$(read_env EBAY_CLIENT_ID)"
upsert_secret ttv-ebay-client-secret "$(read_env EBAY_CLIENT_SECRET)"
upsert_secret ttv-ebay-ru-name "$(read_env EBAY_RU_NAME)"
upsert_secret ttv-ebay-refresh "$(read_env EBAY_REFRESH_TOKEN)"
upsert_secret ttv-ebay-location "$(read_env EBAY_MERCHANT_LOCATION_KEY)"
upsert_secret ttv-ebay-fulfillment "$(read_env EBAY_FULFILLMENT_POLICY_ID)"
upsert_secret ttv-ebay-payment "$(read_env EBAY_PAYMENT_POLICY_ID)"
upsert_secret ttv-ebay-return "$(read_env EBAY_RETURN_POLICY_ID)"
upsert_secret ttv-ebay-verification-token "$(read_env EBAY_VERIFICATION_TOKEN)"
upsert_secret ttv-ebay-deletion-endpoint "$(read_env EBAY_ACCOUNT_DELETION_ENDPOINT_URL)"
upsert_secret ttv-internal-token "$(read_env INTERNAL_JOB_TOKEN)"
upsert_secret ttv-alert-email "$(read_env ALERT_TO_EMAIL)"
upsert_secret ttv-mail-host "$MAIL_HOST"
upsert_secret ttv-mail-port "$MAIL_PORT"
upsert_secret ttv-mail-user "$MAIL_USERNAME"
upsert_secret ttv-mail-password "$MAIL_PASSWORD"
upsert_secret ttv-mail-from "$MAIL_FROM"
upsert_secret ttv-mail-smtp-auth "$MAIL_SMTP_AUTH"
upsert_secret ttv-mail-smtp-starttls "$MAIL_SMTP_STARTTLS"

JOB_TOKEN="$(read_env INTERNAL_JOB_TOKEN)"
SERVICE_URL="$(gcloud run services describe timeless-vault --region="$REGION" --format='value(status.url)' 2>/dev/null || true)"
if [ -n "$SERVICE_URL" ] && [ -n "$JOB_TOKEN" ] && [ "$JOB_TOKEN" != "change-me-internal-job-token" ]; then
  if gcloud scheduler jobs describe ttv-market-scan --location="$REGION" >/dev/null 2>&1; then
    gcloud scheduler jobs update http ttv-market-scan \
      --location="$REGION" \
      --uri="${SERVICE_URL}/internal/jobs/market-scan" \
      --http-method=POST \
      --update-headers="X-Internal-Token=${JOB_TOKEN}" \
      --attempt-deadline=320s \
      --quiet
    echo "Updated Cloud Scheduler job ttv-market-scan."
  else
    gcloud scheduler jobs create http ttv-market-scan \
      --location="$REGION" \
      --schedule="every 5 minutes" \
      --uri="${SERVICE_URL}/internal/jobs/market-scan" \
      --http-method=POST \
      --headers="X-Internal-Token=${JOB_TOKEN}" \
      --attempt-deadline=320s \
      --quiet
    echo "Created Cloud Scheduler job ttv-market-scan."
  fi
  if gcloud scheduler jobs describe ttv-sales-sync --location="$REGION" >/dev/null 2>&1; then
    gcloud scheduler jobs update http ttv-sales-sync \
      --location="$REGION" \
      --uri="${SERVICE_URL}/internal/jobs/sales-sync" \
      --http-method=POST \
      --update-headers="X-Internal-Token=${JOB_TOKEN}" \
      --attempt-deadline=320s \
      --quiet
    echo "Updated Cloud Scheduler job ttv-sales-sync."
  else
    gcloud scheduler jobs create http ttv-sales-sync \
      --location="$REGION" \
      --schedule="every 5 minutes" \
      --uri="${SERVICE_URL}/internal/jobs/sales-sync" \
      --http-method=POST \
      --headers="X-Internal-Token=${JOB_TOKEN}" \
      --attempt-deadline=320s \
      --quiet
    echo "Created Cloud Scheduler job ttv-sales-sync."
  fi
elif [ -z "$SERVICE_URL" ]; then
  echo "Cloud Run service not found yet; create ttv-market-scan after the first deploy."
else
  echo "Set INTERNAL_JOB_TOKEN in .env before creating/updating ttv-market-scan."
fi

echo
echo "Bootstrap finished."
echo "Deploy with:"
echo "  gcloud builds submit --config infra/cloudbuild.yaml --substitutions=_CLOUDSQL_INSTANCE=${PROJECT_ID}:${REGION}:${INSTANCE},_GCS_BUCKET=${BUCKET}"
echo
echo "After the first deploy, set the Cloud Run URL as APP_BASE_URL / APP_FRONTEND_ORIGIN,"
echo "and add this Google OAuth redirect URI:"
echo "  https://CLOUD_RUN_URL/login/oauth2/code/google"
