import { createRouter, createWebHistory } from "vue-router";
import InventoryList from "./views/InventoryList.vue";
import ItemNew from "./views/ItemNew.vue";
import ItemDetail from "./views/ItemDetail.vue";
import WatchList from "./views/WatchList.vue";
import WatchNew from "./views/WatchNew.vue";
import WatchDetail from "./views/WatchDetail.vue";
import MarketView from "./views/MarketView.vue";
import AlertsView from "./views/AlertsView.vue";
import ScanLogView from "./views/ScanLogView.vue";
import SettingsView from "./views/SettingsView.vue";
import LoginView from "./views/LoginView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView },
    { path: "/", redirect: "/inventory" },
    { path: "/inventory", component: InventoryList },
    { path: "/inventory/new", component: ItemNew },
    { path: "/inventory/:id", component: ItemDetail },
    { path: "/watches", component: WatchList },
    { path: "/watches/new", component: WatchNew },
    { path: "/watches/:id", component: WatchDetail },
    { path: "/market", component: MarketView },
    { path: "/market/:catalogId", component: MarketView },
    { path: "/alerts", component: AlertsView },
    { path: "/scans", component: ScanLogView },
    { path: "/settings", component: SettingsView },
  ],
});
