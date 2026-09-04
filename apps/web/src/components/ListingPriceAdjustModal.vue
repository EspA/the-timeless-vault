<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { api, type ListingAdjustment } from "../api";
import ChannelLogo from "./ChannelLogo.vue";

const props = defineProps<{
  row: ListingAdjustment;
}>();

const emit = defineEmits<{
  close: [];
  saved: [row: ListingAdjustment];
}>();

const error = ref("");
const saving = ref(false);
const recommendedPrice = ref(
  props.row.recommendedPrice == null ? "" : Number(props.row.recommendedPrice).toFixed(2)
);

const typeLabel = (type: string) => (type || "").replaceAll("_", " ");

const money = (value?: number | null) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const marginHint = computed(() => {
  switch ((props.row.platform || "").toUpperCase()) {
    case "EBAY":
      return "45% eBay margin";
    case "BRICKLINK":
      return "40% BrickLink margin";
    case "BRICKOWL":
      return "40% Brick Owl margin";
    case "SHOPIFY":
      return "32% Shopify margin";
    default:
      return "platform margin";
  }
});

const onKey = (event: KeyboardEvent) => {
  if (event.key === "Escape" && !saving.value) emit("close");
};

onMounted(() => {
  document.body.style.overflow = "hidden";
  window.addEventListener("keydown", onKey);
});

onUnmounted(() => {
  document.body.style.overflow = "";
  window.removeEventListener("keydown", onKey);
});

const save = async () => {
  const price = Number(String(recommendedPrice.value).trim());
  if (!Number.isFinite(price) || price < 0.01) {
    error.value = "Enter a recommended price of at least 0.01";
    return;
  }
  error.value = "";
  saving.value = true;
  try {
    const saved = await api.post<ListingAdjustment>(`/api/listing-adjustments/${props.row.id}/apply`, { price });
    emit("saved", saved);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not update listing price";
  } finally {
    saving.value = false;
  }
};
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="saving ? undefined : emit('close')">
      <form class="modal form-modal card" role="dialog" aria-modal="true" aria-labelledby="adjust-price-title" @submit.prevent="save">
        <div class="form-modal-head">
          <h2 id="adjust-price-title">Adjust listing price</h2>
          <button class="btn secondary compact" type="button" :disabled="saving" @click="emit('close')">Cancel</button>
        </div>
        <div class="form-modal-body grid">
          <p v-if="error" class="error">{{ error }}</p>
          <div class="list-card-row">
            <span class="badge notify-type price">{{ typeLabel(row.type) }}</span>
            <ChannelLogo v-if="row.platform" :platform="row.platform" :height="16" />
          </div>
          <p class="muted" style="margin:0">{{ row.inventoryLabel || "Listing" }}</p>
          <label>Cost
            <input :value="money(row.cost)" type="text" readonly />
          </label>
          <label>Current listing price
            <input :value="money(row.currentListingPrice)" type="text" readonly />
          </label>
          <label>Recommend price
            <input v-model="recommendedPrice" type="number" min="0.01" step="0.01" required />
          </label>
          <p class="muted" style="margin:0">
            Floor is cost plus the {{ marginHint }}.
            {{ row.type === "PRICE_LOW" ? "PRICE LOW uses the market median when that stays above the floor." : "PRICE HIGH starts at that floor." }}
          </p>
        </div>
        <div class="form-modal-foot">
          <button class="btn secondary" type="button" :disabled="saving" @click="emit('close')">Cancel</button>
          <button class="btn gold" type="submit" :disabled="saving">
            {{ saving ? "Updating…" : "Update listing" }}
          </button>
        </div>
      </form>
    </div>
  </Teleport>
</template>
