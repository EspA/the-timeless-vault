<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref, watch } from "vue";
import { useRoute } from "vue-router";
import ConfirmModal from "./components/ConfirmModal.vue";
import ChannelLogo from "./components/ChannelLogo.vue";
import LoginGate from "./components/LoginGate.vue";
import { api } from "./api";

const route = useRoute();
const ready = ref(false);
const authenticated = ref(false);
const unread = ref(0);
const navOpen = ref(false);
const showLogin = computed(() => ready.value && !authenticated.value);

const closeNav = () => {
  navOpen.value = false;
};

const toggleNav = () => {
  navOpen.value = !navOpen.value;
};

const refreshUnread = async () => {
  if (!authenticated.value) return;
  const count = await api.get<{ count: number }>("/api/alerts/unread-count").catch(() => ({ count: 0 }));
  unread.value = count.count;
};

provide("refreshUnread", refreshUnread);
watch(() => route.fullPath, () => {
  closeNav();
  void refreshUnread();
});

watch(navOpen, (open) => {
  document.body.style.overflow = open ? "hidden" : "";
});

const onKey = (event: KeyboardEvent) => {
  if (event.key === "Escape") closeNav();
};

onMounted(async () => {
  window.addEventListener("keydown", onKey);
  const status = await api.get<{ authenticated: boolean; devBypass: boolean }>("/api/auth/status");
  authenticated.value = status.authenticated || status.devBypass;
  await refreshUnread();
  ready.value = true;
});

onUnmounted(() => {
  window.removeEventListener("keydown", onKey);
  document.body.style.overflow = "";
});
</script>

<template>
  <LoginGate v-if="!ready" loading />
  <LoginGate v-else-if="showLogin" />
  <div v-else class="shell" :class="{ 'nav-open': navOpen }">
    <header class="mobile-bar">
      <button
        class="nav-toggle"
        type="button"
        :aria-expanded="navOpen"
        aria-controls="app-sidebar"
        :aria-label="navOpen ? 'Close menu' : 'Open menu'"
        @click="toggleNav"
      >
        <span></span>
        <span></span>
        <span></span>
      </button>
      <img class="brand-logo" src="/logos/ttl-logo.png" alt="" />
      <p class="brand-mark">The Timeless Vault</p>
    </header>
    <button
      v-if="navOpen"
      class="nav-scrim"
      type="button"
      aria-label="Close menu"
      @click="closeNav"
    />
    <aside id="app-sidebar" class="sidebar">
      <div class="sidebar-brand">
        <img class="brand-logo" src="/logos/ttl-logo.png" alt="The Timeless Vault" />
        <div>
          <p class="brand-mark">The Timeless Vault</p>
          <p class="brand-sub">
            <a href="https://thetimelessvault.com" target="_blank" rel="noopener noreferrer">thetimelessvault.com</a>
          </p>
        </div>
      </div>
      <nav>
        <div class="nav-group">
          <p class="nav-label">Inventory</p>
          <router-link to="/inventory">Inventory</router-link>
          <router-link to="/listing-logs">Listing logs</router-link>
        </div>
        <div class="nav-group">
          <p class="nav-label">Sales</p>
          <router-link to="/sales">Sales</router-link>
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
          <p class="nav-label">Sales Channels</p>
          <a
            class="sales-channel"
            href="https://thetimelessvault.com"
            target="_blank"
            rel="noopener noreferrer"
          >
            <ChannelLogo platform="SHOPIFY" :height="18" />
          </a>
          <a
            class="sales-channel"
            href="https://www.ebay.com/str/thetimelessvaultshop"
            target="_blank"
            rel="noopener noreferrer"
          >
            <ChannelLogo platform="EBAY" :height="18" />
          </a>
          <a
            class="sales-channel"
            href="https://store.bricklink.com/EspA"
            target="_blank"
            rel="noopener noreferrer"
          >
            <ChannelLogo platform="BRICKLINK" :height="18" />
          </a>
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
