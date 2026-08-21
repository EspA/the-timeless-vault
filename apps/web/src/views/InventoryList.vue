<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { api, CONDITIONS, quantityForStockStatus, STOCK_STATUSES, visibilityStatusLabel, type InventoryItem } from "../api";
import { askConfirm, confirmStockStatusChange } from "../confirm";
import ItemNewModal from "../components/ItemNewModal.vue";
import StockStatusButtons from "../components/StockStatusButtons.vue";

const router = useRouter();
const adding = ref(false);

type Column = "sku" | "set" | "title" | "created" | "ebayPrice" | "bricklinkPrice" | "shopifyPrice" | "cost" | "stockStatus" | "quantity" | "condition" | "shopify" | "bricklink" | "ebay";
type Sort = { key: Column; dir: "asc" | "desc" };

const items = ref<InventoryItem[]>([]);
const error = ref("");
const stockBusyId = ref("");
const search = ref("");
const sort = ref<Sort>({ key: "created", dir: "desc" });
const filters = ref({
  sku: "",
  set: "",
  title: "",
  created: "",
  ebayPrice: "",
  bricklinkPrice: "",
  shopifyPrice: "",
  cost: "",
  stockStatus: "",
  quantity: "",
  condition: "",
  shopify: "",
  bricklink: "",
  ebay: "",
});

const statusOptions = [
  { value: "", label: "All" },
  { value: "ACTIVE", label: "Active" },
  { value: "UNLISTED", label: "Inactive" },
  { value: "none", label: "None" },
];

const valueFor = (item: InventoryItem, key: Column): string | number | null => {
  switch (key) {
    case "sku":
      return item.sku;
    case "set":
      return item.catalog.setNumber;
    case "title":
      return item.title;
    case "created":
      return item.createdAt ? Date.parse(item.createdAt) : null;
    case "ebayPrice":
      return item.ebayPrice ?? item.price;
    case "bricklinkPrice":
      return item.bricklinkPrice ?? item.price;
    case "shopifyPrice":
      return item.shopifyPrice ?? item.price;
    case "cost":
      return item.cost ?? null;
    case "stockStatus":
      return item.stockStatus ?? "";
    case "quantity":
      return item.quantity;
    case "condition":
      return item.condition;
    case "shopify":
      return item.shopifyStatus ?? "";
    case "bricklink":
      return item.bricklinkStatus ?? "";
    case "ebay":
      return item.ebayStatus ?? "";
  }
};

const formatCreated = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
};

const contains = (value: string | number | null, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const matchesStatus = (status: string | undefined, filter: string) => {
  if (!filter) return true;
  if (filter === "none") return !status;
  return status === filter;
};

const filtered = computed(() =>
  items.value.filter((item) =>
    contains(item.sku, filters.value.sku)
    && contains(item.catalog.setNumber, filters.value.set)
    && contains(item.title, filters.value.title)
    && contains(formatCreated(item.createdAt), filters.value.created)
    && contains(item.ebayPrice ?? item.price, filters.value.ebayPrice)
    && contains(item.bricklinkPrice ?? item.price, filters.value.bricklinkPrice)
    && contains(item.shopifyPrice ?? item.price, filters.value.shopifyPrice)
    && contains(item.cost ?? "", filters.value.cost)
    && (!filters.value.stockStatus || item.stockStatus === filters.value.stockStatus)
    && contains(item.quantity, filters.value.quantity)
    && (!filters.value.condition || item.condition === filters.value.condition)
    && matchesStatus(item.shopifyStatus, filters.value.shopify)
    && matchesStatus(item.bricklinkStatus, filters.value.bricklink)
    && matchesStatus(item.ebayStatus, filters.value.ebay)
    && (
      !search.value.trim()
      || contains(item.sku, search.value)
      || contains(item.catalog.setNumber, search.value)
      || contains(item.title, search.value)
    )
  )
);

const visible = computed(() => {
  const rows = [...filtered.value];
  const { key, dir } = sort.value;
  const direction = dir === "asc" ? 1 : -1;
  rows.sort((a, b) => {
    const av = valueFor(a, key);
    const bv = valueFor(b, key);
    const aEmpty = av == null || av === "";
    const bEmpty = bv == null || bv === "";
    if (aEmpty && bEmpty) return 0;
    if (aEmpty) return 1;
    if (bEmpty) return -1;
    if (typeof av === "number" && typeof bv === "number") {
      return (av - bv) * direction;
    }
    return String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: "base" }) * direction;
  });
  return rows;
});

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const sortBy = (key: Column) => {
  sort.value = sort.value.key === key
    ? { key, dir: sort.value.dir === "asc" ? "desc" : "asc" }
    : { key, dir: "asc" };
};

const sortMark = (key: Column) => {
  if (sort.value.key !== key) return "";
  return sort.value.dir === "asc" ? " ▲" : " ▼";
};

const clearFilters = () => {
  filters.value = {
    sku: "",
    set: "",
    title: "",
    created: "",
    ebayPrice: "",
    bricklinkPrice: "",
    shopifyPrice: "",
    cost: "",
    stockStatus: "",
    quantity: "",
    condition: "",
    shopify: "",
    bricklink: "",
    ebay: "",
  };
};

const load = async () => {
  items.value = await api.get<InventoryItem[]>("/api/inventory");
};

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
    items.value = items.value.filter((row) => row.id !== item.id);
  } catch (e) {
    error.value = (e as Error).message;
  }
};

const onSaved = async (item: InventoryItem) => {
  adding.value = false;
  await router.push(`/inventory/${item.id}`);
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
      <div v-if="items.length" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ visible.length }} of {{ items.length }} items</span>
        <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
          Clear filters
        </button>
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
              <th><button class="sort-btn" type="button" @click="sortBy('sku')">SKU{{ sortMark("sku") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('set')">Set{{ sortMark("set") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('title')">Title{{ sortMark("title") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('ebayPrice')">eBay ${{ sortMark("ebayPrice") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('bricklinkPrice')">BrickLink ${{ sortMark("bricklinkPrice") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('shopifyPrice')">Shopify ${{ sortMark("shopifyPrice") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('cost')">Cost{{ sortMark("cost") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('stockStatus')">Status{{ sortMark("stockStatus") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('quantity')">Quantity{{ sortMark("quantity") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('condition')">Condition{{ sortMark("condition") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('shopify')">Shopify{{ sortMark("shopify") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('bricklink')">BrickLink{{ sortMark("bricklink") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('ebay')">eBay{{ sortMark("ebay") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('created')">Created{{ sortMark("created") }}</button></th>
              <th></th>
            </tr>
            <tr>
              <th><input v-model="filters.sku" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.set" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.title" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.ebayPrice" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.bricklinkPrice" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.shopifyPrice" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.cost" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.stockStatus" class="column-filter">
                  <option value="">All</option>
                  <option v-for="status in STOCK_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
                </select>
              </th>
              <th><input v-model="filters.quantity" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.condition" class="column-filter">
                  <option value="">All</option>
                  <option v-for="condition in CONDITIONS" :key="condition" :value="condition">{{ condition }}</option>
                </select>
              </th>
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
                <select v-model="filters.ebay" class="column-filter">
                  <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </th>
              <th><input v-model="filters.created" class="column-filter" type="search" placeholder="Filter" /></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in visible" :key="item.id">
              <td class="muted">{{ item.sku }}</td>
              <td>{{ item.catalog.setNumber }}</td>
              <td><router-link :to="`/inventory/${item.id}`">{{ item.title }}</router-link></td>
              <td>${{ item.ebayPrice ?? item.price }}</td>
              <td>${{ item.bricklinkPrice ?? item.price }}</td>
              <td>${{ item.shopifyPrice ?? item.price }}</td>
              <td>{{ item.cost != null ? `$${item.cost}` : "—" }}</td>
              <td>
                <StockStatusButtons
                  compact
                  :model-value="item.stockStatus"
                  :disabled="!!stockBusyId"
                  @update:model-value="setStockStatus(item, $event)"
                />
              </td>
              <td>{{ item.quantity }}</td>
              <td><span class="badge">{{ item.condition }}</span></td>
              <td>
                <span
                  v-if="item.shopifyStatus"
                  class="badge"
                  :class="{ ok: item.shopifyStatus === 'ACTIVE', warn: item.shopifyStatus === 'UNLISTED' }"
                >{{ visibilityStatusLabel(item.shopifyStatus) }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td>
                <span
                  v-if="item.bricklinkStatus"
                  class="badge"
                  :class="{ ok: item.bricklinkStatus === 'ACTIVE', warn: item.bricklinkStatus === 'UNLISTED' }"
                >{{ visibilityStatusLabel(item.bricklinkStatus) }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td>
                <span
                  v-if="item.ebayStatus"
                  class="badge"
                  :class="{ ok: item.ebayStatus === 'ACTIVE', warn: item.ebayStatus === 'UNLISTED' }"
                >{{ visibilityStatusLabel(item.ebayStatus) }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td>{{ formatCreated(item.createdAt) }}</td>
              <td>
                <button class="btn danger compact" type="button" @click="remove(item)">Delete</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="15" class="muted">{{ items.length ? "No items match those filters." : "No inventory yet. Add a sealed set to begin." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="item in visible" :key="item.id" class="list-card">
          <h3><router-link :to="`/inventory/${item.id}`">{{ item.title }}</router-link></h3>
          <div class="list-card-meta muted">{{ item.catalog.setNumber }} · {{ item.sku }} · Qty {{ item.quantity }}</div>
          <StockStatusButtons
            compact
            :model-value="item.stockStatus"
            :disabled="!!stockBusyId"
            @update:model-value="setStockStatus(item, $event)"
          />
          <div class="list-card-prices">
            <span><span class="muted">eBay</span>${{ item.ebayPrice ?? item.price }}</span>
            <span><span class="muted">BrickLink</span>${{ item.bricklinkPrice ?? item.price }}</span>
            <span><span class="muted">Shopify</span>${{ item.shopifyPrice ?? item.price }}</span>
          </div>
          <div class="list-card-row">
            <span
              v-if="item.shopifyStatus"
              class="badge"
              :class="{ ok: item.shopifyStatus === 'ACTIVE', warn: item.shopifyStatus === 'UNLISTED' }"
            >Shopify {{ visibilityStatusLabel(item.shopifyStatus) }}</span>
            <span
              v-if="item.bricklinkStatus"
              class="badge"
              :class="{ ok: item.bricklinkStatus === 'ACTIVE', warn: item.bricklinkStatus === 'UNLISTED' }"
            >BrickLink {{ visibilityStatusLabel(item.bricklinkStatus) }}</span>
            <span
              v-if="item.ebayStatus"
              class="badge"
              :class="{ ok: item.ebayStatus === 'ACTIVE', warn: item.ebayStatus === 'UNLISTED' }"
            >eBay {{ visibilityStatusLabel(item.ebayStatus) }}</span>
          </div>
          <div class="list-card-actions">
            <router-link class="btn secondary compact" :to="`/inventory/${item.id}`">Open</router-link>
            <button class="btn danger compact" type="button" @click="remove(item)">Delete</button>
          </div>
        </article>
        <p v-if="!visible.length" class="muted">{{ items.length ? "No items match those filters." : "No inventory yet. Add a sealed set to begin." }}</p>
      </div>
    </div>
    <ItemNewModal v-if="adding" @close="adding = false" @saved="onSaved" />
  </div>
</template>
