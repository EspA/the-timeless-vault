<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { api, type SetWatch } from "../api";
import ScanProgressModal from "../components/ScanProgressModal.vue";
import ChannelLogo from "../components/ChannelLogo.vue";

type Listing = {
  id: string;
  platform: string;
  title?: string;
  price?: number;
  quantity?: number;
  condition?: string;
  seller?: string;
  sellerCountry?: string;
  url?: string;
  imageUrl?: string;
  sellerFeedbackScore?: number;
  sellerFeedbackPercentage?: string;
  auction?: boolean;
  currentBid?: number;
  bestOffer?: boolean;
  shippingCost?: number;
  shippingCalculated?: boolean;
  own: boolean;
};
type Snapshot = { min?: number; avg?: number; median?: number; max?: number; count?: number; scannedAt?: string };
type Dashboard = {
  catalogId: string;
  setNumber: string;
  name: string;
  ebay?: Snapshot;
  bricklink?: Snapshot;
  ebayListings: Listing[];
  bricklinkListings: Listing[];
  ebayError?: string;
  bricklinkError?: string;
};
type Sort = { key: string; dir: "asc" | "desc" };
type EbayFilters = {
  photo: string;
  title: string;
  price: string;
  shipping: string;
  total: string;
  lastBid: string;
  bestOffer: string;
  feedback: string;
  seller: string;
};
type BricklinkFilters = {
  photo: string;
  title: string;
  country: string;
  price: string;
  condition: string;
  seller: string;
  qty: string;
};

const emptyEbayFilters = (): EbayFilters => ({
  photo: "",
  title: "",
  price: "",
  shipping: "",
  total: "",
  lastBid: "",
  bestOffer: "",
  feedback: "",
  seller: "",
});
const emptyBricklinkFilters = (): BricklinkFilters => ({
  photo: "",
  title: "",
  country: "",
  price: "",
  condition: "",
  seller: "",
  qty: "",
});

const PAGE_SIZE = 10;
const route = useRoute();
const watches = ref<SetWatch[]>([]);
const selected = ref("");
const dash = ref<Dashboard | null>(null);
const error = ref("");
const ebaySort = ref<Sort>({ key: "total", dir: "asc" });
const bricklinkSort = ref<Sort>({ key: "price", dir: "asc" });
const ebayFilters = ref(emptyEbayFilters());
const bricklinkFilters = ref(emptyBricklinkFilters());
const ebayPage = ref(1);
const bricklinkPage = ref(1);
const listingTab = ref<"ebay" | "bricklink">("ebay");
const minPriceInput = ref<number | null>(null);
const maxPriceInput = ref<number | null>(null);
const scanning = ref<"ebay" | "bricklink" | "all" | "">("");
const applyingAlertRange = ref(false);
const filterMessage = ref("");

const loadDash = async (catalogId: string) => {
  selected.value = catalogId;
  dash.value = await api.get<Dashboard>(`/api/market/${catalogId}`);
};

const scan = async (target: "ebay" | "bricklink" | "all") => {
  if (!selected.value || scanning.value) return;
  error.value = "";
  scanning.value = target;
  try {
    const query = target === "all" ? "" : `?platform=${target}`;
    dash.value = await api.post<Dashboard>(`/api/market/${selected.value}/scan${query}`);
    const scannedAt = dash.value.ebay?.scannedAt || dash.value.bricklink?.scannedAt;
    if (selectedWatch.value && scannedAt) {
      selectedWatch.value.lastScannedAt = scannedAt;
    }
    if (target === "bricklink") {
      listingTab.value = "bricklink";
    } else {
      listingTab.value = "ebay";
    }
    if (dash.value.ebayError || dash.value.bricklinkError) {
      error.value = [dash.value.ebayError, dash.value.bricklinkError].filter(Boolean).join(" ");
    }
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    scanning.value = "";
  }
};

const scanMessage = computed(() => {
  if (scanning.value === "ebay") return "Waiting for eBay to respond…";
  if (scanning.value === "bricklink") return "Waiting for BrickLink to respond…";
  if (scanning.value === "all") return "Waiting for eBay and BrickLink to respond…";
  return "";
});

const scanTitle = computed(() => {
  if (scanning.value === "ebay") return "Scanning eBay";
  if (scanning.value === "bricklink") return "Scanning BrickLink";
  if (scanning.value === "all") return "Scanning all platforms";
  return "Scanning market";
});

const money = (value?: number | null) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const medianOf = (stored: number | undefined, rows: Listing[]) => {
  if (stored != null && !Number.isNaN(Number(stored))) {
    return stored;
  }
  const prices = rows
    .map((row) => row.price)
    .filter((price): price is number => price != null && !Number.isNaN(Number(price)))
    .sort((a, b) => a - b);
  if (!prices.length) {
    return null;
  }
  const mid = Math.floor(prices.length / 2);
  return prices.length % 2 === 1 ? prices[mid] : (prices[mid - 1] + prices[mid]) / 2;
};

const ebayMedian = computed(() => medianOf(dash.value?.ebay?.median, dash.value?.ebayListings ?? []));
const bricklinkMedian = computed(() => medianOf(dash.value?.bricklink?.median, dash.value?.bricklinkListings ?? []));

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
  { value: dash.value?.ebay?.avg ?? null, count: dash.value?.ebay?.count ?? 0 },
  { value: dash.value?.bricklink?.avg ?? null, count: dash.value?.bricklink?.count ?? 0 }
));

const combinedMedian = computed(() => weightedMean(
  { value: ebayMedian.value, count: dash.value?.ebay?.count ?? 0 },
  { value: bricklinkMedian.value, count: dash.value?.bricklink?.count ?? 0 }
));

const averageMedian = computed(() => {
  const medians = [ebayMedian.value, bricklinkMedian.value]
    .filter((value): value is number => value != null && !Number.isNaN(Number(value)));
  if (!medians.length) {
    return null;
  }
  return medians.reduce((sum, value) => sum + value, 0) / medians.length;
});

const recommendedMaxPrice = computed(() => {
  if (averageMedian.value == null) {
    return null;
  }
  return Math.round(averageMedian.value * 0.75 * 100) / 100;
});

const useRecommendedMaxPrice = () => {
  if (recommendedMaxPrice.value == null) {
    return;
  }
  maxPriceInput.value = recommendedMaxPrice.value;
};

const feedback = (row: Listing) => {
  if (row.sellerFeedbackScore == null && !row.sellerFeedbackPercentage) {
    return "—";
  }
  const score = row.sellerFeedbackScore ?? "—";
  const pct = row.sellerFeedbackPercentage ? `${row.sellerFeedbackPercentage}%` : "—";
  return `${score} / ${pct}`;
};

const lastBid = (row: Listing) => (row.auction ? money(row.currentBid) : "—");

const shipping = (row: Listing) => {
  if (row.shippingCalculated) return "Calculated";
  if (row.shippingCost == null) return "—";
  if (Number(row.shippingCost) === 0) return "Free";
  return money(row.shippingCost);
};

const shippingAmount = (row: Listing) => {
  if (row.shippingCalculated || row.shippingCost == null) {
    return null;
  }
  return Number(row.shippingCost);
};

const totalCost = (row: Listing) => {
  if (row.price == null || Number.isNaN(Number(row.price))) {
    return null;
  }
  const ship = shippingAmount(row);
  if (ship == null) {
    return null;
  }
  return Number(row.price) + ship;
};

const sortValue = (row: Listing, key: string): unknown => {
  switch (key) {
    case "photo":
      return row.imageUrl ? 1 : 0;
    case "title":
      return row.title || "";
    case "price":
      return row.price ?? null;
    case "lastBid":
      return row.auction ? row.currentBid ?? null : null;
    case "bestOffer":
      return row.bestOffer ? 1 : 0;
    case "shipping":
      return row.shippingCalculated ? Number.POSITIVE_INFINITY : row.shippingCost ?? null;
    case "total":
      return totalCost(row);
    case "feedback":
      return row.sellerFeedbackScore ?? null;
    case "seller":
      return row.seller || "";
    case "country":
      return row.sellerCountry || "";
    case "condition":
      return row.condition || "";
    case "qty":
      return row.quantity ?? null;
    default:
      return null;
  }
};

const compareValues = (left: unknown, right: unknown) => {
  if (left == null && right == null) return 0;
  if (left == null) return 1;
  if (right == null) return -1;
  if (typeof left === "number" && typeof right === "number") {
    return left - right;
  }
  return String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: "base" });
};

const contains = (value: unknown, needle: string) => {
  if (!needle.trim()) return true;
  return String(value ?? "").toLowerCase().includes(needle.trim().toLowerCase());
};

const matchesPhoto = (row: Listing, filter: string) => {
  if (!filter) return true;
  const has = Boolean(row.imageUrl);
  return filter === "yes" ? has : !has;
};

const matchesPriceRange = (price?: number | null) => {
  if (minPriceInput.value == null && maxPriceInput.value == null) {
    return true;
  }
  if (price == null || Number.isNaN(Number(price))) {
    return false;
  }
  if (minPriceInput.value != null && price < minPriceInput.value) {
    return false;
  }
  return maxPriceInput.value == null || price <= maxPriceInput.value;
};

const matchesEbay = (row: Listing, filters: EbayFilters) =>
  matchesPriceRange(row.price)
  && matchesPhoto(row, filters.photo)
  && contains(row.title, filters.title)
  && contains(money(row.price), filters.price)
  && contains(shipping(row), filters.shipping)
  && contains(money(totalCost(row)), filters.total)
  && contains(lastBid(row), filters.lastBid)
  && (!filters.bestOffer || (filters.bestOffer === "yes") === Boolean(row.bestOffer))
  && contains(feedback(row), filters.feedback)
  && contains(row.seller, filters.seller);

const matchesBricklink = (row: Listing, filters: BricklinkFilters) =>
  matchesPriceRange(row.price)
  && matchesPhoto(row, filters.photo)
  && contains(row.title, filters.title)
  && contains(row.sellerCountry, filters.country)
  && contains(money(row.price), filters.price)
  && (!filters.condition || row.condition === filters.condition)
  && contains(row.seller, filters.seller)
  && contains(row.quantity, filters.qty);

const uniqueOptions = (rows: Listing[], pick: (row: Listing) => string | undefined) =>
  [...new Set(rows.map((row) => pick(row)?.trim() || "").filter(Boolean))]
    .sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));

const sortedListings = (rows: Listing[], sort: Sort) =>
  [...rows].sort((a, b) => {
    const cmp = compareValues(sortValue(a, sort.key), sortValue(b, sort.key));
    return sort.dir === "asc" ? cmp : -cmp;
  });

const paginate = (rows: Listing[], page: number) => {
  const start = (page - 1) * PAGE_SIZE;
  return rows.slice(start, start + PAGE_SIZE);
};

const ebayFiltered = computed(() =>
  (dash.value?.ebayListings ?? []).filter((row) => matchesEbay(row, ebayFilters.value))
);
const bricklinkFiltered = computed(() =>
  (dash.value?.bricklinkListings ?? []).filter((row) => matchesBricklink(row, bricklinkFilters.value))
);
const ebaySorted = computed(() => sortedListings(ebayFiltered.value, ebaySort.value));
const bricklinkSorted = computed(() => sortedListings(bricklinkFiltered.value, bricklinkSort.value));
const bricklinkCountries = computed(() => uniqueOptions(dash.value?.bricklinkListings ?? [], (row) => row.sellerCountry));
const bricklinkConditions = computed(() => uniqueOptions(dash.value?.bricklinkListings ?? [], (row) => row.condition));
const ebayFilterCount = computed(() => Object.values(ebayFilters.value).filter((value) => value.trim()).length);
const bricklinkFilterCount = computed(() => Object.values(bricklinkFilters.value).filter((value) => value.trim()).length);
const ebayPages = computed(() => Math.max(1, Math.ceil(ebaySorted.value.length / PAGE_SIZE)));
const bricklinkPages = computed(() => Math.max(1, Math.ceil(bricklinkSorted.value.length / PAGE_SIZE)));
const ebayListings = computed(() => paginate(ebaySorted.value, ebayPage.value));
const bricklinkListings = computed(() => paginate(bricklinkSorted.value, bricklinkPage.value));

const rangeLabel = (page: number, total: number) => {
  if (!total) return "0 listings";
  const start = (page - 1) * PAGE_SIZE + 1;
  const end = Math.min(page * PAGE_SIZE, total);
  return `${start}–${end} of ${total}`;
};

const toggleSort = (current: Sort, key: string): Sort =>
  current.key === key
    ? { key, dir: current.dir === "asc" ? "desc" : "asc" }
    : { key, dir: "asc" };

const sortEbay = (key: string) => {
  ebaySort.value = toggleSort(ebaySort.value, key);
  ebayPage.value = 1;
};

const sortBricklink = (key: string) => {
  bricklinkSort.value = toggleSort(bricklinkSort.value, key);
  bricklinkPage.value = 1;
};

const sortMark = (sort: Sort, key: string) => {
  if (sort.key !== key) return "";
  return sort.dir === "asc" ? " ▲" : " ▼";
};

const clearEbayFilters = () => {
  ebayFilters.value = emptyEbayFilters();
};
const clearBricklinkFilters = () => {
  bricklinkFilters.value = emptyBricklinkFilters();
};

watch(ebayPages, (pages) => {
  if (ebayPage.value > pages) ebayPage.value = pages;
});
watch(bricklinkPages, (pages) => {
  if (bricklinkPage.value > pages) bricklinkPage.value = pages;
});
watch(
  () => dash.value?.catalogId,
  () => {
    ebayPage.value = 1;
    bricklinkPage.value = 1;
    ebayFilters.value = emptyEbayFilters();
    bricklinkFilters.value = emptyBricklinkFilters();
    minPriceInput.value = null;
    maxPriceInput.value = null;
    filterMessage.value = "";
  }
);
watch(ebayFilters, () => { ebayPage.value = 1; }, { deep: true });
watch(bricklinkFilters, () => { bricklinkPage.value = 1; }, { deep: true });
watch([minPriceInput, maxPriceInput], () => {
  ebayPage.value = 1;
  bricklinkPage.value = 1;
});

const selectedWatch = computed(() =>
  watches.value.find((watch) => watch.catalogId === selected.value) ?? null
);

const parsePrice = (raw: string) => {
  if (!raw.trim()) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
};

const applyRangeForAlert = async () => {
  const watch = selectedWatch.value;
  if (!watch || applyingAlertRange.value) return;
  error.value = "";
  filterMessage.value = "";
  applyingAlertRange.value = true;
  try {
    const saved = await api.put<SetWatch>(`/api/set-watches/${watch.id}`, {
      enabled: watch.enabled,
      ebaySearchQuery: watch.ebaySearchQuery,
      ebayExcludeWords: watch.ebayExcludeWords,
      ebayFeedbackMin: watch.ebayFeedbackMin,
      ebayScanIntervalMinutes: watch.ebayScanIntervalMinutes,
      bricklinkScanIntervalMinutes: watch.bricklinkScanIntervalMinutes,
      minPrice: minPriceInput.value,
      maxPrice: maxPriceInput.value,
    });
    watches.value = watches.value.map((row) => (row.id === saved.id ? saved : row));
    filterMessage.value = "Alert price range updated.";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not save alert price range";
  } finally {
    applyingAlertRange.value = false;
  }
};

const lastScannedAt = computed(() => {
  const times = [
    dash.value?.ebay?.scannedAt,
    dash.value?.bricklink?.scannedAt,
    selectedWatch.value?.lastScannedAt,
  ].filter((value): value is string => Boolean(value));
  if (!times.length) {
    return null;
  }
  return times.reduce((latest, time) => (time > latest ? time : latest));
});

const lastScanLabel = computed(() => {
  if (!lastScannedAt.value) {
    return "No scan yet.";
  }
  const parsed = new Date(lastScannedAt.value);
  if (Number.isNaN(parsed.getTime())) {
    return "No scan yet.";
  }
  return `Last scan ${parsed.toLocaleString()}`;
});

onMounted(async () => {
  try {
    watches.value = await api.get<SetWatch[]>("/api/set-watches");
    const fromRoute = String(route.params.catalogId || "");
    const first = fromRoute || watches.value[0]?.catalogId;
    if (first) await loadDash(first);
  } catch (e) {
    error.value = (e as Error).message;
  }
});
</script>

<template>
  <div class="grid">
    <h1>Market Monitoring</h1>
    <p v-if="selectedWatch" class="muted">{{ lastScanLabel }}</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="filterMessage" class="muted">{{ filterMessage }}</p>
    <p v-if="!watches.length" class="muted">No sets are watched yet. Add one with <router-link to="/watches">New item watch</router-link>.</p>
    <div class="card grid">
      <div class="toolbar">
        <label>Watched set
          <select :value="selected" :disabled="!!scanning" @change="loadDash(($event.target as HTMLSelectElement).value)">
            <option v-for="watch in watches" :key="watch.catalogId" :value="watch.catalogId">
              {{ watch.setNumber }} {{ watch.name }}
            </option>
          </select>
        </label>
        <button class="btn secondary channel-scan" type="button" :disabled="!!scanning" @click="scan('ebay')">
          {{ scanning === "ebay" ? "Scanning" : "Scan" }}
          <ChannelLogo platform="EBAY" :height="16" />
        </button>
        <button class="btn secondary channel-scan" type="button" :disabled="!!scanning" @click="scan('bricklink')">
          {{ scanning === "bricklink" ? "Scanning" : "Scan" }}
          <ChannelLogo platform="BRICKLINK" :height="16" />
        </button>
        <button class="btn gold" type="button" :disabled="!!scanning" @click="scan('all')">
          {{ scanning === "all" ? "Scanning…" : "Scan all" }}
        </button>
        <router-link
          v-if="selectedWatch"
          class="btn secondary"
          :to="`/watches/${selectedWatch.id}`"
        >Market filters</router-link>
      </div>
      <div v-if="selectedWatch" class="toolbar">
        <label>Min price
          <input
            :value="minPriceInput ?? ''"
            type="number"
            min="0"
            step="0.01"
            placeholder="No minimum"
            @input="minPriceInput = parsePrice(($event.target as HTMLInputElement).value)"
          />
        </label>
        <label>Max price
          <input
            :value="maxPriceInput ?? ''"
            type="number"
            min="0"
            step="0.01"
            placeholder="No maximum"
            @input="maxPriceInput = parsePrice(($event.target as HTMLInputElement).value)"
          />
        </label>
        <button
          v-if="recommendedMaxPrice != null"
          class="btn secondary"
          type="button"
          :disabled="!!scanning"
          @click="useRecommendedMaxPrice"
        >
          Use recommended {{ money(recommendedMaxPrice) }}
        </button>
        <button
          class="btn gold"
          type="button"
          :disabled="!!scanning || applyingAlertRange"
          @click="applyRangeForAlert"
        >
          {{ applyingAlertRange ? "Saving…" : "Apply range for alert" }}
        </button>
      </div>
    </div>
    <ScanProgressModal :open="!!scanning" :title="scanTitle" :message="scanMessage" />

    <div v-if="dash" class="grid">
      <div class="card grid">
        <p v-if="dash.ebayError" class="error">{{ dash.ebayError }}</p>
        <p v-if="dash.bricklinkError" class="error">{{ dash.bricklinkError }}</p>
        <div class="grid three">
          <div class="stat">
            <span class="stat-value hero">{{ money(combinedMedian) }}</span>
            <span class="stat-label">Median</span>
          </div>
          <div class="stat">
            <span class="stat-value hero">{{ money(combinedAverage) }}</span>
            <span class="stat-label">Average</span>
          </div>
          <div class="stat">
            <span class="stat-value hero">{{ money(recommendedMaxPrice) }}</span>
            <span class="stat-label">Max recommended</span>
          </div>
        </div>
        <div class="platform-stats">
          <div class="grid">
            <h4 class="channel-heading">
              <ChannelLogo platform="EBAY" :height="18" />
              <span class="muted">{{ dash.ebay?.count != null ? `${dash.ebay.count} listings` : "No scan yet" }}</span>
            </h4>
            <div class="grid four stats">
              <div class="stat">
                <span class="stat-value">{{ money(dash.ebay?.min) }}</span>
                <span class="stat-label">Min</span>
              </div>
              <div class="stat">
                <span class="stat-value">{{ money(dash.ebay?.avg) }}</span>
                <span class="stat-label">Average</span>
              </div>
              <div class="stat">
                <span class="stat-value">{{ money(ebayMedian) }}</span>
                <span class="stat-label">Median</span>
              </div>
              <div class="stat">
                <span class="stat-value">{{ money(dash.ebay?.max) }}</span>
                <span class="stat-label">Max</span>
              </div>
            </div>
          </div>
          <div class="grid">
            <h4 class="channel-heading">
              <ChannelLogo platform="BRICKLINK" :height="18" />
              <span class="muted">{{ dash.bricklink?.count != null ? `${dash.bricklink.count} listings` : "No scan yet" }}</span>
            </h4>
            <div class="grid four stats">
              <div class="stat">
                <span class="stat-value">{{ money(dash.bricklink?.min) }}</span>
                <span class="stat-label">Min</span>
              </div>
              <div class="stat">
                <span class="stat-value">{{ money(dash.bricklink?.avg) }}</span>
                <span class="stat-label">Average</span>
              </div>
              <div class="stat">
                <span class="stat-value">{{ money(bricklinkMedian) }}</span>
                <span class="stat-label">Median</span>
              </div>
              <div class="stat">
                <span class="stat-value">{{ money(dash.bricklink?.max) }}</span>
                <span class="stat-label">Max</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="tabs">
          <button class="tab" type="button" :class="{ on: listingTab === 'ebay' }" @click="listingTab = 'ebay'">
            <ChannelLogo platform="EBAY" :height="16" />
          </button>
          <button class="tab" type="button" :class="{ on: listingTab === 'bricklink' }" @click="listingTab = 'bricklink'">
            <ChannelLogo platform="BRICKLINK" :height="16" />
          </button>
        </div>
        <div v-if="listingTab === 'ebay'">
        <p v-if="dash.ebayError" class="error">{{ dash.ebayError }}</p>
        <div class="table-scroll desktop-only">
          <table>
            <thead>
              <tr>
                <th><button class="sort-btn" type="button" @click="sortEbay('photo')">Photo{{ sortMark(ebaySort, 'photo') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('title')">Title{{ sortMark(ebaySort, 'title') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('price')">Price{{ sortMark(ebaySort, 'price') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('shipping')">Shipping{{ sortMark(ebaySort, 'shipping') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('total')">Total{{ sortMark(ebaySort, 'total') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('lastBid')">Last bid{{ sortMark(ebaySort, 'lastBid') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('bestOffer')">Best offer{{ sortMark(ebaySort, 'bestOffer') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('feedback')">Feedback{{ sortMark(ebaySort, 'feedback') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortEbay('seller')">Seller{{ sortMark(ebaySort, 'seller') }}</button></th>
              </tr>
              <tr>
                <th>
                  <select v-model="ebayFilters.photo" class="column-filter">
                    <option value="">All</option>
                    <option value="yes">Has photo</option>
                    <option value="no">No photo</option>
                  </select>
                </th>
                <th><input v-model="ebayFilters.title" class="column-filter" type="search" placeholder="Filter" /></th>
                <th><input v-model="ebayFilters.price" class="column-filter" type="search" placeholder="Filter" /></th>
                <th><input v-model="ebayFilters.shipping" class="column-filter" type="search" placeholder="Filter" /></th>
                <th><input v-model="ebayFilters.total" class="column-filter" type="search" placeholder="Filter" /></th>
                <th><input v-model="ebayFilters.lastBid" class="column-filter" type="search" placeholder="Filter" /></th>
                <th>
                  <select v-model="ebayFilters.bestOffer" class="column-filter">
                    <option value="">All</option>
                    <option value="yes">Yes</option>
                    <option value="no">No</option>
                  </select>
                </th>
                <th><input v-model="ebayFilters.feedback" class="column-filter" type="search" placeholder="Filter" /></th>
                <th><input v-model="ebayFilters.seller" class="column-filter" type="search" placeholder="Filter" /></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in ebayListings" :key="row.id" :style="{ fontWeight: row.own ? '700' : '400' }">
                <td>
                  <img v-if="row.imageUrl" class="listing-thumb" :src="row.imageUrl" :alt="row.title || 'Listing photo'" />
                  <span v-else class="muted">—</span>
                </td>
                <td class="listing-title">
                  <a v-if="row.url" :href="row.url" target="_blank">{{ row.title || "Untitled" }}</a>
                  <span v-else>{{ row.title || "—" }}</span>
                </td>
                <td>{{ money(row.price) }}</td>
                <td>{{ shipping(row) }}</td>
                <td>{{ money(totalCost(row)) }}</td>
                <td>{{ lastBid(row) }}</td>
                <td>{{ row.bestOffer ? "Yes" : "No" }}</td>
                <td>{{ feedback(row) }}</td>
                <td>{{ row.seller || "—" }}</td>
              </tr>
              <tr v-if="!ebaySorted.length">
                <td colspan="9" class="muted">{{ dash.ebayListings.length ? "No eBay listings match those filters." : "No eBay listings in this scan." }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="list-cards mobile-only">
          <article v-for="row in ebayListings" :key="row.id" class="list-card listing-card" :style="{ fontWeight: row.own ? '700' : '400' }">
            <img v-if="row.imageUrl" class="listing-thumb" :src="row.imageUrl" :alt="row.title || 'Listing photo'" />
            <span v-else class="muted">—</span>
            <div class="grid" style="gap:0.35rem">
              <h3>
                <a v-if="row.url" :href="row.url" target="_blank">{{ row.title || "Untitled" }}</a>
                <span v-else>{{ row.title || "—" }}</span>
              </h3>
              <div class="list-card-meta">
                <strong>{{ money(totalCost(row)) }}</strong>
                <span class="muted">{{ money(row.price) }} + {{ shipping(row) }}</span>
              </div>
              <div class="list-card-meta muted">{{ row.seller || "—" }} · {{ feedback(row) }}</div>
            </div>
          </article>
          <p v-if="!ebaySorted.length" class="muted">{{ dash.ebayListings.length ? "No eBay listings match those filters." : "No eBay listings in this scan." }}</p>
        </div>
        <div v-if="ebaySorted.length || ebayFilterCount" class="pager">
          <span class="muted">{{ rangeLabel(ebayPage, ebaySorted.length) }}{{ ebayFilterCount ? ` · ${dash.ebayListings.length} total` : "" }}</span>
          <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
            <button v-if="ebayFilterCount" class="btn secondary compact" type="button" @click="clearEbayFilters">Clear filters</button>
            <template v-if="ebayPages > 1">
              <button class="btn secondary compact" type="button" :disabled="ebayPage <= 1" @click="ebayPage -= 1">Previous</button>
              <button class="btn secondary compact" type="button" :disabled="ebayPage >= ebayPages" @click="ebayPage += 1">Next</button>
            </template>
          </div>
        </div>
        </div>
        <div v-else>
        <p v-if="dash.bricklinkError" class="error">{{ dash.bricklinkError }}</p>
        <div class="table-scroll desktop-only">
          <table>
            <thead>
              <tr>
                <th><button class="sort-btn" type="button" @click="sortBricklink('photo')">Photo{{ sortMark(bricklinkSort, 'photo') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortBricklink('title')">Description{{ sortMark(bricklinkSort, 'title') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortBricklink('country')">Country{{ sortMark(bricklinkSort, 'country') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortBricklink('price')">Price{{ sortMark(bricklinkSort, 'price') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortBricklink('condition')">Condition{{ sortMark(bricklinkSort, 'condition') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortBricklink('seller')">Seller{{ sortMark(bricklinkSort, 'seller') }}</button></th>
                <th><button class="sort-btn" type="button" @click="sortBricklink('qty')">Qty{{ sortMark(bricklinkSort, 'qty') }}</button></th>
              </tr>
              <tr>
                <th>
                  <select v-model="bricklinkFilters.photo" class="column-filter">
                    <option value="">All</option>
                    <option value="yes">Has photo</option>
                    <option value="no">No photo</option>
                  </select>
                </th>
                <th><input v-model="bricklinkFilters.title" class="column-filter" type="search" placeholder="Filter" /></th>
                <th>
                  <select v-model="bricklinkFilters.country" class="column-filter">
                    <option value="">All</option>
                    <option v-for="country in bricklinkCountries" :key="country" :value="country">{{ country }}</option>
                  </select>
                </th>
                <th><input v-model="bricklinkFilters.price" class="column-filter" type="search" placeholder="Filter" /></th>
                <th>
                  <select v-model="bricklinkFilters.condition" class="column-filter">
                    <option value="">All</option>
                    <option v-for="condition in bricklinkConditions" :key="condition" :value="condition">{{ condition }}</option>
                  </select>
                </th>
                <th><input v-model="bricklinkFilters.seller" class="column-filter" type="search" placeholder="Filter" /></th>
                <th><input v-model="bricklinkFilters.qty" class="column-filter" type="search" placeholder="Filter" /></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in bricklinkListings" :key="row.id">
                <td>
                  <img v-if="row.imageUrl" class="listing-thumb" :src="row.imageUrl" :alt="row.title || 'Listing photo'" />
                  <span v-else class="muted">—</span>
                </td>
                <td class="listing-title">
                  <a v-if="row.url" :href="row.url" target="_blank">{{ row.title || "—" }}</a>
                  <span v-else>{{ row.title || "—" }}</span>
                </td>
                <td>{{ row.sellerCountry || "—" }}</td>
                <td>{{ money(row.price) }}</td>
                <td>{{ row.condition }}</td>
                <td>{{ row.seller }}</td>
                <td>{{ row.quantity ?? "—" }}</td>
              </tr>
              <tr v-if="!bricklinkSorted.length">
                <td colspan="7" class="muted">{{ dash.bricklinkListings.length ? "No BrickLink listings match those filters." : (dash.bricklinkError || "No BrickLink listings in this scan.") }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="list-cards mobile-only">
          <article v-for="row in bricklinkListings" :key="row.id" class="list-card listing-card">
            <img v-if="row.imageUrl" class="listing-thumb" :src="row.imageUrl" :alt="row.title || 'Listing photo'" />
            <span v-else class="muted">—</span>
            <div class="grid" style="gap:0.35rem">
              <h3>
                <a v-if="row.url" :href="row.url" target="_blank">{{ row.title || "—" }}</a>
                <span v-else>{{ row.title || "—" }}</span>
              </h3>
              <div class="list-card-meta">
                <strong>{{ money(row.price) }}</strong>
                <span class="muted">{{ row.condition }} · Qty {{ row.quantity ?? "—" }}</span>
              </div>
              <div class="list-card-meta muted">{{ row.seller }} · {{ row.sellerCountry || "—" }}</div>
            </div>
          </article>
          <p v-if="!bricklinkSorted.length" class="muted">{{ dash.bricklinkListings.length ? "No BrickLink listings match those filters." : (dash.bricklinkError || "No BrickLink listings in this scan.") }}</p>
        </div>
        <div v-if="bricklinkSorted.length || bricklinkFilterCount" class="pager">
          <span class="muted">{{ rangeLabel(bricklinkPage, bricklinkSorted.length) }}{{ bricklinkFilterCount ? ` · ${dash.bricklinkListings.length} total` : "" }}</span>
          <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
            <button v-if="bricklinkFilterCount" class="btn secondary compact" type="button" @click="clearBricklinkFilters">Clear filters</button>
            <template v-if="bricklinkPages > 1">
              <button class="btn secondary compact" type="button" :disabled="bricklinkPage <= 1" @click="bricklinkPage -= 1">Previous</button>
              <button class="btn secondary compact" type="button" :disabled="bricklinkPage >= bricklinkPages" @click="bricklinkPage += 1">Next</button>
            </template>
          </div>
        </div>
        </div>
      </div>
    </div>
  </div>
</template>
