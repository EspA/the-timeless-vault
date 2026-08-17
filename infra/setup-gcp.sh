#!/usr/bin/env bash
set -euo pipefail

# One-time GCP bootstrap for The Timeless Vault.
# Usage: PROJECT_ID=your-project REGION=us-east1 bash infra/setup-gcp.sh

PROJECT_ID="${PROJECT_ID:?Set PROJECT_ID}"
REGION="${REGION:-us-east1}"
REPO="${REPO:-timeless-vault}"
BUCKET="${BUCKET:-${PROJECT_ID}-vault-photos}"
INSTANCE="${INSTANCE:-timeless-vault}"

gcloud config set project "$PROJECT_ID"

gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  cloudscheduler.googleapis.com \
  cloudbuild.googleapis.com \
  storage.googleapis.com

gcloud artifacts repositories create "$REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --description="The Timeless Vault" || true

gcloud sql instances create "$INSTANCE" \
  --database-version=POSTGRES_16 \
  --tier=db-f1-micro \
  --region="$REGION" || true

gcloud sql databases create timeless_vault --instance="$INSTANCE" || true

gsutil mb -p "$PROJECT_ID" -l "$REGION" "gs://$BUCKET" || true
gsutil iam ch allUsers:objectViewer "gs://$BUCKET" || true

echo "Create secrets from .env values, for example:"
echo "  printf '%s' \"\$VALUE\" | gcloud secrets create ttv-database-url --data-file=-"
echo
echo "Then deploy with Cloud Build:"
echo "  gcloud builds submit --config infra/cloudbuild.yaml --substitutions=_CLOUDSQL_INSTANCE=${PROJECT_ID}:${REGION}:${INSTANCE},_GCS_BUCKET=${BUCKET}"
echo
echo "Create the market-scan scheduler after the Cloud Run URL exists:"
echo "  gcloud scheduler jobs create http ttv-market-scan \\"
echo "    --schedule='every 5 minutes' \\"
echo "    --uri=\"https://CLOUD_RUN_URL/internal/jobs/market-scan\" \\"
echo "    --http-method=POST \\"
echo "    --headers=X-Internal-Token=YOUR_INTERNAL_JOB_TOKEN \\"
echo "    --oidc-service-account-email=SCHEDULER_SA@${PROJECT_ID}.iam.gserviceaccount.com \\"
echo "    --location=${REGION}"
