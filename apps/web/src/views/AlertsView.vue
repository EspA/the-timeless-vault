<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import { api } from "../api";

type AlertEvent = {
  id: string;
  type: string;
  platform?: string;
  title: string;
  body?: string;
  url?: string;
  createdAt: string;
  read: boolean;
  emailed: boolean;
};

const TYPE_OPTIONS = [
  { value: "", label: "All" },
  { value: "BUYING_OPPORTUNITY", label: "BUYING OPPORTUNITY" },
  { value: "PRICE_HIGH", label: "PRICE HIGH" },
  { value: "PRICE_LOW", label: "PRICE LOW" },
];

const emptyFilters = () => ({
  when: "",
  type: "",
  alert: "",
  emailed: "",
  read: "",
});

const alerts = ref<AlertEvent[]>([]);
const filters = ref(emptyFilters());
const markingAll = ref(false);
const refreshUnread = inject<() => Promise<void>>("refreshUnread", async () => {});
const unreadCount = computed(() => alerts.value.filter((alert) => !alert.read).length);

const typeLabel = (type: string) => (type || "").replaceAll("_", " ");

const typeBadge = (type: string) => {
  if (type === "BUYING_OPPORTUNITY") return "ok";
  if (type === "PRICE_HIGH" || type === "PRICE_LOW") return "price";
  return "";
};

const whenLabel = (value: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const error = ref("");

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const typeOptions = computed(() => {
  const known = new Set(TYPE_OPTIONS.map((option) => option.value).filter(Boolean));
  const extra = [...new Set(alerts.value.map((alert) => alert.type))]
    .filter((type) => type && !known.has(type))
    .sort()
    .map((type) => ({ value: type, label: typeLabel(type) }));
  return [...TYPE_OPTIONS, ...extra];
});

const filtered = computed(() =>
  alerts.value.filter((alert) =>
    contains(whenLabel(alert.createdAt), filters.value.when)
    && (!filters.value.type || alert.type === filters.value.type)
    && contains(`${alert.title} ${alert.body || ""}`, filters.value.alert)
    && (!filters.value.emailed || String(alert.emailed) === filters.value.emailed)
    && (!filters.value.read || String(alert.read) === filters.value.read)
  )
);

const listingHref = (url?: string) => !!url && url.startsWith("/") && !url.startsWith("//");

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const clearFilters = () => {
  filters.value = emptyFilters();
};

const load = async () => {
  error.value = "";
  try {
    alerts.value = await api.get<AlertEvent[]>("/api/alerts");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load alerts";
  }
};

onMounted(load);

const read = async (id: string) => {
  await api.post(`/api/alerts/${id}/read`);
  await load();
  await refreshUnread();
};

const readAll = async () => {
  if (!unreadCount.value || markingAll.value) return;
  markingAll.value = true;
  try {
    await api.post("/api/alerts/read-all");
    await load();
    await refreshUnread();
  } finally {
    markingAll.value = false;
  }
};
</script>

<template>
  <div class="grid">
    <div style="display:flex;justify-content:space-between;align-items:end;gap:1rem;flex-wrap:wrap">
      <div>
        <h1>Alerts</h1>
      </div>
      <button
        class="btn secondary compact"
        type="button"
        :disabled="!unreadCount || markingAll"
        @click="readAll"
      >
        {{ markingAll ? "Marking…" : "Mark all read" }}
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <div v-if="alerts.length" class="pager" style="margin:0 0 0.85rem">
        <span class="muted">{{ filtered.length }} of {{ alerts.length }} alerts</span>
        <button v-if="filterCount" class="btn secondary compact" type="button" @click="clearFilters">
          Clear filters
        </button>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Alert</th>
              <th>Email</th>
              <th>Status</th>
            </tr>
            <tr>
              <th><input v-model="filters.when" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.type" class="column-filter">
                  <option v-for="option in typeOptions" :key="option.value || 'all'" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </th>
              <th><input v-model="filters.alert" class="column-filter" type="search" placeholder="Filter" /></th>
              <th>
                <select v-model="filters.emailed" class="column-filter">
                  <option value="">All</option>
                  <option value="true">Sent</option>
                  <option value="false">Not sent</option>
                </select>
              </th>
              <th>
                <select v-model="filters.read" class="column-filter">
                  <option value="">All</option>
                  <option value="false">Unread</option>
                  <option value="true">Read</option>
                </select>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="alert in filtered" :key="alert.id" :style="{ opacity: alert.read ? 0.55 : 1 }">
              <td>{{ whenLabel(alert.createdAt) }}</td>
              <td><span class="badge" :class="typeBadge(alert.type)">{{ typeLabel(alert.type) }}</span></td>
              <td>
                <strong>{{ alert.title }}</strong>
                <div class="muted">{{ alert.body }}</div>
                <router-link v-if="alert.url && listingHref(alert.url)" :to="alert.url">Open listing</router-link>
                <a v-else-if="alert.url" :href="alert.url" target="_blank" rel="noopener noreferrer">Open listing</a>
              </td>
              <td>
                <input
                  type="checkbox"
                  :checked="alert.emailed"
                  disabled
                  :title="alert.emailed ? 'Email sent' : 'Email not sent'"
                  :aria-label="alert.emailed ? 'Email sent' : 'Email not sent'"
                />
              </td>
              <td>
                <button v-if="!alert.read" class="btn secondary compact" type="button" @click="read(alert.id)">Mark read</button>
                <span v-else class="muted">Read</span>
              </td>
            </tr>
            <tr v-if="!filtered.length">
              <td colspan="5" class="muted">{{ alerts.length ? "No alerts match those filters." : "No alerts yet." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
