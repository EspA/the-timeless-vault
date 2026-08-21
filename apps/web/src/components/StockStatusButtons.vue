<script setup lang="ts">
import { STOCK_STATUSES } from "../api";

withDefaults(defineProps<{
  modelValue?: string;
  disabled?: boolean;
  compact?: boolean;
}>(), {
  disabled: false,
  compact: false,
});

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const select = (value: string, current?: string, disabled?: boolean) => {
  if (disabled || value === current) return;
  emit("update:modelValue", value);
};
</script>

<template>
  <div class="stock-status" :class="{ compact }" role="group" aria-label="Stock status">
    <button
      v-for="status in STOCK_STATUSES"
      :key="status.value"
      type="button"
      :class="['stock-status-btn', status.value.toLowerCase(), { active: modelValue === status.value }]"
      :disabled="disabled"
      :aria-pressed="modelValue === status.value"
      @click="select(status.value, modelValue, disabled)"
    >
      {{ compact ? status.shortLabel : status.label }}
    </button>
  </div>
</template>
