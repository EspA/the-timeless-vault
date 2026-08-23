<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { api, ORDER_STATUSES, type Order, type OrderStatus, type OrdersPage } from "../api";
import ChannelLogo from "../components/ChannelLogo.vue";
import OrderNewModal from "../components/OrderNewModal.vue";
import StockStatusButtons from "../components/StockStatusButtons.vue";
import TrackingNumber from "../components/TrackingNumber.vue";
import { askConfirm } from "../confirm";

const PAGE_SIZE = 20;
const REFRESH_MS = 60_000;
const emptyFilters = () => ({
  when: "",
  platform: "",
  item: "",
  qty: "",
  price: "",
  shipping: "",
  fee: "",
  order: "",
  status: "",
  tracking: "",
  provider: "",
});

const items = ref<Order[]>([]);
const page = ref(0);
const total = ref(0);
const totalPages = ref(1);
const lastSyncedAt = ref<string | undefined>();
const error = ref("");
const loading = ref(false);
const syncing = ref(false);
const adding = ref(false);
const deletingId = ref("");
const savingId = ref("");
const filters = ref(emptyFilters());
let timer: ReturnType<typeof setInterval> | undefined;

const money = (value: number | null | undefined, currency = "USD") => {
  if (value == null || Number.isNaN(Number(value))) return "—";
  const amount = `$${Number(value).toFixed(2)}`;
  return currency && currency !== "USD" ? `${amount} ${currency}` : amount;
};

const whenLabel = (value: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const itemLabel = (row: Order) => {
  const set = [row.setNumber, row.itemTitle].filter(Boolean).join(" ");
  if (row.sku && set) return `${row.sku} · ${set}`;
  return row.sku || set || "—";
};

const orderLabel = (row: Order) => row.externalOrderId || "—";

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const visible = computed(() =>
  items.value.filter((row) =>
    contains(whenLabel(row.soldAt), filters.value.when)
    && (!filters.value.platform || row.platform === filters.value.platform)
    && contains(itemLabel(row), filters.value.item)
    && contains(row.quantity, filters.value.qty)
    && contains(money(row.unitPrice, row.currency), filters.value.price)
    && contains(money(row.shippingCost ?? 0, row.currency), filters.value.shipping)
    && contains(money(row.platformFee ?? 0, row.currency), filters.value.fee)
    && contains(orderLabel(row), filters.value.order)
    && (!filters.value.status || row.status === filters.value.status)
    && contains(row.trackingNumber, filters.value.tracking)
    && contains(row.shippingProvider, filters.value.provider)
  )
);

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const rangeLabel = computed(() => {
  if (!total.value) return "0 orders";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  const pages = totalPages.value > 1 ? ` · page ${page.value + 1} of ${totalPages.value}` : "";
  return `${start}–${end} of ${total.value}${pages}`;
});

const syncedLabel = computed(() => {
  if (!lastSyncedAt.value) return "Not synced yet";
  return `Last synced ${whenLabel(lastSyncedAt.value)}`;
});

const load = async (pageIndex = page.value) => {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<OrdersPage>(`/api/orders?page=${pageIndex}&size=${PAGE_SIZE}`);
    const pages = Math.max(1, result.totalPages);
    const nextPage = Math.min(pageIndex, pages - 1);
    items.value = result.items;
    total.value = result.total;
    totalPages.value = pages;
    page.value = nextPage;
    lastSyncedAt.value = result.lastSyncedAt;
    if (nextPage !== pageIndex && result.total > 0) {
      loading.value = false;
      await load(nextPage);
      return;
    }
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
};

const syncNow = async () => {
  if (syncing.value) return;
  syncing.value = true;
  error.value = "";
  try {
    await api.post("/api/orders/sync");
    await load(0);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    syncing.value = false;
  }
};

const persist = async (row: Order, patch: { status?: OrderStatus; trackingNumber?: string; shippingProvider?: string }) => {
  if (savingId.value) return;
  savingId.value = row.id;
  error.value = "";
  try {
    const updated = await api.put<Order>(`/api/orders/${row.id}`, {
      status: patch.status ?? row.status,
      trackingNumber: patch.trackingNumber ?? row.trackingNumber ?? "",
      shippingProvider: patch.shippingProvider ?? row.shippingProvider ?? "",
    });
    items.value = items.value.map((item) => (item.id === updated.id ? updated : item));
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    savingId.value = "";
  }
};

const onAdded = async () => {
  adding.value = false;
  await load(0);
};

const remove = async (row: Order) => {
  const label = itemLabel(row);
  const confirmed = await askConfirm(
    `Delete ${label} from orders? The inventory item will be kept. Sync will not bring this channel order back.`,
    { title: "Delete order" }
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  error.value = "";
  try {
    await api.del(`/api/orders/${row.id}`);
    await load(page.value);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    deletingId.value = "";
  }
};

const previous = () => {
  if (page.value <= 0 || loading.value) return;
  void load(page.value - 1);
};

const next = () => {
  if (page.value + 1 >= totalPages.value || loading.value) return;
  void load(page.value + 1);
};

const clearFilters = () => {
  filters.value = emptyFilters();
};

onMounted(() => {
  void load(0);
  timer = setInterval(() => {
    if (!document.hidden) void load(page.value);
  }, REFRESH_MS);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="grid">
    <div class="page-head">
      <div class="page-title-row">
        <h1>Orders</h1>
        <p class="muted page-meta">{{ syncedLabel }}</p>
      </div>
      <div class="pager-actions">
        <button class="btn gold" type="button" @click="adding = true">Add order</button>
        <button class="btn" type="button" :disabled="syncing" @click="syncNow">
          {{ syncing ? "Syncing…" : "Sync now" }}
        </button>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="total" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
            Clear filters
          </button>
          <template v-if="totalPages > 1">
            <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
            <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
          </template>
        </div>
      </div>
      <div class="mobile-filters mobile-only">
        <label>Search
          <input v-model="filters.item" type="search" placeholder="Item, SKU, or set" />
        </label>
        <label>Status
          <select v-model="filters.status">
            <option value="">All</option>
            <option v-for="status in ORDER_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
          </select>
        </label>
        <label>Channel
          <select v-model="filters.platform">
            <option value="">All</option>
            <option value="EBAY">eBay</option>
            <option value="BRICKLINK">BrickLink</option>
            <option value="SHOPIFY">Shopify</option>
            <option value="LOCAL">Local</option>
          </select>
        </label>
      </div>
      <div class="table-scroll desktop-only">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Channel Order</th>
              <th>Channel</th>
              <th>Item</th>
              <th>Status</th>
              <th>Qty</th>
              <th>Price</th>
              <th>Shipping</th>
              <th>Fee</th>
              <th>Tracking</th>
              <th>Shipping provider</th>
              <th></th>
            </tr>
            <tr>
              <th><input v-model="filters.when" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.order" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.platform" class="column-filter">
                  <option value="">All</option>
                  <option value="EBAY">eBay</option>
                  <option value="BRICKLINK">BrickLink</option>
                  <option value="SHOPIFY">Shopify</option>
                  <option value="LOCAL">Local</option>
                </select>
              </th>
              <th><input v-model="filters.item" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.status" class="column-filter">
                  <option value="">All</option>
                  <option v-for="status in ORDER_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
                </select>
              </th>
              <th><input v-model="filters.qty" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.price" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.shipping" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.fee" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.tracking" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.provider" class="column-filter" type="search" placeholder="Filter" /></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>{{ whenLabel(row.soldAt) }}</td>
              <td>
                <a v-if="row.orderUrl" :href="row.orderUrl" target="_blank" rel="noopener noreferrer">
                  {{ orderLabel(row) }}
                </a>
                <span v-else>{{ orderLabel(row) }}</span>
              </td>
              <td><ChannelLogo :platform="row.platform" :height="16" /></td>
              <td>
                <router-link :to="`/orders/${row.id}`">{{ itemLabel(row) }}</router-link>
                <span v-if="row.inventoryCreated" class="badge" style="margin-left:0.4rem">Added</span>
              </td>
              <td>
                <StockStatusButtons
                  compact
                  aria-label="Order status"
                  :options="ORDER_STATUSES"
                  :model-value="row.status"
                  :disabled="savingId === row.id"
                  @update:model-value="persist(row, { status: $event as OrderStatus })"
                />
              </td>
              <td>{{ row.quantity }}</td>
              <td>{{ money(row.unitPrice, row.currency) }}</td>
              <td>{{ money(row.shippingCost ?? 0, row.currency) }}</td>
              <td>{{ money(row.platformFee ?? 0, row.currency) }}</td>
              <td>
                <TrackingNumber :tracking="row.trackingNumber" :provider="row.shippingProvider" />
              </td>
              <td>{{ row.shippingProvider || "—" }}</td>
              <td>
                <button
                  class="btn danger compact"
                  type="button"
                  :disabled="deletingId === row.id"
                  @click="remove(row)"
                >Delete</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="12" class="muted">
                {{ loading
                  ? "Loading orders…"
                  : items.length
                    ? "No orders match those filters."
                    : "No orders recorded yet. Orders are pulled from eBay, BrickLink, and Shopify every 5 minutes." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="row in visible" :key="row.id" class="list-card">
          <div class="list-card-row">
            <ChannelLogo :platform="row.platform" :height="16" />
            <span class="muted">{{ whenLabel(row.soldAt) }}</span>
            <span v-if="row.inventoryCreated" class="badge">Added</span>
          </div>
          <h3>
            <router-link :to="`/orders/${row.id}`">{{ itemLabel(row) }}</router-link>
          </h3>
          <div class="list-card-meta muted">
            Qty {{ row.quantity }} · {{ money(row.unitPrice, row.currency) }}
            · Ship {{ money(row.shippingCost ?? 0, row.currency) }}
            · Fee {{ money(row.platformFee ?? 0, row.currency) }}
          </div>
          <p class="muted" style="margin:0">
            Channel order
            <a v-if="row.orderUrl" :href="row.orderUrl" target="_blank" rel="noopener noreferrer">
              {{ orderLabel(row) }}
            </a>
            <span v-else>{{ orderLabel(row) }}</span>
          </p>
          <StockStatusButtons
            compact
            aria-label="Order status"
            :options="ORDER_STATUSES"
            :model-value="row.status"
            :disabled="savingId === row.id"
            @update:model-value="persist(row, { status: $event as OrderStatus })"
          />
          <p class="muted" style="margin:0">
            Tracking
            <TrackingNumber :tracking="row.trackingNumber" :provider="row.shippingProvider" />
            · {{ row.shippingProvider || "—" }}
          </p>
          <div class="list-card-actions">
            <button
              class="btn danger compact"
              type="button"
              :disabled="deletingId === row.id"
              @click="remove(row)"
            >Delete</button>
          </div>
        </article>
        <p v-if="!visible.length" class="muted">
          {{ loading
            ? "Loading orders…"
            : items.length
              ? "No orders match those filters."
              : "No orders recorded yet. Orders are pulled from eBay, BrickLink, and Shopify every 5 minutes." }}
        </p>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
          <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
        </div>
      </div>
    </div>
    <OrderNewModal v-if="adding" @close="adding = false" @saved="onAdded" />
  </div>
</template>
