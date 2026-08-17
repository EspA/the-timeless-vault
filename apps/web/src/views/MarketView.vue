<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { api, type SetWatch } from "../api";
import ScanProgressModal from "../components/ScanProgressModal.vue";

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
};
type Sort = { key: string; dir: "asc" | "desc" };

const PAGE_SIZE = 10;
const route = useRoute();
const watches = ref<SetWatch[]>([]);
const selected = ref("");
const dash = ref<Dashboard | null>(null);
const error = ref("");
const ebaySort = ref<Sort>({ key: "total", dir: "asc" });
const bricklinkSort = ref<Sort>({ key: "price", dir: "asc" });
const ebayPage = ref(1);
const bricklinkPage = ref(1);
const listingTab = ref<"ebay" | "bricklink">("ebay");
const maxPriceInput = ref<number | null>(null);
const filterMessage = ref("");
const scanning = ref<"ebay" | "bricklink" | "all" | "">("");

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
    if (dash.value.ebayError) {
      error.value = dash.value.ebayError;
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

const sortedListings = (rows: Listing[], sort: Sort) =>
  [...rows].sort((a, b) => {
    const cmp = compareValues(sortValue(a, sort.key), sortValue(b, sort.key));
    return sort.dir === "asc" ? cmp : -cmp;
  });

const paginate = (rows: Listing[], page: number) => {
  const start = (page - 1) * PAGE_SIZE;
  return rows.slice(start, start + PAGE_SIZE);
};

const ebaySorted = computed(() => sortedListings(dash.value?.ebayListings ?? [], ebaySort.value));
const bricklinkSorted = computed(() => sortedListings(dash.value?.bricklinkListings ?? [], bricklinkSort.value));
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
  }
);

const selectedWatch = computed(() =>
  watches.value.find((watch) => watch.catalogId === selected.value) ?? null
);

watch(
  selectedWatch,
  (watch) => {
    maxPriceInput.value = watch?.maxPrice ?? null;
    filterMessage.value = "";
  },
  { immediate: true }
);

const parsePrice = (raw: string) => {
  if (!raw.trim()) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
};

const saveMaxPrice = async () => {
  const watch = selectedWatch.value;
  if (!watch) return;
  error.value = "";
  filterMessage.value = "";
  try {
    const saved = await api.put<SetWatch>(`/api/set-watches/${watch.id}`, {
      enabled: watch.enabled,
      ebaySearchQuery: watch.ebaySearchQuery,
      ebayExcludeWords: watch.ebayExcludeWords,
      ebayFeedbackMin: watch.ebayFeedbackMin,
      ebayScanIntervalMinutes: watch.ebayScanIntervalMinutes,
      bricklinkScanIntervalMinutes: watch.bricklinkScanIntervalMinutes,
      minPrice: watch.minPrice ?? null,
      maxPrice: maxPriceInput.value,
    });
    watches.value = watches.value.map((row) => (row.id === saved.id ? saved : row));
    filterMessage.value = "Max price saved.";
  } catch (e) {
    error.value = (e as Error).message;
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
    <p v-if="!watches.length" class="muted">No sets are watched yet. Add one from <router-link to="/watches/new">New item watch</router-link>.</p>
    <div class="card grid">
      <div style="display:flex;gap:0.75rem;align-items:end;flex-wrap:wrap">
        <label style="flex:1">Watched set
          <select :value="selected" :disabled="!!scanning" @change="loadDash(($event.target as HTMLSelectElement).value)">
            <option v-for="watch in watches" :key="watch.catalogId" :value="watch.catalogId">
              {{ watch.setNumber }} {{ watch.name }}
            </option>
          </select>
        </label>
        <button class="btn secondary" type="button" :disabled="!!scanning" @click="scan('ebay')">
          {{ scanning === "ebay" ? "Scanning eBay…" : "Scan eBay" }}
        </button>
        <button class="btn secondary" type="button" :disabled="!!scanning" @click="scan('bricklink')">
          {{ scanning === "bricklink" ? "Scanning BrickLink…" : "Scan BrickLink" }}
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
      <div v-if="selectedWatch" style="display:flex;gap:0.75rem;align-items:end;flex-wrap:wrap">
        <label style="max-width:12rem">Max price
          <input
            :value="maxPriceInput ?? ''"
            type="number"
            min="0"
            step="0.01"
            placeholder="No maximum"
            @input="maxPriceInput = parsePrice(($event.target as HTMLInputElement).value)"
          />
        </label>
        <button class="btn gold" type="button" :disabled="!!scanning" @click="saveMaxPrice">Save</button>
      </div>
    </div>
    <ScanProgressModal :open="!!scanning" :title="scanTitle" :message="scanMessage" />

    <div v-if="dash" class="grid">
      <div class="card grid">
        <p v-if="dash.ebayError" class="error">{{ dash.ebayError }}</p>
        <div class="grid two">
          <div class="stat">
            <span class="stat-value hero">{{ money(combinedMedian) }}</span>
            <span class="stat-label">Median</span>
          </div>
          <div class="stat">
            <span class="stat-value hero">{{ money(combinedAverage) }}</span>
            <span class="stat-label">Average</span>
          </div>
        </div>
        <div class="platform-stats">
          <div class="grid">
            <h4>eBay <span class="muted">{{ dash.ebay?.count != null ? `${dash.ebay.count} listings` : "No scan yet" }}</span></h4>
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
            <h4>BrickLink <span class="muted">{{ dash.bricklink?.count != null ? `${dash.bricklink.count} listings` : "No scan yet" }}</span></h4>
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
          <button class="tab" type="button" :class="{ on: listingTab === 'ebay' }" @click="listingTab = 'ebay'">eBay</button>
          <button class="tab" type="button" :class="{ on: listingTab === 'bricklink' }" @click="listingTab = 'bricklink'">BrickLink</button>
        </div>
        <div v-if="listingTab === 'ebay'">
        <p v-if="dash.ebayError" class="error">{{ dash.ebayError }}</p>
        <div class="table-scroll">
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
                <td colspan="9" class="muted">No eBay listings in this scan.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="ebaySorted.length" class="pager">
          <span class="muted">{{ rangeLabel(ebayPage, ebaySorted.length) }}</span>
          <div v-if="ebayPages > 1" style="display:flex;gap:0.5rem">
            <button class="btn secondary" type="button" :disabled="ebayPage <= 1" @click="ebayPage -= 1">Previous</button>
            <button class="btn secondary" type="button" :disabled="ebayPage >= ebayPages" @click="ebayPage += 1">Next</button>
          </div>
        </div>
        </div>
        <div v-else>
        <div class="table-scroll">
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
                <td colspan="7" class="muted">No BrickLink listings in this scan.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="bricklinkSorted.length" class="pager">
          <span class="muted">{{ rangeLabel(bricklinkPage, bricklinkSorted.length) }}</span>
          <div v-if="bricklinkPages > 1" style="display:flex;gap:0.5rem">
            <button class="btn secondary" type="button" :disabled="bricklinkPage <= 1" @click="bricklinkPage -= 1">Previous</button>
            <button class="btn secondary" type="button" :disabled="bricklinkPage >= bricklinkPages" @click="bricklinkPage += 1">Next</button>
          </div>
        </div>
        </div>
      </div>
    </div>
  </div>
</template>
