import { createRouter, createWebHistory } from "vue-router";
import InventoryList from "./views/InventoryList.vue";
import ItemDetail from "./views/ItemDetail.vue";
import WatchList from "./views/WatchList.vue";
import WatchDetail from "./views/WatchDetail.vue";
import MarketView from "./views/MarketView.vue";
import AlertsView from "./views/AlertsView.vue";
import ScanLogsView from "./views/ScanLogsView.vue";
import ListingLogsView from "./views/ListingLogsView.vue";
import SalesView from "./views/SalesView.vue";
import SalesLedgerView from "./views/SalesLedgerView.vue";
import SettingsView from "./views/SettingsView.vue";
import LoginView from "./views/LoginView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView },
    { path: "/", redirect: "/inventory" },
    { path: "/inventory", component: InventoryList },
    { path: "/inventory/new", redirect: "/inventory" },
    { path: "/inventory/:id", component: ItemDetail },
    { path: "/listing-logs", component: ListingLogsView },
    { path: "/sales", component: SalesView },
    { path: "/sales-ledger", component: SalesLedgerView },
    { path: "/watches", component: WatchList },
    { path: "/watches/new", redirect: "/watches" },
    { path: "/watches/:id", component: WatchDetail },
    { path: "/market", component: MarketView },
    { path: "/market/:catalogId", component: MarketView },
    { path: "/alerts", component: AlertsView },
    { path: "/buying-opportunities", redirect: "/alerts" },
    { path: "/scans", redirect: "/scan-logs" },
    { path: "/scan-logs", component: ScanLogsView },
    { path: "/settings", component: SettingsView },
  ],
});
