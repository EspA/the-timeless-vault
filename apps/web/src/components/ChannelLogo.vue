<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(defineProps<{
  platform: string;
  height?: number;
}>(), {
  height: 20,
});

const meta = computed(() => {
  const platform = (props.platform || "").toUpperCase();
  if (platform === "EBAY") {
    return { src: "/logos/ebay-logo.png", alt: "eBay" };
  }
  if (platform === "BRICKLINK") {
    return { src: "/logos/bricklink-logo.png", alt: "BrickLink" };
  }
  if (platform === "SHOPIFY") {
    return { src: "/logos/shopify-logo.png", alt: "Shopify" };
  }
  return null;
});

const label = computed(() => {
  const platform = (props.platform || "").toUpperCase();
  if (platform === "LOCAL") return "Local";
  return meta.value?.alt || props.platform;
});
</script>

<template>
  <img
    v-if="meta"
    class="channel-logo"
    :src="meta.src"
    :alt="meta.alt"
    :title="meta.alt"
    :style="{ height: `${height}px` }"
  />
  <span v-else>{{ label }}</span>
</template>
