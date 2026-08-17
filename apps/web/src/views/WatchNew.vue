<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { api, defaultEbayExcludeWords, defaultEbaySearchQuery, DEFAULT_EBAY_SCAN_INTERVAL_MINUTES, DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES, type Catalog, type SetWatch } from "../api";
import WatchFilters from "../components/WatchFilters.vue";

const router = useRouter();
const setNumber = ref("");
const catalog = ref<Catalog | null>(null);
const enabled = ref(true);
const ebaySearchQuery = ref("");
const ebayExcludeWords = ref(defaultEbayExcludeWords);
const ebayFeedbackMin = ref(1);
const ebayScanIntervalMinutes = ref(DEFAULT_EBAY_SCAN_INTERVAL_MINUTES);
const bricklinkScanIntervalMinutes = ref(DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES);
const minPrice = ref<number | null>(null);
const maxPrice = ref<number | null>(null);
const error = ref("");

onMounted(async () => {
  try {
    const health = await api.get<{ ebayDefaultExcludeWords?: string }>("/api/settings/health");
    if (typeof health.ebayDefaultExcludeWords === "string") {
      ebayExcludeWords.value = health.ebayDefaultExcludeWords;
    }
  } catch {
    // Built-in list stays until Settings can be reached.
  }
});

const money = (value?: number | null) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const lookup = async (refresh = false) => {
  error.value = "";
  try {
    catalog.value = await api.get<Catalog>(
      `/api/catalog/lookup?setNumber=${encodeURIComponent(setNumber.value)}&refresh=${refresh}`
    );
    ebaySearchQuery.value = defaultEbaySearchQuery(catalog.value);
  } catch (e) {
    error.value = (e as Error).message;
  }
};

const save = async () => {
  if (!catalog.value) return;
  error.value = "";
  try {
    const created = await api.post<SetWatch>("/api/set-watches", {
      setNumber: setNumber.value,
      enabled: enabled.value,
      ebaySearchQuery: ebaySearchQuery.value,
      ebayExcludeWords: ebayExcludeWords.value,
      ebayFeedbackMin: ebayFeedbackMin.value,
      ebayScanIntervalMinutes: ebayScanIntervalMinutes.value,
      bricklinkScanIntervalMinutes: bricklinkScanIntervalMinutes.value,
      minPrice: minPrice.value,
      maxPrice: maxPrice.value,
    });
    await router.push(`/watches/${created.id}`);
  } catch (e) {
    error.value = (e as Error).message;
  }
};
</script>

<template>
  <div class="grid">
    <h1>New item watch</h1>
    <p class="muted">Watch a LEGO set once, independent of how many copies you list in inventory.</p>
    <div class="card grid">
      <label>LEGO set number
        <div style="display:flex;gap:0.6rem">
          <input v-model="setNumber" placeholder="10143-1" @keyup.enter="lookup(false)" />
          <button class="btn gold" type="button" @click="lookup(false)">Look up</button>
          <button class="btn secondary" type="button" @click="lookup(true)">Refresh</button>
        </div>
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <div v-if="catalog" class="grid two">
        <p><strong>Set</strong><br>{{ catalog.setNumber }} {{ catalog.name }}</p>
        <p><strong>Theme</strong><br>{{ catalog.theme || "—" }}{{ catalog.subtheme ? ` / ${catalog.subtheme}` : "" }}</p>
        <p><strong>Released</strong><br>{{ catalog.releasedDate || catalog.year || "—" }}</p>
        <p><strong>Retired</strong><br>{{ catalog.retiredDate || (catalog.retired ? "Yes" : "No") }}</p>
        <p><strong>Pieces</strong><br>{{ catalog.piecesCount ?? "—" }}</p>
        <p><strong>Minifigs</strong><br>{{ catalog.minifigsCount ?? "—" }}</p>
        <p><strong>Retail (US)</strong><br>{{ money(catalog.retailPriceUs) }}</p>
        <p><strong>Current value new</strong><br>{{ money(catalog.currentValueNew) }}</p>
      </div>
    </div>

    <form v-if="catalog" class="grid" @submit.prevent="save">
      <WatchFilters
        :set-number="catalog.setNumber"
        v-model:enabled="enabled"
        v-model:ebay-search-query="ebaySearchQuery"
        v-model:ebay-exclude-words="ebayExcludeWords"
        v-model:ebay-feedback-min="ebayFeedbackMin"
        v-model:ebay-scan-interval-minutes="ebayScanIntervalMinutes"
        v-model:bricklink-scan-interval-minutes="bricklinkScanIntervalMinutes"
        v-model:min-price="minPrice"
        v-model:max-price="maxPrice"
      />
      <div>
        <button class="btn gold" type="submit">Save watch</button>
      </div>
    </form>
  </div>
</template>
