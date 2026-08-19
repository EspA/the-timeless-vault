<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { api } from "../api";

type ScanLog = {
  id: string;
  catalogId?: string;
  setNumber: string;
  setName?: string;
  platform: string;
  trigger?: "AUTOMATIC" | "MANUAL" | null;
  status: "SUCCESS" | "FAILED";
  listingCount?: number;
  message?: string;
  scannedAt: string;
};

type ScanLogsPage = {
  items: ScanLog[];
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
  set: "",
  trigger: "",
  status: "",
  listings: "",
  notes: "",
});

const allItems = ref<ScanLog[]>([]);
const page = ref(0);
const error = ref("");
const loading = ref(false);
const filters = ref(emptyFilters());
let timer: ReturnType<typeof setInterval> | undefined;

const platformLabel = (platform: string) => {
  if (platform === "EBAY") return "eBay";
  if (platform === "BRICKLINK") return "BrickLink";
  return platform;
};

const triggerLabel = (trigger?: string | null) => {
  if (trigger === "AUTOMATIC") return "Automatic";
  if (trigger === "MANUAL") return "Manual";
  return "—";
};

const whenLabel = (value: string) => new Date(value).toLocaleString();

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const filtered = computed(() =>
  allItems.value.filter((row) =>
    contains(whenLabel(row.scannedAt), filters.value.when)
    && (!filters.value.platform || row.platform === filters.value.platform)
    && contains(`${row.setNumber} ${row.setName || ""}`, filters.value.set)
    && (!filters.value.trigger || (row.trigger || "") === filters.value.trigger)
    && (!filters.value.status || row.status === filters.value.status)
    && contains(row.listingCount ?? "", filters.value.listings)
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
  if (!total.value) return "0 scans";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  return `${start}–${end} of ${total.value}`;
});

const load = async () => {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<ScanLogsPage>(`/api/scan-logs?page=0&size=${FETCH_SIZE}`);
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
      <h1>Scan logs</h1>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="allItems.length" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
          <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
            Clear filters
          </button>
          <template v-if="totalPages > 1">
            <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
            <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
          </template>
        </div>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Platform</th>
              <th>Set</th>
              <th>Trigger</th>
              <th>Status</th>
              <th>Listings</th>
              <th>Notes</th>
            </tr>
            <tr>
              <th><input v-model="filters.when" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.platform" class="column-filter">
                  <option value="">All</option>
                  <option value="EBAY">eBay</option>
                  <option value="BRICKLINK">BrickLink</option>
                </select>
              </th>
              <th><input v-model="filters.set" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.trigger" class="column-filter">
                  <option value="">All</option>
                  <option value="AUTOMATIC">Automatic</option>
                  <option value="MANUAL">Manual</option>
                </select>
              </th>
              <th>
                <select v-model="filters.status" class="column-filter">
                  <option value="">All</option>
                  <option value="SUCCESS">Success</option>
                  <option value="FAILED">Failed</option>
                </select>
              </th>
              <th><input v-model="filters.listings" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.notes" class="column-filter" type="search" placeholder="Filter" /></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.id">
              <td>{{ whenLabel(row.scannedAt) }}</td>
              <td><span class="badge">{{ platformLabel(row.platform) }}</span></td>
              <td>
                <router-link v-if="row.catalogId" :to="`/market/${row.catalogId}`">
                  {{ row.setNumber }} {{ row.setName }}
                </router-link>
                <span v-else>{{ row.setNumber }} {{ row.setName }}</span>
              </td>
              <td>{{ triggerLabel(row.trigger) }}</td>
              <td>
                <span class="badge" :class="{ ok: row.status === 'SUCCESS', bad: row.status === 'FAILED' }">
                  {{ row.status === "SUCCESS" ? "Success" : "Failed" }}
                </span>
              </td>
              <td>{{ row.listingCount ?? "—" }}</td>
              <td class="muted">{{ row.message || "—" }}</td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="7" class="muted">
                {{ loading
                  ? "Loading scans…"
                  : allItems.length
                    ? "No scans match those filters."
                    : "No scans recorded yet." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div style="display:flex;gap:0.5rem">
          <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
        </div>
      </div>
    </div>
  </div>
</template>
