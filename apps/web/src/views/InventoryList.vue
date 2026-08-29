<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { api, quantityForStockStatus, STOCK_STATUSES, type InventoryItem, type InventoryPage } from "../api";
import { askConfirm, confirmStockStatusChange } from "../confirm";
import ChannelListingBadge from "../components/ChannelListingBadge.vue";
import ItemNewModal from "../components/ItemNewModal.vue";
import StockStatusButtons from "../components/StockStatusButtons.vue";

const router = useRouter();
const adding = ref(false);

type Column = "set" | "title" | "created" | "updated" | "ebayPrice" | "stockStatus" | "quantity" | "shopify" | "bricklink" | "brickowl" | "ebay";
type Sort = { key: Column; dir: "asc" | "desc" };

const PAGE_SIZE = 10;

const items = ref<InventoryItem[]>([]);
const page = ref(0);
const total = ref(0);
const totalPages = ref(1);
const loading = ref(false);
const error = ref("");
const stockBusyId = ref("");
const search = ref("");
const sort = ref<Sort>({ key: "updated", dir: "desc" });
const filters = ref({
  title: "",
  ebayPrice: "",
  stockStatus: "",
  shopify: "",
  bricklink: "",
  brickowl: "",
  ebay: "",
});

const statusOptions = [
  { value: "", label: "All" },
  { value: "ACTIVE", label: "Active" },
  { value: "UNLISTED", label: "Inactive" },
  { value: "none", label: "None" },
];

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const rangeLabel = computed(() => {
  if (!total.value) return "0 items";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  const pages = totalPages.value > 1 ? ` · page ${page.value + 1} of ${totalPages.value}` : "";
  return `${start}–${end} of ${total.value}${pages}`;
});

const queryPath = (pageIndex: number) => {
  const params = new URLSearchParams({
    page: String(pageIndex),
    size: String(PAGE_SIZE),
    sort: sort.value.key,
    dir: sort.value.dir,
  });
  if (search.value.trim()) params.set("q", search.value.trim());
  const entries = Object.entries(filters.value) as Array<[keyof typeof filters.value, string]>;
  for (const [key, value] of entries) {
    if (value.trim()) params.set(key, value.trim());
  }
  return `/api/inventory?${params.toString()}`;
};

const sortBy = (key: Column) => {
  sort.value = sort.value.key === key
    ? { key, dir: sort.value.dir === "asc" ? "desc" : "asc" }
    : { key, dir: "asc" };
  page.value = 0;
};

const sortMark = (key: Column) => {
  if (sort.value.key !== key) return "";
  return sort.value.dir === "asc" ? " ▲" : " ▼";
};

const clearFilters = () => {
  filters.value = {
    title: "",
    ebayPrice: "",
    stockStatus: "",
    shopify: "",
    bricklink: "",
    brickowl: "",
    ebay: "",
  };
  page.value = 0;
};

const previous = () => {
  if (page.value <= 0 || loading.value) return;
  void load(page.value - 1);
};

const next = () => {
  if (page.value + 1 >= totalPages.value || loading.value) return;
  void load(page.value + 1);
};

let requestId = 0;
const load = async (pageIndex = page.value) => {
  const current = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<InventoryPage>(queryPath(pageIndex));
    if (current !== requestId) return;
    const pages = Math.max(1, result.totalPages);
    const nextPage = Math.min(pageIndex, pages - 1);
    items.value = result.items;
    total.value = result.total;
    totalPages.value = pages;
    page.value = nextPage;
    if (nextPage !== pageIndex && result.total > 0) {
      await load(nextPage);
    }
  } catch (e) {
    if (current !== requestId) return;
    error.value = (e as Error).message;
  } finally {
    if (current === requestId) loading.value = false;
  }
};

let filterTimer: ReturnType<typeof setTimeout> | undefined;
watch([filters, search, sort], () => {
  clearTimeout(filterTimer);
  filterTimer = setTimeout(() => {
    void load(0);
  }, 250);
}, { deep: true });

const setStockStatus = async (item: InventoryItem, next: string) => {
  if (item.stockStatus === next || stockBusyId.value) return;
  if (!(await confirmStockStatusChange(next))) return;
  const previousStatus = item.stockStatus;
  const previousQuantity = item.quantity;
  item.stockStatus = next;
  item.quantity = quantityForStockStatus(previousStatus, previousQuantity, next);
  stockBusyId.value = item.id;
  error.value = "";
  try {
    const updated = await api.put<InventoryItem>(`/api/inventory/${item.id}`, { stockStatus: next });
    const index = items.value.findIndex((row) => row.id === item.id);
    if (index >= 0) {
      items.value[index] = updated;
    }
  } catch (e) {
    item.stockStatus = previousStatus;
    item.quantity = previousQuantity;
    error.value = (e as Error).message;
  } finally {
    stockBusyId.value = "";
  }
};

const remove = async (item: InventoryItem) => {
  if (!(await askConfirm(`Delete "${item.title}" from inventory? This cannot be undone.`, { title: "Delete item" }))) {
    return;
  }
  error.value = "";
  try {
    await api.del(`/api/inventory/${item.id}`);
    await load(page.value);
  } catch (e) {
    error.value = (e as Error).message;
  }
};

const onSaved = async (item: InventoryItem) => {
  adding.value = false;
  await router.push(`/inventory/${item.id}`);
};

const listingUrl = (item: InventoryItem, platform: "SHOPIFY" | "BRICKLINK" | "BRICKOWL" | "EBAY") => {
  if (platform === "SHOPIFY") {
    return item.shopifyStatus === "ACTIVE" && item.shopifyLiveUrl ? item.shopifyLiveUrl : "";
  }
  if (platform === "BRICKLINK") {
    return item.bricklinkStatus === "ACTIVE" && item.bricklinkLiveUrl ? item.bricklinkLiveUrl : "";
  }
  if (platform === "BRICKOWL") {
    return item.brickowlStatus === "ACTIVE" && item.brickowlLiveUrl ? item.brickowlLiveUrl : "";
  }
  return item.ebayStatus === "ACTIVE" && item.ebayLiveUrl ? item.ebayLiveUrl : "";
};

onMounted(async () => {
  try {
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  }
});
</script>

<template>
  <div class="grid">
    <div class="page-head">
      <h1>Inventory</h1>
      <button class="btn gold" type="button" @click="adding = true">Add item</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="total > 0 || filterCount || search.trim()" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button v-if="filterCount || search.trim()" class="btn secondary compact" type="button" @click="clearFilters(); search = ''">
            Clear filters
          </button>
          <template v-if="totalPages > 1">
            <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
            <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
          </template>
        </div>
      </div>
      <div class="mobile-filters mobile-only">
        <label>Search
          <input v-model="search" type="search" placeholder="Title, SKU, or set" />
        </label>
        <label>Status
          <select v-model="filters.stockStatus">
            <option value="">All</option>
            <option v-for="status in STOCK_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
          </select>
        </label>
      </div>
      <div class="table-scroll desktop-only">
        <table>
          <thead>
            <tr>
              <th class="inventory-col-set"><button class="sort-btn" type="button" @click="sortBy('set')">Set{{ sortMark("set") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('title')">Title{{ sortMark("title") }}</button></th>
              <th class="inventory-col-price"><button class="sort-btn" type="button" @click="sortBy('ebayPrice')">eBay ${{ sortMark("ebayPrice") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('stockStatus')">Status{{ sortMark("stockStatus") }}</button></th>
              <th class="inventory-col-qty"><button class="sort-btn" type="button" @click="sortBy('quantity')">Qty{{ sortMark("quantity") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('shopify')">Shopify{{ sortMark("shopify") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('bricklink')">BrickLink{{ sortMark("bricklink") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('brickowl')">Brick Owl{{ sortMark("brickowl") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('ebay')">eBay{{ sortMark("ebay") }}</button></th>
              <th></th>
            </tr>
            <tr>
              <th class="inventory-col-set"></th>
              <th><input v-model="filters.title" class="column-filter" type="search" placeholder="Filter" /></th>
              <th class="inventory-col-price"><input v-model="filters.ebayPrice" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.stockStatus" class="column-filter">
                  <option value="">All</option>
                  <option v-for="status in STOCK_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
                </select>
              </th>
              <th class="inventory-col-qty"></th>
              <th>
                <select v-model="filters.shopify" class="column-filter">
                  <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </th>
              <th>
                <select v-model="filters.bricklink" class="column-filter">
                  <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </th>
              <th>
                <select v-model="filters.brickowl" class="column-filter">
                  <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </th>
              <th>
                <select v-model="filters.ebay" class="column-filter">
                  <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td class="inventory-col-set">{{ item.catalog.setNumber }}</td>
              <td><router-link :to="`/inventory/${item.id}`">{{ item.title }}</router-link></td>
              <td class="inventory-col-price">${{ item.ebayPrice ?? item.price }}</td>
              <td>
                <StockStatusButtons
                  compact
                  :model-value="item.stockStatus"
                  :disabled="!!stockBusyId"
                  @update:model-value="setStockStatus(item, $event)"
                />
              </td>
              <td class="inventory-col-qty">{{ item.quantity }}</td>
              <td>
                <ChannelListingBadge
                  platform="SHOPIFY"
                  :status="item.shopifyStatus"
                  :href="listingUrl(item, 'SHOPIFY')"
                />
              </td>
              <td>
                <ChannelListingBadge
                  platform="BRICKLINK"
                  :status="item.bricklinkStatus"
                  :href="listingUrl(item, 'BRICKLINK')"
                />
              </td>
              <td>
                <ChannelListingBadge
                  platform="BRICKOWL"
                  :status="item.brickowlStatus"
                  :href="listingUrl(item, 'BRICKOWL')"
                />
              </td>
              <td>
                <ChannelListingBadge
                  platform="EBAY"
                  :status="item.ebayStatus"
                  :href="listingUrl(item, 'EBAY')"
                />
              </td>
              <td>
                <button class="btn danger compact" type="button" @click="remove(item)">Delete</button>
              </td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="10" class="muted">{{ loading ? "Loading…" : filterCount || search.trim() ? "No items match those filters." : "No inventory yet. Add a sealed set to begin." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="item in items" :key="item.id" class="list-card">
          <h3><router-link :to="`/inventory/${item.id}`">{{ item.title }}</router-link></h3>
          <div class="list-card-meta muted">{{ item.catalog.setNumber }} · Qty {{ item.quantity }}</div>
          <StockStatusButtons
            compact
            :model-value="item.stockStatus"
            :disabled="!!stockBusyId"
            @update:model-value="setStockStatus(item, $event)"
          />
          <div class="list-card-prices">
            <span><span class="muted">eBay</span>${{ item.ebayPrice ?? item.price }}</span>
          </div>
          <div class="list-card-row">
            <ChannelListingBadge
              v-if="item.shopifyStatus"
              platform="SHOPIFY"
              :status="item.shopifyStatus"
              :href="listingUrl(item, 'SHOPIFY')"
            />
            <ChannelListingBadge
              v-if="item.bricklinkStatus"
              platform="BRICKLINK"
              :status="item.bricklinkStatus"
              :href="listingUrl(item, 'BRICKLINK')"
            />
            <ChannelListingBadge
              v-if="item.brickowlStatus"
              platform="BRICKOWL"
              :status="item.brickowlStatus"
              :href="listingUrl(item, 'BRICKOWL')"
            />
            <ChannelListingBadge
              v-if="item.ebayStatus"
              platform="EBAY"
              :status="item.ebayStatus"
              :href="listingUrl(item, 'EBAY')"
            />
          </div>
          <div class="list-card-actions">
            <router-link class="btn secondary compact" :to="`/inventory/${item.id}`">Open</router-link>
            <button class="btn danger compact" type="button" @click="remove(item)">Delete</button>
          </div>
        </article>
        <p v-if="!items.length" class="muted">{{ loading ? "Loading…" : filterCount || search.trim() ? "No items match those filters." : "No inventory yet. Add a sealed set to begin." }}</p>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
        </div>
      </div>
    </div>
    <ItemNewModal v-if="adding" @close="adding = false" @saved="onSaved" />
  </div>
</template>
