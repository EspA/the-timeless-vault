# Feature flows

Interactive Cursor canvas (open beside chat):

[`app-feature-flows.canvas.tsx`](./app-feature-flows.canvas.tsx)

Cursor only auto-loads canvases from its project `canvases/` folder. If this view is missing in the IDE, copy that file to:

`~/.cursor/projects/Users-USERNAME-Projects-the-timeless-vault/canvases/app-feature-flows.canvas.tsx`

The graphs below are the same four flows.

## List a set

```mermaid
flowchart LR
  lookup[Set lookup<br/>BrickEconomy] --> item[Inventory item<br/>SKU · photos · price]
  item --> publish[Publish]
  publish --> shopify[Shopify]
  publish --> bricklink[BrickLink]
  publish --> ebay[eBay]
  shopify --> logs[Listing logs]
  bricklink --> logs
  ebay --> logs
```

## Market watch

```mermaid
flowchart LR
  rule[Watch rule] --> trigger[Scan every 5 min]
  trigger --> ebayScan[eBay Browse]
  trigger --> blScan[BrickLink price guide]
  ebayScan --> snapshot[Market snapshot]
  blScan --> snapshot
  snapshot --> newLot[New listing alert]
  snapshot --> guard[Price HIGH / LOW]
  newLot --> alerts[Alerts + email]
  guard --> alerts
```

## Sales

```mermaid
flowchart LR
  source[Poll or Add sale] --> poll[Channel orders]
  poll --> match[Match SKU or listing id]
  match --> hit[Existing item<br/>unlist + Sold]
  match --> miss[No vault item<br/>create as Sold]
  hit --> record[Record sale<br/>fee snapshotted]
  miss --> record
```

## System map

```mermaid
flowchart LR
  sso[Google SSO] --> ui[Vue admin]
  ui --> api[Spring Boot API]
  jobs[Scheduler 5 min] --> api
  api --> db[PostgreSQL]
  api --> photos[Photos disk / GCS]
  api --> secrets[Secret Manager]
  api --> channels[Shopify · BrickLink · eBay]
```
