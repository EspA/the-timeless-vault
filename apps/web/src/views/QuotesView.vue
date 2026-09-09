<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { api, QUOTE_STATUSES, type Quote, type QuoteMarginTone, type QuotePage } from "../api";
import { askConfirm } from "../confirm";

const router = useRouter();
const PAGE_SIZE = 10;
const items = ref<Quote[]>([]);
const page = ref(0);
const total = ref(0);
const totalPages = ref(1);
const error = ref("");
const loading = ref(false);
const deletingId = ref("");
const emptyFilters = () => ({
  number: "",
  lines: "",
  cost: "",
  shipping: "",
  landed: "",
  margin: "",
  status: "",
});
const filters = ref(emptyFilters());

const money = (value: number | null | undefined) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const percent = (value: number | null | undefined) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `${Number(value)}%`;

const whenLabel = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const statusLabel = (status: string) =>
  QUOTE_STATUSES.find((row) => row.value === status)?.label || status;

const toneClass = (tone?: QuoteMarginTone | null) => (tone ? `quote-margin ${tone}` : "");

const filterCount = computed(() => Object.values(filters.value).filter(Boolean).length);

const visible = computed(() => {
  const f = filters.value;
  return items.value.filter((row) => {
    if (f.number && !row.number.toLowerCase().includes(f.number.toLowerCase())) return false;
    if (f.lines && !String(row.lineCount).includes(f.lines)) return false;
    if (f.cost && !money(row.totalCost).includes(f.cost)) return false;
    if (f.shipping && !money(row.shippingTotal).includes(f.shipping)) return false;
    if (f.landed && !money(row.totalCostWithShipping).includes(f.landed)) return false;
    if (f.margin && !percent(row.averageMarginPercent).toLowerCase().includes(f.margin.toLowerCase())) return false;
    if (f.status && row.status !== f.status) return false;
    return true;
  });
});

const rangeLabel = computed(() => {
  if (!total.value) return "No quotes";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min(total.value, start + items.value.length - 1);
  return `${start}–${end} of ${total.value}`;
});

const load = async (pageIndex = page.value) => {
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<QuotePage>(`/api/quotes?page=${pageIndex}&size=${PAGE_SIZE}`);
    items.value = result.items;
    page.value = result.page;
    total.value = result.total;
    totalPages.value = result.totalPages;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load quotes";
  } finally {
    loading.value = false;
  }
};

const canDelete = (row: Quote) => row.status !== "CONVERTED" && !row.purchaseOrderId;

const remove = async (row: Quote) => {
  if (!canDelete(row) || deletingId.value) return;
  const confirmed = await askConfirm(`Delete ${row.number}? This cannot be undone.`, {
    title: "Delete quote",
    confirmLabel: "Delete",
    variant: "danger",
  });
  if (!confirmed) return;
  deletingId.value = row.id;
  error.value = "";
  try {
    await api.del(`/api/quotes/${row.id}`);
    await load(page.value);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not delete quote";
  } finally {
    deletingId.value = "";
  }
};

onMounted(() => {
  void load(0);
});
</script>

<template>
  <div class="grid">
    <div class="page-head">
      <h1>Quotes</h1>
      <button class="btn gold" type="button" @click="router.push('/quotes/new')">New quote</button>
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
              <th>Quote</th>
              <th>Lines</th>
              <th>Cost</th>
              <th>Shipping</th>
              <th>Total with shipping</th>
              <th>Margin %</th>
              <th>Status</th>
              <th>Updated</th>
              <th></th>
            </tr>
            <tr>
              <th><input v-model="filters.number" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.lines" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.cost" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.shipping" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.landed" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.margin" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.status" class="column-filter">
                  <option value="">All</option>
                  <option v-for="status in QUOTE_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
                </select>
              </th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td><router-link :to="`/quotes/${row.id}`">{{ row.number }}</router-link></td>
              <td>{{ row.lineCount }}</td>
              <td>{{ money(row.totalCost) }}</td>
              <td>{{ money(row.shippingTotal) }}</td>
              <td>{{ money(row.totalCostWithShipping) }}</td>
              <td :class="toneClass(row.averageMarginTone)">{{ percent(row.averageMarginPercent) }}</td>
              <td>{{ statusLabel(row.status) }}</td>
              <td>{{ whenLabel(row.updatedAt) }}</td>
              <td>
                <button
                  v-if="canDelete(row)"
                  class="btn danger compact"
                  type="button"
                  :disabled="deletingId === row.id"
                  @click="remove(row)"
                >Delete</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="9" class="muted">{{ loading ? "Loading…" : "No quotes yet." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="row in visible" :key="row.id" class="list-card">
          <h3><router-link :to="`/quotes/${row.id}`">{{ row.number }}</router-link></h3>
          <p class="muted">{{ row.lineCount }} line{{ row.lineCount === 1 ? "" : "s" }} · {{ statusLabel(row.status) }}</p>
          <p>{{ money(row.totalCostWithShipping) }} with shipping</p>
          <p :class="toneClass(row.averageMarginTone)">Margin {{ percent(row.averageMarginPercent) }}</p>
          <div v-if="canDelete(row)" class="list-card-actions">
            <button
              class="btn danger compact"
              type="button"
              :disabled="deletingId === row.id"
              @click="remove(row)"
            >Delete</button>
          </div>
        </article>
        <p v-if="!visible.length" class="muted">{{ loading ? "Loading…" : "No quotes yet." }}</p>
      </div>
    </div>
  </div>
</template>
