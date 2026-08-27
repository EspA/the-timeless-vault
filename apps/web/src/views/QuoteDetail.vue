<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  api,
  defaultListingTitle,
  SHIPPING_CARRIERS,
  type Catalog,
  type Quote,
  type QuoteLine,
  type QuoteMarginTone,
  type ShippingCarrier,
  type Supplier,
} from "../api";
import { askConfirm } from "../confirm";
import SupplierModal from "../components/SupplierModal.vue";
import TrackingNumber from "../components/TrackingNumber.vue";

type DraftLine = {
  key: string;
  id?: string;
  setNumber: string;
  title: string;
  cost: string;
  catalogItemId?: string;
  medianMarketPrice?: number | null;
  lookingUp?: boolean;
  lookupError?: string;
};

type MarketSnap = { median?: number; count?: number };

const route = useRoute();
const router = useRouter();
const isNew = computed(() => route.path === "/quotes/new");
const quote = ref<Quote | null>(null);
const description = ref("");
const shipping = ref("0");
let lineSeq = 0;
const emptyLine = (): DraftLine => {
  lineSeq += 1;
  return { key: `new-${lineSeq}`, setNumber: "", title: "", cost: "" };
};
const lines = ref<DraftLine[]>([emptyLine()]);
const error = ref("");
const loading = ref(false);
const saving = ref(false);
const converting = ref(false);
const justSaved = ref(false);
let savedTimer: ReturnType<typeof setTimeout> | undefined;
const convertingOpen = ref(false);
const supplierList = ref<Supplier[]>([]);
const supplierId = ref("");
const carrier = ref<ShippingCarrier | "">("UPS");
const trackingNumber = ref("");
const expectedArrival = ref("");
const addingSupplier = ref(false);

const readOnly = computed(() => quote.value?.status === "CONVERTED");

const money = (value: number | null | undefined) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const percent = (value: number | null | undefined) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `${Number(value)}%`;

const round2 = (value: number) => Math.round(value * 100) / 100;

const costOf = (line: DraftLine) => {
  const n = Number(line.cost);
  return Number.isFinite(n) && n > 0 ? n : 0;
};

const shippingAmount = computed(() => {
  const n = Number(shipping.value);
  return Number.isFinite(n) && n > 0 ? n : 0;
});

const totalCost = computed(() => lines.value.reduce((sum, line) => sum + costOf(line), 0));

const toneFor = (pct: number | null): QuoteMarginTone | null => {
  if (pct == null) return null;
  if (pct < 25) return "low";
  if (pct <= 35) return "mid";
  return "high";
};

const computedRows = computed(() => {
  const ship = shippingAmount.value;
  const costTotal = totalCost.value;
  return lines.value.map((line) => {
    const cost = costOf(line);
    const prorated = costTotal > 0 && cost > 0 ? round2((cost / costTotal) * ship) : 0;
    const landed = round2(cost + prorated);
    const median = line.medianMarketPrice ?? null;
    const marginDollars = median == null ? null : round2(median - landed);
    const marginPercent = marginDollars == null || landed <= 0
      ? null
      : Math.round((marginDollars / landed) * 100);
    return {
      ...line,
      cost,
      proratedShipping: prorated,
      costWithShipping: landed,
      medianMarketPrice: median,
      marginDollars,
      marginPercent,
      marginTone: toneFor(marginPercent),
    };
  });
});

const totals = computed(() => {
  const cost = round2(totalCost.value);
  const ship = round2(shippingAmount.value);
  const landed = round2(cost + ship);
  const rows = computedRows.value;
  const medianTotal = rows.reduce((sum, row) => sum + (row.medianMarketPrice ?? 0), 0);
  const hasMedian = rows.some((row) => row.medianMarketPrice != null);
  const marginTotal = rows.reduce((sum, row) => sum + (row.marginDollars ?? 0), 0);
  const hasMargin = rows.some((row) => row.marginDollars != null);
  const averageMargin = hasMargin && landed > 0 ? Math.round((marginTotal / landed) * 100) : null;
  return {
    cost,
    prorated: round2(rows.reduce((sum, row) => sum + row.proratedShipping, 0)),
    landed,
    median: hasMedian ? round2(medianTotal) : null,
    marginDollars: hasMargin ? round2(marginTotal) : null,
    marginPercent: averageMargin,
    marginTone: toneFor(averageMargin),
  };
});

const canConvert = computed(() =>
  !readOnly.value && computedRows.value.some((row) => row.setNumber.trim() && row.costWithShipping > 0)
);

const combinedMedian = (ebay?: MarketSnap | null, bricklink?: MarketSnap | null) => {
  const parts = [ebay, bricklink].filter((snap): snap is MarketSnap => snap?.median != null);
  if (!parts.length) return null;
  let weighted = 0;
  let count = 0;
  for (const part of parts) {
    const n = part.count ?? 0;
    if (n > 0 && part.median != null) {
      weighted += part.median * n;
      count += n;
    }
  }
  if (count > 0) return round2(weighted / count);
  const sum = parts.reduce((total, part) => total + (part.median ?? 0), 0);
  return round2(sum / parts.length);
};

const loadMedian = async (catalogId: string) => {
  try {
    const dash = await api.get<{ ebay?: MarketSnap; bricklink?: MarketSnap }>(`/api/market/${catalogId}`);
    return combinedMedian(dash.ebay, dash.bricklink);
  } catch {
    return null;
  }
};

const fromServerLine = (line: QuoteLine): DraftLine => {
  lineSeq += 1;
  return {
    key: line.id,
    id: line.id,
    setNumber: line.setNumber,
    title: line.title,
    cost: line.cost == null ? "" : String(line.cost),
    catalogItemId: line.catalogItemId,
    medianMarketPrice: line.medianMarketPrice ?? null,
  };
};

const apply = (loaded: Quote) => {
  quote.value = loaded;
  description.value = loaded.description || "";
  shipping.value = loaded.shippingTotal == null ? "0" : String(loaded.shippingTotal);
  lines.value = loaded.lines.length ? loaded.lines.map(fromServerLine) : [emptyLine()];
};

const loadSuppliers = async () => {
  supplierList.value = (await api.get<Supplier[]>("/api/suppliers")) ?? [];
};

const load = async () => {
  if (isNew.value) {
    quote.value = null;
    description.value = "";
    error.value = "";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    apply(await api.get<Quote>(`/api/quotes/${String(route.params.id)}`));
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load quote";
    quote.value = null;
  } finally {
    loading.value = false;
  }
};

const needsLookup = (line: DraftLine) => !line.catalogItemId;

const lookupLine = async (line: DraftLine) => {
  if (!line.setNumber.trim() || readOnly.value) return;
  line.lookingUp = true;
  line.lookupError = "";
  try {
    const catalog = await api.get<Catalog>(
      `/api/catalog/lookup?setNumber=${encodeURIComponent(line.setNumber.trim())}&refresh=false`
    );
    line.setNumber = catalog.setNumber;
    line.title = defaultListingTitle(catalog);
    line.catalogItemId = catalog.id;
    line.medianMarketPrice = await loadMedian(catalog.id);
  } catch (e) {
    line.lookupError = e instanceof Error ? e.message : "Could not look up set";
  } finally {
    line.lookingUp = false;
  }
};

const addLine = () => {
  lines.value.push(emptyLine());
};

const removeLine = (key: string) => {
  if (lines.value.length <= 1) return;
  lines.value = lines.value.filter((line) => line.key !== key);
};

const payload = () => ({
  shippingTotal: Number(shipping.value) || 0,
  description: description.value.trim() || null,
  lines: lines.value
    .filter((line) => line.setNumber.trim())
    .map((line) => ({
      ...(line.id ? { id: line.id } : {}),
      setNumber: line.setNumber.trim(),
      title: line.title.trim(),
      cost: Number(line.cost) || 0,
    })),
});

const save = async () => {
  if (readOnly.value || saving.value) return null;
  error.value = "";
  justSaved.value = false;
  saving.value = true;
  try {
    const body = payload();
    const saved = isNew.value
      ? await api.post<Quote>("/api/quotes", body)
      : await api.put<Quote>(`/api/quotes/${quote.value!.id}`, body);
    justSaved.value = true;
    clearTimeout(savedTimer);
    savedTimer = setTimeout(() => {
      justSaved.value = false;
    }, 1800);
    if (isNew.value) {
      await router.replace(`/quotes/${saved.id}`);
      apply(saved);
      return saved;
    }
    apply(saved);
    return saved;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not save quote";
    return null;
  } finally {
    saving.value = false;
  }
};

const openConvert = async () => {
  if (!canConvert.value) return;
  error.value = "";
  try {
    await loadSuppliers();
    convertingOpen.value = true;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load suppliers";
  }
};

const convert = async () => {
  if (!canConvert.value || converting.value) return;
  error.value = "";
  converting.value = true;
  try {
    const saved = await save();
    const id = saved?.id || quote.value?.id;
    if (!id) return;
    const updated = await api.post<Quote>(`/api/quotes/${id}/purchase-order`, {
      supplierId: supplierId.value,
      carrier: carrier.value || null,
      trackingNumber: trackingNumber.value.trim() || null,
      expectedArrival: expectedArrival.value || null,
    });
    convertingOpen.value = false;
    apply(updated);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not create purchase order";
  } finally {
    converting.value = false;
  }
};

const removeQuote = async () => {
  if (!quote.value || readOnly.value) return;
  const confirmed = await askConfirm("Delete this quote? This cannot be undone.", {
    title: "Delete quote",
    confirmLabel: "Delete",
    variant: "danger",
  });
  if (!confirmed) return;
  try {
    await api.del(`/api/quotes/${quote.value.id}`);
    await router.push("/quotes");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not delete quote";
  }
};

const onSupplierSaved = (supplier: Supplier) => {
  supplierList.value = [...supplierList.value.filter((row) => row.id !== supplier.id), supplier]
    .sort((a, b) => a.name.localeCompare(b.name));
  supplierId.value = supplier.id;
  addingSupplier.value = false;
};

onMounted(() => {
  void load();
});

onBeforeUnmount(() => {
  clearTimeout(savedTimer);
});
</script>

<template>
  <div v-if="loading" class="muted">Loading…</div>
  <div v-else class="grid">
    <div class="page-head">
      <div>
        <p class="muted"><router-link to="/quotes">Quotes</router-link></p>
        <h1>{{ quote?.number || "New quote" }}</h1>
      </div>
      <div class="pager-actions">
        <router-link
          v-if="quote?.purchaseOrderId"
          class="btn secondary"
          :to="`/purchase-orders/${quote.purchaseOrderId}`"
        >
          {{ quote.purchaseOrderNumber || "Purchase order" }}
        </router-link>
        <button
          v-if="quote && !readOnly"
          class="btn danger compact"
          type="button"
          @click="removeQuote"
        >
          Delete
        </button>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="readOnly" class="muted">This quote was converted to a purchase order and can no longer be edited.</p>

    <label class="quote-description">
      Description
      <textarea
        v-model="description"
        rows="3"
        :disabled="readOnly"
        placeholder="Anything useful about this quote"
      />
    </label>

    <div class="card grid">
      <div class="page-head" style="margin:0">
        <h2 style="margin:0;font-size:1.05rem">Lines</h2>
        <button v-if="!readOnly" class="btn secondary compact" type="button" @click="addLine">Add line</button>
      </div>
      <div class="table-scroll desktop-only">
        <table class="po-table quote-sheet">
          <colgroup>
            <col class="quote-col-set" />
            <col class="quote-col-cost" />
            <col class="quote-col-money" />
            <col class="quote-col-money" />
            <col class="quote-col-money" />
            <col class="quote-col-pct" />
            <col class="quote-col-money" />
            <col class="quote-col-actions" />
          </colgroup>
          <thead>
            <tr>
              <th>Set</th>
              <th class="num">Cost</th>
              <th class="num">Prorated shipping</th>
              <th class="num">Cost with shipping</th>
              <th class="num">Median market price</th>
              <th class="num">Margin %</th>
              <th class="num">Margin $</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, index) in lines" :key="line.key">
              <td>
                <div v-if="needsLookup(line)" class="form-modal-lookup quote-set-lookup">
                  <input
                    v-model="line.setNumber"
                    :disabled="readOnly"
                    placeholder="10236-1"
                  />
                  <button
                    v-if="!readOnly"
                    class="btn gold compact"
                    type="button"
                    :disabled="line.lookingUp"
                    @click="lookupLine(line)"
                  >
                    {{ line.lookingUp ? "…" : "Look up" }}
                  </button>
                </div>
                <p v-else class="quote-set-title">{{ line.title }}</p>
                <p v-if="line.lookupError" class="error">{{ line.lookupError }}</p>
              </td>
              <td>
                <input
                  v-model="line.cost"
                  type="number"
                  min="0"
                  step="0.01"
                  :disabled="readOnly"
                />
              </td>
              <td class="num">{{ money(computedRows[index]?.proratedShipping) }}</td>
              <td class="num">{{ money(computedRows[index]?.costWithShipping) }}</td>
              <td class="num">{{ money(computedRows[index]?.medianMarketPrice) }}</td>
              <td
                class="num"
                :class="computedRows[index]?.marginTone ? `quote-margin ${computedRows[index].marginTone}` : ''"
              >
                {{ percent(computedRows[index]?.marginPercent) }}
              </td>
              <td class="num">{{ money(computedRows[index]?.marginDollars) }}</td>
              <td>
                <button
                  v-if="!readOnly && lines.length > 1"
                  class="btn danger compact"
                  type="button"
                  @click="removeLine(line.key)"
                >
                  Remove
                </button>
              </td>
            </tr>
            <tr class="quote-total">
              <td>Total</td>
              <td class="num">{{ money(totals.cost) }}</td>
              <td class="num">{{ money(totals.prorated) }}</td>
              <td class="num">{{ money(totals.landed) }}</td>
              <td class="num">{{ money(totals.median) }}</td>
              <td class="num" :class="totals.marginTone ? `quote-margin ${totals.marginTone}` : ''">
                {{ percent(totals.marginPercent) }}
              </td>
              <td class="num">{{ money(totals.marginDollars) }}</td>
              <td></td>
            </tr>
            <tr>
              <td>Shipping</td>
              <td>
                <input v-model="shipping" type="number" min="0" step="0.01" :disabled="readOnly" />
              </td>
              <td colspan="6"></td>
            </tr>
            <tr class="quote-total">
              <td>Total with shipping</td>
              <td class="num">{{ money(totals.landed) }}</td>
              <td colspan="6"></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="list-cards mobile-only">
        <article v-for="(line, index) in lines" :key="line.key" class="list-card grid">
          <label v-if="needsLookup(line)">Set
            <div class="form-modal-lookup">
              <input
                v-model="line.setNumber"
                :disabled="readOnly"
                placeholder="10236-1"
              />
              <button
                v-if="!readOnly"
                class="btn gold compact"
                type="button"
                :disabled="line.lookingUp"
                @click="lookupLine(line)"
              >
                {{ line.lookingUp ? "…" : "Look up" }}
              </button>
            </div>
          </label>
          <p v-else class="quote-set-title">{{ line.title }}</p>
          <p v-if="line.lookupError" class="error">{{ line.lookupError }}</p>
          <label>Cost
            <input
              v-model="line.cost"
              type="number"
              min="0"
              step="0.01"
              :disabled="readOnly"
            />
          </label>
          <p>Prorated shipping {{ money(computedRows[index]?.proratedShipping) }}</p>
          <p>Cost with shipping {{ money(computedRows[index]?.costWithShipping) }}</p>
          <p>Median market {{ money(computedRows[index]?.medianMarketPrice) }}</p>
          <p :class="computedRows[index]?.marginTone ? `quote-margin ${computedRows[index].marginTone}` : ''">
            Margin {{ percent(computedRows[index]?.marginPercent) }} · {{ money(computedRows[index]?.marginDollars) }}
          </p>
          <button
            v-if="!readOnly && lines.length > 1"
            class="btn danger compact"
            type="button"
            @click="removeLine(line.key)"
          >
            Remove
          </button>
        </article>
        <label>Shipping
          <input v-model="shipping" type="number" min="0" step="0.01" :disabled="readOnly" />
        </label>
        <p><strong>Total</strong> {{ money(totals.cost) }}</p>
        <p><strong>Total with shipping</strong> {{ money(totals.landed) }}</p>
        <p :class="totals.marginTone ? `quote-margin ${totals.marginTone}` : ''">
          Margin {{ percent(totals.marginPercent) }} · {{ money(totals.marginDollars) }}
        </p>
      </div>

      <div v-if="!readOnly" class="save-row">
        <button class="btn secondary" :class="{ saved: justSaved }" type="button" :disabled="saving" @click="save()">
          {{ saving ? "Saving…" : justSaved ? "Saved" : isNew ? "Save quote" : "Save changes" }}
        </button>
        <button class="btn gold" type="button" :disabled="!canConvert || saving || converting" @click="openConvert">
          Create purchase order
        </button>
        <span v-if="justSaved" class="save-note">Changes saved</span>
      </div>
    </div>

    <Teleport v-if="convertingOpen" to="body">
      <div class="modal-backdrop">
        <form class="modal form-modal card" role="dialog" aria-modal="true" @submit.prevent="convert">
          <div class="form-modal-head">
            <h2>Create purchase order</h2>
            <button class="btn secondary compact" type="button" :disabled="converting" @click="convertingOpen = false">
              Cancel
            </button>
          </div>
          <div class="form-modal-body grid">
            <p class="muted">This uses each line’s cost with shipping as the inventory cost and creates In-transit SKUs.</p>
            <label>Supplier
              <div class="form-modal-lookup">
                <select v-model="supplierId" required>
                  <option value="" disabled>Select supplier</option>
                  <option v-for="row in supplierList" :key="row.id" :value="row.id">{{ row.name }}</option>
                </select>
                <button class="btn secondary compact" type="button" @click="addingSupplier = true">New</button>
              </div>
            </label>
            <div class="grid two">
              <label>Carrier
                <select v-model="carrier">
                  <option value="">—</option>
                  <option v-for="row in SHIPPING_CARRIERS" :key="row.value" :value="row.value">{{ row.label }}</option>
                </select>
              </label>
              <label>Expected arrival
                <input v-model="expectedArrival" type="date" />
              </label>
            </div>
            <label>Tracking
              <input v-model="trackingNumber" placeholder="Tracking number" />
              <TrackingNumber v-if="trackingNumber" :tracking="trackingNumber" :provider="carrier" />
            </label>
          </div>
          <div class="form-modal-foot">
            <button class="btn gold" type="submit" :disabled="converting || !supplierId">
              {{ converting ? "Creating…" : "Create purchase order" }}
            </button>
          </div>
        </form>
      </div>
    </Teleport>
    <SupplierModal v-if="addingSupplier" @close="addingSupplier = false" @saved="onSupplierSaved" />
  </div>
</template>
