<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import { api } from "../api";

type Alert = {
  id: string;
  type: string;
  platform?: string;
  title: string;
  body?: string;
  url?: string;
  createdAt: string;
  read: boolean;
};

const alerts = ref<Alert[]>([]);
const markingAll = ref(false);
const refreshUnread = inject<() => Promise<void>>("refreshUnread", async () => {});
const unreadCount = computed(() => alerts.value.filter((alert) => !alert.read).length);

const load = async () => {
  alerts.value = await api.get<Alert[]>("/api/alerts");
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
        <h1>Buying Opportunities</h1>
        <p class="muted">New marketplace listings and price-guard warnings. Price changes stay manual.</p>
      </div>
      <button
        class="btn secondary"
        type="button"
        :disabled="!unreadCount || markingAll"
        @click="readAll"
      >
        {{ markingAll ? "Marking…" : "Mark all read" }}
      </button>
    </div>
    <div class="card">
      <table>
        <thead><tr><th>When</th><th>Type</th><th>Alert</th><th></th></tr></thead>
        <tbody>
          <tr v-for="alert in alerts" :key="alert.id" :style="{ opacity: alert.read ? 0.55 : 1 }">
            <td>{{ new Date(alert.createdAt).toLocaleString() }}</td>
            <td><span class="badge">{{ alert.type }}</span></td>
            <td>
              <strong>{{ alert.title }}</strong>
              <div class="muted">{{ alert.body }}</div>
              <a v-if="alert.url" :href="alert.url" target="_blank">Open listing</a>
            </td>
            <td><button v-if="!alert.read" class="btn secondary" type="button" @click="read(alert.id)">Mark read</button></td>
          </tr>
          <tr v-if="!alerts.length"><td colspan="4" class="muted">No alerts yet.</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
