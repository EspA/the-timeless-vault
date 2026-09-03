<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  api,
  defaultListingTitle,
  LISTING_TITLE_MAX,
  PURCHASE_ORDER_STATUSES,
  SHIPPING_CARRIERS,
  trackingEntries,
  type Catalog,
  type PurchaseOrder,
  type PurchaseOrderLine,
  type ShippingCarrier,
  type Supplier,
} from "../api";
import { askConfirm } from "../confirm";
import StockStatusButtons from "../components/StockStatusButtons.vue";
import SupplierModal from "../components/SupplierModal.vue";
import TrackingEntries, { type TrackingDraft } from "../components/TrackingEntries.vue";

type DraftLine = {
  key: string;
  id?: string;
  setNumber: string;
  title: string;
  quantity: number;
  unitValue: string;
  inventoryItemId?: string;
  sku?: string;
  lookingUp?: boolean;
  lookupError?: string;
};

const route = useRoute();
const router = useRouter();
const isNew = computed(() => route.path === "/purchase-orders/new");
const order = ref<PurchaseOrder | null>(null);
const supplierList = ref<Supplier[]>([]);
const supplierId = ref("");
const expectedArrival = ref("");
const trackings = ref<TrackingDraft[]>([{ trackingNumber: "", carrier: "" }]);
const note = ref("");
let lineSeq = 0;
const emptyLine = (): DraftLine => {
  lineSeq += 1;
  return {
    key: `new-${lineSeq}`,
    setNumber: "",
    title: "",
    quantity: 1,
    unitValue: "",
  };
};
const lines = ref<DraftLine[]>([emptyLine()]);
const addingSupplier = ref(false);
const error = ref("");
const loading = ref(false);
const saving = ref(false);
const actionBusy = ref(false);
const justSaved = ref(false);
let savedTimer: ReturnType<typeof setTimeout> | undefined;

const readOnly = computed(() => !!order.value && order.value.status !== "IN_TRANSIT" && order.value.status !== "DELIVERED");
const statusLocked = computed(() => isNew.value || readOnly.value || actionBusy.value || saving.value);

const money = (value: number | null | undefined) =>
  value == null || Number.isNaN(Number(value)) ? "—" : `$${Number(value).toFixed(2)}`;

const whenLabel = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const totalValue = computed(() =>
  lines.value.reduce((sum, line) => {
    const qty = Number(line.quantity) || 0;
    const unit = Number(line.unitValue) || 0;
    return sum + qty * unit;
  }, 0)
);

const fromServerLine = (line: PurchaseOrderLine): DraftLine => {
  lineSeq += 1;
  return {
    key: line.id,
    id: line.id,
    setNumber: line.setNumber,
    title: line.title,
    quantity: line.quantity,
    unitValue: line.unitValue == null ? "" : String(line.unitValue),
    inventoryItemId: line.inventoryItemId,
    sku: line.sku,
  };
};

const apply = (loaded: PurchaseOrder) => {
  order.value = loaded;
  supplierId.value = loaded.supplierId;
  expectedArrival.value = loaded.expectedArrival || "";
  const entries = trackingEntries(loaded);
  trackings.value = entries.length ? entries : [{ trackingNumber: "", carrier: "" }];
  note.value = loaded.note || "";
  lines.value = loaded.lines.length ? loaded.lines.map(fromServerLine) : [emptyLine()];
};

const loadSuppliers = async () => {
  supplierList.value = (await api.get<Supplier[]>("/api/suppliers")) ?? [];
};

const load = async () => {
  if (isNew.value) {
    order.value = null;
    error.value = "";
    try {
      await loadSuppliers();
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Could not load suppliers";
    }
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    await loadSuppliers();
    apply(await api.get<PurchaseOrder>(`/api/purchase-orders/${String(route.params.id)}`));
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load purchase order";
    order.value = null;
  } finally {
    loading.value = false;
  }
};

const lookupLine = async (line: DraftLine, refresh = false) => {
  if (!line.setNumber.trim() || readOnly.value) return;
  line.lookingUp = true;
  line.lookupError = "";
  try {
    const catalog = await api.get<Catalog>(
      `/api/catalog/lookup?setNumber=${encodeURIComponent(line.setNumber.trim())}&refresh=${refresh}`
    );
    line.setNumber = catalog.setNumber;
    line.title = defaultListingTitle(catalog);
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
  supplierId: supplierId.value,
  expectedArrival: expectedArrival.value || null,
  trackingNumber: trackings.value.find((row) => row.trackingNumber.trim())?.trackingNumber.trim() || null,
  carrier: (trackings.value.find((row) => row.trackingNumber.trim())?.carrier || null) as ShippingCarrier | null,
  trackings: trackings.value
    .filter((row) => row.trackingNumber.trim())
    .map((row) => ({
      trackingNumber: row.trackingNumber.trim(),
      carrier: (row.carrier || null) as ShippingCarrier | null,
    })),
  note: note.value.trim() || null,
  lines: lines.value.map((line) => ({
    ...(line.id ? { id: line.id } : {}),
    setNumber: line.setNumber.trim(),
    title: line.title.trim(),
    quantity: Number(line.quantity) || 1,
    unitValue: Number(line.unitValue),
  })),
});

const save = async () => {
  if (readOnly.value || saving.value) return;
  error.value = "";
  justSaved.value = false;
  saving.value = true;
  try {
    const body = payload();
    const saved = isNew.value
      ? await api.post<PurchaseOrder>("/api/purchase-orders", body)
      : await api.put<PurchaseOrder>(`/api/purchase-orders/${order.value!.id}`, body);
    justSaved.value = true;
    clearTimeout(savedTimer);
    savedTimer = setTimeout(() => {
      justSaved.value = false;
    }, 1800);
    if (isNew.value) {
      await router.replace(`/purchase-orders/${saved.id}`);
      return;
    }
    apply(saved);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not save purchase order";
  } finally {
    saving.value = false;
  }
};

const markDelivered = async () => {
  if (!order.value || readOnly.value) return;
  const confirmed = await askConfirm(
    "Mark this purchase order as delivered? Inventory stays in transit until you mark it received.",
    { title: "Mark delivered", confirmLabel: "Delivered", variant: "gold" }
  );
  if (!confirmed) return;
  actionBusy.value = true;
  error.value = "";
  try {
    apply(await api.post<PurchaseOrder>(`/api/purchase-orders/${order.value.id}/deliver`));
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not mark purchase order delivered";
  } finally {
    actionBusy.value = false;
  }
};

const receive = async () => {
  if (!order.value || readOnly.value) return;
  const confirmed = await askConfirm(
    "Mark this purchase order as received? Linked SKUs will be set In stock with the line quantities.",
    { title: "Receive purchase order", confirmLabel: "Received", variant: "gold" }
  );
  if (!confirmed) return;
  actionBusy.value = true;
  error.value = "";
  try {
    apply(await api.post<PurchaseOrder>(`/api/purchase-orders/${order.value.id}/receive`));
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not receive purchase order";
  } finally {
    actionBusy.value = false;
  }
};

const cancelOrder = async () => {
  if (!order.value || readOnly.value) return;
  const confirmed = await askConfirm(
    "Cancel this purchase order? Linked SKUs and any live listings will be deleted.",
    { title: "Cancel purchase order", confirmLabel: "Cancel order" }
  );
  if (!confirmed) return;
  actionBusy.value = true;
  error.value = "";
  try {
    apply(await api.post<PurchaseOrder>(`/api/purchase-orders/${order.value.id}/cancel`));
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not cancel purchase order";
  } finally {
    actionBusy.value = false;
  }
};

const setStatus = async (next: string) => {
  if (next === "DELIVERED") await markDelivered();
  if (next === "RECEIVED") await receive();
  if (next === "CANCELLED") await cancelOrder();
};

const onSupplierSaved = async (supplier: Supplier) => {
  addingSupplier.value = false;
  await loadSuppliers();
  supplierId.value = supplier.id;
};

onMounted(() => {
  void load();
});

onBeforeUnmount(() => clearTimeout(savedTimer));
</script>

<template>
  <p v-if="error && !isNew && !order && !loading" class="error">{{ error }}</p>
  <p v-else-if="loading && !order" class="muted">Loading purchase order…</p>
  <div v-else class="grid">
    <div class="page-head">
      <div>
        <p class="muted">
          <router-link to="/purchase-orders">Purchase orders</router-link>
        </p>
        <h1>{{ isNew ? "New purchase order" : order?.number || "Purchase order" }}</h1>
        <p v-if="order" class="muted">Updated {{ whenLabel(order.updatedAt) }}</p>
      </div>
      <StockStatusButtons
        compact
        aria-label="Purchase order status"
        :options="PURCHASE_ORDER_STATUSES"
        :model-value="order?.status || 'IN_TRANSIT'"
        :disabled="statusLocked"
        @update:model-value="setStatus"
      />
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="grid save-fields" :class="{ flash: justSaved }">
      <div class="card grid">
        <div class="grid po-header-fields">
          <label>Supplier
            <div class="po-supplier-field">
              <select v-model="supplierId" required :disabled="readOnly">
                <option value="" disabled>Select a supplier</option>
                <option v-for="row in supplierList" :key="row.id" :value="row.id">{{ row.name }}</option>
              </select>
              <button
                v-if="!readOnly"
                class="btn secondary compact"
                type="button"
                @click="addingSupplier = true"
              >
                {{ supplierList.length ? "Add" : "Create" }}
              </button>
            </div>
          </label>
          <label>Expected arrival
            <input v-model="expectedArrival" type="date" :disabled="readOnly" />
          </label>
          <label class="po-tracking-field">Tracking
            <TrackingEntries
              v-model="trackings"
              :carriers="SHIPPING_CARRIERS"
              :disabled="readOnly"
            />
          </label>
        </div>
        <details class="po-note">
          <summary>
            Note
            <span v-if="note.trim()" class="muted">{{ note.trim() }}</span>
          </summary>
          <textarea v-model="note" rows="3" :disabled="readOnly"></textarea>
        </details>
      </div>

      <div class="card grid">
        <div class="page-head" style="margin:0">
          <h2 style="margin:0;font-size:1.05rem">Lines</h2>
          <button v-if="!readOnly" class="btn secondary compact" type="button" @click="addLine">Add line</button>
        </div>
        <p class="muted" style="margin:0">Total {{ money(totalValue) }}</p>
        <div class="table-scroll desktop-only">
          <table class="po-lines">
            <colgroup>
              <col class="po-col-set" />
              <col class="po-col-title" />
              <col class="po-col-qty" />
              <col class="po-col-unit" />
              <col class="po-col-sku" />
              <col class="po-col-actions" />
            </colgroup>
            <thead>
              <tr>
                <th>Set</th>
                <th>Title</th>
                <th>Qty</th>
                <th>Unit value</th>
                <th>SKU</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in lines" :key="line.key">
                <td>
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
                      @click="lookupLine(line, false)"
                    >
                      {{ line.lookingUp ? "…" : "Look up" }}
                    </button>
                  </div>
                  <p v-if="line.lookupError" class="error">{{ line.lookupError }}</p>
                </td>
                <td>
                  <input v-model="line.title" :disabled="readOnly" :maxlength="LISTING_TITLE_MAX" />
                </td>
                <td>
                  <input v-model.number="line.quantity" type="number" min="1" step="1" :disabled="readOnly" />
                </td>
                <td>
                  <input v-model="line.unitValue" type="number" min="0.01" step="0.01" :disabled="readOnly" required />
                </td>
                <td>
                  <router-link v-if="line.inventoryItemId && line.sku" :to="`/inventory/${line.inventoryItemId}`">
                    {{ line.sku }}
                  </router-link>
                  <span v-else class="muted">{{ line.sku || "—" }}</span>
                </td>
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
            </tbody>
          </table>
        </div>
        <div class="list-cards mobile-only">
          <article v-for="line in lines" :key="line.key" class="list-card grid">
            <label>Set
              <div class="form-modal-lookup">
                <input v-model="line.setNumber" :disabled="readOnly" placeholder="10236-1" />
                <button
                  v-if="!readOnly"
                  class="btn gold compact"
                  type="button"
                  :disabled="line.lookingUp"
                  @click="lookupLine(line, false)"
                >
                  {{ line.lookingUp ? "…" : "Look up" }}
                </button>
              </div>
            </label>
            <p v-if="line.lookupError" class="error">{{ line.lookupError }}</p>
            <label>Title
              <input v-model="line.title" :disabled="readOnly" :maxlength="LISTING_TITLE_MAX" />
            </label>
            <div class="grid two">
              <label>Qty
                <input v-model.number="line.quantity" type="number" min="1" step="1" :disabled="readOnly" />
              </label>
              <label>Unit value
                <input v-model="line.unitValue" type="number" min="0.01" step="0.01" :disabled="readOnly" />
              </label>
            </div>
            <p v-if="line.sku" class="muted">
              SKU
              <router-link v-if="line.inventoryItemId" :to="`/inventory/${line.inventoryItemId}`">{{ line.sku }}</router-link>
              <span v-else>{{ line.sku }}</span>
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
        </div>
        <div v-if="!readOnly" class="save-row">
          <button class="btn secondary" :class="{ saved: justSaved }" type="button" :disabled="saving" @click="save">
            {{ saving ? "Saving…" : justSaved ? "Saved" : isNew ? "Create purchase order" : "Save changes" }}
          </button>
          <span v-if="justSaved" class="save-note">Changes saved</span>
        </div>
      </div>
    </div>

    <SupplierModal v-if="addingSupplier" @close="addingSupplier = false" @saved="onSupplierSaved" />
  </div>
</template>
