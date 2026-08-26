<script setup lang="ts">
import { computed } from "vue";
import { trackingUrl } from "../api";

const props = defineProps<{
  tracking?: string | null;
  provider?: string | null;
}>();

const href = computed(() => trackingUrl(props.tracking, props.provider));
const label = computed(() => props.tracking?.trim() || "—");
</script>

<template>
  <a
    v-if="href"
    :href="href"
    target="_blank"
    rel="noopener noreferrer"
    @click.stop
  >{{ label }}</a>
  <span v-else>{{ label }}</span>
</template>
