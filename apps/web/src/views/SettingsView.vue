<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, defaultEbayExcludeWords } from "../api";

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
const location = ref({
  name: "Private Mail Box",
  addressLine1: "",
  city: "",
  stateOrProvince: "",
  postalCode: "",
  country: "US",
});

const loadHealth = async () => {
  health.value = await api.get("/api/settings/health");
};

onMounted(async () => {
  await loadHealth();
  buyerPostalCode.value = String(health.value?.ebayBuyerPostalCode || "");
  const words = health.value?.ebayDefaultExcludeWords;
  defaultExcludeWords.value = typeof words === "string" ? words : defaultEbayExcludeWords;
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

const pill = (ok: unknown) => (ok ? "ok" : "bad");
</script>

<template>
  <div class="grid">
    <h1>Settings</h1>
    <p class="muted">Credentials live in environment variables / Secret Manager. This page only shows connection health.</p>
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
      <p>Alert email <span class="muted">{{ health.alertTo || "not set" }}</span></p>
      <button class="btn gold" type="button" @click="connectEbay">Connect eBay account</button>
      <p class="muted">
        Reconnect after enabling the Stores API on your eBay developer app if the inventory
        store-category dropdown still shows the fallback list.
      </p>
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
