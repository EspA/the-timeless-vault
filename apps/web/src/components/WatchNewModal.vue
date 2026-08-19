<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { api, ApiError, defaultEbayExcludeWords, defaultEbaySearchQuery, DEFAULT_EBAY_SCAN_INTERVAL_MINUTES, DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES, type Catalog, type SetWatch } from "../api";
import { askAlert } from "../confirm";
import WatchFilters from "./WatchFilters.vue";

const emit = defineEmits<{
  close: [];
  saved: [watch: SetWatch];
}>();

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
const saving = ref(false);

onMounted(async () => {
  document.body.style.overflow = "hidden";
  window.addEventListener("keydown", onKey);
  try {
    const health = await api.get<{ ebayDefaultExcludeWords?: string }>("/api/settings/health");
    if (typeof health.ebayDefaultExcludeWords === "string") {
      ebayExcludeWords.value = health.ebayDefaultExcludeWords;
    }
  } catch {
    // Built-in list stays until Settings can be reached.
  }
});

onUnmounted(() => {
  document.body.style.overflow = "";
  window.removeEventListener("keydown", onKey);
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
  saving.value = true;
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
    emit("saved", created);
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      const label = `${catalog.value.setNumber} ${catalog.value.name}`.trim();
      let existing: SetWatch | undefined;
      try {
        const watches = await api.get<SetWatch[]>("/api/set-watches");
        const wanted = catalog.value.setNumber.toLowerCase();
        existing = watches.find((watch) => watch.setNumber.toLowerCase() === wanted);
      } catch {
        // Still show the duplicate message if the list cannot be loaded.
      }
      const view = await askAlert(
        `${label} is already on the watch list.`,
        {
          title: "Already watching",
          confirmLabel: existing ? "View watch" : "OK",
          cancelLabel: existing ? "Close" : undefined,
        }
      );
      if (view && existing) {
        emit("saved", existing);
      }
      return;
    }
    error.value = e instanceof Error ? e.message : "Could not save watch";
  } finally {
    saving.value = false;
  }
};

const onKey = (event: KeyboardEvent) => {
  if (event.key === "Escape" && !saving.value) {
    event.preventDefault();
    emit("close");
  }
};
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop">
      <form
        class="modal form-modal card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="watch-new-title"
        @submit.prevent="save"
      >
        <div class="form-modal-head">
          <h2 id="watch-new-title">New item watch</h2>
          <button class="btn secondary compact" type="button" :disabled="saving" @click="emit('close')">
            Cancel
          </button>
        </div>

        <div class="form-modal-body grid">
          <p class="muted">Watch a LEGO set once, independent of how many copies you list in inventory.</p>
          <div class="card grid">
            <label>LEGO set number
              <div class="form-modal-lookup">
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

          <WatchFilters
            v-if="catalog"
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
        </div>

        <div class="form-modal-foot">
          <button class="btn secondary" type="button" :disabled="saving" @click="emit('close')">Cancel</button>
          <button class="btn gold" type="submit" :disabled="!catalog || saving">
            {{ saving ? "Saving…" : "Save watch" }}
          </button>
        </div>
      </form>
    </div>
  </Teleport>
</template>
