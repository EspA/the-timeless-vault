import { reactive } from "vue";

export type ConfirmOptions = {
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "danger" | "gold";
};

export const confirmDialog = reactive({
  open: false,
  title: "Delete",
  message: "",
  confirmLabel: "Delete",
  cancelLabel: "Cancel",
  variant: "danger" as "danger" | "gold",
  showCancel: true,
});

let resolveConfirm: ((ok: boolean) => void) | null = null;

export const askConfirm = (message: string, options?: Omit<ConfirmOptions, "message">) => {
  confirmDialog.title = options?.title ?? "Delete";
  confirmDialog.message = message;
  confirmDialog.confirmLabel = options?.confirmLabel ?? "Delete";
  confirmDialog.cancelLabel = options?.cancelLabel ?? "Cancel";
  confirmDialog.variant = options?.variant ?? "danger";
  confirmDialog.showCancel = true;
  confirmDialog.open = true;
  return new Promise<boolean>((resolve) => {
    resolveConfirm = resolve;
  });
};

export const askAlert = (message: string, options?: Omit<ConfirmOptions, "message">) => {
  confirmDialog.title = options?.title ?? "Notice";
  confirmDialog.message = message;
  confirmDialog.confirmLabel = options?.confirmLabel ?? "OK";
  confirmDialog.cancelLabel = options?.cancelLabel ?? "Close";
  confirmDialog.variant = "gold";
  confirmDialog.showCancel = Boolean(options?.cancelLabel);
  confirmDialog.open = true;
  return new Promise<boolean>((resolve) => {
    resolveConfirm = resolve;
  });
};

const STOCK_STATUS_LABELS: Record<string, string> = {
  IN_TRANSIT: "In transit",
  IN_STOCK: "In stock",
  SOLD: "Sold",
};

export const confirmStockStatusChange = (nextStatus: string) => {
  const label = STOCK_STATUS_LABELS[nextStatus] ?? nextStatus;
  const quantityNote = nextStatus === "IN_STOCK"
    ? "Quantity will increase by 1."
    : "Quantity will be set to 0.";
  const listingsNote = nextStatus === "SOLD"
    ? " Active listings on Shopify, eBay, and BrickLink will be deactivated."
    : "";
  return askConfirm(`Switch status to ${label}? ${quantityNote}${listingsNote}`, {
    title: "Change status",
    confirmLabel: "Switch",
    cancelLabel: "Cancel",
    variant: "gold",
  });
};

export const closeConfirm = (ok: boolean) => {
  confirmDialog.open = false;
  resolveConfirm?.(ok);
  resolveConfirm = null;
};
