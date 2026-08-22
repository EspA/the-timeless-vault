<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { api, type InventoryItem, type InventoryPage, type Sale } from "../api";
import ChannelLogo from "./ChannelLogo.vue";

const emit = defineEmits<{
  close: [];
  saved: [sale: Sale];
}>();

const pad = (value: number) => String(value).padStart(2, "0");
const localDateTime = (date = new Date()) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;

const channelPrice = (item: InventoryItem, platform: string) => {
  if (platform === "EBAY") return item.ebayPrice ?? item.price;
  if (platform === "BRICKLINK") return item.bricklinkPrice ?? item.price;
  if (platform === "SHOPIFY") return item.shopifyPrice ?? item.price;
  return item.price;
};

const error = ref("");
const saving = ref(false);
const loading = ref(true);
const inStock = ref<InventoryItem[]>([]);
const form = reactive({
  platform: "EBAY",
  soldAt: localDateTime(),
  sku: "",
  unitPrice: "",
  externalOrderId: "",
  orderUrl: "",
});

const selected = computed(() => inStock.value.find((item) => item.sku === form.sku) ?? null);

const itemLabel = (item: InventoryItem) => {
  const set = item.catalog?.setNumber ? `${item.catalog.setNumber} ` : "";
  const title = item.title || "";
  return `${item.sku} · ${set}${title}`.trim();
};

watch(
  () => [form.sku, form.platform] as const,
  () => {
    if (!selected.value) return;
    const price = channelPrice(selected.value, form.platform);
    form.unitPrice = price == null || Number.isNaN(Number(price)) ? "" : String(price);
  }
);

const loadStock = async () => {
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<InventoryPage>("/api/inventory?stockStatus=IN_STOCK&size=10000&sort=sku&dir=asc");
    inStock.value = result.items;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
};

const save = async () => {
  error.value = "";
  if (!selected.value) {
    error.value = "Select an in-stock item.";
    return;
  }
  const price = Number(form.unitPrice);
  if (!Number.isFinite(price) || price < 0) {
    error.value = "Enter a price.";
    return;
  }
  saving.value = true;
  try {
    const soldAt = form.soldAt ? new Date(form.soldAt).toISOString() : new Date().toISOString();
    const created = await api.post<Sale>("/api/sales", {
      platform: form.platform,
      sku: selected.value.sku,
      setNumber: selected.value.catalog?.setNumber || null,
      itemTitle: selected.value.title || null,
      quantity: Math.max(1, Number(selected.value.quantity) || 1),
      unitPrice: price,
      currency: "USD",
      soldAt,
      externalOrderId: form.externalOrderId.trim() || null,
      orderUrl: form.orderUrl.trim() || null,
    });
    emit("saved", created);
  } catch (e) {
    error.value = (e as Error).message;
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

onMounted(() => {
  document.body.style.overflow = "hidden";
  window.addEventListener("keydown", onKey);
  void loadStock();
});

onUnmounted(() => {
  document.body.style.overflow = "";
  window.removeEventListener("keydown", onKey);
});
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop">
      <form
        class="modal card grid"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sale-new-title"
        @submit.prevent="save"
      >
        <h2 id="sale-new-title">Add sale</h2>
        <p class="muted">Choose an in-stock item. It will be marked Sold and its listings unlisted.</p>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="grid two">
          <label>Channel
            <select v-model="form.platform" required>
              <option value="EBAY">eBay</option>
              <option value="BRICKLINK">BrickLink</option>
              <option value="SHOPIFY">Shopify</option>
              <option value="LOCAL">Local</option>
            </select>
          </label>
          <label>Sold at
            <input v-model="form.soldAt" type="datetime-local" required />
          </label>
        </div>
        <label>SKU
          <select v-model="form.sku" required :disabled="loading || !inStock.length">
            <option value="" disabled>
              {{ loading ? "Loading stock…" : inStock.length ? "Select an in-stock item" : "No in-stock items" }}
            </option>
            <option v-for="item in inStock" :key="item.id" :value="item.sku">
              {{ itemLabel(item) }}
            </option>
          </select>
        </label>
        <p v-if="selected" class="muted" style="margin:0">
          {{ selected.catalog?.setNumber || "—" }}
          · {{ selected.title || "Untitled" }}
          · Qty {{ selected.quantity }}
        </p>
        <label>
          <span class="channel-field-label"><ChannelLogo :platform="form.platform" :height="16" /> price</span>
          <input v-model="form.unitPrice" type="number" min="0" step="0.01" required />
        </label>
        <label>Order ID
          <input v-model="form.externalOrderId" placeholder="Optional" />
        </label>
        <label>Order URL
          <input v-model="form.orderUrl" type="url" placeholder="Optional" />
        </label>
        <div class="confirm-actions">
          <button class="btn secondary" type="button" :disabled="saving" @click="emit('close')">Cancel</button>
          <button class="btn gold" type="submit" :disabled="saving || !selected">
            {{ saving ? "Saving…" : "Add sale" }}
          </button>
        </div>
      </form>
    </div>
  </Teleport>
</template>
