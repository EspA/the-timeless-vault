<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import { api } from "../api";
import ChannelLogo from "../components/ChannelLogo.vue";

type NotificationEvent = {
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

type NotificationsPage = {
  items: NotificationEvent[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

const TYPE_OPTIONS = [
  { value: "", label: "All" },
  { value: "BUYING_OPPORTUNITY", label: "BUYING OPPORTUNITY" },
  { value: "PRICE_HIGH", label: "PRICE HIGH" },
  { value: "PRICE_LOW", label: "PRICE LOW" },
  { value: "NEW_SALE", label: "NEW SALE" },
  { value: "ORDER_DELIVERED", label: "ORDER DELIVERED" },
  { value: "PURCHASE_ORDER_DELIVERED", label: "PURCHASE ORDER DELIVERED" },
  { value: "SCAN_FAILED", label: "SCAN FAILED" },
];

const PAGE_SIZE = 10;
const emptyFilters = () => ({
  when: "",
  type: "",
  platform: "",
  alert: "",
  emailed: "",
  read: "",
});

const items = ref<NotificationEvent[]>([]);
const page = ref(0);
const total = ref(0);
const totalPages = ref(1);
const unreadCount = ref(0);
const filters = ref(emptyFilters());
const markingAll = ref(false);
const loading = ref(false);
const error = ref("");
const refreshUnread = inject<() => Promise<void>>("refreshUnread", async () => {});

const typeLabel = (type: string) => (type || "").replaceAll("_", " ");

const typeBadge = (type: string) => {
  if (type === "BUYING_OPPORTUNITY" || type === "NEW_SALE" || type === "ORDER_DELIVERED" || type === "PURCHASE_ORDER_DELIVERED") return "ok";
  if (type === "PRICE_HIGH" || type === "PRICE_LOW") return "price";
  if (type === "SCAN_FAILED") return "bad";
  return "";
};

const whenLabel = (value: string) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  const haystack = value == null ? "" : String(value);
  return haystack.toLowerCase().includes(needle.trim().toLowerCase());
};

const typeOptions = computed(() => {
  const known = new Set(TYPE_OPTIONS.map((option) => option.value).filter(Boolean));
  const extra = [...new Set(items.value.map((alert) => alert.type))]
    .filter((type) => type && !known.has(type))
    .sort()
    .map((type) => ({ value: type, label: typeLabel(type) }));
  return [...TYPE_OPTIONS, ...extra];
});

const visible = computed(() =>
  items.value.filter((alert) =>
    contains(whenLabel(alert.createdAt), filters.value.when)
    && (!filters.value.type || alert.type === filters.value.type)
    && (!filters.value.platform || alert.platform === filters.value.platform)
    && contains(`${alert.title} ${alert.body || ""}`, filters.value.alert)
    && (!filters.value.emailed || String(alert.emailed) === filters.value.emailed)
    && (!filters.value.read || String(alert.read) === filters.value.read)
  )
);

const listingHref = (url?: string) => !!url && url.startsWith("/") && !url.startsWith("//");

const linkLabel = (alert: NotificationEvent) => {
  if (alert.type === "SCAN_FAILED") {
    return alert.url?.includes("/scan-logs") ? "Open scan logs" : "Open market";
  }
  if (alert.type === "NEW_SALE" || alert.type === "ORDER_DELIVERED") {
    return alert.url?.startsWith("/orders/") ? "Open order" : listingHref(alert.url) ? "Open item" : "Open order";
  }
  if (alert.type === "PURCHASE_ORDER_DELIVERED") {
    return "Open purchase order";
  }
  return "Open listing";
};

const filterCount = computed(() => Object.values(filters.value).filter((value) => value.trim()).length);

const rangeLabel = computed(() => {
  if (!total.value) return "0 notifications";
  const start = page.value * PAGE_SIZE + 1;
  const end = Math.min((page.value + 1) * PAGE_SIZE, total.value);
  const pages = totalPages.value > 1 ? ` · page ${page.value + 1} of ${totalPages.value}` : "";
  return `${start}–${end} of ${total.value}${pages}`;
});

const clearFilters = () => {
  filters.value = emptyFilters();
};

const loadUnread = async () => {
  const count = await api.get<{ count: number }>("/api/notifications/unread-count").catch(() => ({ count: 0 }));
  unreadCount.value = count.count;
};

const load = async (pageIndex = page.value) => {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<NotificationsPage>(`/api/notifications?page=${pageIndex}&size=${PAGE_SIZE}`);
    const pages = Math.max(1, result.totalPages);
    const nextPage = Math.min(pageIndex, pages - 1);
    items.value = result.items;
    total.value = result.total;
    totalPages.value = pages;
    page.value = nextPage;
    await loadUnread();
    if (nextPage !== pageIndex && result.total > 0) {
      loading.value = false;
      await load(nextPage);
      return;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load notifications";
  } finally {
    loading.value = false;
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

onMounted(() => {
  void load(0);
});

const read = async (id: string) => {
  await api.post(`/api/notifications/${id}/read`);
  await load(page.value);
  await refreshUnread();
};

const unread = async (id: string) => {
  await api.post(`/api/notifications/${id}/unread`);
  await load(page.value);
  await refreshUnread();
};

const readAll = async () => {
  if (!unreadCount.value || markingAll.value) return;
  markingAll.value = true;
  try {
    await api.post("/api/notifications/read-all");
    await load(page.value);
    await refreshUnread();
  } finally {
    markingAll.value = false;
  }
};
</script>

<template>
  <div class="grid">
    <div class="page-head">
      <div>
        <h1>Notifications</h1>
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
          <input v-model="filters.alert" type="search" placeholder="Notification text" />
        </label>
        <label>Status
          <select v-model="filters.read">
            <option value="">All</option>
            <option value="false">Unread</option>
            <option value="true">Read</option>
          </select>
        </label>
      </div>
      <div class="table-scroll desktop-only">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Platform</th>
              <th>Notification</th>
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
              <th>
                <select v-model="filters.platform" class="column-filter">
                  <option value="">All</option>
                  <option value="EBAY">eBay</option>
                  <option value="BRICKLINK">BrickLink</option>
                  <option value="BRICKOWL">Brick Owl</option>
                  <option value="SHOPIFY">Shopify</option>
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
            <tr v-for="alert in visible" :key="alert.id" class="notification-row" :class="alert.read ? 'is-read' : 'is-unread'">
              <td>{{ whenLabel(alert.createdAt) }}</td>
              <td><span class="badge notify-type" :class="typeBadge(alert.type)">{{ typeLabel(alert.type) }}</span></td>
              <td>
                <ChannelLogo v-if="alert.platform" :platform="alert.platform" :height="16" />
                <span v-else class="muted">—</span>
              </td>
              <td>
                <strong>{{ alert.title }}</strong>
                <div class="muted">{{ alert.body }}</div>
                <router-link v-if="alert.url && listingHref(alert.url)" class="btn secondary compact notify-open" :to="alert.url">{{ linkLabel(alert) }}</router-link>
                <a v-else-if="alert.url" class="btn secondary compact notify-open" :href="alert.url" target="_blank" rel="noopener noreferrer">{{ linkLabel(alert) }}</a>
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
                <button v-else class="btn secondary compact" type="button" @click="unread(alert.id)">Mark unread</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="6" class="muted">
                {{ loading
                  ? "Loading notifications…"
                  : items.length
                    ? "No notifications match those filters."
                    : "No notifications yet." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="alert in visible" :key="alert.id" class="list-card" :class="alert.read ? 'is-read' : 'is-unread'">
          <div class="list-card-row">
            <span class="badge notify-type" :class="typeBadge(alert.type)">{{ typeLabel(alert.type) }}</span>
            <ChannelLogo v-if="alert.platform" :platform="alert.platform" :height="16" />
            <span class="muted">{{ whenLabel(alert.createdAt) }}</span>
          </div>
          <h3>{{ alert.title }}</h3>
          <p v-if="alert.body" class="muted" style="margin:0">{{ alert.body }}</p>
          <div class="list-card-actions">
            <router-link v-if="alert.url && listingHref(alert.url)" class="btn secondary compact" :to="alert.url">{{ linkLabel(alert) }}</router-link>
            <a v-else-if="alert.url" class="btn secondary compact" :href="alert.url" target="_blank" rel="noopener noreferrer">{{ linkLabel(alert) }}</a>
            <button v-if="!alert.read" class="btn secondary compact" type="button" @click="read(alert.id)">Mark read</button>
            <button v-else class="btn secondary compact" type="button" @click="unread(alert.id)">Mark unread</button>
          </div>
        </article>
        <p v-if="!visible.length" class="muted">
          {{ loading ? "Loading notifications…" : items.length ? "No notifications match those filters." : "No notifications yet." }}
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
  </div>
</template>
