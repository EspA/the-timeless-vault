<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref, watch } from "vue";
import { useRoute } from "vue-router";
import ConfirmModal from "./components/ConfirmModal.vue";
import ChannelLogo from "./components/ChannelLogo.vue";
import LoginGate from "./components/LoginGate.vue";
import ThemeToggle from "./components/ThemeToggle.vue";
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
  const count = await api.get<{ count: number }>("/api/notifications/unread-count").catch(() => ({ count: 0 }));
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
  try {
    const status = await api.get<{ authenticated: boolean; devBypass: boolean }>("/api/auth/status");
    authenticated.value = status.authenticated || status.devBypass;
    await refreshUnread();
  } catch {
    authenticated.value = false;
  }
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
      <router-link to="/" class="brand-home" aria-label="Home">
        <img class="brand-logo" src="/logos/ttl-logo.png" alt="" />
      </router-link>
      <p class="brand-mark">The Timeless Vault</p>
      <div class="mobile-bar-actions">
        <ThemeToggle compact />
        <router-link
          to="/notifications"
          class="mobile-notify"
          :aria-label="unread ? `${unread} unread notifications` : 'Notifications'"
        >
          <svg class="mobile-notify-bell" viewBox="0 0 24 24" aria-hidden="true">
            <path
              fill="currentColor"
              d="M12 2a6 6 0 0 0-6 6v3.1c0 .7-.2 1.3-.6 1.9L4 15.6c-.5.7 0 1.7.9 1.7h14.2c.9 0 1.4-1 .9-1.7l-1.4-2.6c-.4-.6-.6-1.2-.6-1.9V8a6 6 0 0 0-6-6Zm0 20a2.8 2.8 0 0 0 2.7-2H9.3A2.8 2.8 0 0 0 12 22Z"
            />
          </svg>
          <span v-if="unread" class="mobile-notify-count">{{ unread }}</span>
        </router-link>
      </div>
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
        <router-link to="/" class="brand-home" aria-label="Home">
          <img class="brand-logo" src="/logos/ttl-logo.png" alt="The Timeless Vault" />
        </router-link>
        <div>
          <p class="brand-mark">The Timeless Vault</p>
          <p class="brand-sub">
            <a href="https://thetimelessvault.com" target="_blank" rel="noopener noreferrer">thetimelessvault.com</a>
          </p>
        </div>
      </div>
      <nav>
        <div class="nav-group">
          <router-link to="/notifications">
            Notifications
            <span v-if="unread" class="badge unread">{{ unread }}</span>
          </router-link>
        </div>
        <div class="nav-group">
          <p class="nav-label">Inventory</p>
          <router-link to="/inventory">Inventory</router-link>
          <router-link to="/listing-logs">Listing logs</router-link>
        </div>
        <div class="nav-group">
          <p class="nav-label">Inbound</p>
          <router-link to="/purchase-orders">Purchase orders</router-link>
          <router-link to="/suppliers">Suppliers</router-link>
          <router-link to="/quotes">Quotes</router-link>
        </div>
        <div class="nav-group">
          <p class="nav-label">Outbound</p>
          <router-link to="/orders">Orders</router-link>
          <router-link to="/sales-ledger">Sales Ledger</router-link>
        </div>
        <div class="nav-group">
          <p class="nav-label">Market Watch</p>
          <router-link to="/watches">Items watch</router-link>
          <router-link to="/market">Market Monitoring</router-link>
          <router-link to="/scan-logs">Scan logs</router-link>
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
          <a
            class="sales-channel"
            href="https://www.brickowl.com/mystore"
            target="_blank"
            rel="noopener noreferrer"
          >
            <ChannelLogo platform="BRICKOWL" :height="18" />
          </a>
        </div>
        <div class="nav-group">
          <p class="nav-label">Settings</p>
          <router-link to="/settings" active-class="" exact-active-class="router-link-active">Settings</router-link>
          <router-link to="/settings/statistics">Statistics</router-link>
          <ThemeToggle />
        </div>
      </nav>
    </aside>
    <main class="main">
      <router-view :key="route.fullPath" />
    </main>
    <ConfirmModal />
  </div>
</template>
