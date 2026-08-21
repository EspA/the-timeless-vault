import {
  Callout,
  Card,
  CardBody,
  CardHeader,
  Divider,
  Grid,
  H1,
  H2,
  Pill,
  Row,
  Stack,
  Stat,
  Table,
  Text,
  computeDAGLayout,
  useCanvasState,
  useHostTheme,
} from "cursor/canvas";

type FlowId = "listing" | "watch" | "sales" | "stack";

type FlowNode = {
  id: string;
  title: string;
  subtitle: string;
  detail: string;
};

type FlowDef = {
  id: FlowId;
  label: string;
  summary: string;
  direction: "horizontal" | "vertical";
  nodes: FlowNode[];
  edges: Array<{ from: string; to: string }>;
};

const FLOWS: Record<FlowId, FlowDef> = {
  listing: {
    id: "listing",
    label: "List a set",
    summary:
      "Look up a LEGO set, save it in the vault, then publish the same item to Shopify, BrickLink, and eBay.",
    direction: "horizontal",
    nodes: [
      {
        id: "lookup",
        title: "Set lookup",
        subtitle: "BrickEconomy",
        detail:
          "Enter a set number such as 10236-1. Catalog data (name, theme, year, image) is fetched from BrickEconomy and cached because that API is capped at 100 lookups per day. A stub catalog row is used if lookup fails.",
      },
      {
        id: "item",
        title: "Inventory item",
        subtitle: "SKU · photos · price",
        detail:
          "Save title, description, condition, package weight, channel prices, and photos. Stock is In transit, In stock, or Sold. Photos go to local disk in development and GCS in production.",
      },
      {
        id: "publish",
        title: "Publish",
        subtitle: "One click, three channels",
        detail:
          "Publish any combination of Shopify, BrickLink, and eBay from the item page. Each channel gets its own listing row, price, and status. Retry a single platform without republishing the others. BrickLink photos are uploaded in a follow-up step because their create-lot API cannot attach a custom image.",
      },
      {
        id: "shopify",
        title: "Shopify",
        subtitle: "Storefront product",
        detail:
          "Creates or updates a Shopify product and variant, including collections. Status can be flipped between Active and Unlisted from the item page.",
      },
      {
        id: "bricklink",
        title: "BrickLink",
        subtitle: "Store lot",
        detail:
          "Creates a BrickLink inventory lot (OAuth 1.0). Completeness is C/B/S. After a successful publish, upload the vault photo separately. Lots can be moved between Active and Unlisted (stockroom).",
      },
      {
        id: "ebay",
        title: "eBay",
        subtitle: "Inventory → offer",
        detail:
          "Uses the Sell APIs: inventory item, then offer, then publish. Needs a connected eBay account, warehouse location, and business policies (fulfillment, payment, return). Store category is optional.",
      },
      {
        id: "logs",
        title: "Listing logs",
        subtitle: "Publish history",
        detail:
          "Every publish, update, retry, and status change is recorded so you can see which channel succeeded and what the remote API returned.",
      },
    ],
    edges: [
      { from: "lookup", to: "item" },
      { from: "item", to: "publish" },
      { from: "publish", to: "shopify" },
      { from: "publish", to: "bricklink" },
      { from: "publish", to: "ebay" },
      { from: "shopify", to: "logs" },
      { from: "bricklink", to: "logs" },
      { from: "ebay", to: "logs" },
    ],
  },
  watch: {
    id: "watch",
    label: "Market watch",
    summary:
      "Scan eBay and BrickLink for a catalog set, then email when a new listing appears or your live price drifts from the market.",
    direction: "horizontal",
    nodes: [
      {
        id: "rule",
        title: "Watch rule",
        subtitle: "Per catalog set",
        detail:
          "Enable a watch on a catalog item (from Inventory or Items watch). Exclude words (for example -custom -moc) are copied from Settings onto new watches. Price-guard thresholds default to ±15% and are also editable in Settings.",
      },
      {
        id: "trigger",
        title: "Scan trigger",
        subtitle: "Every 5 minutes",
        detail:
          "Production Cloud Scheduler job ttv-market-scan POSTs /internal/jobs/market-scan. Locally, SALES/MARKET local-schedule flags run the same job on a 5-minute delay. You can also scan one set from Market Monitoring.",
      },
      {
        id: "ebay-scan",
        title: "eBay Browse",
        subtitle: "Sold + live listings",
        detail:
          "Queries eBay Browse with the buyer ZIP from Settings so shipping can be quoted. Results are stored as market listings with a fingerprint used to detect new lots.",
      },
      {
        id: "bl-scan",
        title: "BrickLink guide",
        subtitle: "Price guide lots",
        detail:
          "Pulls BrickLink price-guide / for-sale lots for the set. Fingerprints distinguish lots already seen from new ones so buying-opportunity alerts do not repeat.",
      },
      {
        id: "snapshot",
        title: "Market snapshot",
        subtitle: "Average + listings",
        detail:
          "Market Monitoring shows the scanned lots and a market average. Scan logs record each run (automatic vs manual) and what was found.",
      },
      {
        id: "new-lot",
        title: "New listing",
        subtitle: "BUYING_OPPORTUNITY",
        detail:
          "If an automatic scan finds a fingerprint that was not seen before, an alert is created. Deduped by catalog + fingerprint so the same lot does not fire twice.",
      },
      {
        id: "guard",
        title: "Price guard",
        subtitle: "PRICE_HIGH / LOW",
        detail:
          "Compares your live eBay or BrickLink listing price to the scanned market average. Fires PRICE HIGH when you are above the high threshold, PRICE LOW when you are below the low threshold. Repricing stays manual.",
      },
      {
        id: "alerts",
        title: "Alerts + email",
        subtitle: "Inbox in the app",
        detail:
          "Alerts appear on the Alerts screen with an unread badge. Buying opportunities and price-guard events are emailed to the address in Settings (Mailpit locally, SMTP in production).",
      },
    ],
    edges: [
      { from: "rule", to: "trigger" },
      { from: "trigger", to: "ebay-scan" },
      { from: "trigger", to: "bl-scan" },
      { from: "ebay-scan", to: "snapshot" },
      { from: "bl-scan", to: "snapshot" },
      { from: "snapshot", to: "new-lot" },
      { from: "snapshot", to: "guard" },
      { from: "new-lot", to: "alerts" },
      { from: "guard", to: "alerts" },
    ],
  },
  sales: {
    id: "sales",
    label: "Sales",
    summary:
      "Import sold orders from the three channels (or add a local sale), snapshot the fee, and take remaining listings down.",
    direction: "horizontal",
    nodes: [
      {
        id: "source",
        title: "Sale source",
        subtitle: "Poll or Add sale",
        detail:
          "Cloud Scheduler job ttv-sales-sync POSTs /internal/jobs/sales-sync every 5 minutes. Sales → Sync now does the same. Add sale records a Local, eBay, BrickLink, or Shopify sale against an In-stock SKU without waiting for a poll.",
      },
      {
        id: "poll",
        title: "Channel orders",
        subtitle: "eBay · BrickLink · Shopify",
        detail:
          "First poll looks back 7 days; later polls overlap the last watermark by 2 hours. Watermarks live in app_setting (sales.last_sync.*). eBay uses Fulfillment orders; BrickLink uses store orders plus order cost; Shopify uses paid orders (needs read_orders).",
      },
      {
        id: "match",
        title: "Match vault item",
        subtitle: "SKU or listing id",
        detail:
          "Match order of: SKU, then channel listing external id (BrickLink lot, Shopify product GID, eBay item URL). BrickLink SKU is read from lot remarks.",
      },
      {
        id: "hit",
        title: "Existing item",
        subtitle: "Unlist + mark Sold",
        detail:
          "If a vault item is found, all active listings are taken down. The sold channel is marked inactive locally without a remote unlist (already gone). If another channel’s unlist fails, it is still marked inactive locally. The item becomes Sold with quantity 0.",
      },
      {
        id: "miss",
        title: "No vault item",
        subtitle: "Create as Sold",
        detail:
          "Unmatched channel sales create a Sold inventory row (qty 0) from the order title/SKU/set number so the sale still has somewhere to attach. Those items have no listings, so nothing is delisted.",
      },
      {
        id: "record",
        title: "Record sale",
        subtitle: "Fee at this moment",
        detail:
          "Each order line is stored once (platform + order id + line id). Shipping comes from the channel. eBay fee is the marketplace fee from the order. BrickLink fee is (price + shipping) × BrickLink %. Shopify fee is (price + shipping) × Shopify %. Rates are in Settings; changing them does not rewrite past sales.",
      },
    ],
    edges: [
      { from: "source", to: "poll" },
      { from: "poll", to: "match" },
      { from: "match", to: "hit" },
      { from: "match", to: "miss" },
      { from: "hit", to: "record" },
      { from: "miss", to: "record" },
    ],
  },
  stack: {
    id: "stack",
    label: "System map",
    summary:
      "Allowed-email Google login into a Vue admin that talks to Spring Boot. Postgres is the source of truth; channels and jobs sit around the API.",
    direction: "horizontal",
    nodes: [
      {
        id: "sso",
        title: "Google SSO",
        subtitle: "Allowed emails only",
        detail:
          "No public signup. AUTH_ALLOWED_EMAILS gates who can sign in. Locally APP_SECURITY_DEV_BYPASS can skip OAuth.",
      },
      {
        id: "ui",
        title: "Vue admin",
        subtitle: "Inventory · Sales · Watch",
        detail:
          "Vue 3 + Vite app. Nav groups: Inventory (list + listing logs), Sales, Market Watch (watches, market, scan logs, alerts), Settings. Production UI is served from the same Cloud Run service as the API.",
      },
      {
        id: "api",
        title: "Spring Boot API",
        subtitle: "Java 21",
        detail:
          "REST under /api plus internal jobs under /internal/jobs (X-Internal-Token). Flyway migrations own the schema. eBay account-deletion webhook is a public callback required by eBay.",
      },
      {
        id: "jobs",
        title: "Scheduler",
        subtitle: "Market + sales, 5 min",
        detail:
          "Cloud Scheduler jobs ttv-market-scan and ttv-sales-sync hit the API. Locally the same work can run inside the process when the local-schedule flags are on.",
      },
      {
        id: "db",
        title: "PostgreSQL",
        subtitle: "Source of truth",
        detail:
          "Inventory, catalog, channel listings, sales, watches, market listings, alerts, and app_setting (tokens, watermarks, fee percents, alert email).",
      },
      {
        id: "photos",
        title: "Photos",
        subtitle: "Disk or GCS",
        detail:
          "Item photos are stored on local disk in development and in the the-timeless-vault-photos GCS bucket in production.",
      },
      {
        id: "secrets",
        title: "Secrets",
        subtitle: "Env / Secret Manager",
        detail:
          "Channel credentials, Google OAuth, database URL, and INTERNAL_JOB_TOKEN live in .env locally and GCP Secret Manager in production. Never in git.",
      },
      {
        id: "channels",
        title: "Sales channels",
        subtitle: "Shopify · BrickLink · eBay",
        detail:
          "Shopify Admin GraphQL (client credentials). BrickLink OAuth 1.0. eBay OAuth 2.0 user token with Sell + Browse. BrickEconomy is catalog-only, not a sales channel.",
      },
    ],
    edges: [
      { from: "sso", to: "ui" },
      { from: "ui", to: "api" },
      { from: "jobs", to: "api" },
      { from: "api", to: "db" },
      { from: "api", to: "photos" },
      { from: "api", to: "secrets" },
      { from: "api", to: "channels" },
    ],
  },
};

const FEATURES: Array<{
  area: string;
  screen: string;
  what: string;
}> = [
  {
    area: "Inventory",
    screen: "Inventory / item",
    what: "Create SKUs from BrickEconomy, edit copy and channel prices, manage photos and stock (In transit / In stock / Sold).",
  },
  {
    area: "Inventory",
    screen: "Listing logs",
    what: "History of publish, update, retry, and channel status changes.",
  },
  {
    area: "Sales",
    screen: "Sales",
    what: "Poll or add sales, show shipping and fee, delete a sale row without changing inventory.",
  },
  {
    area: "Watch",
    screen: "Items watch",
    what: "Watch rules and exclude words per catalog set.",
  },
  {
    area: "Watch",
    screen: "Market Monitoring",
    what: "Scanned lots, market average, manual scan for one set.",
  },
  {
    area: "Watch",
    screen: "Scan logs",
    what: "Each automatic or manual scan run.",
  },
  {
    area: "Watch",
    screen: "Alerts",
    what: "New-lot opportunities and PRICE HIGH / PRICE LOW, with email.",
  },
  {
    area: "Settings",
    screen: "Settings",
    what: "eBay OAuth and warehouse, buyer ZIP, exclude words, price-guard %, BrickLink/Shopify fee %, alert email, store categories.",
  },
];

function nodeMap(flow: FlowDef): Record<string, FlowNode> {
  return Object.fromEntries(flow.nodes.map((node) => [node.id, node]));
}

function FlowGraph({
  flow,
  selectedId,
  onSelect,
}: {
  flow: FlowDef;
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  const theme = useHostTheme();
  const nodeWidth = 158;
  const nodeHeight = 52;
  const layout = computeDAGLayout({
    nodes: flow.nodes.map((node) => ({ id: node.id })),
    edges: flow.edges,
    direction: flow.direction,
    nodeWidth,
    nodeHeight,
    rankGap: 48,
    nodeGap: 20,
    padding: 12,
  });
  const labels = nodeMap(flow);

  return (
    <div
      style={{
        position: "relative",
        width: layout.width,
        height: layout.height,
        maxWidth: "100%",
      }}
    >
      <svg
        width={layout.width}
        height={layout.height}
        style={{ position: "absolute", inset: 0, overflow: "visible" }}
      >
        {layout.edges.map((edge) => {
          const midX = (edge.sourceX + edge.targetX) / 2;
          const midY = (edge.sourceY + edge.targetY) / 2;
          const d =
            flow.direction === "horizontal"
              ? `M ${edge.sourceX} ${edge.sourceY} C ${midX} ${edge.sourceY}, ${midX} ${edge.targetY}, ${edge.targetX} ${edge.targetY}`
              : `M ${edge.sourceX} ${edge.sourceY} C ${edge.sourceX} ${midY}, ${edge.targetX} ${midY}, ${edge.targetX} ${edge.targetY}`;
          return (
            <path
              key={`${edge.from}-${edge.to}`}
              d={d}
              fill="none"
              stroke={theme.stroke.primary}
              strokeWidth={1.5}
              strokeDasharray={edge.isBackEdge ? "4 3" : undefined}
            />
          );
        })}
      </svg>
      {layout.nodes.map((placed) => {
        const node = labels[placed.id];
        const selected = placed.id === selectedId;
        return (
          <button
            key={placed.id}
            type="button"
            onClick={() => onSelect(placed.id)}
            style={{
              position: "absolute",
              left: placed.x,
              top: placed.y,
              width: nodeWidth,
              height: nodeHeight,
              margin: 0,
              padding: "6px 8px",
              border: `1px solid ${selected ? theme.accent.primary : theme.stroke.primary}`,
              borderRadius: 6,
              background: selected ? theme.accent.primary : theme.bg.elevated,
              color: selected ? theme.text.onAccent : theme.text.primary,
              cursor: "pointer",
              textAlign: "left",
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              gap: 2,
            }}
          >
            <span style={{ fontSize: 12, fontWeight: 600, lineHeight: 1.2 }}>
              {node.title}
            </span>
            <span
              style={{
                fontSize: 11,
                lineHeight: 1.2,
                color: selected ? theme.text.onAccent : theme.text.secondary,
                opacity: selected ? 0.9 : 1,
              }}
            >
              {node.subtitle}
            </span>
          </button>
        );
      })}
    </div>
  );
}

export default function AppFeatureFlows() {
  const [flowId, setFlowId] = useCanvasState<FlowId>("flow", "listing");
  const [nodeId, setNodeId] = useCanvasState<string>("node", "lookup");
  const flow = FLOWS[flowId] ?? FLOWS.listing;
  const nodes = nodeMap(flow);
  const selected = nodes[nodeId] ?? flow.nodes[0];

  const selectFlow = (id: FlowId) => {
    setFlowId(id);
    setNodeId(FLOWS[id].nodes[0].id);
  };

  return (
    <Stack gap={20}>
      <Stack gap={8}>
        <H1>The Timeless Vault</H1>
        <Text tone="secondary">
          Internal listing and market-monitor app for thetimelessvault.com.
          Click a flow, then a node, to see what that step does.
        </Text>
      </Stack>

      <Row gap={20} wrap>
        <Stat value="3" label="Listing channels" />
        <Stat value="5 min" label="Scan and sales jobs" />
        <Stat value="3" label="Alert types" />
      </Row>

      <Row gap={8} wrap>
        {(Object.values(FLOWS) as FlowDef[]).map((item) => (
          <span key={item.id}>
            <Pill active={item.id === flow.id} onClick={() => selectFlow(item.id)}>
              {item.label}
            </Pill>
          </span>
        ))}
      </Row>

      <Stack gap={6}>
        <H2>{flow.label}</H2>
        <Text>{flow.summary}</Text>
      </Stack>

      <div style={{ overflowX: "auto" }}>
        <FlowGraph flow={flow} selectedId={selected.id} onSelect={setNodeId} />
      </div>

      <Card>
        <CardHeader trailing={<Pill size="sm">{selected.subtitle}</Pill>}>
          {selected.title}
        </CardHeader>
        <CardBody>
          <Text>{selected.detail}</Text>
        </CardBody>
      </Card>

      {flow.id === "sales" ? (
        <Callout tone="info">
          BrickLink fee defaults to 5.4% of price + shipping. Shopify defaults
          to 2.9%. Both are configurable in Settings and are snapshotted on the
          sale row so later rate changes do not rewrite history. eBay still uses
          the marketplace fee from the order.
        </Callout>
      ) : null}

      <Divider />

      <H2>Screens</H2>
      <Table
        headers={["Area", "Screen", "What it does"]}
        rows={FEATURES.map((row) => [row.area, row.screen, row.what])}
        striped
      />

      <H2>Daily path</H2>
      <Grid columns={2} gap={16}>
        <Stack gap={6}>
          <Text weight="semibold">List and sell</Text>
          <Text>
            Look up a set, edit the item, publish to the channels you want,
            then let sales sync close the loop: unlist leftovers and mark the
            SKU Sold.
          </Text>
        </Stack>
        <Stack gap={6}>
          <Text weight="semibold">Watch the market</Text>
          <Text>
            Turn on a watch, scan on a 5-minute cadence, and get an in-app
            alert plus email when a new lot appears or your live price is too
            far from the average. Adjustment is still manual.
          </Text>
        </Stack>
      </Grid>
    </Stack>
  );
}
