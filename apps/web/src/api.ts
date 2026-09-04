export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const json = async <T>(res: Response): Promise<T> => {
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    const err = body as { error?: string; message?: string };
    throw new ApiError(err.message || err.error || res.statusText, res.status);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
};

export const api = {
  get: <T>(path: string) => fetch(path, { credentials: "include" }).then(json<T>),
  post: <T>(path: string, body?: unknown) =>
    fetch(path, {
      method: "POST",
      credentials: "include",
      headers: body instanceof FormData ? {} : { "Content-Type": "application/json" },
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    }).then(json<T>),
  put: <T>(path: string, body?: unknown) =>
    fetch(path, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    }).then(json<T>),
  patch: <T>(path: string, body?: unknown) =>
    fetch(path, {
      method: "PATCH",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    }).then(json<T>),
  del: <T = void>(path: string) => fetch(path, { method: "DELETE", credentials: "include" }).then(json<T>),
};

export type Catalog = {
  id: string;
  setNumber: string;
  name: string;
  theme?: string;
  subtheme?: string;
  year?: number;
  piecesCount?: number;
  minifigsCount?: number;
  retired?: boolean;
  releasedDate?: string;
  retiredDate?: string;
  currentValueNew?: number;
  currentValueUsed?: number;
  retailPriceUs?: number;
  twentyPercentBelowNew?: number;
  suggestedTitle: string;
  suggestedEbayStoreCategory: string;
  fetchedAt: string;
  bricklinkPackage?: {
    shipping: {
      lbs: number;
      oz: number;
      length?: number;
      width?: number;
      height?: number;
    };
    original: {
      lbs: number;
      oz: number;
      length?: number;
      width?: number;
      height?: number;
    };
  };
};

export type Photo = {
  id: string;
  url: string;
  filename?: string;
  sortOrder: number;
  primaryForBricklink: boolean;
};

export type ShopifyCollection = {
  id: string;
  title: string;
};

export type EbayStoreCategory = {
  id: string;
  name: string;
  path: string;
};

export type InventoryItem = {
  id: string;
  sku: string;
  catalog: Catalog;
  title: string;
  description?: string;
  shortDescription?: string;
  price: number;
  ebayPrice?: number;
  bricklinkPrice?: number;
  shopifyPrice?: number;
  brickowlPrice?: number;
  quantity: number;
  stockStatus: string;
  cost?: number;
  itemType: string;
  condition: string;
  shopifyCollectionIds: string[];
  ebayStoreCategory?: string;
  minimumOffer?: number;
  packageLbs: number;
  packageOz: number;
  packageLength?: number;
  packageWidth?: number;
  packageHeight?: number;
  notes?: string;
  shopifyStatus?: string;
  bricklinkStatus?: string;
  ebayStatus?: string;
  brickowlStatus?: string;
  shopifyLiveUrl?: string;
  bricklinkLiveUrl?: string;
  ebayLiveUrl?: string;
  brickowlLiveUrl?: string;
  photos: Photo[];
  createdAt?: string;
  updatedAt?: string;
};

export type InventoryPage = {
  items: InventoryItem[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type ChannelListing = {
  id: string;
  platform: string;
  status: string;
  shopifyStatus?: string;
  bricklinkStatus?: string;
  ebayStatus?: string;
  brickowlStatus?: string;
  externalId?: string;
  liveUrl?: string;
  lastPublishedPrice?: number;
  bricklinkPhotoUploadUrl?: string;
  lastError?: string;
};

export type ListingAdjustment = {
  id: string;
  when: string;
  type: "PRICE_HIGH" | "PRICE_LOW" | string;
  platform?: string;
  listingUrl?: string;
  listingStatus?: string;
  inventoryItemId?: string;
  inventoryLabel?: string;
  cost?: number | null;
  currentListingPrice?: number | null;
  marketPrice?: number | null;
  recommendedPrice?: number | null;
};

export type OrderStatus = "OPEN" | "SHIPPED" | "COMPLETED" | "CANCELLED";

export type OrderLine = {
  id: string;
  inventoryItemId?: string;
  sku?: string;
  setNumber?: string;
  itemTitle?: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  inventoryCreated: boolean;
};

export type ShipmentTracking = {
  id?: string;
  trackingNumber: string;
  shippingProvider?: string | null;
  carrier?: string | null;
};

export type Order = {
  id: string;
  inventoryItemId?: string;
  sku?: string;
  setNumber?: string;
  itemTitle?: string;
  platform: string;
  externalOrderId: string;
  quantity: number;
  unitPrice: number;
  merchandiseTotal?: number;
  shippingCost?: number;
  platformFee?: number;
  currency: string;
  soldAt: string;
  orderUrl?: string;
  inventoryCreated: boolean;
  status: OrderStatus;
  trackingNumber?: string;
  shippingProvider?: string;
  trackings?: ShipmentTracking[];
  lines?: OrderLine[];
};

export const shipmentCarrier = (row?: ShipmentTracking | null) =>
  row?.carrier || row?.shippingProvider || "";

export const trackingEntries = (row?: {
  trackings?: ShipmentTracking[];
  trackingNumber?: string;
  shippingProvider?: string;
  carrier?: string | null;
} | null) => {
  if (row?.trackings?.length) {
    return row.trackings
      .filter((tracking) => tracking.trackingNumber?.trim())
      .map((tracking) => ({
        trackingNumber: tracking.trackingNumber,
        carrier: shipmentCarrier(tracking),
      }));
  }
  if (row?.trackingNumber?.trim()) {
    return [{ trackingNumber: row.trackingNumber.trim(), carrier: row.shippingProvider || row.carrier || "" }];
  }
  return [];
};

export const extraTrackingCount = (row?: { trackings?: ShipmentTracking[]; trackingNumber?: string } | null) =>
  Math.max(0, (row?.trackings?.length || (row?.trackingNumber ? 1 : 0)) - 1);

export type OrdersPage = {
  items: Order[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  lastSyncedAt?: string;
};

export const ORDER_STATUSES: { value: OrderStatus; label: string; shortLabel: string }[] = [
  { value: "OPEN", label: "Open", shortLabel: "Open" },
  { value: "SHIPPED", label: "Shipped", shortLabel: "Shipped" },
  { value: "COMPLETED", label: "Delivered", shortLabel: "Delivered" },
  { value: "CANCELLED", label: "Cancelled", shortLabel: "Cancel" },
];

export const orderStatusLabel = (status?: string | null) =>
  ORDER_STATUSES.find((row) => row.value === status)?.label || status || "";

export const TRACKABLE_SHIPPING_PROVIDERS: { value: "UPS" | "USPS" | "FEDEX"; label: string }[] = [
  { value: "UPS", label: "UPS" },
  { value: "USPS", label: "USPS" },
  { value: "FEDEX", label: "FedEx" },
];

export const canonicalizeShippingProvider = (value?: string | null) => {
  if (!value?.trim()) return "";
  const normalized = value.trim().toUpperCase();
  if (normalized.includes("USPS") || normalized.includes("POSTAL SERVICE") || normalized === "US POSTAL") {
    return "USPS";
  }
  if (normalized.includes("FEDEX") || normalized.includes("FED EX") || normalized.includes("FEDERAL EXPRESS")) {
    return "FEDEX";
  }
  if (normalized === "UPS" || normalized.startsWith("UPS ") || normalized.includes("UNITED PARCEL")) {
    return "UPS";
  }
  return value.trim();
};

export const shippingProviderSelectOptions = (current?: string | null) => {
  const options: { value: string; label: string }[] = TRACKABLE_SHIPPING_PROVIDERS.map((row) => ({ ...row }));
  const trimmed = current?.trim() ?? "";
  if (!trimmed) return options;
  const canonical = canonicalizeShippingProvider(trimmed);
  if (TRACKABLE_SHIPPING_PROVIDERS.some((row) => row.value === canonical)) {
    return options;
  }
  options.push({ value: trimmed, label: trimmed });
  return options;
};

export const isUpsTracking = (tracking?: string | null, provider?: string | null) => {
  const number = tracking?.trim() ?? "";
  if (!number) return false;
  return number.toUpperCase().startsWith("1Z") || canonicalizeShippingProvider(provider) === "UPS";
};

export const trackingUrl = (tracking?: string | null, provider?: string | null) => {
  const number = tracking?.trim();
  if (!number) return "";
  const encoded = encodeURIComponent(number);
  const carrier = canonicalizeShippingProvider(provider);
  if (isUpsTracking(number, provider) || carrier === "UPS") {
    return `https://www.ups.com/track?loc=en_US&requester=ST&trackNums=${encoded}`;
  }
  if (carrier === "USPS") {
    return `https://tools.usps.com/go/TrackConfirmAction?tLabels=${encoded}`;
  }
  if (carrier === "DHL") {
    return `https://www.dhl.com/en/express/tracking.html?AWB=${encoded}`;
  }
  if (carrier === "FEDEX") {
    return `https://www.fedex.com/fedextrack/?trknbr=${encoded}`;
  }
  if (carrier === "COLISSIMO") {
    return `https://www.laposte.fr/outils/suivre-vos-envois?code=${encoded}`;
  }
  if (carrier === "POSTNL") {
    return `https://jouw.postnl.nl/track-and-trace/${encoded}`;
  }
  return "";
};

export const upsTrackingUrl = trackingUrl;

export type LedgerSale = {
  kind: "SET" | "MINIFIG";
  itemNumber?: string;
  name?: string;
  theme?: string;
  subtheme?: string;
  year?: number;
  currency: string;
  salePriceTotal: number;
  salePriceUnit: number;
  salePriceShipping: number;
  salePriceFees: number;
  saleQuantity: number;
  saleCondition?: string;
  saleDate?: string;
  buyDate?: string;
  buyCondition?: string;
  buyPrice: number;
  profit: number;
};

export type SalesLedgerPage = {
  items: LedgerSale[];
  total: number;
  revenue: number;
  profit: number;
  fetchedAt?: string;
};

export const visibilityStatusLabel = (status?: string | null) => {
  if (status === "ACTIVE") return "Active";
  if (status === "UNLISTED") return "Inactive";
  return status || "";
};

export const visibilityActionLabel = (status?: string | null) =>
  status === "ACTIVE" ? "Deactivate" : "Activate";

export const nextVisibilityStatus = (status?: string | null) =>
  status === "UNLISTED" ? "ACTIVE" : "UNLISTED";

export const shopifyStatusLabel = visibilityStatusLabel;
export const nextShopifyStatus = nextVisibilityStatus;

export const EBAY_DELETE_CONFIRM =
  "This removes the listing from this app only.\n\nTo permanently delete it on eBay, go to Seller Hub → Listings → Inactive → Delete.\n\nContinue?";

export const SHOPIFY_DELETE_CONFIRM =
  "Permanently delete this Shopify product? This cannot be undone.";

export const BRICKLINK_DELETE_CONFIRM =
  "Permanently delete this BrickLink inventory item? This cannot be undone.";

export const BRICKOWL_DELETE_CONFIRM =
  "Permanently delete this Brick Owl lot? This cannot be undone.";

export type EbayCatalogPreview = {
  setNumber: string;
  title: string;
  condition?: string;
  catalogMatch: boolean;
  source: string;
  summary: string;
  epid?: string;
  brand?: string;
  mpn?: string;
  upc: string[];
  ean: string[];
  aspects: { name: string; values: string[] }[];
};

export type PublishJob = {
  id: string;
  platform: string;
  status: string;
  error?: string;
  createdAt: string;
  finishedAt?: string;
};

export const LISTING_TITLE_MAX = 80;

export const defaultListingTitle = (catalog: Pick<Catalog, "setNumber" | "name" | "theme" | "subtheme">) => {
  const number = catalog.setNumber.replace(/-1$/, "");
  const theme = catalog.theme?.trim() ? ` ${catalog.theme.trim()}` : "";
  const subtheme = catalog.subtheme?.trim() && catalog.subtheme.trim() !== catalog.theme?.trim()
    ? ` ${catalog.subtheme.trim()}`
    : "";
  const name = catalog.name?.trim() ? ` ${catalog.name.trim()}` : "";
  return `LEGO ${number}${theme}${subtheme}${name} (New Sealed In Box)`.replace(/ +/g, " ").trim().slice(0, LISTING_TITLE_MAX);
};

export const defaultEbayExcludeWords =
  "-custom -moc -replica -case -kit -led -minifigure -no -minifigures -figures -bricks -blocks -minifig -minifigs -figure -sticker -stickers -display -copy -creative -bag -compatible -only -generic -manuals -manual -displaycase -brick -toys -unofficial -kids -gift -fake -adults -mini-figures -mock";

export const defaultEbaySearchQuery = (catalog: Pick<Catalog, "setNumber" | "name">) => {
  const number = catalog.setNumber.replace(/-\d+$/, "");
  const name = catalog.name?.trim() ? ` ${catalog.name.trim()}` : "";
  return `LEGO ${number}${name}`.replace(/ +/g, " ").trim();
};

const yearFrom = (date?: string | number | number[], fallback?: number | string) => {
  if (Array.isArray(date) && date[0]) {
    return String(date[0]);
  }
  if (typeof date === "number" && date > 0) {
    return String(date);
  }
  if (typeof date === "string" && date) {
    const year = Number(date.slice(0, 4));
    if (year) return String(year);
  }
  return fallback == null || fallback === "" ? "" : String(fallback);
};

const catalogFacts = (catalog?: Partial<Catalog> | null) => {
  const loaded = Boolean(catalog?.setNumber);
  return {
    setNumber: catalog?.setNumber?.replace(/-1$/, "") || "00000",
    released: yearFrom(catalog?.releasedDate, catalog?.year) || (loaded ? "—" : "2026"),
    retired: yearFrom(catalog?.retiredDate, catalog?.retired ? catalog.year : undefined) || (loaded ? "—" : "2026"),
    pieces: catalog?.piecesCount != null ? String(catalog.piecesCount) : loaded ? "—" : "100",
    minifigs: !loaded
      ? "10"
      : catalog?.minifigsCount && catalog.minifigsCount > 0
        ? String(catalog.minifigsCount)
        : "",
  };
};

const paragraphHtmlPattern = () => /<p\b[^>]*>[\s\S]*?<\/p>/gi;

const paragraphPlainText = (paragraph: string) =>
  paragraph
    .replace(/^<p\b[^>]*>/i, "")
    .replace(/<\/p>\s*$/i, "")
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/\s+/g, " ")
    .trim();

const mapParagraphs = (html: string, fn: (paragraph: string, plain: string) => string) =>
  html.replace(paragraphHtmlPattern(), (paragraph) => fn(paragraph, paragraphPlainText(paragraph)));

const replaceLabeledValue = (html: string, label: string, value: string) => {
  const prefix = new RegExp(`^${label}:`, "i");
  let replaced = false;
  const next = mapParagraphs(html, (paragraph, plain) => {
    if (replaced || !prefix.test(plain)) {
      return paragraph;
    }
    replaced = true;
    return `<p><strong>${label}:</strong> <span>${value}</span></p>`;
  });
  return replaced ? next : html;
};

const removeMinifigsLine = (html: string) =>
  mapParagraphs(html, (paragraph, plain) => (/^minifigs:/i.test(plain) ? "" : paragraph));

const minifigsLine = (count: string) => `<p><strong>Minifigs:</strong> <span>${count}</span></p>`;

const insertMinifigsAfterPieces = (html: string, count: string) => {
  const line = minifigsLine(count);
  let inserted = false;
  const next = mapParagraphs(html, (paragraph, plain) => {
    if (inserted || !/^pieces:/i.test(plain)) {
      return paragraph;
    }
    inserted = true;
    return `${paragraph}${line}`;
  });
  if (inserted) {
    return next;
  }
  let gradingInserted = false;
  const withGrading = mapParagraphs(html, (paragraph, plain) => {
    if (gradingInserted || !/grading system/i.test(plain)) {
      return paragraph;
    }
    gradingInserted = true;
    return `${line}${paragraph}`;
  });
  return gradingInserted ? withGrading : `${html}${line}`;
};

const htmlParagraphPlainTexts = (html: string) =>
  [...html.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)].map((match) =>
    match[1]
      .replace(/<br\s*\/?>/gi, " ")
      .replace(/<[^>]+>/g, " ")
      .replace(/&nbsp;/gi, " ")
      .replace(/&amp;/gi, "&")
      .replace(/\s+/g, " ")
      .trim()
  );

const BRICKLINK_PHOTO_ASK = "Ask for more photos!";

export const BOX_GRADES = [
  { score: 10, band: "Collector Grade", description: "Gift-ready or investment-grade. Sharp corners, original seal tension, the box is in mint condition." },
  { score: 9, band: "Collector Grade", description: "Gift-ready or investment-grade. Sharp corners, original seal tension, and minimal to no shelf wear." },
  { score: 8, band: "Excellent", description: "Minor shelf wear, small corner blunting, or a clean price sticker. Perfect for display." },
  { score: 7, band: "Very good", description: "Minor shelf wear, small corner blunting, or a clean price sticker. Perfect for display." },
  { score: 6, band: "Good", description: "Noticeable creasing, small punctures, or minor \"shelf-push\" (dents)." },
  { score: 5, band: "Fair", description: "Noticeable creasing, small punctures, or minor \"shelf-push\" (dents)." },
  { score: 4, band: "Poor/Damaged", description: "Significant crushing, heavy tape, or structural tears. Recommended for builders who don't keep the box." },
] as const;

export const DEFAULT_BOX_GRADE = 10;

export type ListingCopyOptions = {
  condition?: string;
  boxGrade?: number;
};

const isConditionParagraph = (plain: string) =>
  /^condition:/i.test(plain)
  || /^used 100% complete:/i.test(plain)
  || /^used missing parts:/i.test(plain);

const isBoxGradeParagraph = (plain: string) => /^box grade:/i.test(plain);

export const boxGradeFromScore = (score: number) =>
  BOX_GRADES.find((grade) => grade.score === score) ?? BOX_GRADES[0];

export const boxGradeOptionLabel = (grade: (typeof BOX_GRADES)[number]) =>
  `${grade.score}/10 (${grade.band})`;

export const conditionParagraphHtml = (condition: string) => {
  switch (condition) {
    case "NEW_COMPLETE":
      return `<p><strong>Condition:</strong><span> </span><b>New Open Box (NOB)<span> </span></b>-<span> </span>All bags sealed with instructions.</p>`;
    case "NEW_INCOMPLETE":
      return `<p><strong>Condition:</strong><span> </span><b>New Open Box (NOB)<span> </span></b>-<span> </span>Some bags or instructions missing</p>`;
    case "NEW_OTHER":
      return `<p><strong>Condition:</strong><span> </span>new other</p>`;
    case "USED_COMPLETE":
      return `<p><strong>Used 100% Complete:</strong> Previously built. Verified against official part lists to include all bricks, minifigures, and instructions.</p>`;
    case "USED_INCOMPLETE":
      return `<p><strong>Used Missing Parts:</strong> Previously built. Known missing pieces will be listed in the item description.</p>`;
    default:
      return `<p><strong>Condition:</strong><span> </span><b>New Sealed In Box (NISB)<span> </span></b>-<span> </span>Factory seals intact. Never opened.</p>`;
  }
};

export const boxGradeParagraphHtml = (score: number) => {
  const grade = boxGradeFromScore(score);
  return `<p><strong>Box Grade:</strong> ${grade.score}/10 (${grade.band}): ${grade.description}</p>`;
};

const replaceOrInsertParagraph = (
  html: string,
  match: (plain: string) => boolean,
  line: string,
  after: (plain: string) => boolean
) => {
  let replaced = false;
  const next = mapParagraphs(html, (paragraph, plain) => {
    if (replaced || !match(plain)) {
      return paragraph;
    }
    replaced = true;
    return line;
  });
  if (replaced) {
    return next;
  }
  let inserted = false;
  const afterMatch = mapParagraphs(html, (paragraph, plain) => {
    if (inserted || !after(plain)) {
      return paragraph;
    }
    inserted = true;
    return `${paragraph}${line}`;
  });
  return inserted ? afterMatch : `${html}${line}`;
};

export const applyConditionToDescription = (html: string, condition: string) =>
  replaceOrInsertParagraph(
    html || "",
    isConditionParagraph,
    conditionParagraphHtml(condition),
    (plain) => /^set number:/i.test(plain)
  );

export const applyBoxGradeToDescription = (html: string, score: number) =>
  replaceOrInsertParagraph(html || "", isBoxGradeParagraph, boxGradeParagraphHtml(score), isConditionParagraph);

export const inferBoxGradeFromHtml = (html: string) => {
  const boxGrade = htmlParagraphPlainTexts(html || "").find(isBoxGradeParagraph);
  if (!boxGrade) {
    return DEFAULT_BOX_GRADE;
  }
  const match = boxGrade.match(/box grade:\s*(\d+)/i);
  if (!match) {
    return DEFAULT_BOX_GRADE;
  }
  const score = Number(match[1]);
  return BOX_GRADES.some((grade) => grade.score === score) ? score : DEFAULT_BOX_GRADE;
};

export const brickLinkShortDescriptionFromHtml = (html: string) => {
  const paragraphs = htmlParagraphPlainTexts(html || "");
  const condition = paragraphs.find(isConditionParagraph);
  const boxGrade = paragraphs.find(isBoxGradeParagraph);
  if (!condition || !boxGrade) {
    return "";
  }
  const remainder = condition.replace(/^condition:\s*new sealed in box\s*/i, "").trim();
  const text = `${remainder} ${boxGrade} ${BRICKLINK_PHOTO_ASK}`.replace(/\s+/g, " ").trim();
  return text.length <= 255 ? text : text.slice(0, 255);
};

export const isListingDumpShortDescription = (short?: string) =>
  /^set number:/i.test((short || "").trim());

export const CHANNEL_PRICE_MARKUPS = {
  ebayPrice: 1.45,
  bricklinkPrice: 1.4,
  brickowlPrice: 1.4,
  shopifyPrice: 1.32,
} as const;

export type ChannelPriceFields = {
  ebayPrice: string;
  bricklinkPrice: string;
  brickowlPrice: string;
  shopifyPrice: string;
};

export const channelPricesFromCost = (cost: string | number | null | undefined): ChannelPriceFields => {
  const empty = { ebayPrice: "", bricklinkPrice: "", brickowlPrice: "", shopifyPrice: "" };
  if (cost == null || cost === "") {
    return empty;
  }
  const parsed = typeof cost === "number" ? cost : Number(String(cost).trim());
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return empty;
  }
  const cents = Math.round(parsed * 100);
  const money = (markup: number) => (Math.round(cents * markup) / 100).toFixed(2);
  return {
    ebayPrice: money(CHANNEL_PRICE_MARKUPS.ebayPrice),
    bricklinkPrice: money(CHANNEL_PRICE_MARKUPS.bricklinkPrice),
    brickowlPrice: money(CHANNEL_PRICE_MARKUPS.brickowlPrice),
    shopifyPrice: money(CHANNEL_PRICE_MARKUPS.shopifyPrice),
  };
};

export const numericChannelPricesFromCost = (cost: string | number | null | undefined) => {
  const generated = channelPricesFromCost(cost);
  if (!generated.ebayPrice) {
    return { ebayPrice: undefined, bricklinkPrice: undefined, brickowlPrice: undefined, shopifyPrice: undefined };
  }
  return {
    ebayPrice: Number(generated.ebayPrice),
    bricklinkPrice: Number(generated.bricklinkPrice),
    brickowlPrice: Number(generated.brickowlPrice),
    shopifyPrice: Number(generated.shopifyPrice),
  };
};

export const minimumOfferFromEbayPrice = (ebayPrice: string | number | null | undefined) => {
  if (ebayPrice == null || ebayPrice === "") {
    return "";
  }
  const parsed = typeof ebayPrice === "number" ? ebayPrice : Number(String(ebayPrice).trim());
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return "";
  }
  const cents = Math.round(parsed * 100);
  return (Math.round(cents * 0.9) / 100).toFixed(2);
};

export const defaultDescriptionHtml = (catalog?: Partial<Catalog> | null, options?: ListingCopyOptions) => {
  const facts = catalogFacts(catalog);
  const condition = options?.condition ?? "NEW_SEALED";
  const boxGrade = options?.boxGrade ?? DEFAULT_BOX_GRADE;
  return [
    `<p><strong>Set number:</strong> <span>${facts.setNumber}</span></p>`,
    conditionParagraphHtml(condition),
    boxGradeParagraphHtml(boxGrade),
    `<p><strong>Released: </strong>${facts.released}</p>`,
    `<p><strong>Retired:</strong> ${facts.retired}</p>`,
    `<p><strong>Pieces: </strong>${facts.pieces}</p>`,
    facts.minifigs ? minifigsLine(facts.minifigs) : "",
    `<p><span>For more details about our grading system, </span><a href="https://www.thetimelessvault.shop/pages/grading">click here</a><span>.</span></p>`,
    `<p><span><strong>Condition Disclaimer:</strong> This is a retired, second-hand set. Please review all high-resolution photos carefully. By purchasing, you acknowledge the specific box/seal condition as shown.</span></p>`,
  ].join("");
};

export const applyCatalogToDescription = (html: string, catalog: Catalog, options?: ListingCopyOptions) => {
  if (!html || !/Set number:/i.test(html)) {
    return defaultDescriptionHtml(catalog, options);
  }
  const facts = catalogFacts(catalog);
  let next = html;
  next = replaceLabeledValue(next, "Set number", facts.setNumber);
  next = replaceLabeledValue(next, "Released", facts.released);
  next = replaceLabeledValue(next, "Retired", facts.retired);
  next = replaceLabeledValue(next, "Pieces", facts.pieces);
  next = removeMinifigsLine(next);
  if (facts.minifigs) {
    next = insertMinifigsAfterPieces(next, facts.minifigs);
  }
  return next;
};

export type SetWatch = {
  id: string;
  catalogId: string;
  setNumber: string;
  name: string;
  theme?: string;
  subtheme?: string;
  releasedDate?: string;
  retiredDate?: string;
  retired?: boolean;
  piecesCount?: number;
  minifigsCount?: number;
  retailPriceUs?: number;
  currentValueNew?: number;
  brickeconomyFetchedAt?: string;
  ebayCurrentValueNew?: number | null;
  ebayScannedAt?: string;
  bricklinkCurrentValueNew?: number | null;
  bricklinkScannedAt?: string;
  medianPrice?: number | null;
  enabled: boolean;
  ebaySearchQuery?: string;
  ebayFeedbackMin: number;
  ebayExcludeWords?: string;
  ebayScanIntervalMinutes: number;
  bricklinkScanIntervalMinutes: number;
  minPrice?: number | null;
  maxPrice?: number | null;
  lastScannedAt?: string;
  updatedAt: string;
};

export const DEFAULT_EBAY_SCAN_INTERVAL_MINUTES = 360;
export const DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES = 360;

export type Supplier = {
  id: string;
  name: string;
  email?: string;
  phone?: string;
  website?: string;
  street?: string;
  city?: string;
  zip?: string;
  country?: string;
  createdAt: string;
  updatedAt: string;
};

export type ShippingCarrier = "UPS" | "USPS" | "DHL" | "FEDEX" | "COLISSIMO" | "POSTNL";

export const SHIPPING_CARRIERS: { value: ShippingCarrier; label: string }[] = [
  { value: "UPS", label: "UPS" },
  { value: "USPS", label: "USPS" },
  { value: "DHL", label: "DHL" },
  { value: "FEDEX", label: "FedEx" },
  { value: "COLISSIMO", label: "Colissimo" },
  { value: "POSTNL", label: "PostNL" },
];

export type PurchaseOrderStatus = "IN_TRANSIT" | "DELIVERED" | "RECEIVED" | "CANCELLED";

export const PURCHASE_ORDER_STATUSES = [
  { value: "IN_TRANSIT", label: "In transit", shortLabel: "Transit" },
  { value: "DELIVERED", label: "Delivered", shortLabel: "Delivered" },
  { value: "RECEIVED", label: "Received", shortLabel: "Received" },
  { value: "CANCELLED", label: "Cancelled", shortLabel: "Cancel" },
] as const;

export type PurchaseOrderLine = {
  id: string;
  setNumber: string;
  title: string;
  quantity: number;
  unitValue: number;
  lineTotal: number;
  inventoryItemId?: string;
  sku?: string;
};

export type PurchaseOrder = {
  id: string;
  number: string;
  supplierId: string;
  supplierName: string;
  status: PurchaseOrderStatus;
  totalValue: number;
  expectedArrival?: string;
  trackingNumber?: string;
  carrier?: ShippingCarrier | null;
  trackings?: ShipmentTracking[];
  note?: string;
  lines: PurchaseOrderLine[];
  createdAt: string;
  updatedAt: string;
};

export type PurchaseOrderPage = {
  items: PurchaseOrder[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type QuoteStatus = "DRAFT" | "CONVERTED";

export const QUOTE_STATUSES = [
  { value: "DRAFT", label: "Draft" },
  { value: "CONVERTED", label: "Converted" },
] as const;

export type QuoteMarginTone = "low" | "mid" | "high";

export type QuoteLine = {
  id: string;
  catalogItemId: string;
  setNumber: string;
  title: string;
  cost: number;
  proratedShipping: number;
  costWithShipping: number;
  medianMarketPrice?: number | null;
  marginPercent?: number | null;
  marginDollars?: number | null;
  marginTone?: QuoteMarginTone | null;
};

export type Quote = {
  id: string;
  number: string;
  status: QuoteStatus;
  description?: string | null;
  lineCount: number;
  shippingTotal: number;
  totalCost: number;
  totalProratedShipping: number;
  totalCostWithShipping: number;
  totalMedianMarketPrice?: number | null;
  averageMarginPercent?: number | null;
  averageMarginTone?: QuoteMarginTone | null;
  totalMarginDollars?: number | null;
  purchaseOrderId?: string | null;
  purchaseOrderNumber?: string | null;
  lines: QuoteLine[];
  createdAt: string;
  updatedAt: string;
};

export type QuotePage = {
  items: Quote[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};


export const SCAN_INTERVALS = [
  { minutes: 5, label: "Every 5 minutes" },
  { minutes: 10, label: "Every 10 minutes" },
  { minutes: 15, label: "Every 15 minutes" },
  { minutes: 30, label: "Every 30 minutes" },
  { minutes: 60, label: "Every hour" },
  { minutes: 120, label: "Every 2 hours" },
  { minutes: 360, label: "Every 6 hours" },
  { minutes: 720, label: "Every 12 hours" },
  { minutes: 1440, label: "Every 24 hours" },
];

export const CONDITIONS = [
  { value: "NEW_SEALED", label: "New Sealed" },
  { value: "NEW_COMPLETE", label: "New Complete" },
  { value: "NEW_INCOMPLETE", label: "New Incomplete" },
  { value: "NEW_OTHER", label: "New Other" },
  { value: "USED_COMPLETE", label: "Used Complete" },
  { value: "USED_INCOMPLETE", label: "Used Incomplete" },
] as const;

export const conditionLabel = (condition?: string) =>
  CONDITIONS.find((option) => option.value === condition)?.label ?? condition ?? "—";

export const STOCK_STATUSES = [
  { value: "IN_TRANSIT", label: "In transit", shortLabel: "Transit" },
  { value: "IN_STOCK", label: "In stock", shortLabel: "In stock" },
  { value: "SOLD", label: "Sold", shortLabel: "Sold" },
] as const;

export type StockStatusValue = (typeof STOCK_STATUSES)[number]["value"];

export const stockStatusLabel = (status?: string) =>
  STOCK_STATUSES.find((option) => option.value === status)?.label ?? status ?? "—";

export const quantityForStockStatus = (
  previousStatus: string | undefined,
  previousQuantity: number,
  nextStatus: string
) => {
  if (nextStatus === "SOLD" || nextStatus === "IN_TRANSIT") return 0;
  const quantity = Number.isFinite(previousQuantity) ? previousQuantity : 0;
  if (nextStatus === "IN_STOCK" && previousStatus !== "IN_STOCK") {
    return quantity + 1;
  }
  return Math.max(0, quantity);
};
