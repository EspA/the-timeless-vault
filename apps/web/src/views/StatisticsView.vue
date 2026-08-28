<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api } from "../api";

type DayCounts = {
  day: string;
  ebay: number;
  bricklink: number;
  brickowl: number;
  shopify: number;
  brickeconomy: number;
  total: number;
};

type Snapshot = {
  timeZone: string;
  today: DayCounts;
  recent: DayCounts[];
};

const snapshot = ref<Snapshot | null>(null);
const error = ref("");
const loading = ref(true);

const formatDay = (value: string) => {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });
};

const formatCount = (value?: number) => (value ?? 0).toLocaleString();

const todayLabel = computed(() => (snapshot.value ? formatDay(snapshot.value.today.day) : ""));

onMounted(async () => {
  try {
    snapshot.value = await api.get<Snapshot>("/api/settings/statistics");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load statistics";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="grid">
    <div class="page-head">
      <h1>Statistics</h1>
    </div>
    <p class="muted">
      Days roll over at midnight {{ snapshot?.timeZone || "America/New_York" }}.
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading" class="muted">Loading…</p>
    <template v-else-if="snapshot">
      <div class="grid six stats">
        <div class="card stat">
          <span class="stat-label">eBay</span>
          <span class="stat-value">{{ formatCount(snapshot.today.ebay) }}</span>
          <span class="muted">{{ todayLabel }}</span>
        </div>
        <div class="card stat">
          <span class="stat-label">BrickLink</span>
          <span class="stat-value">{{ formatCount(snapshot.today.bricklink) }}</span>
          <span class="muted">{{ todayLabel }}</span>
        </div>
        <div class="card stat">
          <span class="stat-label">Brick Owl</span>
          <span class="stat-value">{{ formatCount(snapshot.today.brickowl) }}</span>
          <span class="muted">{{ todayLabel }}</span>
        </div>
        <div class="card stat">
          <span class="stat-label">Shopify</span>
          <span class="stat-value">{{ formatCount(snapshot.today.shopify) }}</span>
          <span class="muted">{{ todayLabel }}</span>
        </div>
        <div class="card stat">
          <span class="stat-label">BrickEconomy</span>
          <span class="stat-value">{{ formatCount(snapshot.today.brickeconomy) }}</span>
          <span class="muted">{{ todayLabel }}</span>
        </div>
        <div class="card stat">
          <span class="stat-label">Total</span>
          <span class="stat-value">{{ formatCount(snapshot.today.total) }}</span>
          <span class="muted">{{ todayLabel }}</span>
        </div>
      </div>
      <div class="card">
        <h3>Last 14 days</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Day</th>
                <th>eBay</th>
                <th>BrickLink</th>
                <th>Brick Owl</th>
                <th>Shopify</th>
                <th>BrickEconomy</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in snapshot.recent" :key="row.day">
                <td>{{ formatDay(row.day) }}</td>
                <td>{{ formatCount(row.ebay) }}</td>
                <td>{{ formatCount(row.bricklink) }}</td>
                <td>{{ formatCount(row.brickowl) }}</td>
                <td>{{ formatCount(row.shopify) }}</td>
                <td>{{ formatCount(row.brickeconomy) }}</td>
                <td>{{ formatCount(row.total) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>
