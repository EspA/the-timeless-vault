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
  shopifyLiveUrl?: string;
  bricklinkLiveUrl?: string;
  ebayLiveUrl?: string;
  photos: Photo[];
  createdAt?: string;
  updatedAt?: string;
};

export type ChannelListing = {
  id: string;
  platform: string;
  status: string;
  shopifyStatus?: string;
  bricklinkStatus?: string;
  ebayStatus?: string;
  externalId?: string;
  liveUrl?: string;
  lastPublishedPrice?: number;
  bricklinkPhotoUploadUrl?: string;
  lastError?: string;
};

export type Sale = {
  id: string;
  inventoryItemId?: string;
  sku?: string;
  setNumber?: string;
  itemTitle?: string;
  platform: string;
  externalOrderId: string;
  quantity: number;
  unitPrice: number;
  shippingCost?: number;
  platformFee?: number;
  currency: string;
  soldAt: string;
  orderUrl?: string;
  inventoryCreated: boolean;
};

export type SalesPage = {
  items: Sale[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  lastSyncedAt?: string;
};

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

export const defaultListingTitle = (catalog: Pick<Catalog, "setNumber" | "name" | "theme">) => {
  const number = catalog.setNumber.replace(/-1$/, "");
  const theme = catalog.theme?.trim() ? ` ${catalog.theme.trim()}` : "";
  const name = catalog.name?.trim() ? ` ${catalog.name.trim()}` : "";
  return `LEGO ${number}${theme}${name} (New Sealed In Box)`.replace(/ +/g, " ").trim().slice(0, LISTING_TITLE_MAX);
};

export const defaultEbayExcludeWords =
  "-custom -moc -replica -case -kit -led -minifigure -no -minifigures -figures -bricks -blocks -minifig -minifigs -figure -sticker -stickers -display -copy -creative -bag -compatible -only -generic -manuals -manual -displaycase -brick -toys -unofficial -kids -gift -fake -adults -mini-figures -mock";

export const defaultEbaySearchQuery = (catalog: Pick<Catalog, "setNumber" | "name">) => {
  const number = catalog.setNumber.replace(/-\d+$/, "");
  const name = catalog.name?.trim() ? ` ${catalog.name.trim()}` : "";
  return `LEGO ${number}${name}`.replace(/ +/g, " ").trim();
};

const yearFrom = (date?: string, fallback?: number | string) => {
  if (date) {
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

const replaceLabeledValue = (html: string, label: string, value: string) => {
  const pattern = new RegExp(`<p>\\s*(<strong>${label}:\\s*</strong>)[\\s\\S]*?</p>`, "i");
  if (!pattern.test(html)) {
    return html;
  }
  return html.replace(pattern, `<p>$1 <span>${value}</span></p>`);
};

const removeMinifigsLine = (html: string) =>
  html.replace(/<p>\s*(?:<span>)?<strong>Minifigs:<\/strong>[\s\S]*?<\/p>/i, "");

const minifigsLine = (count: string) => `<p><strong>Minifigs:</strong> <span>${count}</span></p>`;

const insertMinifigsAfterPieces = (html: string, count: string) => {
  const line = minifigsLine(count);
  const piecesPara = /<p>\s*<strong>Pieces:[\s\S]*?<\/p>/i;
  if (piecesPara.test(html)) {
    return html.replace(piecesPara, (match) => `${match}${line}`);
  }
  const gradingPara = /<p>[\s\S]*?grading system[\s\S]*?<\/p>/i;
  if (gradingPara.test(html)) {
    return html.replace(gradingPara, (match) => `${line}${match}`);
  }
  return `${html}${line}`;
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

export const brickLinkShortDescriptionFromHtml = (html: string) => {
  const paragraphs = htmlParagraphPlainTexts(html || "");
  const condition = paragraphs.find((text) => /^condition:/i.test(text));
  const boxGrade = paragraphs.find((text) => /^box grade:/i.test(text));
  if (!condition || !boxGrade) {
    return "";
  }
  const nisb = condition.replace(/^condition:\s*new sealed in box\s*/i, "").trim();
  const text = `${nisb} ${boxGrade} ${BRICKLINK_PHOTO_ASK}`.replace(/\s+/g, " ").trim();
  return text.length <= 255 ? text : text.slice(0, 255);
};

export const CHANNEL_PRICE_MARKUPS = {
  ebayPrice: 1.45,
  bricklinkPrice: 1.4,
  shopifyPrice: 1.32,
} as const;

export type ChannelPriceFields = {
  ebayPrice: string;
  bricklinkPrice: string;
  shopifyPrice: string;
};

export const channelPricesFromCost = (cost: string | number | null | undefined): ChannelPriceFields => {
  const empty = { ebayPrice: "", bricklinkPrice: "", shopifyPrice: "" };
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
    shopifyPrice: money(CHANNEL_PRICE_MARKUPS.shopifyPrice),
  };
};

export const numericChannelPricesFromCost = (cost: string | number | null | undefined) => {
  const generated = channelPricesFromCost(cost);
  if (!generated.ebayPrice) {
    return { ebayPrice: undefined, bricklinkPrice: undefined, shopifyPrice: undefined };
  }
  return {
    ebayPrice: Number(generated.ebayPrice),
    bricklinkPrice: Number(generated.bricklinkPrice),
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

export const defaultDescriptionHtml = (catalog?: Partial<Catalog> | null) => {
  const facts = catalogFacts(catalog);
  return [
    `<p><strong>Set number:</strong> <span>${facts.setNumber}</span></p>`,
    `<p><strong>Condition:</strong><span> </span><b>New Sealed In Box (NISB)<span> </span></b>-<span> </span>Factory seals intact. Never opened.</p>`,
    `<p><strong>Box Grade:<span> 10<b> (Collector Grade):</b></span></strong> Gift-ready or investment-grade. Sharp corners, original seal tension, and minimal to no shelf wear.</p>`,
    `<p><strong>Released: </strong>${facts.released}</p>`,
    `<p><strong>Retired:</strong> ${facts.retired}</p>`,
    `<p><strong>Pieces: </strong>${facts.pieces}</p>`,
    facts.minifigs ? minifigsLine(facts.minifigs) : "",
    `<p><span>For more details about our grading system, </span><a href="https://www.thetimelessvault.shop/pages/grading">click here</a><span>.</span></p>`,
    `<p><span><strong>Condition Disclaimer:</strong> This is a retired, second-hand set. Please review all high-resolution photos carefully. By purchasing, you acknowledge the specific box/seal condition as shown.</span></p>`,
  ].join("");
};

export const applyCatalogToDescription = (html: string, catalog: Catalog) => {
  if (!html || !/Set number:/i.test(html)) {
    return defaultDescriptionHtml(catalog);
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

export const DEFAULT_EBAY_SCAN_INTERVAL_MINUTES = 5;
export const DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES = 360;

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
  "NEW_SEALED",
  "NEW_COMPLETE",
  "NEW_INCOMPLETE",
  "NEW_OTHER",
  "USED_COMPLETE",
  "USED_INCOMPLETE",
];

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
