<script setup lang="ts">
defineProps<{
  open: boolean;
  title: string;
  message: string;
}>();
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop">
      <div class="modal card grid" role="dialog" aria-modal="true" aria-labelledby="scan-progress-title">
        <h3 id="scan-progress-title">{{ title }}</h3>
        <div class="loading-bar" aria-live="polite" :aria-label="title">
          <span></span>
        </div>
        <p class="muted">{{ message }}</p>
      </div>
    </div>
  </Teleport>
</template>
