<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  api,
  canonicalizeShippingProvider,
  isUpsTracking,
  ORDER_STATUSES,
  shippingProviderSelectOptions,
  type Order,
  type OrderStatus,
} from "../api";
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

const lines = computed(() => order.value?.lines ?? []);

const title = computed(() => {
  if (!order.value) return "Order";
  if (lines.value.length > 1) {
    return `${lines.value.length} items`;
  }
  return order.value.itemTitle || order.value.sku || order.value.externalOrderId || "Order";
});

const subtitle = computed(() => {
  if (!order.value) return "";
  if (lines.value.length > 1) {
    return order.value.externalOrderId || "";
  }
  return [order.value.sku, order.value.setNumber].filter(Boolean).join(" · ");
});

const merchandiseTotal = computed(() => {
  if (!order.value) return 0;
  if (order.value.merchandiseTotal != null) return order.value.merchandiseTotal;
  return lines.value.reduce((sum, line) => sum + Number(line.lineTotal ?? 0), 0);
});

const apply = (loaded: Order) => {
  order.value = loaded;
  trackingNumber.value = loaded.trackingNumber || "";
  const provider = loaded.shippingProvider || "";
  shippingProvider.value = canonicalizeShippingProvider(provider) || provider || "UPS";
};

const providerOptions = computed(() => shippingProviderSelectOptions(shippingProvider.value));

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
          <label>Shipping
            <input :value="money(order.shippingCost ?? 0, order.currency)" disabled />
          </label>
          <label>Fee
            <input :value="money(order.platformFee ?? 0, order.currency)" disabled />
          </label>
        </div>
      </div>

      <div class="card grid">
        <div class="page-head" style="margin:0">
          <h2 style="margin:0;font-size:1.05rem">Lines</h2>
          <p class="muted" style="margin:0">Total {{ money(merchandiseTotal, order.currency) }}</p>
        </div>
        <div class="table-scroll desktop-only">
          <table class="po-lines">
            <colgroup>
              <col class="po-col-set" />
              <col class="po-col-title" />
              <col class="po-col-qty" />
              <col class="po-col-unit" />
              <col class="po-col-sku" />
            </colgroup>
            <thead>
              <tr>
                <th>Set</th>
                <th>Title</th>
                <th>Qty</th>
                <th>Unit price</th>
                <th>SKU</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in lines" :key="line.id">
                <td>{{ line.setNumber || "—" }}</td>
                <td>{{ line.itemTitle || "—" }}</td>
                <td>{{ line.quantity }}</td>
                <td>{{ money(line.unitPrice, order.currency) }}</td>
                <td>
                  <router-link v-if="line.inventoryItemId && line.sku" :to="`/inventory/${line.inventoryItemId}`">
                    {{ line.sku }}
                  </router-link>
                  <span v-else>{{ line.sku || "—" }}</span>
                  <span v-if="line.inventoryCreated" class="badge" style="margin-left:0.4rem">Added</span>
                </td>
              </tr>
              <tr v-if="!lines.length">
                <td colspan="5" class="muted">No lines on this order.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="list-cards mobile-only">
          <article v-for="line in lines" :key="line.id" class="list-card grid">
            <h3 style="margin:0;font-size:1rem">{{ line.itemTitle || line.sku || "Line" }}</h3>
            <p class="muted" style="margin:0">
              {{ [line.setNumber, `Qty ${line.quantity}`, money(line.unitPrice, order.currency)].filter(Boolean).join(" · ") }}
            </p>
            <p v-if="line.sku" class="muted" style="margin:0">
              SKU
              <router-link v-if="line.inventoryItemId" :to="`/inventory/${line.inventoryItemId}`">{{ line.sku }}</router-link>
              <span v-else>{{ line.sku }}</span>
              <span v-if="line.inventoryCreated" class="badge" style="margin-left:0.4rem">Added</span>
            </p>
          </article>
        </div>
      </div>

      <div class="card grid">
        <div class="grid two">
          <label>Tracking
            <input v-model="trackingNumber" placeholder="Tracking number" />
            <TrackingNumber
              v-if="trackingNumber.trim()"
              :tracking="trackingNumber"
              :provider="shippingProvider"
            />
          </label>
          <label>Shipping provider
            <select v-model="shippingProvider">
              <option value="">None</option>
              <option
                v-for="row in providerOptions"
                :key="row.value"
                :value="row.value"
              >{{ row.label }}</option>
            </select>
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
