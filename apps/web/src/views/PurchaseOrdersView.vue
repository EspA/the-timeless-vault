<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { api, PURCHASE_ORDER_STATUSES, SHIPPING_CARRIERS, trackingEntries, type PurchaseOrder, type PurchaseOrderPage } from "../api";
import { askConfirm } from "../confirm";
import StockStatusButtons from "../components/StockStatusButtons.vue";
import TrackingNumbers from "../components/TrackingNumbers.vue";

const router = useRouter();
const PAGE_SIZE = 10;
const items = ref<PurchaseOrder[]>([]);
const page = ref(0);
const total = ref(0);
const totalPages = ref(1);
const error = ref("");
const loading = ref(false);
const syncing = ref(false);
const emptyFilters = () => ({
  number: "",
  supplier: "",
  status: "",
  total: "",
  arrival: "",
  carrier: "",
  tracking: "",
});
const filters = ref(emptyFilters());

const money = (value: number | null | undefined) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const whenLabel = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const dayLabel = (value?: string) => {
  if (!value) return "—";
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return value;
  return new Date(year, month - 1, day).toLocaleDateString();
};

const carrierLabel = (value?: string | null) =>
  SHIPPING_CARRIERS.find((row) => row.value === value)?.label || value || "—";

const busyId = ref("");

const replaceRow = (updated: PurchaseOrder) => {
  items.value = items.value.map((row) => (row.id === updated.id ? updated : row));
};

const canChangeStatus = (row: PurchaseOrder) => row.status === "IN_TRANSIT" || row.status === "DELIVERED";

const setStatus = async (row: PurchaseOrder, next: string) => {
  if (!canChangeStatus(row) || next === row.status || busyId.value) return;
  if (next === "IN_TRANSIT") return;
  if (next === "DELIVERED") {
    const confirmed = await askConfirm(
      "Mark this purchase order as delivered? Inventory stays in transit until you mark it received.",
      { title: "Mark delivered", confirmLabel: "Delivered", variant: "gold" }
    );
    if (!confirmed) return;
    busyId.value = row.id;
    error.value = "";
    try {
      replaceRow(await api.post<PurchaseOrder>(`/api/purchase-orders/${row.id}/deliver`));
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Could not mark purchase order delivered";
    } finally {
      busyId.value = "";
    }
    return;
  }
  if (next === "RECEIVED") {
    const confirmed = await askConfirm(
      "Mark this purchase order as received? Linked SKUs will be set In stock with the line quantities.",
      { title: "Receive purchase order", confirmLabel: "Received", variant: "gold" }
    );
    if (!confirmed) return;
    busyId.value = row.id;
    error.value = "";
    try {
      replaceRow(await api.post<PurchaseOrder>(`/api/purchase-orders/${row.id}/receive`));
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Could not receive purchase order";
    } finally {
      busyId.value = "";
    }
    return;
  }
  if (next === "CANCELLED") {
    const confirmed = await askConfirm(
      "Cancel this purchase order? Linked SKUs and any live listings will be deleted.",
      { title: "Cancel purchase order", confirmLabel: "Cancel order" }
    );
    if (!confirmed) return;
    busyId.value = row.id;
    error.value = "";
    try {
      replaceRow(await api.post<PurchaseOrder>(`/api/purchase-orders/${row.id}/cancel`));
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Could not cancel purchase order";
    } finally {
      busyId.value = "";
    }
  }
};

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  return String(value ?? "").toLowerCase().includes(needle.trim().toLowerCase());
};

const visible = computed(() =>
  items.value.filter((row) =>
    contains(row.number, filters.value.number)
    && contains(row.supplierName, filters.value.supplier)
    && (!filters.value.status || row.status === filters.value.status)
    && contains(money(row.totalValue), filters.value.total)
    && contains(dayLabel(row.expectedArrival), filters.value.arrival)
    && (!filters.value.carrier || trackingEntries(row).some((tracking) => tracking.carrier === filters.value.carrier) || row.carrier === filters.value.carrier)
    && (!filters.value.tracking.trim() || trackingEntries(row).some((tracking) => contains(tracking.trackingNumber, filters.value.tracking)))
  )
);

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const rangeLabel = computed(() => {
  if (!total.value) return "0 purchase orders";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  const pages = totalPages.value > 1 ? ` · page ${page.value + 1} of ${totalPages.value}` : "";
  return `${start}–${end} of ${total.value}${pages}`;
});

const load = async (pageIndex = page.value) => {
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<PurchaseOrderPage>(`/api/purchase-orders?page=${pageIndex}&size=${PAGE_SIZE}`);
    items.value = result.items;
    page.value = result.page;
    total.value = result.total;
    totalPages.value = result.totalPages;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load purchase orders";
  } finally {
    loading.value = false;
  }
};

const syncNow = async () => {
  if (syncing.value) return;
  syncing.value = true;
  error.value = "";
  try {
    await api.post("/api/purchase-orders/sync");
    await load(page.value);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not sync purchase orders";
  } finally {
    syncing.value = false;
  }
};

onMounted(() => {
  void load(0);
});
</script>

<template>
  <div class="grid">
    <div class="page-head">
      <h1>Purchase orders</h1>
      <div class="pager-actions">
        <button class="btn gold" type="button" @click="router.push('/purchase-orders/new')">New purchase order</button>
        <button class="btn" type="button" :disabled="syncing || loading" @click="syncNow">
          {{ syncing ? "Syncing…" : "Sync now" }}
        </button>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="total > 0 || filterCount" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div class="pager-actions">
          <button v-if="filterCount" class="btn secondary compact" type="button" @click="filters = emptyFilters()">
            Clear filters
          </button>
          <template v-if="totalPages > 1">
            <button class="btn secondary compact" type="button" :disabled="page <= 0 || loading" @click="load(page - 1)">Previous</button>
            <button class="btn secondary compact" type="button" :disabled="page + 1 >= totalPages || loading" @click="load(page + 1)">Next</button>
          </template>
        </div>
      </div>
      <div class="table-scroll desktop-only">
        <table class="po-table">
          <thead>
            <tr>
              <th>PO</th>
              <th>Supplier</th>
              <th>Status</th>
              <th>Total</th>
              <th>Expected arrival</th>
              <th>Carrier</th>
              <th>Tracking</th>
              <th>Created</th>
            </tr>
            <tr>
              <th><input v-model="filters.number" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.supplier" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.status" class="column-filter">
                  <option value="">All</option>
                  <option v-for="status in PURCHASE_ORDER_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
                </select>
              </th>
              <th><input v-model="filters.total" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.arrival" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.carrier" class="column-filter">
                  <option value="">All</option>
                  <option v-for="carrier in SHIPPING_CARRIERS" :key="carrier.value" :value="carrier.value">{{ carrier.label }}</option>
                </select>
              </th>
              <th><input v-model="filters.tracking" class="column-filter" type="search" placeholder="Filter" /></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td><router-link :to="`/purchase-orders/${row.id}`">{{ row.number }}</router-link></td>
              <td>{{ row.supplierName }}</td>
              <td>
                <StockStatusButtons
                  compact
                  aria-label="Purchase order status"
                  :options="PURCHASE_ORDER_STATUSES"
                  :model-value="row.status"
                  :disabled="!canChangeStatus(row) || !!busyId"
                  @update:model-value="setStatus(row, $event)"
                />
              </td>
              <td>{{ money(row.totalValue) }}</td>
              <td>{{ dayLabel(row.expectedArrival) }}</td>
              <td>{{ carrierLabel(row.carrier) }}</td>
              <td>
                <TrackingNumbers :row="row" />
              </td>
              <td>{{ whenLabel(row.createdAt) }}</td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="8" class="muted">{{ loading ? "Loading…" : "No purchase orders yet." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="row in visible" :key="row.id" class="list-card">
          <h3><router-link :to="`/purchase-orders/${row.id}`">{{ row.number }}</router-link></h3>
          <p class="muted">{{ row.supplierName }} · {{ money(row.totalValue) }}</p>
          <StockStatusButtons
            compact
            aria-label="Purchase order status"
            :options="PURCHASE_ORDER_STATUSES"
            :model-value="row.status"
            :disabled="!canChangeStatus(row) || !!busyId"
            @update:model-value="setStatus(row, $event)"
          />
          <p class="muted" style="margin:0">Expected arrival {{ dayLabel(row.expectedArrival) }}</p>
          <p class="muted" style="margin:0">Carrier {{ carrierLabel(row.carrier) }}</p>
          <p class="muted" style="margin:0">
            Tracking
            <TrackingNumbers :row="row" />
          </p>
        </article>
        <p v-if="!visible.length" class="muted">{{ loading ? "Loading…" : "No purchase orders yet." }}</p>
      </div>
    </div>
  </div>
</template>
