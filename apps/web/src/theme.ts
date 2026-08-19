import { ref } from "vue";

export type Theme = "light" | "dark";

const STORAGE_KEY = "ttv-theme";

export const theme = ref<Theme>(readTheme());

function readTheme(): Theme {
  try {
    return localStorage.getItem(STORAGE_KEY) === "dark" ? "dark" : "light";
  } catch {
    return "light";
  }
}

export function applyTheme(next: Theme) {
  theme.value = next;
  document.documentElement.dataset.theme = next;
  document.documentElement.style.colorScheme = next;
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    // Private browsing can block storage; the in-memory theme still applies.
  }
}

export function initTheme() {
  applyTheme(readTheme());
}
