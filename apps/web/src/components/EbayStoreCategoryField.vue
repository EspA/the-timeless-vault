<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api, type EbayStoreCategory } from "../api";

const selected = defineModel<string>({ default: "" });
const categories = ref<EbayStoreCategory[]>([]);
const error = ref("");

onMounted(async () => {
  try {
    categories.value = await api.get<EbayStoreCategory[]>("/api/ebay/store-categories");
  } catch (e) {
    error.value = (e as Error).message;
  }
});

const options = computed(() => {
  const rows = [...categories.value];
  const current = selected.value?.trim();
  if (current && !rows.some((row) => row.path === current || row.name === current)) {
    rows.unshift({ id: current, name: current, path: current });
  }
  return rows;
});
</script>

<template>
  <label>
    eBay store category
    <select v-model="selected">
      <option v-if="!selected" value="" disabled>Select a store category</option>
      <option v-for="category in options" :key="category.id || category.path" :value="category.path">
        {{ category.path }}
      </option>
    </select>
    <span v-if="error" class="error">{{ error }}</span>
  </label>
</template>
