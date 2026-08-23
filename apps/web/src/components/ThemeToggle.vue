<script setup lang="ts">
import { theme, toggleTheme } from "../theme";

defineProps<{ compact?: boolean }>();

const nextLabel = () => (theme.value === "dark" ? "Switch to light mode" : "Switch to dark mode");
</script>

<template>
  <button
    v-if="compact"
    class="theme-toggle-icon"
    type="button"
    :aria-label="nextLabel()"
    :title="nextLabel()"
    @click="toggleTheme"
  >
    <svg v-if="theme === 'dark'" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M12 4.5a.8.8 0 0 1 .8-.8h.01a.8.8 0 0 1 0 1.6H12a.8.8 0 0 1-.8-.8Zm0 14.2a.8.8 0 0 1 .8.8v.01a.8.8 0 0 1-1.6 0V19.5a.8.8 0 0 1 .8-.8ZM4.5 12a.8.8 0 0 1 .8-.8H5.3a.8.8 0 0 1 0 1.6h-.01A.8.8 0 0 1 4.5 12Zm14.2 0a.8.8 0 0 1 .8-.8h.01a.8.8 0 1 1 0 1.6H19.5a.8.8 0 0 1-.8-.8ZM6.7 6.7a.8.8 0 0 1 1.13 0l.01.01a.8.8 0 1 1-1.14 1.13L6.7 7.83A.8.8 0 0 1 6.7 6.7Zm9.47 9.47a.8.8 0 0 1 1.13 0l.01.01a.8.8 0 0 1-1.14 1.13l-.01-.01a.8.8 0 0 1 0-1.13ZM17.3 6.7a.8.8 0 0 1 0 1.13l-.01.01a.8.8 0 1 1-1.13-1.14l.01-.01a.8.8 0 0 1 1.13 0ZM7.83 16.17a.8.8 0 0 1 0 1.13l-.01.01A.8.8 0 1 1 6.7 16.18l.01-.01a.8.8 0 0 1 1.13 0ZM12 7.4A4.6 4.6 0 1 1 7.4 12 4.6 4.6 0 0 1 12 7.4Z"
      />
    </svg>
    <svg v-else viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"
      />
    </svg>
  </button>
  <label v-else class="switch theme-toggle-switch">
    <input type="checkbox" :checked="theme === 'dark'" @change="toggleTheme" />
    <span class="switch-track" aria-hidden="true"></span>
    <span class="switch-label">{{ theme === "dark" ? "Dark mode" : "Light mode" }}</span>
  </label>
</template>
