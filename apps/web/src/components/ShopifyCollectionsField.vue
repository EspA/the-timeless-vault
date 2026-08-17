<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { api, type ShopifyCollection } from "../api";

const selected = defineModel<string[]>({ default: () => [] });
const props = defineProps<{ defaultTitle?: string }>();
const collections = ref<ShopifyCollection[]>([]);
const error = ref("");
const open = ref(false);
const query = ref("");
const root = ref<HTMLElement | null>(null);

onMounted(async () => {
  document.addEventListener("mousedown", onDocumentClick);
  document.addEventListener("keydown", onKey);
  try {
    collections.value = await api.get<ShopifyCollection[]>("/api/shopify/collections");
    applyDefaultSelection();
  } catch (e) {
    error.value = (e as Error).message;
  }
});

onUnmounted(() => {
  document.removeEventListener("mousedown", onDocumentClick);
  document.removeEventListener("keydown", onKey);
});

const selectedIds = computed(() => selected.value ?? []);

const selectedRows = computed(() =>
  collections.value.filter((collection) => selectedIds.value.includes(collection.id))
);

const filtered = computed(() => {
  const needle = query.value.trim().toLowerCase();
  return collections.value.filter((collection) =>
    !needle || collection.title.toLowerCase().includes(needle)
  );
});

const onDocumentClick = (event: MouseEvent) => {
  if (root.value && !root.value.contains(event.target as Node)) {
    open.value = false;
  }
};

const onKey = (event: KeyboardEvent) => {
  if (event.key === "Escape") {
    open.value = false;
  }
};

const toggle = (id: string) => {
  const current = selectedIds.value;
  selected.value = current.includes(id)
    ? current.filter((value) => value !== id)
    : [...current, id];
};

const remove = (id: string) => {
  selected.value = selectedIds.value.filter((value) => value !== id);
};

const applyDefaultSelection = () => {
  const title = props.defaultTitle?.trim().toLowerCase();
  if (!title || selectedIds.value.length) {
    return;
  }
  const match = collections.value.find((collection) => collection.title.trim().toLowerCase() === title);
  if (match) {
    selected.value = [match.id];
  }
};
</script>

<template>
  <label>
    Shopify collections
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="!collections.length" class="muted">No Shopify collections loaded. Check the Shopify connection in Settings.</p>
    <div v-else ref="root" class="multi-select">
      <div class="multi-select-control" @click="open = !open">
        <span v-if="!selectedRows.length" class="muted">Select collections</span>
        <span
          v-for="collection in selectedRows"
          :key="collection.id"
          class="multi-select-chip"
        >
          {{ collection.title }}
          <button type="button" title="Remove" @click.stop="remove(collection.id)">×</button>
        </span>
      </div>
      <div v-if="open" class="multi-select-menu" @click.stop>
        <input v-model="query" class="multi-select-search" type="search" placeholder="Search collections" />
        <button
          v-for="collection in filtered"
          :key="collection.id"
          class="multi-select-option"
          :class="{ on: selectedIds.includes(collection.id) }"
          type="button"
          @click="toggle(collection.id)"
        >
          {{ collection.title }}
        </button>
        <p v-if="!filtered.length" class="muted" style="padding:0.65rem 0.75rem;margin:0">No matching collections.</p>
      </div>
    </div>
  </label>
</template>
