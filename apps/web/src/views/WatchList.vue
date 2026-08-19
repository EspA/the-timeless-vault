<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { api, SCAN_INTERVALS, type SetWatch } from "../api";
import { askConfirm } from "../confirm";
import WatchNewModal from "../components/WatchNewModal.vue";

const router = useRouter();
const adding = ref(false);

type Column = "set" | "name" | "status" | "theme" | "ebayScan" | "bricklinkScan";
type Sort = { key: Column; dir: "asc" | "desc" };

const PAGE_SIZE = 20;
const watches = ref<SetWatch[]>([]);
const page = ref(0);
const error = ref("");
const sort = ref<Sort>({ key: "set", dir: "asc" });
const filters = ref({
  set: "",
  name: "",
  status: "",
  theme: "",
  ebayScan: "",
  bricklinkScan: "",
});

const scanFrequency = (minutes?: number) => {
  if (!minutes) return "—";
  return SCAN_INTERVALS.find((option) => option.minutes === minutes)?.label
    || `Every ${minutes} minutes`;
};

const statusLabel = (enabled: boolean) => (enabled ? "Watching" : "Paused");

const valueFor = (watch: SetWatch, key: Column): string | number | null => {
  switch (key) {
    case "set":
      return watch.setNumber;
    case "name":
      return watch.name;
    case "status":
      return watch.enabled ? 1 : 0;
    case "theme":
      return watch.theme || "";
    case "ebayScan":
      return watch.ebayScanIntervalMinutes;
    case "bricklinkScan":
      return watch.bricklinkScanIntervalMinutes;
  }
};

const contains = (value: string | number | null, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const filtered = computed(() =>
  watches.value.filter((watch) =>
    contains(watch.setNumber, filters.value.set)
    && contains(watch.name, filters.value.name)
    && (!filters.value.status || String(watch.enabled) === filters.value.status)
    && contains(watch.theme || "", filters.value.theme)
    && (!filters.value.ebayScan || String(watch.ebayScanIntervalMinutes) === filters.value.ebayScan)
    && (!filters.value.bricklinkScan || String(watch.bricklinkScanIntervalMinutes) === filters.value.bricklinkScan)
  )
);

const sorted = computed(() => {
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

const total = computed(() => sorted.value.length);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)));
const visible = computed(() => {
  const start = page.value * PAGE_SIZE;
  return sorted.value.slice(start, start + PAGE_SIZE);
});

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const rangeLabel = computed(() => {
  if (!total.value) return `0 of ${watches.value.length} watches`;
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  return `${start}–${end} of ${total.value} watches`;
});

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
    set: "",
    name: "",
    status: "",
    theme: "",
    ebayScan: "",
    bricklinkScan: "",
  };
};

const previous = () => {
  if (page.value <= 0) return;
  page.value -= 1;
};

const next = () => {
  if (page.value + 1 >= totalPages.value) return;
  page.value += 1;
};

watch(filters, () => {
  page.value = 0;
}, { deep: true });

watch(totalPages, (pages) => {
  if (page.value >= pages) page.value = Math.max(0, pages - 1);
});

const load = async () => {
  watches.value = await api.get<SetWatch[]>("/api/set-watches");
};

const remove = async (watch: SetWatch) => {
  if (!(await askConfirm(
    `Stop watching ${watch.setNumber} ${watch.name}? Buying opportunity alerts and scan history for this set will also be deleted.`,
    {
      title: "Stop watching",
      confirmLabel: "Stop watching",
    }
  ))) {
    return;
  }
  error.value = "";
  try {
    await api.del(`/api/set-watches/${watch.id}`);
    watches.value = watches.value.filter((row) => row.id !== watch.id);
  } catch (e) {
    error.value = (e as Error).message;
  }
};

const onSaved = async (watch: SetWatch) => {
  adding.value = false;
  await router.push(`/watches/${watch.id}`);
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
    <div style="display:flex;justify-content:space-between;align-items:end">
      <div>
        <h1>Items watch</h1>
      </div>
      <button class="btn gold" type="button" @click="adding = true">New item watch</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="watches.length" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
          <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
            Clear filters
          </button>
          <template v-if="totalPages > 1">
            <button class="btn secondary compact" type="button" :disabled="page <= 0" @click="previous">Previous</button>
            <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages" @click="next">Next</button>
          </template>
        </div>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th><button class="sort-btn" type="button" @click="sortBy('set')">Set{{ sortMark("set") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('name')">Name{{ sortMark("name") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('status')">Status{{ sortMark("status") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('theme')">Theme{{ sortMark("theme") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('ebayScan')">eBay scan{{ sortMark("ebayScan") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('bricklinkScan')">BrickLink scan{{ sortMark("bricklinkScan") }}</button></th>
              <th></th>
            </tr>
            <tr>
              <th><input v-model="filters.set" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.name" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.status" class="column-filter">
                  <option value="">All</option>
                  <option value="true">Watching</option>
                  <option value="false">Paused</option>
                </select>
              </th>
              <th><input v-model="filters.theme" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.ebayScan" class="column-filter">
                  <option value="">All</option>
                  <option v-for="option in SCAN_INTERVALS" :key="'ebay-' + option.minutes" :value="String(option.minutes)">
                    {{ option.label }}
                  </option>
                </select>
              </th>
              <th>
                <select v-model="filters.bricklinkScan" class="column-filter">
                  <option value="">All</option>
                  <option v-for="option in SCAN_INTERVALS" :key="'bl-' + option.minutes" :value="String(option.minutes)">
                    {{ option.label }}
                  </option>
                </select>
              </th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="watch in visible" :key="watch.id">
              <td>{{ watch.setNumber }}</td>
              <td><router-link :to="`/watches/${watch.id}`">{{ watch.name }}</router-link></td>
              <td><span class="badge" :class="{ ok: watch.enabled }">{{ statusLabel(watch.enabled) }}</span></td>
              <td>{{ watch.theme || "—" }}</td>
              <td>{{ scanFrequency(watch.ebayScanIntervalMinutes) }}</td>
              <td>{{ scanFrequency(watch.bricklinkScanIntervalMinutes) }}</td>
              <td>
                <button class="btn danger compact" type="button" @click="remove(watch)">Delete</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="7" class="muted">{{ watches.length ? "No watches match those filters." : "No sets watched yet. Add a LEGO reference to begin." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div style="display:flex;gap:0.5rem">
          <button class="btn secondary compact" type="button" :disabled="page <= 0" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages" @click="next">Next</button>
        </div>
      </div>
    </div>
    <WatchNewModal v-if="adding" @close="adding = false" @saved="onSaved" />
  </div>
</template>
