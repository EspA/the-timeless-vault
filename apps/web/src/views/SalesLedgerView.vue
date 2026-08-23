<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { api, type LedgerSale, type SalesLedgerPage } from "../api";

const items = ref<LedgerSale[]>([]);
const fetchedAt = ref<string | undefined>();
const error = ref("");
const loading = ref(false);
const loaded = ref(false);
const narrow = ref(false);
const hoverKey = ref<string | null>(null);
const selectedYear = ref(new Date().getFullYear());
const filters = ref({
  date: "",
  kind: "",
  item: "",
});

const money = (value: number | null | undefined, currency = "USD", digits = 2) => {
  if (value == null || Number.isNaN(Number(value))) return "—";
  const amount = `$${Number(value).toFixed(digits)}`;
  return currency && currency !== "USD" ? `${amount} ${currency}` : amount;
};

const percent = (numerator: number | null | undefined, denominator: number | null | undefined) => {
  const top = Number(numerator);
  const bottom = Number(denominator);
  if (!Number.isFinite(top) || !Number.isFinite(bottom) || bottom === 0) return "—";
  return `${((top / bottom) * 100).toFixed(1)}%`;
};

const margin = (profit: number | null | undefined, revenue: number | null | undefined) =>
  percent(profit, revenue);

const roi = (profit: number | null | undefined, cost: number | null | undefined) =>
  percent(profit, cost);

const saleMargin = (row: LedgerSale) => margin(row.profit, row.salePriceUnit);
const saleRoi = (row: LedgerSale) => roi(row.profit, row.buyPrice);

const dateLabel = (value?: string) => {
  if (!value) return "—";
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
};

const itemLabel = (row: LedgerSale) => {
  const number = row.itemNumber || "";
  const name = row.name || "";
  return [number, name].filter(Boolean).join(" · ") || "—";
};

const contains = (value: string | number | null | undefined, needle: string) => {
  if (!needle.trim()) return true;
  return String(value ?? "").toLowerCase().includes(needle.trim().toLowerCase());
};

const saleYear = (row: LedgerSale) => {
  const year = Number((row.saleDate || "").slice(0, 4));
  return Number.isInteger(year) && year >= 1990 ? year : 0;
};

const years = computed(() => {
  const now = new Date().getFullYear();
  const fromData = items.value.map(saleYear).filter((year) => year > 0);
  const min = fromData.length ? Math.min(...fromData, now) : now;
  const max = fromData.length ? Math.max(...fromData, now) : now;
  const list: number[] = [];
  for (let year = max; year >= min; year -= 1) list.push(year);
  return list;
});

const yearItems = computed(() =>
  items.value.filter((row) => saleYear(row) === selectedYear.value)
);

const yearTotal = computed(() => yearItems.value.length);
const yearRevenue = computed(() =>
  yearItems.value.reduce((sum, row) => sum + Number(row.salePriceTotal || 0), 0)
);
const yearProfit = computed(() =>
  yearItems.value.reduce((sum, row) => sum + Number(row.profit || 0), 0)
);
const yearCost = computed(() =>
  yearItems.value.reduce((sum, row) => sum + Number(row.buyPrice || 0), 0)
);

const visible = computed(() =>
  yearItems.value.filter((row) =>
    contains(dateLabel(row.saleDate), filters.value.date)
    && (!filters.value.kind || row.kind === filters.value.kind)
    && contains(itemLabel(row), filters.value.item)
  )
);

const monthOnlyLabel = (value: string) => {
  const date = new Date(`${value}-01T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(undefined, { month: "short" });
};

const monthLabel = (value: string) => {
  const date = new Date(`${value}-01T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(undefined, { month: "long" });
};

type Bucket = { key: string; label: string; sales: number; revenue: number; profit: number; cost: number };

const addToBucket = (buckets: Map<string, Bucket>, key: string, label: string, row: LedgerSale) => {
  const current = buckets.get(key) || { key, label, sales: 0, revenue: 0, profit: 0, cost: 0 };
  current.sales += 1;
  current.revenue += Number(row.salePriceTotal || 0);
  current.profit += Number(row.profit || 0);
  current.cost += Number(row.buyPrice || 0);
  buckets.set(key, current);
};

const months = computed(() => {
  const buckets = new Map<string, Bucket>();
  for (const row of yearItems.value) {
    const month = (row.saleDate || "").slice(0, 7);
    if (!month) continue;
    addToBucket(buckets, month, monthLabel(month), row);
  }
  return [...buckets.values()].sort((a, b) => b.key.localeCompare(a.key));
});

const monthsAsc = computed(() => {
  const byKey = new Map(months.value.map((row) => [row.key, row]));
  const year = selectedYear.value;
  return Array.from({ length: 12 }, (_, index) => {
    const key = `${year}-${String(index + 1).padStart(2, "0")}`;
    const current = byKey.get(key);
    return {
      key,
      label: monthOnlyLabel(key),
      sales: current?.sales || 0,
      revenue: current?.revenue || 0,
      profit: current?.profit || 0,
      cost: current?.cost || 0,
    };
  });
});

const axisMoney = (value: number) => {
  const rounded = Math.round(value);
  const abs = Math.abs(rounded);
  const sign = rounded < 0 ? "-" : "";
  if (abs >= 10_000) return `${sign}$${Math.round(abs / 1000)}k`;
  if (abs >= 1000) return `${sign}$${(abs / 1000).toFixed(1).replace(/\.0$/, "")}k`;
  return `${sign}$${abs.toLocaleString()}`;
};

const layout = (count: number, extra?: { left?: number; top?: number }) => {
  const pad = { top: extra?.top ?? 16, right: 16, bottom: 28, left: extra?.left ?? 40 };
  return {
    width: 720,
    height: 220,
    pad,
    innerW: 720 - pad.left - pad.right,
    innerH: 220 - pad.top - pad.bottom,
    count,
  };
};

const xLabelsFor = (
  series: Bucket[],
  x: (index: number) => number
) => {
  const maxLabels = narrow.value ? 4 : 8;
  const step = Math.max(1, Math.ceil(series.length / maxLabels));
  return series
    .map((point, index) => ({ x: x(index), label: point.label, key: point.key, index }))
    .filter((point) => point.index === 0 || point.index === series.length - 1 || point.index % step === 0);
};

const barChart = computed(() => {
  const series = monthsAsc.value;
  const box = layout(series.length, { top: 24 });
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((part) => (box.pad.top + box.innerH - part * box.innerH).toFixed(1));
  if (!series.length) {
    return { ...box, maxSales: 1, bars: [] as Array<{ x: number; y: number; width: number; height: number; key: string; sales: number; showCount: boolean; labelX: number; labelY: number }>, ticks, xLabels: [] as Array<{ x: number; label: string; key: string }> };
  }
  const maxSales = Math.max(1, ...series.map((point) => point.sales));
  const slot = box.innerW / series.length;
  const width = Math.max(2, slot * 0.72);
  const x = (index: number) => box.pad.left + index * slot + (slot - width) / 2;
  const showCounts = (!narrow.value || series.length <= 10);
  const bars = series.map((point, index) => {
    const height = (point.sales / maxSales) * box.innerH;
    const y = box.pad.top + box.innerH - height;
    return {
      key: point.key,
      sales: point.sales,
      showCount: showCounts && point.sales > 0,
      x: x(index),
      y,
      width,
      height,
      labelX: x(index) + width / 2,
      labelY: y < 14 ? y + 12 : y - 4,
    };
  });
  return {
    ...box,
    maxSales,
    bars,
    ticks,
    xLabels: xLabelsFor(series, (index) => x(index) + width / 2),
  };
});

type AreaDot = {
  key: string;
  label: string;
  x: number;
  revenueY: number;
  profitY: number;
  revenue: number;
  profit: number;
  revenueLabelY: number;
  profitLabelY: number;
  anchor: "start" | "middle" | "end";
};

const areaChart = computed(() => {
  const series = monthsAsc.value;
  const box = layout(series.length, { left: 56, top: 24 });
  const empty = {
    ...box,
    revenue: "",
    profit: "",
    revenueArea: "",
    profitArea: "",
    yTicks: [] as Array<{ y: string; label: string }>,
    xLabels: [] as Array<{ x: number; label: string; key: string }>,
    dots: [] as AreaDot[],
  };
  if (!series.length) return empty;
  const moneyValues = series.flatMap((point) => [point.revenue, point.profit]);
  const minMoney = Math.min(0, ...moneyValues);
  const maxMoney = Math.max(1, ...moneyValues);
  const moneySpan = Math.max(1, maxMoney - minMoney);
  const x = (index: number) =>
    box.pad.left + (series.length === 1 ? box.innerW / 2 : (index / (series.length - 1)) * box.innerW);
  const y = (value: number) => box.pad.top + box.innerH - ((value - minMoney) / moneySpan) * box.innerH;
  const line = (key: "revenue" | "profit") =>
    series.map((point, index) => `${index === 0 ? "M" : "L"} ${x(index).toFixed(1)} ${y(point[key]).toFixed(1)}`).join(" ");
  const area = (key: "revenue" | "profit") => {
    const path = line(key);
    if (!path) return "";
    const zero = y(0).toFixed(1);
    return `${path} L ${x(series.length - 1).toFixed(1)} ${zero} L ${x(0).toFixed(1)} ${zero} Z`;
  };
  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((part) => {
    const value = minMoney + part * moneySpan;
    return { y: y(value).toFixed(1), label: axisMoney(value) };
  });
  const dots = series.map((point, index) => {
    const cx = x(index);
    const revenueY = y(point.revenue);
    const profitY = y(point.profit);
    const collide = Math.abs(revenueY - profitY) < 16;
    let revenueLabelY = revenueY - 9;
    let profitLabelY = profitY - 9;
    if (revenueLabelY < 12) revenueLabelY = revenueY + 14;
    if (profitLabelY < 12) profitLabelY = profitY + 14;
    if (collide) {
      if (revenueY <= profitY) profitLabelY = profitY + 14;
      else revenueLabelY = revenueY + 14;
    }
    const anchor = index === 0 ? "start" : index === series.length - 1 ? "end" : "middle";
    return {
      key: point.key,
      label: point.label,
      x: cx,
      revenueY,
      profitY,
      revenue: point.revenue,
      profit: point.profit,
      revenueLabelY,
      profitLabelY,
      anchor,
    };
  });
  return {
    ...box,
    revenue: line("revenue"),
    profit: line("profit"),
    revenueArea: area("revenue"),
    profitArea: area("profit"),
    yTicks,
    xLabels: xLabelsFor(series, x),
    dots,
  };
});

const hoveredDot = computed(() =>
  areaChart.value.dots.find((dot) => dot.key === hoverKey.value) || null
);

const chartPoint = (event: MouseEvent | PointerEvent) => {
  const svg = event.currentTarget as SVGSVGElement;
  const ctm = svg.getScreenCTM();
  if (!ctm) return null;
  const pt = svg.createSVGPoint();
  pt.x = event.clientX;
  pt.y = event.clientY;
  return pt.matrixTransform(ctm.inverse());
};

const onAreaMove = (event: MouseEvent | PointerEvent) => {
  const loc = chartPoint(event);
  const dots = areaChart.value.dots;
  if (!loc || !dots.length) return;
  let best = dots[0];
  let bestDist = Math.abs(dots[0].x - loc.x);
  for (const dot of dots) {
    const dist = Math.abs(dot.x - loc.x);
    if (dist < bestDist) {
      best = dot;
      bestDist = dist;
    }
  }
  hoverKey.value = best.key;
};

const onAreaLeave = () => {
  hoverKey.value = null;
};

const fetchedLabel = computed(() => {
  if (!fetchedAt.value) return "Loaded from BrickEconomy";
  const date = new Date(fetchedAt.value);
  return Number.isNaN(date.getTime())
    ? "Loaded from BrickEconomy"
    : `BrickEconomy · ${date.toLocaleString()}`;
});

const load = async (refresh = false) => {
  loading.value = true;
  error.value = "";
  try {
    const result = await api.get<SalesLedgerPage>(`/api/sales-ledger${refresh ? "?refresh=true" : ""}`);
    items.value = result.items || [];
    fetchedAt.value = result.fetchedAt;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
    loaded.value = true;
  }
};

watch(selectedYear, () => {
  hoverKey.value = null;
});

let mediaQuery: MediaQueryList | undefined;
const syncNarrow = () => {
  if (mediaQuery) narrow.value = mediaQuery.matches;
};

onMounted(() => {
  mediaQuery = window.matchMedia("(max-width: 900px)");
  syncNarrow();
  mediaQuery.addEventListener("change", syncNarrow);
  void load();
});

onUnmounted(() => {
  mediaQuery?.removeEventListener("change", syncNarrow);
});
</script>

<template>
  <div class="grid ledger-page">
    <div class="page-head ledger-toolbar">
      <div class="page-title-row">
        <h1>Sales Ledger</h1>
        <p class="muted page-meta">{{ fetchedLabel }}</p>
      </div>
      <div class="pager-actions">
        <label class="ledger-year">Year
          <select v-model.number="selectedYear">
            <option v-for="year in years" :key="year" :value="year">{{ year }}</option>
          </select>
        </label>
        <button class="btn" type="button" :disabled="loading" @click="load(true)">
          {{ loading ? "Loading…" : "Refresh" }}
        </button>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loaded" class="ledger-stats">
      <div>
        <p class="muted">Sales</p>
        <p class="ledger-stat">{{ yearTotal }}</p>
      </div>
      <div>
        <p class="muted">Revenue</p>
        <p class="ledger-stat">{{ money(yearRevenue, "USD", 0) }}</p>
      </div>
      <div>
        <p class="muted">Net Profit</p>
        <p class="ledger-stat" :class="{ negative: yearProfit < 0 }">{{ money(yearProfit, "USD", 0) }}</p>
      </div>
      <div>
        <p class="muted">Net Profit margin</p>
        <p class="ledger-stat" :class="{ negative: yearProfit < 0 }">{{ margin(yearProfit, yearRevenue) }}</p>
      </div>
      <div>
        <p class="muted">ROI</p>
        <p class="ledger-stat" :class="{ negative: yearProfit < 0 }">{{ roi(yearProfit, yearCost) }}</p>
      </div>
    </div>

    <p v-if="loaded && !yearItems.length" class="muted">No BrickEconomy sales in {{ selectedYear }}.</p>

    <div v-if="loaded" class="card ledger-chart-card">
      <p class="muted">Sales count by month</p>
      <div class="ledger-chart-scroll">
        <svg class="ledger-chart" :viewBox="`0 0 ${barChart.width} ${barChart.height}`" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Sales count by month">
        <line
          v-for="tick in barChart.ticks"
          :key="tick"
          :x1="barChart.pad.left"
          :x2="barChart.width - barChart.pad.right"
          :y1="tick"
          :y2="tick"
        />
        <rect
          v-for="bar in barChart.bars"
          :key="bar.key"
          class="ledger-bar"
          :x="bar.x"
          :y="bar.y"
          :width="bar.width"
          :height="bar.height"
        />
        <text
          v-for="bar in barChart.bars"
          v-show="bar.showCount"
          :key="`${bar.key}-n`"
          class="ledger-bar-count"
          :x="bar.labelX"
          :y="bar.labelY"
          text-anchor="middle"
        >{{ bar.sales }}</text>
        <text
          v-for="label in barChart.xLabels"
          :key="label.key"
          :x="label.x"
          :y="barChart.height - 8"
          text-anchor="middle"
        >{{ label.label }}</text>
      </svg>
      </div>
    </div>

    <div v-if="loaded" class="card ledger-chart-card">
      <p class="muted">Revenue and profit by month</p>
      <div class="ledger-chart-scroll">
        <svg
          class="ledger-chart ledger-chart-hover"
          :viewBox="`0 0 ${areaChart.width} ${areaChart.height}`"
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label="Revenue and profit by month"
          @mousemove="onAreaMove"
          @mouseleave="onAreaLeave"
        >
        <line
          v-for="tick in areaChart.yTicks"
          :key="tick.y"
          :x1="areaChart.pad.left"
          :x2="areaChart.width - areaChart.pad.right"
          :y1="tick.y"
          :y2="tick.y"
        />
        <text
          v-for="tick in areaChart.yTicks"
          :key="`${tick.y}-l`"
          class="ledger-axis"
          :x="areaChart.pad.left - 6"
          :y="tick.y"
          text-anchor="end"
          dominant-baseline="middle"
        >{{ tick.label }}</text>
        <path v-if="areaChart.revenueArea" :d="areaChart.revenueArea" class="series-revenue-area" />
        <path v-if="areaChart.profitArea" :d="areaChart.profitArea" class="series-profit-area" />
        <path v-if="areaChart.revenue" :d="areaChart.revenue" class="series-revenue" />
        <path v-if="areaChart.profit" :d="areaChart.profit" class="series-profit" />
        <g v-if="hoveredDot" class="ledger-hover" pointer-events="none">
          <line
            :x1="hoveredDot.x"
            :x2="hoveredDot.x"
            :y1="areaChart.pad.top"
            :y2="areaChart.height - areaChart.pad.bottom"
          />
        </g>
        <circle
          v-for="dot in areaChart.dots"
          :key="`${dot.key}-rev`"
          class="series-revenue-dot"
          :class="{ active: hoverKey === dot.key }"
          :cx="dot.x"
          :cy="dot.revenueY"
          :r="hoverKey === dot.key ? 5.5 : 3.2"
        />
        <circle
          v-for="dot in areaChart.dots"
          :key="`${dot.key}-profit`"
          class="series-profit-dot"
          :class="{ active: hoverKey === dot.key }"
          :cx="dot.x"
          :cy="dot.profitY"
          :r="hoverKey === dot.key ? 5.5 : 3.2"
        />
        <g v-if="hoveredDot" class="ledger-hover-values" pointer-events="none">
          <text
            class="ledger-dot-value series-revenue-value"
            :x="hoveredDot.x"
            :y="hoveredDot.revenueLabelY"
            :text-anchor="hoveredDot.anchor"
          >{{ money(hoveredDot.revenue) }}</text>
          <text
            class="ledger-dot-value series-profit-value"
            :x="hoveredDot.x"
            :y="hoveredDot.profitLabelY"
            :text-anchor="hoveredDot.anchor"
          >{{ money(hoveredDot.profit) }}</text>
        </g>
        <text
          v-for="label in areaChart.xLabels"
          :key="label.key"
          :x="label.x"
          :y="areaChart.height - 8"
          text-anchor="middle"
        >{{ label.label }}</text>
      </svg>
      </div>
      <div class="ledger-legend">
        <span><i class="swatch revenue"></i> Revenue</span>
        <span><i class="swatch profit"></i> Profit</span>
      </div>
    </div>

    <div v-if="months.length" class="card">
      <p class="muted">Monthly totals</p>
      <div class="table-scroll ledger-compact desktop-only">
        <table>
          <thead>
            <tr>
              <th>Month</th>
              <th>Sales</th>
              <th>Revenue</th>
              <th>Net Profit</th>
              <th>Net Profit margin</th>
              <th>ROI</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in months" :key="row.key">
              <td>{{ row.label }}</td>
              <td>{{ row.sales }}</td>
              <td>{{ money(row.revenue) }}</td>
              <td :class="{ negative: row.profit < 0 }">{{ money(row.profit) }}</td>
              <td :class="{ negative: row.profit < 0 }">{{ margin(row.profit, row.revenue) }}</td>
              <td :class="{ negative: row.profit < 0 }">{{ roi(row.profit, row.cost) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="row in months" :key="`m-${row.key}`" class="list-card">
          <div class="list-card-row">
            <h3>{{ row.label }}</h3>
            <span class="muted">{{ row.sales }} sales</span>
          </div>
          <div class="list-card-meta">
            <span>Revenue {{ money(row.revenue) }}</span>
            <span :class="{ negative: row.profit < 0 }">Net Profit {{ money(row.profit) }}</span>
            <span :class="{ negative: row.profit < 0 }">{{ margin(row.profit, row.revenue) }}</span>
            <span :class="{ negative: row.profit < 0 }">ROI {{ roi(row.profit, row.cost) }}</span>
          </div>
        </article>
      </div>
    </div>

    <div class="card">
      <div class="mobile-filters mobile-only">
        <label>Search
          <input v-model="filters.item" type="search" placeholder="Set or minifig" />
        </label>
        <label>Type
          <select v-model="filters.kind">
            <option value="">All</option>
            <option value="SET">Set</option>
            <option value="MINIFIG">Minifig</option>
          </select>
        </label>
      </div>
      <div class="table-scroll desktop-only">
        <table>
          <thead>
            <tr>
              <th>Sale date</th>
              <th>Item</th>
              <th>Qty</th>
              <th>Unit price</th>
              <th>Fees</th>
              <th>Buy price</th>
              <th>Net Profit</th>
              <th>Net Profit margin</th>
              <th>ROI</th>
            </tr>
            <tr>
              <th><input v-model="filters.date" class="column-filter" type="search" placeholder="Filter" /></th>
              <th><input v-model="filters.item" class="column-filter" type="search" placeholder="Filter" /></th>
              <th></th>
              <th></th>
              <th></th>
              <th></th>
              <th></th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in visible" :key="`${row.kind}-${row.itemNumber}-${row.saleDate}-${index}`">
              <td>{{ dateLabel(row.saleDate) }}</td>
              <td>{{ itemLabel(row) }}</td>
              <td>{{ row.saleQuantity }}</td>
              <td>{{ money(row.salePriceUnit, row.currency) }}</td>
              <td>{{ money(row.salePriceFees, row.currency) }}</td>
              <td>{{ money(row.buyPrice, row.currency) }}</td>
              <td :class="{ negative: row.profit < 0 }">{{ money(row.profit, row.currency) }}</td>
              <td :class="{ negative: row.profit < 0 }">{{ saleMargin(row) }}</td>
              <td :class="{ negative: row.profit < 0 }">{{ saleRoi(row) }}</td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="9" class="muted">
                {{ loading ? "Loading…" : "No matching BrickEconomy sales." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="(row, index) in visible" :key="`m-${row.kind}-${row.itemNumber}-${row.saleDate}-${index}`" class="list-card">
          <div class="list-card-row">
            <span class="muted">{{ dateLabel(row.saleDate) }}</span>
          </div>
          <h3>{{ itemLabel(row) }}</h3>
          <div class="list-card-prices">
            <span>Qty {{ row.saleQuantity }}</span>
            <span>{{ money(row.salePriceUnit, row.currency) }}</span>
            <span :class="{ negative: row.profit < 0 }">{{ money(row.profit, row.currency) }}</span>
          </div>
          <div class="list-card-meta muted">
            Fees {{ money(row.salePriceFees, row.currency) }}
            · Buy {{ money(row.buyPrice, row.currency) }}
            · Margin {{ saleMargin(row) }}
            · ROI {{ saleRoi(row) }}
          </div>
        </article>
        <p v-if="!visible.length" class="muted">
          {{ loading ? "Loading…" : "No matching BrickEconomy sales." }}
        </p>
      </div>
    </div>
  </div>
</template>
