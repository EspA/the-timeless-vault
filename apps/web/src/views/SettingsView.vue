<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, defaultEbayExcludeWords } from "../api";
import { askConfirm } from "../confirm";
import { applyTheme, theme } from "../theme";

const health = ref<Record<string, unknown> | null>(null);
const ebayCode = ref("");
const ebayBusy = ref(false);
const ebayMessage = ref("");
const ebayError = ref("");
const locationBusy = ref(false);
const zipBusy = ref(false);
const zipMessage = ref("");
const buyerPostalCode = ref("");
const excludeBusy = ref(false);
const excludeMessage = ref("");
const excludeError = ref("");
const defaultExcludeWords = ref("");
const alertBusy = ref(false);
const alertTestBusy = ref(false);
const alertMessage = ref("");
const alertError = ref("");
const alertTo = ref("");
const highPercent = ref("15");
const lowPercent = ref("15");
const thresholdBusy = ref(false);
const thresholdMessage = ref("");
const thresholdError = ref("");
const location = ref({
  name: "Private Mail Box",
  addressLine1: "",
  city: "",
  stateOrProvince: "",
  postalCode: "",
  country: "US",
});
const storeCategories = ref<string[]>([]);
const newStoreCategory = ref("");
const storeCategoryBusy = ref(false);
const storeCategoryMessage = ref("");
const storeCategoryError = ref("");

const loadHealth = async () => {
  health.value = await api.get("/api/settings/health");
};

const loadStoreCategories = async () => {
  const result = await api.get<{ categories: string[] }>("/api/settings/ebay-store-categories");
  storeCategories.value = result.categories;
};

onMounted(async () => {
  await loadHealth();
  buyerPostalCode.value = String(health.value?.ebayBuyerPostalCode || "");
  alertTo.value = String(health.value?.alertTo || "");
  highPercent.value = String(health.value?.priceGuardHighPercent ?? "15");
  lowPercent.value = String(health.value?.priceGuardLowPercent ?? "15");
  const words = health.value?.ebayDefaultExcludeWords;
  defaultExcludeWords.value = typeof words === "string" ? words : defaultEbayExcludeWords;
  try {
    await loadStoreCategories();
  } catch {
    storeCategories.value = Array.isArray(health.value?.ebayStoreCategories)
      ? (health.value.ebayStoreCategories as string[])
      : [];
  }
  try {
    const defaults = await api.get<typeof location.value>("/api/ebay/location/defaults");
    location.value = { ...location.value, ...defaults };
    if (!buyerPostalCode.value && defaults.postalCode) {
      buyerPostalCode.value = defaults.postalCode;
    }
  } catch {
    // Shopify address is optional; the form can still be filled by hand.
  }
});

const connectEbay = async () => {
  ebayError.value = "";
  ebayMessage.value = "";
  const result = await api.get<{ url: string }>("/api/ebay/oauth/start");
  window.location.href = result.url;
};

const completeEbay = async () => {
  ebayBusy.value = true;
  ebayError.value = "";
  ebayMessage.value = "";
  try {
    await api.post("/api/ebay/oauth/complete", { code: ebayCode.value });
    ebayCode.value = "";
    ebayMessage.value = "eBay connected. A refresh token is now stored.";
    await loadHealth();
  } catch (e) {
    ebayError.value = e instanceof Error ? e.message : "Could not complete eBay OAuth";
  } finally {
    ebayBusy.value = false;
  }
};

const saveBuyerPostalCode = async () => {
  zipBusy.value = true;
  zipMessage.value = "";
  ebayError.value = "";
  try {
    const saved = await api.put<{ postalCode: string }>("/api/settings/ebay-buyer-postal-code", {
      postalCode: buyerPostalCode.value,
    });
    buyerPostalCode.value = saved.postalCode;
    zipMessage.value = saved.postalCode
      ? `eBay scans will quote shipping to ${saved.postalCode}.`
      : "eBay scans will not calculate destination shipping.";
    await loadHealth();
  } catch (e) {
    ebayError.value = e instanceof Error ? e.message : "Could not save ZIP";
  } finally {
    zipBusy.value = false;
  }
};

const saveDefaultExcludeWords = async () => {
  excludeBusy.value = true;
  excludeMessage.value = "";
  excludeError.value = "";
  try {
    const saved = await api.put<{ excludeWords: string }>("/api/settings/ebay-default-exclude-words", {
      excludeWords: defaultExcludeWords.value,
    });
    defaultExcludeWords.value = saved.excludeWords;
    excludeMessage.value = "New watches will use this exclude list.";
    await loadHealth();
  } catch (e) {
    excludeError.value = e instanceof Error ? e.message : "Could not save exclude words";
  } finally {
    excludeBusy.value = false;
  }
};

const saveAlertEmail = async () => {
  alertBusy.value = true;
  alertMessage.value = "";
  alertError.value = "";
  try {
    const saved = await api.put<{ email: string }>("/api/settings/alert-email", { email: alertTo.value });
    alertTo.value = saved.email;
    alertMessage.value = "Alert email saved.";
    await loadHealth();
  } catch (e) {
    alertError.value = e instanceof Error ? e.message : "Could not save alert email";
  } finally {
    alertBusy.value = false;
  }
};

const sendTestAlertEmail = async () => {
  alertTestBusy.value = true;
  alertMessage.value = "";
  alertError.value = "";
  try {
    const sent = await api.post<{ email: string }>("/api/settings/alert-email/test");
    alertMessage.value = `Test email sent to ${sent.email}.`;
  } catch (e) {
    alertError.value = e instanceof Error ? e.message : "Could not send test email";
  } finally {
    alertTestBusy.value = false;
  }
};

const savePriceGuardThresholds = async () => {
  thresholdBusy.value = true;
  thresholdMessage.value = "";
  thresholdError.value = "";
  try {
    const saved = await api.put<{ highPercent: number; lowPercent: number }>("/api/settings/price-guard-thresholds", {
      highPercent: Number(highPercent.value),
      lowPercent: Number(lowPercent.value),
    });
    highPercent.value = String(saved.highPercent);
    lowPercent.value = String(saved.lowPercent);
    thresholdMessage.value = "Price alert thresholds saved. Existing listings will use these values.";
    await loadHealth();
  } catch (e) {
    thresholdError.value = e instanceof Error ? e.message : "Could not save thresholds";
  } finally {
    thresholdBusy.value = false;
  }
};

const createEbayLocation = async () => {
  locationBusy.value = true;
  ebayError.value = "";
  ebayMessage.value = "";
  try {
    const result = await api.post<{ merchantLocationKey?: string }>("/api/ebay/location", location.value);
    ebayMessage.value = `eBay warehouse location saved (${result.merchantLocationKey || "enabled"}).`;
    await loadHealth();
  } catch (e) {
    ebayError.value = e instanceof Error ? e.message : "Could not create eBay location";
  } finally {
    locationBusy.value = false;
  }
};

const addStoreCategory = async () => {
  const name = newStoreCategory.value.trim();
  if (!name || storeCategoryBusy.value) return;
  storeCategoryBusy.value = true;
  storeCategoryMessage.value = "";
  storeCategoryError.value = "";
  try {
    const saved = await api.post<{ categories: string[] }>("/api/settings/ebay-store-categories", { name });
    storeCategories.value = saved.categories;
    newStoreCategory.value = "";
    storeCategoryMessage.value = `Added ${name}.`;
  } catch (e) {
    storeCategoryError.value = e instanceof Error ? e.message : "Could not add eBay store category";
  } finally {
    storeCategoryBusy.value = false;
  }
};

const deleteStoreCategory = async (name: string) => {
  if (storeCategoryBusy.value) return;
  const confirmed = await askConfirm(
    `Delete "${name}"? Items using this category will have their eBay store category cleared.`,
    { title: "Delete eBay store category" }
  );
  if (!confirmed) return;
  storeCategoryBusy.value = true;
  storeCategoryMessage.value = "";
  storeCategoryError.value = "";
  try {
    const saved = await api.del<{ categories: string[] }>(
      `/api/settings/ebay-store-categories?name=${encodeURIComponent(name)}`
    );
    storeCategories.value = saved.categories;
    storeCategoryMessage.value = `Removed ${name}.`;
  } catch (e) {
    storeCategoryError.value = e instanceof Error ? e.message : "Could not delete eBay store category";
  } finally {
    storeCategoryBusy.value = false;
  }
};

const pill = (ok: unknown) => (ok ? "ok" : "bad");

const onThemeToggle = (event: Event) => {
  const checked = (event.target as HTMLInputElement).checked;
  applyTheme(checked ? "dark" : "light");
};
</script>

<template>
  <div class="grid">
    <h1>Settings</h1>
    <p class="muted">Credentials live in environment variables / Secret Manager. This page only shows connection health.</p>
    <div class="card appearance-row">
      <div class="appearance-copy">
        <h3>Appearance</h3>
        <p class="muted">Dark mode applies across the whole vault, including the login screen.</p>
      </div>
      <label class="switch">
        <input type="checkbox" :checked="theme === 'dark'" @change="onThemeToggle" />
        <span class="switch-track" aria-hidden="true"></span>
        <span class="switch-label">{{ theme === "dark" ? "Dark mode on" : "Dark mode off" }}</span>
      </label>
    </div>
    <div v-if="health" class="card grid">
      <p>BrickEconomy <span class="badge" :class="pill(health.brickeconomy)">{{ health.brickeconomy ? "configured" : "missing" }}</span></p>
      <p>Shopify <span class="badge" :class="pill(health.shopify)">{{ health.shopify ? "configured" : "missing" }}</span></p>
      <p>BrickLink <span class="badge" :class="pill(health.bricklink)">{{ health.bricklink ? "configured" : "missing" }}</span></p>
      <p>eBay app <span class="badge" :class="pill(health.ebay)">{{ health.ebay ? "configured" : "missing" }}</span></p>
      <p>eBay OAuth <span class="badge" :class="pill(health.ebayOAuth)">{{ health.ebayOAuth ? "refresh token stored" : "needs consent" }}</span></p>
      <p>eBay sell-ready <span class="badge" :class="pill(health.ebaySellReady && health.ebayPoliciesReady)">{{ health.ebayPoliciesReady ? "location and policies found" : (health.ebaySellReady ? "OAuth ok, policies/location missing" : "needs OAuth") }}</span></p>
      <p v-if="health.ebayLocation" class="muted">eBay location: {{ health.ebayLocation }}</p>
      <p v-if="health.ebaySellError" class="error">{{ health.ebaySellError }}</p>
      <p>Photo storage <span class="badge">{{ health.storage }}</span></p>
      <button class="btn gold" type="button" @click="connectEbay">Connect eBay account</button>
      <p class="muted">
        eBay requires HTTPS for Auth accepted URLs, so leave those fields blank on the developer site.
        Click Connect, agree, then paste the eBay success-page URL here (it contains <code>code=</code>).
        The code expires in about five minutes. Do not paste a token from Get a User Token Here.
      </p>
      <label>
        eBay success URL or code
        <textarea v-model="ebayCode" rows="3" placeholder="https://signin.ebay.com/...&code=..." />
      </label>
      <button class="btn" type="button" :disabled="ebayBusy || !ebayCode.trim()" @click="completeEbay">
        {{ ebayBusy ? "Saving…" : "Save eBay code" }}
      </button>
      <label>eBay shipping ZIP
        <input v-model="buyerPostalCode" maxlength="10" placeholder="19406" />
      </label>
      <p class="muted">
        Used on market scans so eBay can calculate shipping to this destination. This is not the warehouse
        ship-from ZIP below.
      </p>
      <button class="btn gold" type="button" :disabled="zipBusy" @click="saveBuyerPostalCode">
        {{ zipBusy ? "Saving…" : "Save shipping ZIP" }}
      </button>
      <p v-if="zipMessage" class="muted">{{ zipMessage }}</p>
    </div>

    <div class="card grid">
      <h3>Alert email</h3>
      <label>Send alerts to
        <input v-model="alertTo" type="email" placeholder="you@gmail.com" />
      </label>
      <p class="muted">
        Alerts are emailed here. Local SMTP is Mailpit at
        <a href="http://localhost:8025" target="_blank">localhost:8025</a>.
      </p>
      <div style="display:flex;gap:0.75rem;flex-wrap:wrap">
        <button class="btn gold" type="button" :disabled="alertBusy" @click="saveAlertEmail">
          {{ alertBusy ? "Saving…" : "Save alert email" }}
        </button>
        <button class="btn secondary" type="button" :disabled="alertTestBusy || !alertTo.trim()" @click="sendTestAlertEmail">
          {{ alertTestBusy ? "Sending…" : "Send test email" }}
        </button>
      </div>
      <p v-if="alertMessage" class="muted">{{ alertMessage }}</p>
      <p v-if="alertError" class="error">{{ alertError }}</p>
    </div>

    <div class="card grid">
      <h3>Price alerts</h3>
      <p class="muted">
        When a set you have in stock is this far from the market average, a
        PRICE HIGH or PRICE LOW alert is created.
      </p>
      <div class="grid two">
        <label>High threshold (%)
          <input v-model="highPercent" type="number" min="0" max="999" step="0.01" />
        </label>
        <label>Low threshold (%)
          <input v-model="lowPercent" type="number" min="0" max="999" step="0.01" />
        </label>
      </div>
      <p class="muted">
        Default is 15% either way. High fires when your live price is above market by this percent.
        Low fires when it is below market by this percent.
      </p>
      <button class="btn gold" type="button" :disabled="thresholdBusy" @click="savePriceGuardThresholds">
        {{ thresholdBusy ? "Saving…" : "Save price thresholds" }}
      </button>
      <p v-if="thresholdMessage" class="muted">{{ thresholdMessage }}</p>
      <p v-if="thresholdError" class="error">{{ thresholdError }}</p>
    </div>

    <div class="card grid">
      <h3>eBay watch defaults</h3>
      <label>Default exclude words
        <textarea class="short-description" v-model="defaultExcludeWords" rows="4" placeholder="-custom -moc -replica" />
      </label>
      <p class="muted">
        Copied onto each new item watch. Existing watches keep the list they already have.
      </p>
      <button class="btn gold" type="button" :disabled="excludeBusy" @click="saveDefaultExcludeWords">
        {{ excludeBusy ? "Saving…" : "Save exclude words" }}
      </button>
      <p v-if="excludeMessage" class="muted">{{ excludeMessage }}</p>
      <p v-if="excludeError" class="error">{{ excludeError }}</p>
    </div>

    <div class="card grid">
      <h3>eBay store categories</h3>
      <p class="muted">
        Used when creating or editing an item. Names should match categories in your eBay store.
        Deleting a category clears it on any linked items.
      </p>
      <ul class="settings-list">
        <li v-for="name in storeCategories" :key="name">
          <span>{{ name }}</span>
          <button class="btn secondary compact" type="button" :disabled="storeCategoryBusy" @click="deleteStoreCategory(name)">
            Delete
          </button>
        </li>
      </ul>
      <p v-if="!storeCategories.length" class="muted">No eBay store categories yet.</p>
      <div class="settings-add-row">
        <label>Add category
          <input v-model="newStoreCategory" maxlength="50" placeholder="Star Wars" @keydown.enter.prevent="addStoreCategory" />
        </label>
        <button class="btn gold" type="button" :disabled="storeCategoryBusy || !newStoreCategory.trim()" @click="addStoreCategory">
          {{ storeCategoryBusy ? "Saving…" : "Add" }}
        </button>
      </div>
      <p v-if="storeCategoryMessage" class="muted">{{ storeCategoryMessage }}</p>
      <p v-if="storeCategoryError" class="error">{{ storeCategoryError }}</p>
    </div>

    <div v-if="health" class="card grid">
      <p class="muted">
        Seller Hub does not expose inventory locations for API listings. Create a warehouse location here
        (ZIP is enough). Shopify’s ship-from address is used when available.
      </p>
      <label>Location name <input v-model="location.name" /></label>
      <label>Street <input v-model="location.addressLine1" /></label>
      <label>City <input v-model="location.city" /></label>
      <label>State <input v-model="location.stateOrProvince" maxlength="2" placeholder="FL" /></label>
      <label>ZIP <input v-model="location.postalCode" /></label>
      <button class="btn gold" type="button" :disabled="locationBusy" @click="createEbayLocation">
        {{ locationBusy ? "Saving…" : "Create eBay warehouse location" }}
      </button>
      <p v-if="ebayMessage" class="muted">{{ ebayMessage }}</p>
      <p v-if="ebayError" class="error">{{ ebayError }}</p>
    </div>
  </div>
</template>
