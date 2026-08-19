<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { api } from "../api";

type ListingAction = "CREATE" | "UPDATE" | "ACTIVATE" | "DEACTIVATE" | "DELETE";
type ListingLogStatus = "SUCCESS" | "FAILED";

type ListingLog = {
  id: string;
  inventoryItemId?: string;
  sku?: string;
  setNumber?: string;
  itemTitle?: string;
  platform: string;
  action: ListingAction;
  status: ListingLogStatus;
  message?: string;
  loggedAt: string;
};

type ListingLogsPage = {
  items: ListingLog[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

const PAGE_SIZE = 20;
const FETCH_SIZE = 10_000;
const REFRESH_MS = 60_000;
const emptyFilters = () => ({
  when: "",
  platform: "",
  item: "",
  action: "",
  status: "",
  notes: "",
});

const allItems = ref<ListingLog[]>([]);
const page = ref(0);
const error = ref("");
const loading = ref(false);
const filters = ref(emptyFilters());
let timer: ReturnType<typeof setInterval> | undefined;

const platformLabel = (platform: string) => {
  if (platform === "EBAY") return "eBay";
  if (platform === "BRICKLINK") return "BrickLink";
  if (platform === "SHOPIFY") return "Shopify";
  return platform;
};

const actionLabel = (action: ListingAction) => {
  if (action === "CREATE") return "Create listing";
  if (action === "UPDATE") return "Update listing";
  if (action === "ACTIVATE") return "Activate";
  if (action === "DEACTIVATE") return "Deactivate";
  if (action === "DELETE") return "Delete";
  return action;
};

const itemLabel = (row: ListingLog) => {
  const set = [row.setNumber, row.itemTitle].filter(Boolean).join(" ");
  if (row.sku && set) return `${row.sku} · ${set}`;
  return row.sku || set || "—";
};

const whenLabel = (value: string) => new Date(value).toLocaleString();

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const filtered = computed(() =>
  allItems.value.filter((row) =>
    contains(whenLabel(row.loggedAt), filters.value.when)
    && (!filters.value.platform || row.platform === filters.value.platform)
    && contains(itemLabel(row), filters.value.item)
    && (!filters.value.action || row.action === filters.value.action)
    && (!filters.value.status || row.status === filters.value.status)
    && contains(row.message || "", filters.value.notes)
  )
);

const total = computed(() => filtered.value.length);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)));
const items = computed(() => {
  const start = page.value * PAGE_SIZE;
  return filtered.value.slice(start, start + PAGE_SIZE);
});
const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const rangeLabel = computed(() => {
  if (!total.value) return "0 listing actions";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  const pages = totalPages.value > 1 ? ` · page ${page.value + 1} of ${totalPages.value}` : "";
  return `${start}–${end} of ${total.value}${pages}`;
});

const load = async () => {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<ListingLogsPage>(`/api/listing-logs?page=0&size=${FETCH_SIZE}`);
    allItems.value = result.items;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
};

const previous = () => {
  if (page.value <= 0) return;
  page.value -= 1;
};

const next = () => {
  if (page.value + 1 >= totalPages.value) return;
  page.value += 1;
};

const clearFilters = () => {
  filters.value = emptyFilters();
};

watch(filters, () => {
  page.value = 0;
}, { deep: true });

watch(totalPages, (pages) => {
  if (page.value >= pages) page.value = Math.max(0, pages - 1);
});

onMounted(() => {
  void load();
  timer = setInterval(() => {
    if (!document.hidden) void load();
  }, REFRESH_MS);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="grid">
    <div>
      <h1>Listing logs</h1>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="allItems.length" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
          <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
            Clear filters
          </button>
          <button class="btn secondary compact" type="button" :disabled="page <= 0" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages" @click="next">Next</button>
        </div>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Platform</th>
              <th>Item</th>
              <th>Action</th>
              <th>Status</th>
              <th>Notes</th>
            </tr>
            <tr>
              <th><input v-model="filters.when" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.platform" class="column-filter">
                  <option value="">All</option>
                  <option value="EBAY">eBay</option>
                  <option value="BRICKLINK">BrickLink</option>
                  <option value="SHOPIFY">Shopify</option>
                </select>
              </th>
              <th><input v-model="filters.item" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.action" class="column-filter">
                  <option value="">All</option>
                  <option value="CREATE">Create listing</option>
                  <option value="UPDATE">Update listing</option>
                  <option value="ACTIVATE">Activate</option>
                  <option value="DEACTIVATE">Deactivate</option>
                  <option value="DELETE">Delete</option>
                </select>
              </th>
              <th>
                <select v-model="filters.status" class="column-filter">
                  <option value="">All</option>
                  <option value="SUCCESS">Success</option>
                  <option value="FAILED">Failed</option>
                </select>
              </th>
              <th><input v-model="filters.notes" class="column-filter" type="search" placeholder="Filter" /></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.id">
              <td>{{ whenLabel(row.loggedAt) }}</td>
              <td><span class="badge">{{ platformLabel(row.platform) }}</span></td>
              <td>
                <router-link v-if="row.inventoryItemId" :to="`/inventory/${row.inventoryItemId}`">
                  {{ itemLabel(row) }}
                </router-link>
                <span v-else>{{ itemLabel(row) }}</span>
              </td>
              <td>{{ actionLabel(row.action) }}</td>
              <td>
                <span class="badge" :class="{ ok: row.status === 'SUCCESS', bad: row.status === 'FAILED' }">
                  {{ row.status === "SUCCESS" ? "Success" : "Failed" }}
                </span>
              </td>
              <td class="muted">{{ row.message || "—" }}</td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="6" class="muted">
                {{ loading
                  ? "Loading listing actions…"
                  : allItems.length
                    ? "No listing actions match those filters."
                    : "No listing actions recorded yet." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="allItems.length" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div style="display:flex;gap:0.5rem">
          <button class="btn secondary compact" type="button" :disabled="page <= 0" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages" @click="next">Next</button>
        </div>
      </div>
    </div>
  </div>
</template>
