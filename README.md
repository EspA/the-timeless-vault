# The Timeless Vault

Internal shop app for [thetimelessvault.com](https://thetimelessvault.com).

PostgreSQL is the source of truth. Staff look up a LEGO set, buy it in through quotes and purchase orders, list it on Shopify, BrickLink, Brick Owl, and eBay, then watch the market and record the sale when it sells. Google sign-in is limited to allowlisted emails. There is no public signup.

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

Or `./scripts/dev.sh`, which does the same and creates `.env` if it is missing.

Requires Java 21 (Homebrew: `brew install openjdk@21`).

Start the API (from `apps/api`):

```bash
export $(grep -v '^#' ../../.env | xargs)
./mvnw spring-boot:run
```

`./scripts/run-api.sh` from the repo root does that export for you.

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

### Inbound

1. Keep suppliers on **Suppliers**.
2. Draft a **Quote**, then turn it into a purchase order.
3. Track the purchase order from In transit to Delivered to Received. UPS, USPS, and FedEx tracking can mark an in-transit order Delivered. Several tracking numbers can sit on one order.
4. Adding a line creates the inventory item as In transit, with channel prices marked up from the line cost. Receiving sets that item In stock.

### Inventory

1. **New item** — enter a set number (`10236-1`). BrickEconomy data is cached (500 requests/day).
2. Edit title, description, condition, box grade, prices per channel, package size, and photos. Photos can be reordered.
3. **Save**, then **Publish** to any combination of Shopify, BrickLink, Brick Owl, and eBay. A live listing can also be linked by its URL or id instead of creating a new one.
4. After BrickLink succeeds, use **Upload BrickLink photo** — their API cannot attach a custom image.
5. Export the filtered inventory table as CSV, including the Shopify listing URL when one is published.

Listing logs record each publish, update, retry, and status change.

### Outbound

**Orders** sync from Shopify, BrickLink, Brick Owl, and eBay, or you can add one by hand. A marketplace order stays one order with a line per item. Each line snapshots the channel fee from Settings (eBay uses the fee on the order). Changing a fee percent later does not rewrite past orders.

When a line matches a vault item, by SKU or listing id, the other active listings are taken down and the item is marked Sold. An unmatched sale still creates a Sold inventory row so the order has somewhere to attach.

**Sales Ledger** charts revenue and profit from those orders.

### Market watch

1. Enable a watch on a set from the item or from **Items watch**. Each watch has its own eBay and BrickLink interval. The default is every 6 hours. Exclude words and the ±15% price-guard band come from Settings and can be overridden per watch.
2. **Market Monitoring** scans eBay Browse and the BrickLink price guide. eBay Browse goes through the WaitSeeBuy partner proxy when `WAITSEEBUY_BROWSE_TOKEN` is set.
3. A new lot becomes a buying-opportunity notification. A live eBay or BrickLink price outside the band becomes one price-high or price-low alert and a row on **Listing adjustment**.
4. Repricing stays manual: open the adjustment, set the price, and apply it. Dismiss hides the row until the next day if the price is still out of range.

Notifications also cover new sales, delivered orders and purchase orders, and failed scans. Buying opportunities and the first price alert for a listing are emailed to the address in Settings.

The production market-scan job runs every 5 minutes and only scans watches that are due. Scan logs keep each automatic or manual run. A nightly job deletes old scan logs and market snapshots.

## API notes

| Platform | Auth | Constraint |
| --- | --- | --- |
| BrickEconomy | `x-apikey` | 500 lookups/day, cached. Catalog only |
| Shopify | Client credentials (auto-refreshed Admin token) | GraphQL product create, variant price, collections. Sales sync needs `read_orders` |
| BrickLink | OAuth 1.0 | No photo upload; completeness `C`/`B`/`S` |
| Brick Owl | API key | Inventory and orders. Publish starts unlisted until you set it for sale |
| eBay | OAuth 2.0 user token | Inventory item → offer → publish. Needs business policies |

Connect eBay from **Settings** (one-time consent). The refresh token is stored in `app_setting`. **Statistics** shows daily API call counts.

## Production

```bash
PROJECT_ID=the-timeless-vault bash infra/setup-gcp.sh
gcloud builds submit --config infra/cloudbuild.yaml --substitutions=_CLOUDSQL_INSTANCE=the-timeless-vault:us-east1:timeless-vault,_GCS_BUCKET=the-timeless-vault-photos
```

`infra/setup-gcp.sh` creates Cloud SQL, the photo bucket, Secret Manager values from `.env`, and IAM. After the first deploy, set the Google OAuth authorized redirect URI to:

`https://YOUR_CLOUD_RUN_URL/login/oauth2/code/google`

Then store `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` in Secret Manager (`ttv-google-client-id`, `ttv-google-client-secret`) and redeploy.

Cloud Scheduler posts to the API with `X-Internal-Token`:

| Job | Schedule | Path |
| --- | --- | --- |
| `ttv-market-scan` | Every 5 minutes | `/internal/jobs/market-scan` |
| `ttv-sales-sync` | Every 5 minutes | `/internal/jobs/sales-sync` |
| `ttv-scan-log-purge` | Nightly, America/New_York | `/internal/jobs/scan-log-purge` |

Locally, `MARKET_LOCAL_SCHEDULE` runs the same market check on `MARKET_SCAN_DELAY_MS` (default 5 minutes). `SALES_LOCAL_SCHEDULE` polls orders on `SALES_SYNC_DELAY_MS` (default 1 hour).

## Out of scope

- Automatic repricing. Listing adjustment applies a price you enter.
- Public signup
