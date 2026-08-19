# The Timeless Vault

Internal listing and market-monitor app for [thetimelessvault.com](https://thetimelessvault.com).

The local PostgreSQL database is the source of truth. Look up a LEGO set on BrickEconomy, save it with photos, then publish the same listing to Shopify, BrickLink, and eBay. A scheduled job watches those catalog items and emails you when a new listing appears or your price drifts from the market average.

## Stack

- Java 21 / Spring Boot 3 / Flyway / PostgreSQL
- Vue 3 + Vite
- Docker Compose for local Postgres + Mailpit
- Cloud Run + Cloud SQL + GCS + Secret Manager + Cloud Scheduler in production

## Local setup

```bash
cp .env.example .env
docker compose up -d
```

Requires Java 21 (Homebrew: `brew install openjdk@21`).

Start the API (from `apps/api`):

```bash
export $(grep -v '^#' ../../.env | xargs)
./mvnw spring-boot:run
```

Or without the wrapper:

```bash
mvn spring-boot:run
```

Start the UI (from `apps/web`):

```bash
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

`APP_SECURITY_DEV_BYPASS=true` signs you in as the first email in `AUTH_ALLOWED_EMAILS` so you can work without Google OAuth locally. Set it to `false` and fill `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` for real SSO.

Mailpit UI: [http://localhost:8025](http://localhost:8025).

## Daily workflow

1. **New item** — enter a set number (`10236-1`). BrickEconomy data is cached (100 requests/day).
2. Edit title, description, price, condition, package, and photos.
3. **Save**, then **Publish** to any combination of Shopify, BrickLink, and eBay.
4. After BrickLink succeeds, use **Upload BrickLink photo** — their API cannot attach a custom image.
5. Enable a watch rule on the item, then use **Market** to scan eBay Browse + BrickLink price guide.

Price-guard emails fire when your live eBay or BrickLink price is outside ±15% of the scanned average. Adjustment is manual.

## API notes

| Platform | Auth | Constraint |
| --- | --- | --- |
| BrickEconomy | `x-apikey` | 100 lookups/day, cached |
| Shopify | Client credentials (auto-refreshed Admin token) | GraphQL `productCreate` + variant price |
| BrickLink | OAuth 1.0 | No photo upload; completeness `C`/`B`/`S` |
| eBay | OAuth 2.0 user token | Inventory item → offer → publish. Needs business policies |

Connect eBay from **Settings** (one-time consent). The refresh token is stored in `app_setting`.

## Production

```bash
PROJECT_ID=your-gcp-project bash infra/setup-gcp.sh
gcloud builds submit --config infra/cloudbuild.yaml
```

See `infra/setup-gcp.sh` for Secret Manager names, the GCS photo bucket, and the Cloud Scheduler job that POSTs `/internal/jobs/market-scan` with `X-Internal-Token`.

## Out of scope (V1)

- Import of listings already live on the three platforms
- Buying / purchase-order tracking
- Auto-repricing
- Public signup
