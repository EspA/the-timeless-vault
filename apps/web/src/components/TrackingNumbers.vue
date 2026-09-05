<script setup lang="ts">
import { computed } from "vue";
import { trackingEntries, type ShipmentTracking } from "../api";
import TrackingNumber from "./TrackingNumber.vue";

const props = defineProps<{
  row?: {
    trackings?: ShipmentTracking[];
    trackingNumber?: string;
    shippingProvider?: string;
    carrier?: string | null;
  } | null;
}>();

const entries = computed(() => trackingEntries(props.row));
</script>

<template>
  <span v-if="!entries.length">—</span>
  <span v-else class="tracking-numbers">
    <TrackingNumber
      v-for="entry in entries"
      :key="entry.trackingNumber"
      :tracking="entry.trackingNumber"
      :provider="entry.carrier"
    />
  </span>
</template>
