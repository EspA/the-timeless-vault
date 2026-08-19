<script setup lang="ts">
import { computed, onMounted, provide, ref, watch } from "vue";
import { useRoute } from "vue-router";
import ConfirmModal from "./components/ConfirmModal.vue";
import { api } from "./api";

const route = useRoute();
const ready = ref(false);
const authenticated = ref(false);
const unread = ref(0);
const showLogin = computed(() => ready.value && !authenticated.value);

const refreshUnread = async () => {
  if (!authenticated.value) return;
  const count = await api.get<{ count: number }>("/api/alerts/unread-count").catch(() => ({ count: 0 }));
  unread.value = count.count;
};

provide("refreshUnread", refreshUnread);
watch(() => route.fullPath, () => {
  void refreshUnread();
});

onMounted(async () => {
  const status = await api.get<{ authenticated: boolean; devBypass: boolean }>("/api/auth/status");
  authenticated.value = status.authenticated || status.devBypass;
  await refreshUnread();
  ready.value = true;
});
</script>

<template>
  <div v-if="!ready" class="main">Opening the vault…</div>
  <div v-else-if="showLogin" class="login">
    <div class="card login-card">
      <h1 class="brand-mark">The Timeless Vault</h1>
      <p class="muted">Rare and retired LEGO, listed once.</p>
      <p><a class="btn gold" href="/oauth2/authorization/google">Continue with Google</a></p>
    </div>
  </div>
  <div v-else class="shell">
    <aside class="sidebar">
      <p class="brand-mark">The Timeless Vault</p>
      <p class="brand-sub">thetimelessvault.com</p>
      <nav>
        <div class="nav-group">
          <p class="nav-label">Inventory</p>
          <router-link to="/inventory">Inventory</router-link>
          <router-link to="/listing-logs">Listing logs</router-link>
        </div>
        <div class="nav-group">
          <p class="nav-label">Market Watch</p>
          <router-link to="/watches">Items watch</router-link>
          <router-link to="/market">Market Monitoring</router-link>
          <router-link to="/scan-logs">Scan logs</router-link>
          <router-link to="/alerts">
            Alerts
            <span v-if="unread" class="badge">{{ unread }}</span>
          </router-link>
        </div>
        <div class="nav-group">
          <router-link to="/settings">Settings</router-link>
        </div>
      </nav>
    </aside>
    <main class="main">
      <router-view :key="route.fullPath" />
    </main>
    <ConfirmModal />
  </div>
</template>
