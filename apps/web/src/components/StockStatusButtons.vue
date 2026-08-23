<script setup lang="ts">
import { computed } from "vue";
import { STOCK_STATUSES } from "../api";

const props = withDefaults(defineProps<{
  modelValue?: string;
  disabled?: boolean;
  compact?: boolean;
  options?: readonly { value: string; label: string; shortLabel?: string }[];
  ariaLabel?: string;
}>(), {
  disabled: false,
  compact: false,
  ariaLabel: "Stock status",
});

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const statuses = computed(() => props.options ?? STOCK_STATUSES);

const select = (value: string, current?: string, disabled?: boolean) => {
  if (disabled || value === current) return;
  emit("update:modelValue", value);
};
</script>

<template>
  <div class="stock-status" :class="{ compact }" role="group" :aria-label="ariaLabel">
    <button
      v-for="status in statuses"
      :key="status.value"
      type="button"
      :class="['stock-status-btn', status.value.toLowerCase(), { active: modelValue === status.value }]"
      :disabled="disabled"
      :aria-pressed="modelValue === status.value"
      @click="select(status.value, modelValue, disabled)"
    >
      {{ compact ? (status.shortLabel || status.label) : status.label }}
    </button>
  </div>
</template>
