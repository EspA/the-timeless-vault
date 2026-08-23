<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from "vue";
import {
  api,
  isUpsTracking,
  type InventoryItem,
  type InventoryPage,
  type Order,
  type OrderStatus,
} from "../api";
import StockStatusButtons from "./StockStatusButtons.vue";

const MANUAL_STATUSES = [
  { value: "OPEN", label: "Open", shortLabel: "Open" },
  { value: "SHIPPED", label: "Shipped", shortLabel: "Shipped" },
  { value: "COMPLETED", label: "Delivered", shortLabel: "Delivered" },
] as const;

const emit = defineEmits<{
  close: [];
  saved: [order: Order];
}>();

const pad = (value: number) => String(value).padStart(2, "0");
const localDateTime = (date = new Date()) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;

const error = ref("");
const saving = ref(false);
const looking = ref(false);
const query = ref("");
const matches = ref<InventoryItem[]>([]);
const selected = ref<InventoryItem | null>(null);
const lookupInput = ref<HTMLInputElement | null>(null);
const form = reactive({
  soldAt: localDateTime(),
  status: "OPEN" as OrderStatus,
  unitPrice: "",
  shippingCost: "",
  platformFee: "",
  trackingNumber: "",
  shippingProvider: "",
});

const itemLabel = (item: InventoryItem) => {
  const set = item.catalog?.setNumber ? `${item.catalog.setNumber} ` : "";
  const title = item.title || "";
  return `${item.sku} · ${set}${title}`.trim();
};

const applyItem = (item: InventoryItem) => {
  selected.value = item;
  matches.value = [];
  const price = item.price;
  form.unitPrice = price == null || Number.isNaN(Number(price)) ? "" : String(price);
};

watch(
  () => form.trackingNumber,
  (value) => {
    if (!form.shippingProvider.trim() && isUpsTracking(value, form.shippingProvider)) {
      form.shippingProvider = "UPS";
    }
  }
);

const optionalMoney = (value: string, label: string) => {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const amount = Number(trimmed);
  if (!Number.isFinite(amount) || amount < 0) {
    throw new Error(`Enter a valid ${label}.`);
  }
  return amount;
};

const lookup = async () => {
  const needle = query.value.trim();
  error.value = "";
  selected.value = null;
  matches.value = [];
  if (!needle) {
    error.value = "Enter a SKU or set number.";
    return;
  }
  looking.value = true;
  try {
    const result = await api.get<InventoryPage>(
      `/api/inventory?q=${encodeURIComponent(needle)}&stockStatus=IN_STOCK&size=20&sort=sku&dir=asc`
    );
    if (result.items.length === 1) {
      applyItem(result.items[0]);
      return;
    }
    if (result.items.length > 1) {
      matches.value = result.items;
      return;
    }
    const any = await api.get<InventoryPage>(
      `/api/inventory?q=${encodeURIComponent(needle)}&size=1`
    );
    error.value = any.total ? "That item is not in stock." : "No inventory item found.";
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    looking.value = false;
  }
};

const save = async () => {
  error.value = "";
  if (!selected.value) {
    error.value = "Look up an in-stock item.";
    return;
  }
  const price = Number(form.unitPrice);
  if (!Number.isFinite(price) || price < 0) {
    error.value = "Enter a price.";
    return;
  }
  let shippingCost: number | null;
  let platformFee: number | null;
  try {
    shippingCost = optionalMoney(form.shippingCost, "shipping amount");
    platformFee = optionalMoney(form.platformFee, "fee");
  } catch (e) {
    error.value = (e as Error).message;
    return;
  }
  saving.value = true;
  try {
    const soldAt = form.soldAt ? new Date(form.soldAt).toISOString() : new Date().toISOString();
    const created = await api.post<Order>("/api/orders", {
      platform: "LOCAL",
      sku: selected.value.sku,
      setNumber: selected.value.catalog?.setNumber || null,
      itemTitle: selected.value.title || null,
      quantity: Math.max(1, Number(selected.value.quantity) || 1),
      unitPrice: price,
      currency: "USD",
      soldAt,
      status: form.status,
      trackingNumber: form.trackingNumber.trim() || null,
      shippingProvider: form.shippingProvider.trim() || null,
      shippingCost,
      platformFee,
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
  lookupInput.value?.focus();
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
        aria-labelledby="order-new-title"
        @submit.prevent="save"
      >
        <h2 id="order-new-title">Add order</h2>
        <p class="muted">Look up an in-stock item for a Local order. It will be marked Sold and its listings unlisted.</p>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="grid two">
          <label>Sold at
            <input v-model="form.soldAt" type="datetime-local" required />
          </label>
          <label class="stacked-field">Status
            <StockStatusButtons
              aria-label="Order status"
              :options="MANUAL_STATUSES"
              :model-value="form.status"
              @update:model-value="form.status = $event as OrderStatus"
            />
          </label>
        </div>
        <label>Item
          <div class="form-modal-lookup">
            <input
              ref="lookupInput"
              v-model="query"
              placeholder="SKU or set number"
              @keydown.enter.prevent="lookup"
            />
            <button class="btn gold" type="button" :disabled="looking" @click="lookup">
              {{ looking ? "Looking…" : "Look up" }}
            </button>
          </div>
        </label>
        <div v-if="matches.length" class="card grid" style="margin:0">
          <p class="muted" style="margin:0">{{ matches.length }} in-stock matches</p>
          <button
            v-for="item in matches"
            :key="item.id"
            class="btn secondary"
            type="button"
            @click="applyItem(item)"
          >
            {{ itemLabel(item) }}
          </button>
        </div>
        <p v-if="selected" class="muted" style="margin:0">
          {{ selected.sku }}
          · {{ selected.catalog?.setNumber || "—" }}
          · {{ selected.title || "Untitled" }}
          · Qty {{ selected.quantity }}
        </p>
        <div class="grid three">
          <label>Price
            <input v-model="form.unitPrice" type="number" min="0" step="0.01" required />
          </label>
          <label>Shipping
            <input v-model="form.shippingCost" type="number" min="0" step="0.01" placeholder="Optional" />
          </label>
          <label>Fee
            <input v-model="form.platformFee" type="number" min="0" step="0.01" placeholder="Optional" />
          </label>
        </div>
        <div class="grid two">
          <label>Tracking number
            <input v-model="form.trackingNumber" placeholder="Optional" />
          </label>
          <label>Shipping provider
            <input v-model="form.shippingProvider" placeholder="Optional" />
          </label>
        </div>
        <div class="confirm-actions">
          <button class="btn secondary" type="button" :disabled="saving" @click="emit('close')">Cancel</button>
          <button class="btn gold" type="submit" :disabled="saving || !selected">
            {{ saving ? "Saving…" : "Add order" }}
          </button>
        </div>
      </form>
    </div>
  </Teleport>
</template>
