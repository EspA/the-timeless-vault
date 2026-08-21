<script setup lang="ts">
withDefaults(defineProps<{
  open: boolean;
  title: string;
  message: string;
  percent?: number | null;
}>(), {
  percent: null,
});
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop progress-backdrop">
      <div class="modal card grid" role="dialog" aria-modal="true" aria-labelledby="progress-title">
        <h3 id="progress-title">{{ title }}</h3>
        <div
          class="loading-bar"
          :class="{ determinate: percent != null }"
          aria-live="polite"
          :aria-label="title"
        >
          <span :style="percent != null ? { width: `${Math.min(100, Math.max(0, percent))}%` } : undefined"></span>
        </div>
        <p class="muted">{{ message }}</p>
      </div>
    </div>
  </Teleport>
</template>
