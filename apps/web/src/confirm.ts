import { reactive } from "vue";

export type ConfirmOptions = {
  title?: string;
  message: string;
  confirmLabel?: string;
};

export const confirmDialog = reactive({
  open: false,
  title: "Delete",
  message: "",
  confirmLabel: "Delete",
});

let resolveConfirm: ((ok: boolean) => void) | null = null;

export const askConfirm = (message: string, options?: Omit<ConfirmOptions, "message">) => {
  confirmDialog.title = options?.title ?? "Delete";
  confirmDialog.message = message;
  confirmDialog.confirmLabel = options?.confirmLabel ?? "Delete";
  confirmDialog.open = true;
  return new Promise<boolean>((resolve) => {
    resolveConfirm = resolve;
  });
};

export const closeConfirm = (ok: boolean) => {
  confirmDialog.open = false;
  resolveConfirm?.(ok);
  resolveConfirm = null;
};
