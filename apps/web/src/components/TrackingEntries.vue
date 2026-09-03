<script setup lang="ts">
import TrackingNumber from "./TrackingNumber.vue";

export type TrackingDraft = {
  trackingNumber: string;
  carrier: string;
};

const props = defineProps<{
  modelValue: TrackingDraft[];
  carriers: { value: string; label: string }[];
  disabled?: boolean;
  defaultCarrier?: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: TrackingDraft[]];
}>();

const rows = () => (props.modelValue.length ? props.modelValue : [{ trackingNumber: "", carrier: props.defaultCarrier || "" }]);

const update = (index: number, patch: Partial<TrackingDraft>) => {
  const next = rows().map((row, i) => (i === index ? { ...row, ...patch } : { ...row }));
  emit("update:modelValue", next);
};

const add = () => {
  emit("update:modelValue", [...rows(), { trackingNumber: "", carrier: props.defaultCarrier || "" }]);
};

const remove = (index: number) => {
  const next = rows().filter((_, i) => i !== index);
  emit("update:modelValue", next);
};
</script>

<template>
  <div class="tracking-entries">
    <div v-for="(row, index) in rows()" :key="index" class="tracking-entry">
      <select
        :value="row.carrier"
        :disabled="disabled"
        @change="update(index, { carrier: ($event.target as HTMLSelectElement).value })"
      >
        <option value="">None</option>
        <option v-for="option in carriers" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <div class="tracking-entry-number">
        <input
          :value="row.trackingNumber"
          class="order-field"
          :disabled="disabled"
          placeholder="Tracking number"
          @input="update(index, { trackingNumber: ($event.target as HTMLInputElement).value })"
        />
        <TrackingNumber v-if="row.trackingNumber.trim()" :tracking="row.trackingNumber" :provider="row.carrier" />
      </div>
      <button
        v-if="!disabled && rows().length > 1"
        class="btn secondary compact"
        type="button"
        title="Remove tracking number"
        @click="remove(index)"
      >
        ×
      </button>
    </div>
    <button v-if="!disabled" class="btn secondary compact" type="button" @click="add">Add tracking number</button>
  </div>
</template>
