<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api } from "../api";

type ScanLog = {
  id: string;
  catalogId?: string;
  setNumber: string;
  setName?: string;
  platform: string;
  trigger?: "AUTOMATIC" | "MANUAL" | null;
  status: "SUCCESS" | "FAILED";
  listingCount?: number;
  message?: string;
  scannedAt: string;
};

type ScanPage = {
  items: ScanLog[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

const PAGE_SIZE = 20;
const items = ref<ScanLog[]>([]);
const page = ref(0);
const total = ref(0);
const totalPages = ref(0);
const error = ref("");
const loading = ref(false);

const platformLabel = (platform: string) => {
  if (platform === "EBAY") return "eBay";
  if (platform === "BRICKLINK") return "BrickLink";
  return platform;
};

const triggerLabel = (trigger?: string | null) => {
  if (trigger === "AUTOMATIC") return "Automatic";
  if (trigger === "MANUAL") return "Manual";
  return "—";
};

const rangeLabel = computed(() => {
  if (!total.value) return "0 scans";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  return `${start}–${end} of ${total.value}`;
});

const load = async () => {
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<ScanPage>(`/api/market/scans?page=${page.value}&size=${PAGE_SIZE}`);
    items.value = result.items;
    page.value = result.page;
    total.value = result.total;
    totalPages.value = result.totalPages;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
};

const previous = async () => {
  if (page.value <= 0) return;
  page.value -= 1;
  await load();
};

const next = async () => {
  if (page.value + 1 >= totalPages.value) return;
  page.value += 1;
  await load();
};

onMounted(load);
</script>

<template>
  <div class="grid">
    <div>
      <h1>Alerts log</h1>
      <p class="muted">Automatic scheduler scans and manual market scans, newest first.</p>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="total" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ rangeLabel }}</span>
        <div v-if="totalPages > 1" style="display:flex;gap:0.5rem">
          <button class="btn secondary" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
          <button class="btn secondary" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
        </div>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Platform</th>
              <th>Set</th>
              <th>Trigger</th>
              <th>Status</th>
              <th>Listings</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.id">
              <td>{{ new Date(row.scannedAt).toLocaleString() }}</td>
              <td><span class="badge">{{ platformLabel(row.platform) }}</span></td>
              <td>
                <router-link v-if="row.catalogId" :to="`/market/${row.catalogId}`">
                  {{ row.setNumber }} {{ row.setName }}
                </router-link>
                <span v-else>{{ row.setNumber }} {{ row.setName }}</span>
              </td>
              <td>{{ triggerLabel(row.trigger) }}</td>
              <td>
                <span class="badge" :class="{ ok: row.status === 'SUCCESS', bad: row.status === 'FAILED' }">
                  {{ row.status === "SUCCESS" ? "Success" : "Failed" }}
                </span>
              </td>
              <td>{{ row.listingCount ?? "—" }}</td>
              <td class="muted">{{ row.message || "—" }}</td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="7" class="muted">{{ loading ? "Loading scans…" : "No scans recorded yet." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <span class="muted">{{ rangeLabel }}</span>
        <div style="display:flex;gap:0.5rem">
          <button class="btn secondary" type="button" :disabled="page <= 0 || loading" @click="previous">Previous</button>
          <button class="btn secondary" type="button" :disabled="page + 1 >= totalPages || loading" @click="next">Next</button>
        </div>
      </div>
    </div>
  </div>
</template>
