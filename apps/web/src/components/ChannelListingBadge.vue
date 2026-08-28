<script setup lang="ts">
import { computed } from "vue";
import { visibilityStatusLabel } from "../api";
import ChannelLogo from "./ChannelLogo.vue";

const props = defineProps<{
  platform: "SHOPIFY" | "BRICKLINK" | "BRICKOWL" | "EBAY";
  status?: string;
  href?: string;
}>();

const name = computed(() => {
  if (props.platform === "SHOPIFY") return "Shopify";
  if (props.platform === "BRICKLINK") return "BrickLink";
  if (props.platform === "BRICKOWL") return "Brick Owl";
  return "eBay";
});
</script>

<template>
  <a
    v-if="href"
    class="badge listing-link ok"
    :href="href"
    target="_blank"
    rel="noopener noreferrer"
    :title="`Open ${name} listing`"
  >
    <ChannelLogo :platform="platform" :height="14" />
    {{ visibilityStatusLabel(status) }}
  </a>
  <span
    v-else-if="status"
    class="badge listing-status"
    :class="{ ok: status === 'ACTIVE', warn: status === 'UNLISTED' }"
  >
    <ChannelLogo :platform="platform" :height="14" />
    {{ visibilityStatusLabel(status) }}
  </span>
  <span v-else class="muted">—</span>
</template>
