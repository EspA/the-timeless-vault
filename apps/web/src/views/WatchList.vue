<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { api, SCAN_INTERVALS, type SetWatch } from "../api";
import { askConfirm } from "../confirm";
import WatchNewModal from "../components/WatchNewModal.vue";
import ChannelLogo from "../components/ChannelLogo.vue";

const router = useRouter();
const adding = ref(false);

type Column = "set" | "name" | "median" | "status" | "theme" | "ebayScan" | "bricklinkScan";
type Sort = { key: Column; dir: "asc" | "desc" };

const PAGE_SIZE = 20;
const watches = ref<SetWatch[]>([]);
const page = ref(0);
const error = ref("");
const search = ref("");
const sort = ref<Sort>({ key: "set", dir: "asc" });
const filters = ref({
  set: "",
  name: "",
  median: "",
  status: "",
  theme: "",
  ebayScan: "",
  bricklinkScan: "",
});

const money = (value?: number | null) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

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
    case "median":
      return watch.medianPrice ?? null;
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
    && contains(money(watch.medianPrice), filters.value.median)
    && (!filters.value.status || String(watch.enabled) === filters.value.status)
    && contains(watch.theme || "", filters.value.theme)
    && (!filters.value.ebayScan || String(watch.ebayScanIntervalMinutes) === filters.value.ebayScan)
    && (!filters.value.bricklinkScan || String(watch.bricklinkScanIntervalMinutes) === filters.value.bricklinkScan)
    && (
      !search.value.trim()
      || contains(watch.setNumber, search.value)
      || contains(watch.name, search.value)
      || contains(watch.theme || "", search.value)
    )
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
    median: "",
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

const toggling = ref<Record<string, boolean>>({});

const load = async () => {
  watches.value = await api.get<SetWatch[]>("/api/set-watches");
};

const toggleEnabled = async (watch: SetWatch) => {
  if (toggling.value[watch.id]) return;
  const next = !watch.enabled;
  toggling.value = { ...toggling.value, [watch.id]: true };
  error.value = "";
  watches.value = watches.value.map((row) => (row.id === watch.id ? { ...row, enabled: next } : row));
  try {
    const saved = await api.put<SetWatch>(`/api/set-watches/${watch.id}`, {
      enabled: next,
      ebaySearchQuery: watch.ebaySearchQuery,
      ebayExcludeWords: watch.ebayExcludeWords ?? "",
      ebayFeedbackMin: watch.ebayFeedbackMin,
      ebayScanIntervalMinutes: watch.ebayScanIntervalMinutes,
      bricklinkScanIntervalMinutes: watch.bricklinkScanIntervalMinutes,
      minPrice: watch.minPrice ?? null,
      maxPrice: watch.maxPrice ?? null,
    });
    watches.value = watches.value.map((row) => (row.id === saved.id ? saved : row));
  } catch (e) {
    watches.value = watches.value.map((row) => (row.id === watch.id ? { ...row, enabled: watch.enabled } : row));
    error.value = e instanceof Error ? e.message : "Could not update market watch";
  } finally {
    const { [watch.id]: _, ...rest } = toggling.value;
    toggling.value = rest;
  }
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
    <div class="page-head">
      <div>
        <h1>Items watch</h1>
      </div>
      <button class="btn gold" type="button" @click="adding = true">New item watch</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="watches.length" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
            Clear filters
          </button>
          <template v-if="totalPages > 1">
            <button class="btn secondary compact" type="button" :disabled="page <= 0" @click="previous">Previous</button>
            <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages" @click="next">Next</button>
          </template>
        </div>
      </div>
      <div class="mobile-filters mobile-only">
        <label>Search
          <input v-model="search" type="search" placeholder="Set, name, or theme" />
        </label>
        <label>Status
          <select v-model="filters.status">
            <option value="">All</option>
            <option value="true">Watching</option>
            <option value="false">Paused</option>
          </select>
        </label>
      </div>
      <div class="table-scroll desktop-only">
        <table>
          <thead>
            <tr>
              <th><button class="sort-btn" type="button" @click="sortBy('set')">Set{{ sortMark("set") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('name')">Name{{ sortMark("name") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('median')">Median{{ sortMark("median") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('status')">Status{{ sortMark("status") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('theme')">Theme{{ sortMark("theme") }}</button></th>
              <th>
                <button class="sort-btn channel-sort" type="button" @click="sortBy('ebayScan')">
                  <ChannelLogo platform="EBAY" :height="14" />{{ sortMark("ebayScan") }}
                </button>
              </th>
              <th>
                <button class="sort-btn channel-sort" type="button" @click="sortBy('bricklinkScan')">
                  <ChannelLogo platform="BRICKLINK" :height="14" />{{ sortMark("bricklinkScan") }}
                </button>
              </th>
              <th></th>
            </tr>
            <tr>
              <th><input v-model="filters.set" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.name" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.median" class="column-filter" type="search" placeholder="Filter" /></th>
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
              <td>{{ money(watch.medianPrice) }}</td>
              <td>
                <label class="switch">
                  <input
                    type="checkbox"
                    :checked="watch.enabled"
                    :disabled="!!toggling[watch.id]"
                    :aria-label="watch.enabled ? 'Pause market watch' : 'Enable market watch'"
                    @change="toggleEnabled(watch)"
                  />
                  <span class="switch-track" aria-hidden="true"></span>
                  <span class="switch-label">{{ statusLabel(watch.enabled) }}</span>
                </label>
              </td>
              <td>{{ watch.theme || "—" }}</td>
              <td>{{ scanFrequency(watch.ebayScanIntervalMinutes) }}</td>
              <td>{{ scanFrequency(watch.bricklinkScanIntervalMinutes) }}</td>
              <td>
                <button class="btn danger compact" type="button" @click="remove(watch)">Delete</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="8" class="muted">{{ watches.length ? "No watches match those filters." : "No sets watched yet. Add a LEGO reference to begin." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="watch in visible" :key="watch.id" class="list-card">
          <h3><router-link :to="`/watches/${watch.id}`">{{ watch.name }}</router-link></h3>
          <div class="list-card-meta muted">{{ watch.setNumber }} · {{ watch.theme || "No theme" }} · {{ money(watch.medianPrice) }}</div>
          <label class="switch">
            <input
              type="checkbox"
              :checked="watch.enabled"
              :disabled="!!toggling[watch.id]"
              :aria-label="watch.enabled ? 'Pause market watch' : 'Enable market watch'"
              @change="toggleEnabled(watch)"
            />
            <span class="switch-track" aria-hidden="true"></span>
            <span class="switch-label">{{ statusLabel(watch.enabled) }}</span>
          </label>
          <div class="list-card-meta muted">
            <span>eBay {{ scanFrequency(watch.ebayScanIntervalMinutes) }}</span>
            <span>BrickLink {{ scanFrequency(watch.bricklinkScanIntervalMinutes) }}</span>
          </div>
          <div class="list-card-actions">
            <router-link class="btn secondary compact" :to="`/watches/${watch.id}`">Open</router-link>
            <button class="btn danger compact" type="button" @click="remove(watch)">Delete</button>
          </div>
        </article>
        <p v-if="!visible.length" class="muted">{{ watches.length ? "No watches match those filters." : "No sets watched yet. Add a LEGO reference to begin." }}</p>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button class="btn secondary compact" type="button" :disabled="page <= 0" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages" @click="next">Next</button>
        </div>
      </div>
    </div>
    <WatchNewModal v-if="adding" @close="adding = false" @saved="onSaved" />
  </div>
</template>
