<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from "vue";
import { closeConfirm, confirmDialog } from "../confirm";

const lines = computed(() => confirmDialog.message.split("\n").filter((line) => line.length));

const onKey = (event: KeyboardEvent) => {
  if (!confirmDialog.open) return;
  if (event.key === "Escape") {
    event.preventDefault();
    closeConfirm(false);
  }
};

onMounted(() => window.addEventListener("keydown", onKey));
onUnmounted(() => window.removeEventListener("keydown", onKey));

watch(
  () => confirmDialog.open,
  (open) => {
    document.body.style.overflow = open ? "hidden" : "";
  }
);
</script>

<template>
  <Teleport to="body">
    <div
      v-if="confirmDialog.open"
      class="modal-backdrop confirm-backdrop"
      @click.self="closeConfirm(false)"
    >
      <div class="modal confirm-modal card grid" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
        <h3 id="confirm-title">{{ confirmDialog.title }}</h3>
        <p v-for="(line, index) in lines" :key="index">{{ line }}</p>
        <div class="confirm-actions">
          <button class="btn secondary" type="button" @click="closeConfirm(false)">Cancel</button>
          <button class="btn danger" type="button" @click="closeConfirm(true)">{{ confirmDialog.confirmLabel }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
