<script setup lang="ts">
import { computed } from "vue";
import { EBAY_ITEM_LOCATIONS, SCAN_INTERVALS } from "../api";
import ChannelLogo from "./ChannelLogo.vue";

defineProps<{
  setNumber?: string;
}>();

const enabled = defineModel<boolean>("enabled", { required: true });
const ebaySearchQuery = defineModel<string>("ebaySearchQuery", { required: true });
const ebayExcludeWords = defineModel<string>("ebayExcludeWords", { required: true });
const ebayFeedbackMin = defineModel<number>("ebayFeedbackMin", { required: true });
const ebayItemLocation = defineModel<string>("ebayItemLocation", { required: true });
const ebayScanIntervalMinutes = defineModel<number>("ebayScanIntervalMinutes", { required: true });
const bricklinkScanIntervalMinutes = defineModel<number>("bricklinkScanIntervalMinutes", { required: true });
const minPrice = defineModel<number | null>("minPrice", { required: true });
const maxPrice = defineModel<number | null>("maxPrice", { required: true });

const intervalOptions = (current: number) => {
  if (SCAN_INTERVALS.some((option) => option.minutes === current)) {
    return SCAN_INTERVALS;
  }
  return [{ minutes: current, label: `Every ${current} minutes` }, ...SCAN_INTERVALS];
};

const ebayIntervalOptions = computed(() => intervalOptions(ebayScanIntervalMinutes.value));
const bricklinkIntervalOptions = computed(() => intervalOptions(bricklinkScanIntervalMinutes.value));

const parsePrice = (raw: string) => {
  if (!raw.trim()) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
};
</script>

<template>
  <div class="grid">
    <div class="card grid">
      <h3>Watch</h3>
      <label><input type="checkbox" v-model="enabled" /> Enable market watch</label>
      <div class="grid two">
        <label>Alert min price
          <input
            :value="minPrice ?? ''"
            type="number"
            min="0"
            step="0.01"
            placeholder="No minimum"
            @input="minPrice = parsePrice(($event.target as HTMLInputElement).value)"
          />
        </label>
        <label>Alert max price
          <input
            :value="maxPrice ?? ''"
            type="number"
            min="0"
            step="0.01"
            placeholder="No maximum"
            @input="maxPrice = parsePrice(($event.target as HTMLInputElement).value)"
          />
        </label>
      </div>
    </div>
    <div class="grid two">
      <div class="card grid">
        <h3 class="channel-heading"><ChannelLogo platform="EBAY" :height="22" /></h3>
        <label>Automatic scan frequency
          <select v-model.number="ebayScanIntervalMinutes">
            <option v-for="option in ebayIntervalOptions" :key="option.minutes" :value="option.minutes">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>Search <input v-model="ebaySearchQuery" /></label>
        <label>Exclude words
          <textarea v-model="ebayExcludeWords" rows="4" placeholder="-yellow -box"></textarea>
        </label>
        <label>Item condition <input value="New" disabled /></label>
        <label>Feedback count min <input v-model.number="ebayFeedbackMin" type="number" min="0" /></label>
        <label>Items located
          <select v-model="ebayItemLocation">
            <option v-for="option in EBAY_ITEM_LOCATIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>Listing type <input value="All Item Types" disabled /></label>
      </div>
      <div class="card grid">
        <h3 class="channel-heading"><ChannelLogo platform="BRICKLINK" :height="22" /></h3>
        <label>Automatic scan frequency
          <select v-model.number="bricklinkScanIntervalMinutes">
            <option v-for="option in bricklinkIntervalOptions" :key="option.minutes" :value="option.minutes">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>Set ID <input :value="setNumber || ''" disabled /></label>
        <label>Condition <input value="New and Sealed" disabled /></label>
        <label>Seller location <input value="Anywhere" disabled /></label>
        <label>Seller ships to <input value="USA" disabled /></label>
      </div>
    </div>
  </div>
</template>
