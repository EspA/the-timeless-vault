<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, DEFAULT_EBAY_SCAN_INTERVAL_MINUTES, DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES, type SetWatch } from "../api";
import { askConfirm } from "../confirm";
import WatchFilters from "../components/WatchFilters.vue";

const route = useRoute();
const router = useRouter();
const watch = ref<SetWatch | null>(null);
const enabled = ref(true);
const ebaySearchQuery = ref("");
const ebayExcludeWords = ref("");
const ebayFeedbackMin = ref(1);
const ebayScanIntervalMinutes = ref(DEFAULT_EBAY_SCAN_INTERVAL_MINUTES);
const bricklinkScanIntervalMinutes = ref(DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES);
const minPrice = ref<number | null>(null);
const maxPrice = ref<number | null>(null);
const error = ref("");
const saving = ref(false);
const justSaved = ref(false);
let savedTimer: ReturnType<typeof setTimeout> | undefined;

const money = (value?: number | null) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const year = (value?: string | null) => {
  if (!value) return "";
  const match = String(value).match(/\d{4}/);
  return match ? match[0] : "";
};

const setSummary = computed(() => {
  const item = watch.value;
  if (!item) return "";
  const parts: string[] = [];
  const theme = [item.theme, item.subtheme].filter(Boolean).join(" / ");
  if (theme) parts.push(theme);
  const released = year(item.releasedDate);
  if (released) parts.push(`Released ${released}`);
  const retired = year(item.retiredDate);
  if (retired) {
    parts.push(`Retired ${retired}`);
  } else if (item.retired) {
    parts.push("Retired");
  }
  if (item.piecesCount != null) parts.push(`${item.piecesCount} pieces`);
  if (item.minifigsCount != null) parts.push(`${item.minifigsCount} minifigs`);
  if (item.retailPriceUs != null) parts.push(`Retail ${money(item.retailPriceUs)}`);
  return parts.join(" · ");
});

const apply = (loaded: SetWatch) => {
  watch.value = loaded;
  enabled.value = loaded.enabled;
  ebaySearchQuery.value = loaded.ebaySearchQuery || "";
  ebayExcludeWords.value = loaded.ebayExcludeWords ?? "";
  ebayFeedbackMin.value = loaded.ebayFeedbackMin ?? 1;
  ebayScanIntervalMinutes.value = loaded.ebayScanIntervalMinutes ?? DEFAULT_EBAY_SCAN_INTERVAL_MINUTES;
  bricklinkScanIntervalMinutes.value = loaded.bricklinkScanIntervalMinutes ?? DEFAULT_BRICKLINK_SCAN_INTERVAL_MINUTES;
  minPrice.value = loaded.minPrice ?? null;
  maxPrice.value = loaded.maxPrice ?? null;
};

const load = async () => {
  apply(await api.get<SetWatch>(`/api/set-watches/${String(route.params.id)}`));
};

const save = async () => {
  if (!watch.value || saving.value) return;
  error.value = "";
  justSaved.value = false;
  saving.value = true;
  try {
    apply(await api.put<SetWatch>(`/api/set-watches/${watch.value.id}`, {
      enabled: enabled.value,
      ebaySearchQuery: ebaySearchQuery.value,
      ebayExcludeWords: ebayExcludeWords.value ?? "",
      ebayFeedbackMin: ebayFeedbackMin.value,
      ebayScanIntervalMinutes: ebayScanIntervalMinutes.value,
      bricklinkScanIntervalMinutes: bricklinkScanIntervalMinutes.value,
      minPrice: minPrice.value,
      maxPrice: maxPrice.value,
    }));
    justSaved.value = true;
    clearTimeout(savedTimer);
    savedTimer = setTimeout(() => {
      justSaved.value = false;
    }, 1800);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
};

const remove = async () => {
  if (!watch.value) return;
  if (!(await askConfirm(
    `Stop watching ${watch.value.setNumber} ${watch.value.name}? Buying opportunity alerts and scan history for this set will also be deleted.`,
    {
      title: "Stop watching",
      confirmLabel: "Stop watching",
    }
  ))) {
    return;
  }
  error.value = "";
  try {
    await api.del(`/api/set-watches/${watch.value.id}`);
    await router.push("/watches");
  } catch (e) {
    error.value = (e as Error).message;
  }
};

onMounted(async () => {
  try {
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  }
});

onBeforeUnmount(() => clearTimeout(savedTimer));
</script>

<template>
  <p v-if="error && !watch" class="error">{{ error }}</p>
  <div v-if="watch" class="grid">
    <div class="page-head">
      <div>
        <p class="muted">{{ watch.setNumber }}</p>
        <h1>{{ watch.name }}</h1>
        <p v-if="setSummary" class="muted">{{ setSummary }}</p>
      </div>
      <router-link class="btn secondary" :to="`/market/${watch.catalogId}`">Open market dashboard</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="save-fields" :class="{ flash: justSaved }">
      <WatchFilters
        :set-number="watch.setNumber"
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
    <div class="save-row">
      <button
        class="btn gold"
        :class="{ saved: justSaved }"
        type="button"
        :disabled="saving"
        @click="save"
      >
        {{ saving ? "Saving…" : justSaved ? "Saved" : "Save watch" }}
      </button>
      <span v-if="justSaved" class="save-note">Filters saved</span>
      <button class="btn danger compact" type="button" @click="remove">Delete</button>
    </div>
  </div>
</template>
