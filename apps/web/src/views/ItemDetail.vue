<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, ApiError, applyCatalogToDescription, brickLinkShortDescriptionFromHtml, CONDITIONS, defaultListingTitle, LISTING_TITLE_MAX, minimumOfferFromEbayPrice, nextVisibilityStatus, numericChannelPricesFromCost, visibilityActionLabel, visibilityStatusLabel, type Catalog, type ChannelListing, type EbayCatalogPreview, type InventoryItem, type Photo, type PublishJob, BRICKLINK_DELETE_CONFIRM, EBAY_DELETE_CONFIRM, SHOPIFY_DELETE_CONFIRM } from "../api";
import { askAlert, askConfirm, confirmStockStatusChange } from "../confirm";
import RichTextEditor from "../components/RichTextEditor.vue";
import ShopifyCollectionsField from "../components/ShopifyCollectionsField.vue";
import EbayStoreCategoryField from "../components/EbayStoreCategoryField.vue";
import ChannelLogo from "../components/ChannelLogo.vue";
import ScanProgressModal from "../components/ScanProgressModal.vue";
import StockStatusButtons from "../components/StockStatusButtons.vue";
import PhotoCapture from "../components/PhotoCapture.vue";

const route = useRoute();
const router = useRouter();
const item = ref<InventoryItem | null>(null);
const listings = ref<ChannelListing[]>([]);
const error = ref("");
const saving = ref(false);
const stockBusy = ref(false);
const justSaved = ref(false);
let savedTimer: ReturnType<typeof setTimeout> | undefined;
const platforms = ref(["SHOPIFY", "BRICKLINK", "EBAY"]);
const showLinkListing = ref(false);
const linkPlatform = ref("BRICKLINK");
const linkReference = ref("");
const linkError = ref("");
const linking = ref(false);
const publishing = ref(false);
const showPublishLog = ref(false);
const showEbayCatalog = ref(false);
const ebayCatalogLoading = ref(false);
const ebayCatalog = ref<EbayCatalogPreview | null>(null);
const pendingPublishKind = ref<"publish" | "retry" | null>(null);
const activityTitle = ref("Publish log");
const publishLogs = ref<{ time: string; text: string; kind?: "ok" | "bad" }[]>([]);
const jobStatus = ref<Record<string, string>>({});
type MarketSnapshot = { min?: number; avg?: number; median?: number; max?: number; count?: number; scannedAt?: string };
type MarketDashboard = { ebay?: MarketSnapshot | null; bricklink?: MarketSnapshot | null };
const market = ref<MarketDashboard | null>(null);
const refreshingCatalog = ref(false);
const editorKey = ref(0);
const uploading = ref(false);
const uploadIndex = ref(0);
const uploadTotal = ref(0);
const uploadName = ref("");

const uploadTitle = computed(() => uploadTotal.value === 1 ? "Uploading photo" : "Uploading photos");
const uploadMessage = computed(() => {
  if (!uploadTotal.value) return "Uploading to storage…";
  const current = Math.min(uploadIndex.value + 1, uploadTotal.value);
  const name = uploadName.value ? ` · ${uploadName.value}` : "";
  return `Uploading ${current} of ${uploadTotal.value}${name}`;
});
const uploadPercent = computed(() => {
  if (!uploadTotal.value) return 0;
  return Math.round(((uploadIndex.value + 1) / uploadTotal.value) * 100);
});

const loadMarket = async (catalogId?: string) => {
  if (!catalogId) {
    market.value = null;
    return;
  }
  try {
    market.value = await api.get<MarketDashboard>(`/api/market/${catalogId}`);
  } catch {
    market.value = null;
  }
};

const load = async () => {
  const id = String(route.params.id);
  const loaded = await api.get<InventoryItem>(`/api/inventory/${id}`);
  loaded.shopifyCollectionIds = loaded.shopifyCollectionIds ?? [];
  item.value = loaded;
  listings.value = await api.get<ChannelListing[]>(`/api/inventory/${id}/listings`);
  await loadMarket(loaded.catalog?.id);
};

onMounted(async () => {
  try {
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  }
});

onBeforeUnmount(() => clearTimeout(savedTimer));

watch(
  () => item.value?.cost,
  (cost, previous) => {
    if (!item.value || previous === undefined) return;
    const generated = numericChannelPricesFromCost(cost);
    item.value.ebayPrice = generated.ebayPrice;
    item.value.bricklinkPrice = generated.bricklinkPrice;
    item.value.shopifyPrice = generated.shopifyPrice;
  }
);

watch(
  () => item.value?.ebayPrice,
  (price, previous) => {
    if (!item.value || previous === undefined) return;
    const offer = minimumOfferFromEbayPrice(price);
    item.value.minimumOffer = offer === "" ? undefined : Number(offer);
  }
);

const setStockStatus = async (next: string) => {
  if (!item.value || item.value.stockStatus === next || stockBusy.value) return;
  if (!(await confirmStockStatusChange(next))) return;
  stockBusy.value = true;
  error.value = "";
  try {
    const updated = await api.put<InventoryItem>(`/api/inventory/${item.value.id}`, { stockStatus: next });
    item.value.stockStatus = updated.stockStatus;
    item.value.quantity = updated.quantity;
    item.value.shopifyStatus = updated.shopifyStatus;
    item.value.bricklinkStatus = updated.bricklinkStatus;
    item.value.ebayStatus = updated.ebayStatus;
    listings.value = await api.get<ChannelListing[]>(`/api/inventory/${item.value.id}/listings`);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    stockBusy.value = false;
  }
};

const save = async () => {
  if (!item.value) return;
  item.value = await api.put<InventoryItem>(`/api/inventory/${item.value.id}`, item.value);
};

const saveChanges = async () => {
  if (!item.value || saving.value) return;
  error.value = "";
  justSaved.value = false;
  saving.value = true;
  try {
    await save();
    justSaved.value = true;
    clearTimeout(savedTimer);
    savedTimer = setTimeout(() => {
      justSaved.value = false;
    }, 1800);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
};

const money = (value?: number | null) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const weightedMean = (
  left: { value: number | null; count: number },
  right: { value: number | null; count: number }
) => {
  const parts = [left, right].filter((part) => part.value != null && part.count > 0) as { value: number; count: number }[];
  const total = parts.reduce((sum, part) => sum + part.count, 0);
  if (!total) {
    return null;
  }
  return parts.reduce((sum, part) => sum + part.value * part.count, 0) / total;
};

const combinedAverage = computed(() => weightedMean(
  { value: market.value?.ebay?.avg ?? null, count: market.value?.ebay?.count ?? 0 },
  { value: market.value?.bricklink?.avg ?? null, count: market.value?.bricklink?.count ?? 0 }
));

const combinedMedian = computed(() => weightedMean(
  { value: market.value?.ebay?.median ?? null, count: market.value?.ebay?.count ?? 0 },
  { value: market.value?.bricklink?.median ?? null, count: market.value?.bricklink?.count ?? 0 }
));

const hasMarketStats = computed(() =>
  Boolean(market.value?.ebay?.count || market.value?.bricklink?.count)
);

const marketScanLabel = computed(() => {
  const times = [market.value?.ebay?.scannedAt, market.value?.bricklink?.scannedAt]
    .filter((value): value is string => Boolean(value));
  if (!times.length) {
    return "No scan yet";
  }
  const latest = times.reduce((newest, time) => (time > newest ? time : newest));
  const parsed = new Date(latest);
  if (Number.isNaN(parsed.getTime())) {
    return "No scan yet";
  }
  return `Last scan ${parsed.toLocaleString()}`;
});

const upload = async (files: File[]) => {
  if (!files.length || !item.value || uploading.value) return;
  uploading.value = true;
  uploadTotal.value = files.length;
  uploadIndex.value = 0;
  uploadName.value = "";
  error.value = "";
  try {
    for (let i = 0; i < files.length; i++) {
      uploadIndex.value = i;
      uploadName.value = files[i].name;
      const data = new FormData();
      data.append("file", files[i]);
      await api.post(`/api/inventory/${item.value.id}/photos`, data);
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not upload photos";
  } finally {
    uploading.value = false;
    uploadTotal.value = 0;
    uploadName.value = "";
  }
  await load();
};

const makePrimary = async (photoId: string) => {
  if (!item.value) return;
  await api.put(`/api/inventory/${item.value.id}/photos/${photoId}/primary`);
  await load();
};

const removePhoto = async (photo: Photo) => {
  if (!item.value) return;
  const name = photo.filename || "this photo";
  if (!(await askConfirm(`Delete ${name} from this item?`, { title: "Delete photo" }))) {
    return;
  }
  error.value = "";
  try {
    await api.del(`/api/inventory/${item.value.id}/photos/${photo.id}`);
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  }
};

const logLine = (text: string, kind?: "ok" | "bad") => {
  publishLogs.value.push({ time: new Date().toLocaleTimeString(), text, kind });
};

const waitForJobs = async (ids: string[], successText = "published successfully") => {
  const done = new Set(["SUCCESS", "FAILED"]);
  const started = Date.now();
  while (Date.now() - started < 120000) {
    const jobs = await api.get<PublishJob[]>(`/api/inventory/${item.value!.id}/jobs`);
    const mine = jobs.filter((job) => ids.includes(job.id));
    for (const job of mine) {
      if (jobStatus.value[job.id] === job.status) {
        continue;
      }
      jobStatus.value[job.id] = job.status;
      if (job.status === "RUNNING") {
        logLine(`${job.platform}: calling API…`);
      } else if (job.status === "SUCCESS") {
        logLine(`${job.platform}: ${successText}`, "ok");
      } else if (job.status === "FAILED") {
        logLine(`${job.platform}: failed${job.error ? ` — ${job.error}` : ""}`, "bad");
      } else {
        logLine(`${job.platform}: ${job.status.toLowerCase()}`);
      }
    }
    listings.value = await api.get<ChannelListing[]>(`/api/inventory/${item.value!.id}/listings`);
    if (mine.length && mine.every((job) => done.has(job.status))) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  logLine("Timed out waiting for publish to finish.", "bad");
};

const startPublishLog = (summary: string, title = "Publish log") => {
  publishing.value = true;
  showPublishLog.value = true;
  activityTitle.value = title;
  publishLogs.value = [];
  jobStatus.value = {};
  error.value = "";
  logLine(summary);
};

const runChannelAction = async (summary: string, action: () => Promise<void>) => {
  if (!item.value || publishing.value) return;
  startPublishLog(summary, "Channel log");
  try {
    await action();
    await load();
  } catch (e) {
    error.value = (e as Error).message;
    logLine(`Request failed — ${(e as Error).message}`, "bad");
  } finally {
    publishing.value = false;
  }
};

const includesEbay = (selected: string[]) => selected.includes("EBAY");

const platformLabel = (platform: string) => {
  if (platform === "EBAY") return "eBay";
  if (platform === "BRICKLINK") return "BrickLink";
  if (platform === "SHOPIFY") return "Shopify";
  return platform;
};

const joinAnd = (parts: string[]) => {
  if (parts.length <= 1) return parts[0] || "";
  if (parts.length === 2) return `${parts[0]} and ${parts[1]}`;
  return `${parts.slice(0, -1).join(", ")}, and ${parts[parts.length - 1]}`;
};

const alreadyCreatedPlatforms = (selected: string[]) =>
  selected.filter((platform) => listings.value.some((listing) => listing.platform === platform));

const alreadyCreatedMessage = (platforms: string[]) => {
  const names = platforms.map(platformLabel);
  const hint = platforms.length === 1
    ? "Uncheck that channel, or delete the existing listing. If it failed, use Retry."
    : "Uncheck those channels, or delete the existing listings. If a listing failed, use Retry.";
  if (platforms.length === 1) {
    return `A ${names[0]} listing has already been created for this item.\n${hint}`;
  }
  return `Listings have already been created for ${joinAnd(names)}.\n${hint}`;
};

const warnIfAlreadyCreated = async (selected: string[]) => {
  const existing = alreadyCreatedPlatforms(selected);
  if (!existing.length) return false;
  await askAlert(alreadyCreatedMessage(existing), { title: "Listing already exists" });
  return true;
};

const openEbayCatalogReview = async (kind: "publish" | "retry") => {
  if (!item.value) return;
  pendingPublishKind.value = kind;
  showEbayCatalog.value = true;
  ebayCatalogLoading.value = true;
  ebayCatalog.value = null;
  error.value = "";
  try {
    if (kind === "publish") {
      await save();
    }
    ebayCatalog.value = await api.get<EbayCatalogPreview>(`/api/inventory/${item.value.id}/ebay/catalog`);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load eBay catalog";
    showEbayCatalog.value = false;
    pendingPublishKind.value = null;
  } finally {
    ebayCatalogLoading.value = false;
  }
};

const acceptEbayCatalog = async (bypassEbayCatalog = false) => {
  const kind = pendingPublishKind.value;
  showEbayCatalog.value = false;
  pendingPublishKind.value = null;
  if (kind === "retry") {
    await runRetry("EBAY", bypassEbayCatalog);
    return;
  }
  await runPublish(platforms.value, bypassEbayCatalog);
};

const rejectEbayCatalog = () => {
  showEbayCatalog.value = false;
  ebayCatalog.value = null;
  pendingPublishKind.value = null;
};

const joinValues = (values?: string[]) => (values && values.length ? values.join(", ") : "—");

const publish = async () => {
  if (!item.value || publishing.value || ebayCatalogLoading.value || !platforms.value.length) return;
  if (await warnIfAlreadyCreated(platforms.value)) return;
  if (includesEbay(platforms.value)) {
    await openEbayCatalogReview("publish");
    return;
  }
  await runPublish(platforms.value);
};

const runPublish = async (selected: string[], bypassEbayCatalog = false) => {
  if (!item.value || publishing.value || !selected.length) return;
  if (await warnIfAlreadyCreated(selected)) return;
  startPublishLog(`Publishing ${selected.join(", ")}…`);
  try {
    await save();
    logLine("Item saved. Queuing channel jobs…");
    if (bypassEbayCatalog && selected.includes("EBAY")) {
      logLine("eBay: using vault listing details, not the catalog product.");
    }
    const jobs = await api.post<PublishJob[]>(`/api/inventory/${item.value.id}/publish`, {
      platforms: selected,
      bypassEbayCatalog,
    });
    for (const job of jobs) {
      jobStatus.value[job.id] = job.status;
      logLine(`${job.platform}: queued`);
    }
    await waitForJobs(jobs.map((job) => job.id));
    logLine("Publish finished.");
    await load();
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      publishing.value = false;
      showPublishLog.value = false;
      await askAlert(e.message, { title: "Listing already exists" });
      return;
    }
    error.value = (e as Error).message;
    logLine(`Request failed — ${(e as Error).message}`, "bad");
  } finally {
    publishing.value = false;
  }
};

const retry = async (platform: string) => {
  if (!item.value || publishing.value || ebayCatalogLoading.value) return;
  if (platform === "EBAY") {
    await openEbayCatalogReview("retry");
    return;
  }
  await runRetry(platform);
};

const runRetry = async (platform: string, bypassEbayCatalog = false) => {
  if (!item.value || publishing.value) return;
  startPublishLog(`Retrying ${platform}…`);
  try {
    const job = await api.post<PublishJob>(`/api/inventory/${item.value.id}/publish/${platform}/retry`, {
      bypassEbayCatalog,
    });
    jobStatus.value[job.id] = job.status;
    logLine(`${job.platform}: queued`);
    await waitForJobs([job.id]);
    logLine("Retry finished.");
    await load();
  } catch (e) {
    error.value = (e as Error).message;
    logLine(`Request failed — ${(e as Error).message}`, "bad");
  } finally {
    publishing.value = false;
  }
};

const canToggleShopify = (listing: ChannelListing) =>
  listing.platform === "SHOPIFY" && listing.status === "PUBLISHED" && !!listing.externalId;

const canToggleBricklink = (listing: ChannelListing) =>
  listing.platform === "BRICKLINK" && listing.status === "PUBLISHED" && !!listing.externalId;

const canToggleEbay = (listing: ChannelListing) =>
  listing.platform === "EBAY" && listing.status === "PUBLISHED" && !!listing.externalId;

const canDeleteShopify = (listing: ChannelListing) =>
  listing.platform === "SHOPIFY" && listing.shopifyStatus === "UNLISTED";

const canDeleteBricklink = (listing: ChannelListing) =>
  listing.platform === "BRICKLINK" && listing.bricklinkStatus === "UNLISTED";

const canDeleteEbay = (listing: ChannelListing) =>
  listing.platform === "EBAY" && listing.ebayStatus === "UNLISTED";

const canRetry = (listing: ChannelListing) => listing.status === "FAILED";

const canUpdateListing = (listing: ChannelListing) =>
  listing.status === "PUBLISHED" && !!listing.externalId;

const updatableListings = computed(() => listings.value.filter(canUpdateListing));

const selectedUpdatable = computed(() =>
  updatableListings.value.filter((listing) => platforms.value.includes(listing.platform))
);

const updateSelected = () => updateListings(selectedUpdatable.value);

const linkHint = computed(() => {
  if (linkPlatform.value === "EBAY") return "eBay listing URL or item id, for example https://www.ebay.com/itm/227311449843";
  if (linkPlatform.value === "SHOPIFY") return "Shopify product URL, handle, or product id";
  return "BrickLink listing URL or inventory id, for example https://www.bricklink.com/v2/inventory_detail.page?invID=525841562";
});

const openLinkListing = () => {
  linkError.value = "";
  linkReference.value = "";
  const selected = platforms.value[0];
  if (selected) linkPlatform.value = selected;
  showLinkListing.value = true;
};

const submitLinkListing = async (replaceExisting = false) => {
  if (!item.value || linking.value) return;
  const reference = linkReference.value.trim();
  if (!reference) {
    linkError.value = "Paste a listing URL or id";
    return;
  }
  linking.value = true;
  linkError.value = "";
  try {
    await api.post<ChannelListing>(`/api/inventory/${item.value.id}/listings/link`, {
      platform: linkPlatform.value,
      reference,
      replaceExisting,
    });
    showLinkListing.value = false;
    await load();
  } catch (e) {
    const err = e as ApiError;
    if (err.status === 409 && !replaceExisting) {
      const confirmed = await askConfirm(err.message, {
        title: "Replace listing link",
        confirmLabel: "Link here",
        cancelLabel: "Cancel",
        variant: "gold",
      });
      if (confirmed) {
        linking.value = false;
        await submitLinkListing(true);
        return;
      }
    } else {
      linkError.value = err.message;
    }
  } finally {
    linking.value = false;
  }
};

const updateListing = (listing: ChannelListing) => {
  if (!canUpdateListing(listing)) return;
  return updateListings([listing]);
};

const updateListings = async (targets: ChannelListing[]) => {
  if (!item.value || publishing.value || !targets.length) return;
  const names = targets.map((listing) => platformLabel(listing.platform));
  const bricklink = targets.some((listing) => listing.platform === "BRICKLINK");
  const confirmed = await askConfirm(
    `Push the saved title, description, photos, price, and quantity to the existing ${joinAnd(names)} listing${names.length === 1 ? "" : "s"}?`
      + (bricklink ? " BrickLink photos still have to be uploaded on BrickLink." : ""),
    { title: names.length === 1 ? `Update ${names[0]}` : "Update listings", confirmLabel: "Update", cancelLabel: "Cancel", variant: "gold" }
  );
  if (!confirmed) return;
  startPublishLog(`Updating ${names.join(", ")}…`, "Update log");
  try {
    await save();
    logLine("Item saved. Queuing channel updates…");
    const jobs = await api.post<PublishJob[]>(`/api/inventory/${item.value.id}/publish/update`, {
      platforms: targets.map((listing) => listing.platform),
    });
    for (const job of jobs) {
      jobStatus.value[job.id] = job.status;
      logLine(`${job.platform}: queued`);
    }
    await waitForJobs(jobs.map((job) => job.id), "updated successfully");
    logLine("Update finished.");
    await load();
  } catch (e) {
    error.value = (e as Error).message;
    logLine(`Request failed — ${(e as Error).message}`, "bad");
  } finally {
    publishing.value = false;
  }
};

const toggleShopify = async (listing: ChannelListing) => {
  if (!item.value || publishing.value || !canToggleShopify(listing)) return;
  const next = nextVisibilityStatus(listing.shopifyStatus);
  const action = visibilityActionLabel(listing.shopifyStatus);
  await runChannelAction(`${action} Shopify…`, async () => {
    logLine("Calling Shopify…");
    const updated = await api.put<ChannelListing>(`/api/inventory/${item.value!.id}/listings/shopify/status`, {
      status: next,
    });
    logLine(`Shopify is now ${visibilityStatusLabel(updated.shopifyStatus || next).toLowerCase()}.`, "ok");
  });
};

const toggleBricklink = async (listing: ChannelListing) => {
  if (!item.value || publishing.value || !canToggleBricklink(listing)) return;
  const next = nextVisibilityStatus(listing.bricklinkStatus);
  const action = visibilityActionLabel(listing.bricklinkStatus);
  await runChannelAction(`${action} BrickLink…`, async () => {
    logLine("Calling BrickLink…");
    const updated = await api.put<ChannelListing>(`/api/inventory/${item.value!.id}/listings/bricklink/status`, {
      status: next,
    });
    logLine(`BrickLink is now ${visibilityStatusLabel(updated.bricklinkStatus || next).toLowerCase()}.`, "ok");
  });
};

const toggleEbay = async (listing: ChannelListing) => {
  if (!item.value || publishing.value || !canToggleEbay(listing)) return;
  const next = listing.ebayStatus === "ACTIVE" ? "UNLISTED" : "ACTIVE";
  const action = visibilityActionLabel(listing.ebayStatus);
  await runChannelAction(`${action} eBay…`, async () => {
    logLine("Calling eBay…");
    const updated = await api.put<ChannelListing>(`/api/inventory/${item.value!.id}/listings/ebay/status`, {
      status: next,
    });
    logLine(`eBay is now ${visibilityStatusLabel(updated.ebayStatus || next).toLowerCase()}.`, "ok");
  });
};

const deleteShopifyListing = async (listing: ChannelListing) => {
  if (!item.value || publishing.value || !canDeleteShopify(listing)) return;
  if (!(await askConfirm(SHOPIFY_DELETE_CONFIRM, { title: "Delete listing" }))) {
    return;
  }
  await runChannelAction("Deleting Shopify listing…", async () => {
    logLine("Deleting Shopify product…");
    await api.del(`/api/inventory/${item.value!.id}/listings/shopify`);
    logLine("Shopify product deleted.", "ok");
  });
};

const deleteBricklinkListing = async (listing: ChannelListing) => {
  if (!item.value || publishing.value || !canDeleteBricklink(listing)) return;
  if (!(await askConfirm(BRICKLINK_DELETE_CONFIRM, { title: "Delete listing" }))) {
    return;
  }
  await runChannelAction("Deleting BrickLink listing…", async () => {
    logLine("Deleting BrickLink inventory item…");
    await api.del(`/api/inventory/${item.value!.id}/listings/bricklink`);
    logLine("BrickLink inventory item deleted.", "ok");
  });
};

const deleteEbayListing = async (listing: ChannelListing) => {
  if (!item.value || publishing.value || !canDeleteEbay(listing)) return;
  if (!(await askConfirm(EBAY_DELETE_CONFIRM, { title: "Delete listing" }))) {
    return;
  }
  await runChannelAction("Deleting eBay listing…", async () => {
    logLine("Removing eBay listing from this app…");
    await api.del(`/api/inventory/${item.value!.id}/listings/ebay`);
    logLine("Local eBay listing removed. Delete it in Seller Hub → Inactive if it is still there.", "ok");
  });
};

const applyCatalogRefresh = (catalog: Catalog) => {
  if (!item.value) return;
  const previousGenerated = brickLinkShortDescriptionFromHtml(item.value.description || "");
  item.value.catalog = catalog;
  if (item.value.condition === "NEW_SEALED") {
    item.value.title = defaultListingTitle(catalog);
  }
  item.value.description = applyCatalogToDescription(item.value.description || "", catalog);
  editorKey.value += 1;
  const generatedShort = brickLinkShortDescriptionFromHtml(item.value.description || "");
  if (!item.value.shortDescription || item.value.shortDescription === previousGenerated) {
    item.value.shortDescription = generatedShort;
  }
  item.value.ebayStoreCategory = catalog.suggestedEbayStoreCategory;
  const pkg = catalog.bricklinkPackage?.shipping;
  if (pkg) {
    item.value.packageLbs = pkg.lbs ?? 0;
    item.value.packageOz = 0;
    item.value.packageLength = pkg.length != null ? Number(pkg.length) : undefined;
    item.value.packageWidth = pkg.width != null ? Number(pkg.width) : undefined;
    item.value.packageHeight = pkg.height != null ? Number(pkg.height) : undefined;
  }
};

const reloadItemInfo = async () => {
  if (!item.value || refreshingCatalog.value) return;
  refreshingCatalog.value = true;
  error.value = "";
  try {
    const catalog = await api.get<Catalog>(
      `/api/catalog/lookup?setNumber=${encodeURIComponent(item.value.catalog.setNumber)}&refresh=true`
    );
    applyCatalogRefresh(catalog);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    refreshingCatalog.value = false;
  }
};

const remove = async () => {
  if (!item.value) return;
  if (!(await askConfirm(`Delete "${item.value.title}" from inventory? This cannot be undone.`, { title: "Delete item" }))) {
    return;
  }
  error.value = "";
  try {
    await api.del(`/api/inventory/${item.value.id}`);
    await router.push("/inventory");
  } catch (e) {
    error.value = (e as Error).message;
  }
};
</script>

<template>
  <div v-if="item" class="grid">
    <div class="page-head">
      <div>
        <p class="muted">{{ item.catalog.setNumber }} · {{ item.sku }}</p>
        <h1>{{ item.title }}</h1>
      </div>
      <StockStatusButtons
        :model-value="item.stockStatus"
        :disabled="stockBusy"
        @update:model-value="setStockStatus"
      />
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="grid save-fields" :class="{ flash: justSaved }">
    <div class="card grid">
      <div class="form-modal-lookup">
        <button
          class="btn secondary"
          type="button"
          :disabled="refreshingCatalog || saving"
          @click="reloadItemInfo"
        >
          {{ refreshingCatalog ? "Reloading…" : "Reload Item Info" }}
        </button>
      </div>
      <label>Title
        <input v-model="item.title" :maxlength="LISTING_TITLE_MAX" />
        <span class="muted">{{ (item.title || "").length }}/{{ LISTING_TITLE_MAX }}</span>
      </label>
      <label>Description
        <RichTextEditor :key="editorKey" v-model="item.description" />
      </label>
      <label>Short description (BrickLink)
        <textarea class="short-description" v-model="item.shortDescription" maxlength="255" rows="2" />
        <span class="muted">{{ (item.shortDescription || "").length }}/255</span>
      </label>
    </div>

    <div class="card grid">
      <div style="display:flex;justify-content:space-between;align-items:baseline;gap:1rem;flex-wrap:wrap">
        <h3 style="margin:0">Market prices <span class="muted" style="font-size:1rem;font-weight:400">{{ marketScanLabel }}</span></h3>
        <router-link class="btn secondary compact" :to="`/market/${item.catalog.id}`">Open market</router-link>
      </div>
      <div v-if="hasMarketStats" class="grid two">
        <label>Median
          <input :value="money(combinedMedian)" disabled />
        </label>
        <label>Average
          <input :value="money(combinedAverage)" disabled />
        </label>
      </div>
      <p v-else class="muted">Market statistics will show here after the first scan for this set.</p>
    </div>

    <div class="card grid">
      <div class="grid three">
        <label>
          <span class="channel-field-label"><ChannelLogo platform="EBAY" :height="16" /> price (default 45% margin)</span>
          <input v-model.number="item.ebayPrice" type="number" step="0.01" />
        </label>
        <label>
          <span class="channel-field-label"><ChannelLogo platform="BRICKLINK" :height="16" /> price (default 40% margin)</span>
          <input v-model.number="item.bricklinkPrice" type="number" step="0.01" />
        </label>
        <label>
          <span class="channel-field-label"><ChannelLogo platform="SHOPIFY" :height="16" /> price (default 32% margin)</span>
          <input v-model.number="item.shopifyPrice" type="number" step="0.01" />
        </label>
      </div>
      <div class="grid three">
        <label>Cost <input v-model.number="item.cost" type="number" step="0.01" /></label>
        <label>Quantity <input v-model.number="item.quantity" type="number" min="0" /></label>
        <label>eBay Minimum offer (default 90% eBay price) <input v-model.number="item.minimumOffer" type="number" step="0.01" /></label>
      </div>
      <div class="grid four">
        <label>Condition
          <select v-model="item.condition">
            <option v-for="c in CONDITIONS" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label>Shopify Product Type
          <select v-model="item.itemType">
            <option>SET</option>
            <option>POLYBAG</option>
          </select>
        </label>
        <EbayStoreCategoryField v-model="item.ebayStoreCategory" />
        <ShopifyCollectionsField v-model="item.shopifyCollectionIds" />
      </div>
    </div>

    <div class="card grid">
      <h3>Shipping Dimensions and Weight</h3>
      <p v-if="item.catalog.bricklinkPackage?.original" class="muted">
        BrickLink original:
        {{ item.catalog.bricklinkPackage.original.lbs }} lb
        {{ item.catalog.bricklinkPackage.original.oz }} oz
        ·
        {{ item.catalog.bricklinkPackage.original.length }} ×
        {{ item.catalog.bricklinkPackage.original.width }} ×
        {{ item.catalog.bricklinkPackage.original.height }} in
      </p>
      <div class="grid five">
        <label>lbs <input v-model.number="item.packageLbs" type="number" /></label>
        <label>oz <input v-model.number="item.packageOz" type="number" /></label>
        <label>L <input v-model.number="item.packageLength" type="number" step="0.1" /></label>
        <label>W <input v-model.number="item.packageWidth" type="number" /></label>
        <label>H <input v-model.number="item.packageHeight" type="number" /></label>
      </div>
    </div>

    <div class="card grid">
      <h3>Photos</h3>
      <PhotoCapture :disabled="uploading" @files="upload" />
      <div class="photos">
        <div v-for="photo in item.photos" :key="photo.id" class="photo-tile">
          <img
            :src="photo.url"
            :alt="photo.filename || 'Listing photo'"
            :class="{ primary: photo.primaryForBricklink }"
            @click="makePrimary(photo.id)"
          />
          <button
            class="photo-delete"
            type="button"
            title="Delete photo"
            @click.stop="removePhoto(photo)"
          >
            ×
          </button>
        </div>
      </div>
    </div>

    <div class="card grid">
      <label>Notes <textarea v-model="item.notes" /></label>
      <div class="save-row">
        <button
          class="btn secondary"
          :class="{ saved: justSaved }"
          type="button"
          :disabled="saving"
          @click="saveChanges"
        >
          {{ saving ? "Saving…" : justSaved ? "Saved" : "Save changes" }}
        </button>
        <span v-if="justSaved" class="save-note">Changes saved</span>
        <button class="btn danger compact" type="button" @click="remove">Delete item</button>
      </div>
    </div>
    </div>

    <div class="card grid">
      <h3>Listing channels</h3>
      <div class="channel-picks">
        <label v-for="p in ['SHOPIFY','BRICKLINK','EBAY']" :key="p" class="channel-pick">
          <input type="checkbox" :value="p" v-model="platforms" />
          <ChannelLogo :platform="p" />
        </label>
      </div>
      <div style="display:flex;gap:0.6rem;flex-wrap:wrap">
        <button class="btn gold" type="button" :disabled="publishing || ebayCatalogLoading || !platforms.length" @click="publish">
          {{ publishing || ebayCatalogLoading ? "Working…" : "Create Listing" }}
        </button>
        <button
          class="btn secondary"
          type="button"
          :disabled="publishing || !selectedUpdatable.length"
          @click="updateSelected"
        >
          Update listings
        </button>
        <button class="btn secondary" type="button" :disabled="publishing || linking" @click="openLinkListing">
          Link existing listing
        </button>
      </div>
      <table>
        <thead><tr><th>Channel</th><th>Status</th><th>Link</th><th></th></tr></thead>
        <tbody>
          <tr v-for="listing in listings" :key="listing.id">
            <td><ChannelLogo :platform="listing.platform" /></td>
            <td>
              <div class="shopify-status">
                <span class="badge" :class="{ ok: listing.status === 'PUBLISHED', bad: listing.status === 'FAILED' }">{{ listing.status }}</span>
                <span
                  v-if="listing.platform === 'SHOPIFY' && listing.shopifyStatus"
                  class="badge"
                  :class="{ ok: listing.shopifyStatus === 'ACTIVE', warn: listing.shopifyStatus === 'UNLISTED' }"
                >{{ visibilityStatusLabel(listing.shopifyStatus) }}</span>
                <span
                  v-if="listing.platform === 'BRICKLINK' && listing.bricklinkStatus"
                  class="badge"
                  :class="{ ok: listing.bricklinkStatus === 'ACTIVE', warn: listing.bricklinkStatus === 'UNLISTED' }"
                >{{ visibilityStatusLabel(listing.bricklinkStatus) }}</span>
                <span
                  v-if="listing.platform === 'EBAY' && listing.ebayStatus"
                  class="badge"
                  :class="{ ok: listing.ebayStatus === 'ACTIVE', warn: listing.ebayStatus === 'UNLISTED' }"
                >{{ visibilityStatusLabel(listing.ebayStatus) }}</span>
              </div>
            </td>
            <td>
              <a v-if="listing.liveUrl" :href="listing.liveUrl" target="_blank" rel="noreferrer">Listing</a>
              <a v-if="listing.bricklinkPhotoUploadUrl" :href="listing.bricklinkPhotoUploadUrl" target="_blank"> Upload BrickLink photo</a>
              <div v-if="listing.lastError" class="error">{{ listing.lastError }}</div>
            </td>
            <td>
              <div class="shopify-status">
                <button
                  v-if="canToggleShopify(listing)"
                  class="btn secondary compact"
                  type="button"
                  :disabled="publishing"
                  @click="toggleShopify(listing)"
                >
                  {{ visibilityActionLabel(listing.shopifyStatus) }}
                </button>
                <button
                  v-if="canToggleBricklink(listing)"
                  class="btn secondary compact"
                  type="button"
                  :disabled="publishing"
                  @click="toggleBricklink(listing)"
                >
                  {{ visibilityActionLabel(listing.bricklinkStatus) }}
                </button>
                <button
                  v-if="canToggleEbay(listing)"
                  class="btn secondary compact"
                  type="button"
                  :disabled="publishing"
                  @click="toggleEbay(listing)"
                >
                  {{ visibilityActionLabel(listing.ebayStatus) }}
                </button>
                <button
                  v-if="canUpdateListing(listing)"
                  class="btn secondary compact"
                  type="button"
                  :disabled="publishing"
                  @click="updateListing(listing)"
                >
                  Update
                </button>
                <button
                  v-if="canDeleteShopify(listing)"
                  class="btn danger compact"
                  type="button"
                  :disabled="publishing"
                  @click="deleteShopifyListing(listing)"
                >
                  Delete listing
                </button>
                <button
                  v-if="canDeleteBricklink(listing)"
                  class="btn danger compact"
                  type="button"
                  :disabled="publishing"
                  @click="deleteBricklinkListing(listing)"
                >
                  Delete listing
                </button>
                <button
                  v-if="canDeleteEbay(listing)"
                  class="btn danger compact"
                  type="button"
                  :disabled="publishing"
                  @click="deleteEbayListing(listing)"
                >
                  Delete listing
                </button>
                <button
                  v-if="canRetry(listing)"
                  class="btn secondary compact"
                  type="button"
                  :disabled="publishing || ebayCatalogLoading"
                  @click="retry(listing.platform)"
                >
                  Retry
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
  <div v-if="showEbayCatalog" class="modal-backdrop">
    <div class="modal catalog-preview card grid">
      <h3>Review eBay catalog</h3>
      <div v-if="ebayCatalogLoading" class="loading-bar" aria-live="polite" aria-label="Loading eBay catalog">
        <span></span>
      </div>
      <p v-if="ebayCatalogLoading" class="muted">Searching eBay for a catalog product for this set…</p>
      <template v-else-if="ebayCatalog">
        <p>
          <span class="badge" :class="ebayCatalog.catalogMatch ? 'ok' : 'warn'">
            {{ ebayCatalog.catalogMatch ? "eBay catalog match" : "BrickEconomy fallback" }}
          </span>
        </p>
        <p>{{ ebayCatalog.summary }}</p>
        <table class="catalog-aspects">
          <tbody>
            <tr><th>Set</th><td>{{ ebayCatalog.setNumber }}</td></tr>
            <tr><th>Title</th><td>{{ ebayCatalog.title }}</td></tr>
            <tr><th>Condition</th><td>{{ ebayCatalog.condition }}</td></tr>
            <tr><th>ePID</th><td>{{ ebayCatalog.epid || "—" }}</td></tr>
            <tr><th>Brand</th><td>{{ ebayCatalog.brand || "LEGO" }}</td></tr>
            <tr><th>MPN</th><td>{{ ebayCatalog.mpn || "—" }}</td></tr>
            <tr><th>UPC</th><td>{{ joinValues(ebayCatalog.upc) }}</td></tr>
            <tr><th>EAN</th><td>{{ joinValues(ebayCatalog.ean) }}</td></tr>
            <tr v-for="aspect in ebayCatalog.aspects" :key="aspect.name">
              <th>{{ aspect.name }}</th>
              <td>{{ joinValues(aspect.values) }}</td>
            </tr>
          </tbody>
        </table>
        <div style="display:flex;gap:0.6rem;flex-wrap:wrap">
          <button class="btn gold" type="button" @click="acceptEbayCatalog(false)">
            {{ ebayCatalog.catalogMatch ? "Use catalog product" : "Continue publishing" }}
          </button>
          <button
            v-if="ebayCatalog.catalogMatch"
            class="btn secondary"
            type="button"
            @click="acceptEbayCatalog(true)"
          >
            Use my listing details
          </button>
          <button class="btn secondary" type="button" @click="rejectEbayCatalog">Cancel</button>
        </div>
        <p v-if="ebayCatalog.catalogMatch" class="muted">
          Use catalog product keeps eBay’s ePID and item specifics. Use my listing details
          publishes with this item’s title, photos, description, and specifics from the vault.
        </p>
      </template>
    </div>
  </div>
  <div v-if="showLinkListing" class="modal-backdrop" @click.self="linking ? undefined : (showLinkListing = false)">
    <div class="modal card grid">
      <h3>Link existing listing</h3>
      <p class="muted">
        Attach a listing that already exists on the channel. This does not publish a new listing.
      </p>
      <label>
        Channel
        <select v-model="linkPlatform">
          <option value="BRICKLINK">BrickLink</option>
          <option value="EBAY">eBay</option>
          <option value="SHOPIFY">Shopify</option>
        </select>
      </label>
      <label>
        Listing URL or id
        <input v-model="linkReference" type="text" :placeholder="linkHint" @keydown.enter.prevent="submitLinkListing()" />
      </label>
      <p class="muted">{{ linkHint }}</p>
      <p v-if="linkError" class="error">{{ linkError }}</p>
      <div style="display:flex;gap:0.6rem;flex-wrap:wrap">
        <button class="btn gold" type="button" :disabled="linking" @click="submitLinkListing()">
          {{ linking ? "Linking…" : "Link listing" }}
        </button>
        <button class="btn secondary" type="button" :disabled="linking" @click="showLinkListing = false">Cancel</button>
      </div>
    </div>
  </div>
  <div v-if="showPublishLog" class="modal-backdrop" @click.self="publishing ? undefined : (showPublishLog = false)">
    <div class="modal card grid">
      <h3>{{ activityTitle }}</h3>
      <div v-if="publishing" class="loading-bar" aria-live="polite" aria-label="Working">
        <span></span>
      </div>
      <p v-if="publishing" class="muted">Waiting for channel APIs to respond…</p>
      <div class="publish-log">
        <p v-for="(line, index) in publishLogs" :key="index" :class="line.kind">
          <span class="muted">{{ line.time }}</span> {{ line.text }}
        </p>
      </div>
      <button class="btn secondary" type="button" :disabled="publishing" @click="showPublishLog = false">Close</button>
    </div>
  </div>
  <ScanProgressModal
    :open="uploading"
    :title="uploadTitle"
    :message="uploadMessage"
    :percent="uploadPercent"
  />
</template>
