#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

load_env() {
  local key value
  while IFS= read -r line || [ -n "$line" ]; do
    [[ -z "${line// }" || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ "$value" =~ ^\"(.*)\"$ ]]; then
      value="${BASH_REMATCH[1]}"
    elif [[ "$value" =~ ^\'(.*)\'$ ]]; then
      value="${BASH_REMATCH[1]}"
    fi
    export "$key=$value"
  done < "$1"
}

if [ -f .env ]; then
  load_env .env
fi

DRY_RUN=true
BASE_URL="${BASE_URL:-http://localhost:8080}"

for arg in "$@"; do
  case "$arg" in
    --apply) DRY_RUN=false ;;
    --dry-run) DRY_RUN=true ;;
    --prod) BASE_URL="https://admin.thetimelessvault.com" ;;
    --base=*) BASE_URL="${arg#--base=}" ;;
    *)
      echo "Usage: $0 [--dry-run|--apply] [--prod] [--base=URL]"
      exit 1
      ;;
  esac
done

if [ -z "${INTERNAL_JOB_TOKEN:-}" ]; then
  echo "INTERNAL_JOB_TOKEN is missing. Set it in .env or the environment."
  exit 1
fi

if [ "$DRY_RUN" = "true" ]; then
  echo "Previewing BrickLink listings from ${BASE_URL} (no inventory writes)."
else
  echo "LINKING BrickLink listings into ${BASE_URL}."
  echo "This will attach active BrickLink lots to in-stock items. It will not create items or publish new lots."
fi

curl -sS -X POST "${BASE_URL}/internal/jobs/bricklink-listing-import?dryRun=${DRY_RUN}" \
  -H "X-Internal-Token: ${INTERNAL_JOB_TOKEN}" \
  -H "Accept: application/json" \
  | python3 -m json.tool
