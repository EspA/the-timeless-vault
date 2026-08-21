<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { api } from "../api";
import ChannelLogo from "../components/ChannelLogo.vue";

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

const items = ref<ScanLog[]>([]);
const page = ref(0);
const total = ref(0);
const totalPages = ref(1);
const error = ref("");
const loading = ref(false);
const filters = ref(emptyFilters());
let timer: ReturnType<typeof setInterval> | undefined;

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

const visible = computed(() =>
  items.value.filter((row) =>
    contains(whenLabel(row.scannedAt), filters.value.when)
    && (!filters.value.platform || row.platform === filters.value.platform)
    && contains(`${row.setNumber} ${row.setName || ""}`, filters.value.set)
    && (!filters.value.trigger || (row.trigger || "") === filters.value.trigger)
    && (!filters.value.status || row.status === filters.value.status)
    && contains(row.listingCount ?? "", filters.value.listings)
    && contains(row.message || "", filters.value.notes)
  )
);

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const rangeLabel = computed(() => {
  if (!total.value) return "0 scans";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  const pages = totalPages.value > 1 ? ` · page ${page.value + 1} of ${totalPages.value}` : "";
  return `${start}–${end} of ${total.value}${pages}`;
});

const load = async (pageIndex = page.value) => {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<ScanLogsPage>(`/api/scan-logs?page=${pageIndex}&size=${PAGE_SIZE}`);
    const pages = Math.max(1, result.totalPages);
    const nextPage = Math.min(pageIndex, pages - 1);
    items.value = result.items;
    total.value = result.total;
    totalPages.value = pages;
    page.value = nextPage;
    if (nextPage !== pageIndex && result.total > 0) {
      loading.value = false;
      await load(nextPage);
      return;
    }
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
};

const previous = () => {
  if (page.value <= 0 || loading.value) return;
  void load(page.value - 1);
};

const next = () => {
  if (page.value + 1 >= totalPages.value || loading.value) return;
  void load(page.value + 1);
};

const clearFilters = () => {
  filters.value = emptyFilters();
};

onMounted(() => {
  void load(0);
  timer = setInterval(() => {
    if (!document.hidden) void load(page.value);
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
      <div v-if="total" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
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
          <input v-model="filters.set" type="search" placeholder="Set number or name" />
        </label>
        <label>Status
          <select v-model="filters.status">
            <option value="">All</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILED">Failed</option>
          </select>
        </label>
      </div>
      <div class="table-scroll desktop-only">
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
            <tr v-for="row in visible" :key="row.id">
              <td>{{ whenLabel(row.scannedAt) }}</td>
              <td><ChannelLogo :platform="row.platform" :height="16" /></td>
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
            <tr v-if="!visible.length">
              <td colspan="7" class="muted">
                {{ loading
                  ? "Loading scans…"
                  : items.length
                    ? "No scans match those filters."
                    : "No scans recorded yet." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="row in visible" :key="row.id" class="list-card">
          <div class="list-card-row">
            <ChannelLogo :platform="row.platform" :height="16" />
            <span class="badge" :class="{ ok: row.status === 'SUCCESS', bad: row.status === 'FAILED' }">
              {{ row.status === "SUCCESS" ? "Success" : "Failed" }}
            </span>
            <span class="muted">{{ whenLabel(row.scannedAt) }}</span>
          </div>
          <h3>
            <router-link v-if="row.catalogId" :to="`/market/${row.catalogId}`">
              {{ row.setNumber }} {{ row.setName }}
            </router-link>
            <span v-else>{{ row.setNumber }} {{ row.setName }}</span>
          </h3>
          <div class="list-card-meta muted">{{ triggerLabel(row.trigger) }} · {{ row.listingCount ?? "—" }} listings</div>
          <p v-if="row.message" class="muted" style="margin:0">{{ row.message }}</p>
        </article>
        <p v-if="!visible.length" class="muted">
          {{ loading ? "Loading scans…" : items.length ? "No scans match those filters." : "No scans recorded yet." }}
        </p>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
        </div>
      </div>
    </div>
  </div>
</template>
