#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example — fill in API keys when you have them."
fi

docker compose up -d
echo "Postgres is on :5432 and Mailpit is on :8025"
echo "API:  ./scripts/run-api.sh"
echo "Web:  cd apps/web && npm run dev"
