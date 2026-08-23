<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, isUpsTracking, ORDER_STATUSES, type Order, type OrderStatus } from "../api";
import ChannelLogo from "../components/ChannelLogo.vue";
import StockStatusButtons from "../components/StockStatusButtons.vue";
import TrackingNumber from "../components/TrackingNumber.vue";
import { askConfirm } from "../confirm";

const route = useRoute();
const router = useRouter();
const order = ref<Order | null>(null);
const trackingNumber = ref("");
const shippingProvider = ref("");
const error = ref("");
const loading = ref(false);
const saving = ref(false);
const statusBusy = ref(false);
const justSaved = ref(false);
let savedTimer: ReturnType<typeof setTimeout> | undefined;

const money = (value: number | null | undefined, currency = "USD") => {
  if (value == null || Number.isNaN(Number(value))) return "—";
  const amount = `$${Number(value).toFixed(2)}`;
  return currency && currency !== "USD" ? `${amount} ${currency}` : amount;
};

const whenLabel = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const title = computed(() => {
  if (!order.value) return "Order";
  return order.value.itemTitle || order.value.sku || order.value.externalOrderId || "Order";
});

const subtitle = computed(() => {
  if (!order.value) return "";
  return [order.value.sku, order.value.setNumber].filter(Boolean).join(" · ");
});

const apply = (loaded: Order) => {
  order.value = loaded;
  trackingNumber.value = loaded.trackingNumber || "";
  shippingProvider.value = loaded.shippingProvider || "";
};

watch(trackingNumber, (value) => {
  if (!shippingProvider.value.trim() && isUpsTracking(value, shippingProvider.value)) {
    shippingProvider.value = "UPS";
  }
});

const load = async () => {
  loading.value = true;
  error.value = "";
  try {
    apply(await api.get<Order>(`/api/orders/${String(route.params.id)}`));
  } catch (e) {
    error.value = (e as Error).message;
    order.value = null;
  } finally {
    loading.value = false;
  }
};

const persist = async (patch: { status?: OrderStatus; trackingNumber?: string; shippingProvider?: string }) => {
  if (!order.value) return;
  const updated = await api.put<Order>(`/api/orders/${order.value.id}`, {
    status: patch.status ?? order.value.status,
    trackingNumber: patch.trackingNumber ?? trackingNumber.value,
    shippingProvider: patch.shippingProvider ?? shippingProvider.value,
  });
  apply(updated);
};

const setStatus = async (next: string) => {
  if (!order.value || order.value.status === next || statusBusy.value) return;
  statusBusy.value = true;
  error.value = "";
  try {
    await persist({ status: next as OrderStatus });
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    statusBusy.value = false;
  }
};

const save = async () => {
  if (!order.value || saving.value) return;
  error.value = "";
  justSaved.value = false;
  saving.value = true;
  try {
    await persist({
      trackingNumber: trackingNumber.value,
      shippingProvider: shippingProvider.value,
    });
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
  if (!order.value) return;
  const confirmed = await askConfirm(
    `Delete ${title.value} from orders? The inventory item will be kept. Sync will not bring this channel order back.`,
    { title: "Delete order" }
  );
  if (!confirmed) return;
  error.value = "";
  try {
    await api.del(`/api/orders/${order.value.id}`);
    await router.push("/orders");
  } catch (e) {
    error.value = (e as Error).message;
  }
};

onMounted(() => {
  void load();
});

watch(() => route.params.id, () => {
  void load();
});

onBeforeUnmount(() => clearTimeout(savedTimer));
</script>

<template>
  <p v-if="error && !order" class="error">{{ error }}</p>
  <p v-else-if="loading && !order" class="muted">Loading order…</p>
  <div v-else-if="order" class="grid">
    <div class="page-head">
      <div>
        <p class="muted">
          <router-link to="/orders">Orders</router-link>
          <span v-if="subtitle"> · {{ subtitle }}</span>
        </p>
        <h1>{{ title }}</h1>
      </div>
      <StockStatusButtons
        aria-label="Order status"
        :options="ORDER_STATUSES"
        :model-value="order.status"
        :disabled="statusBusy"
        @update:model-value="setStatus"
      />
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="grid save-fields" :class="{ flash: justSaved }">
      <div class="card grid">
        <div class="list-card-row">
          <ChannelLogo :platform="order.platform" :height="18" />
          <a v-if="order.orderUrl" :href="order.orderUrl" target="_blank" rel="noopener noreferrer">
            {{ order.externalOrderId }}
          </a>
          <span v-else>{{ order.externalOrderId }}</span>
          <span v-if="order.inventoryCreated" class="badge">Added</span>
        </div>
        <div class="grid three">
          <label>When
            <input :value="whenLabel(order.soldAt)" disabled />
          </label>
          <label>Quantity
            <input :value="order.quantity" disabled />
          </label>
          <label>Price
            <input :value="money(order.unitPrice, order.currency)" disabled />
          </label>
        </div>
        <div class="grid three">
          <label>Shipping
            <input :value="money(order.shippingCost ?? 0, order.currency)" disabled />
          </label>
          <label>Fee
            <input :value="money(order.platformFee ?? 0, order.currency)" disabled />
          </label>
          <label>Inventory item
            <p v-if="order.inventoryItemId" style="margin:0.45rem 0 0">
              <router-link :to="`/inventory/${order.inventoryItemId}`">
                {{ order.sku || "Open item" }}
              </router-link>
            </p>
            <input v-else value="—" disabled />
          </label>
        </div>
      </div>

      <div class="card grid">
        <div class="grid two">
          <label>Tracking
            <input v-model="trackingNumber" placeholder="Tracking number" />
            <TrackingNumber
              v-if="isUpsTracking(trackingNumber, shippingProvider)"
              :tracking="trackingNumber"
              :provider="shippingProvider"
            />
          </label>
          <label>Shipping provider
            <input v-model="shippingProvider" placeholder="Shipping provider" />
          </label>
        </div>
        <div class="save-row">
          <button
            class="btn secondary"
            :class="{ saved: justSaved }"
            type="button"
            :disabled="saving"
            @click="save"
          >
            {{ saving ? "Saving…" : justSaved ? "Saved" : "Save changes" }}
          </button>
          <span v-if="justSaved" class="save-note">Changes saved</span>
          <button class="btn danger compact" type="button" @click="remove">Delete order</button>
        </div>
      </div>
    </div>
  </div>
</template>
