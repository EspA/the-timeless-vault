<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { api, type ListingAdjustment } from "../api";
import ChannelLogo from "../components/ChannelLogo.vue";
import ListingPriceAdjustModal from "../components/ListingPriceAdjustModal.vue";

type Column = "when" | "type" | "channel" | "listing" | "status" | "inventory";
type Sort = { key: Column; dir: "asc" | "desc" };

const PAGE_SIZE = 10;
const TYPE_OPTIONS = [
  { value: "", label: "All" },
  { value: "PRICE_HIGH", label: "PRICE HIGH" },
  { value: "PRICE_LOW", label: "PRICE LOW" },
];

const items = ref<ListingAdjustment[]>([]);
const page = ref(0);
const error = ref("");
const search = ref("");
const dismissing = ref<Record<string, boolean>>({});
const adjusting = ref<ListingAdjustment | null>(null);
const sort = ref<Sort>({ key: "when", dir: "desc" });
const filters = ref({
  when: "",
  type: "",
  channel: "",
  listing: "",
  status: "",
  inventory: "",
});

const typeLabel = (type: string) => (type || "").replaceAll("_", " ");

const whenLabel = (value: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const listingHref = (url?: string) => !!url && url.startsWith("/") && !url.startsWith("//");

const valueFor = (row: ListingAdjustment, key: Column): string | number | null => {
  switch (key) {
    case "when":
      return row.when ? new Date(row.when).getTime() : null;
    case "type":
      return row.type || "";
    case "channel":
      return row.platform || "";
    case "listing":
      return row.listingUrl || "";
    case "status":
      return row.listingStatus || "";
    case "inventory":
      return row.inventoryLabel || "";
  }
};

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const filtered = computed(() =>
  items.value.filter((row) =>
    contains(whenLabel(row.when), filters.value.when)
    && (!filters.value.type || row.type === filters.value.type)
    && (!filters.value.channel || row.platform === filters.value.channel)
    && contains(row.listingUrl || "", filters.value.listing)
    && contains(row.listingStatus || "", filters.value.status)
    && contains(row.inventoryLabel || "", filters.value.inventory)
    && (
      !search.value.trim()
      || contains(typeLabel(row.type), search.value)
      || contains(row.platform, search.value)
      || contains(row.listingStatus, search.value)
      || contains(row.inventoryLabel, search.value)
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
  if (!total.value) return `0 of ${items.value.length} adjustments`;
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  return `${start}–${end} of ${total.value} adjustments`;
});

const sortBy = (key: Column) => {
  sort.value = sort.value.key === key
    ? { key, dir: sort.value.dir === "asc" ? "desc" : "asc" }
    : { key, dir: key === "when" ? "desc" : "asc" };
  page.value = 0;
};

const sortMark = (key: Column) => {
  if (sort.value.key !== key) return "";
  return sort.value.dir === "asc" ? " ▲" : " ▼";
};

const clearFilters = () => {
  filters.value = {
    when: "",
    type: "",
    channel: "",
    listing: "",
    status: "",
    inventory: "",
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
  items.value = await api.get<ListingAdjustment[]>("/api/listing-adjustments");
};

const onAdjusted = (row: ListingAdjustment) => {
  adjusting.value = null;
  items.value = items.value.filter((item) => item.id !== row.id);
};

const dismiss = async (row: ListingAdjustment) => {
  if (dismissing.value[row.id]) return;
  dismissing.value = { ...dismissing.value, [row.id]: true };
  error.value = "";
  try {
    await api.post(`/api/listing-adjustments/${row.id}/dismiss`);
    items.value = items.value.filter((item) => item.id !== row.id);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not dismiss listing adjustment";
  } finally {
    const { [row.id]: _, ...rest } = dismissing.value;
    dismissing.value = rest;
  }
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
        <h1>Listing adjustment</h1>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="items.length" class="pager" style="margin:0 0 0.85rem">
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
          <input v-model="search" type="search" placeholder="Type, channel, status, or item" />
        </label>
        <label>Type
          <select v-model="filters.type">
            <option v-for="option in TYPE_OPTIONS" :key="option.value || 'all'" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
      </div>
      <div class="table-scroll desktop-only">
        <table>
          <thead>
            <tr>
              <th><button class="sort-btn" type="button" @click="sortBy('when')">When{{ sortMark("when") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('inventory')">Inventory{{ sortMark("inventory") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('type')">Type{{ sortMark("type") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('channel')">Channel{{ sortMark("channel") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('listing')">Listing{{ sortMark("listing") }}</button></th>
              <th><button class="sort-btn" type="button" @click="sortBy('status')">Listing Status{{ sortMark("status") }}</button></th>
              <th></th>
            </tr>
            <tr>
              <th><input v-model="filters.when" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.inventory" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.type" class="column-filter">
                  <option v-for="option in TYPE_OPTIONS" :key="option.value || 'all'" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </th>
              <th>
                <select v-model="filters.channel" class="column-filter">
                  <option value="">All</option>
                  <option value="EBAY">eBay</option>
                  <option value="BRICKLINK">BrickLink</option>
                </select>
              </th>
              <th><input v-model="filters.listing" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.status" class="column-filter" type="search" placeholder="Filter" /></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>{{ whenLabel(row.when) }}</td>
              <td>
                <router-link v-if="row.inventoryItemId" :to="`/inventory/${row.inventoryItemId}`">
                  {{ row.inventoryLabel || "Open item" }}
                </router-link>
                <span v-else class="muted">—</span>
              </td>
              <td><span class="badge notify-type price">{{ typeLabel(row.type) }}</span></td>
              <td>
                <ChannelLogo v-if="row.platform" :platform="row.platform" :height="16" />
                <span v-else class="muted">—</span>
              </td>
              <td>
                <router-link
                  v-if="row.listingUrl && listingHref(row.listingUrl)"
                  class="btn secondary compact"
                  :to="row.listingUrl"
                >Open listing</router-link>
                <a
                  v-else-if="row.listingUrl"
                  class="btn secondary compact"
                  :href="row.listingUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >Open listing</a>
                <span v-else class="muted">—</span>
              </td>
              <td>{{ row.listingStatus || "—" }}</td>
              <td>
                <button class="btn gold compact" type="button" @click="adjusting = row">Adjust</button>
                <button
                  class="btn danger compact"
                  type="button"
                  :disabled="!!dismissing[row.id]"
                  @click="dismiss(row)"
                >Dismiss</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="7" class="muted">
                {{ items.length
                  ? "No listing adjustments match those filters."
                  : "No listings currently need a price adjustment." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="row in visible" :key="row.id" class="list-card">
          <div class="list-card-row">
            <span class="badge notify-type price">{{ typeLabel(row.type) }}</span>
            <ChannelLogo v-if="row.platform" :platform="row.platform" :height="16" />
            <span class="muted">{{ whenLabel(row.when) }}</span>
          </div>
          <h3>
            <router-link v-if="row.inventoryItemId" :to="`/inventory/${row.inventoryItemId}`">
              {{ row.inventoryLabel || "Open item" }}
            </router-link>
            <span v-else>{{ row.inventoryLabel || "Listing adjustment" }}</span>
          </h3>
          <div class="list-card-meta muted">{{ row.listingStatus || "No listing status" }}</div>
          <div class="list-card-actions">
            <router-link
              v-if="row.listingUrl && listingHref(row.listingUrl)"
              class="btn secondary compact"
              :to="row.listingUrl"
            >Open listing</router-link>
            <a
              v-else-if="row.listingUrl"
              class="btn secondary compact"
              :href="row.listingUrl"
              target="_blank"
              rel="noopener noreferrer"
            >Open listing</a>
            <router-link
              v-if="row.inventoryItemId"
              class="btn secondary compact"
              :to="`/inventory/${row.inventoryItemId}`"
            >Open item</router-link>
            <button class="btn gold compact" type="button" @click="adjusting = row">Adjust</button>
            <button
              class="btn danger compact"
              type="button"
              :disabled="!!dismissing[row.id]"
              @click="dismiss(row)"
            >Dismiss</button>
          </div>
        </article>
        <p v-if="!visible.length" class="muted">
          {{ items.length
            ? "No listing adjustments match those filters."
            : "No listings currently need a price adjustment." }}
        </p>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button class="btn secondary compact" type="button" :disabled="page <= 0" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages" @click="next">Next</button>
        </div>
      </div>
    </div>
    <ListingPriceAdjustModal
      v-if="adjusting"
      :row="adjusting"
      @close="adjusting = null"
      @saved="onAdjusted"
    />
  </div>
</template>
