<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api, type Supplier } from "../api";
import { countryLabel } from "../countries";
import { askConfirm } from "../confirm";
import SupplierModal from "../components/SupplierModal.vue";

const suppliers = ref<Supplier[]>([]);
const error = ref("");
const loading = ref(false);
const editing = ref<Supplier | null | undefined>(undefined);

const search = ref("");
const visible = computed(() => {
  const needle = search.value.trim().toLowerCase();
  if (!needle) return suppliers.value;
  return suppliers.value.filter((row) =>
    [row.name, row.email, row.phone, row.website, row.city, countryLabel(row.country)]
      .filter(Boolean)
      .join(" ")
      .toLowerCase()
      .includes(needle)
  );
});

const load = async () => {
  loading.value = true;
  error.value = "";
  try {
    suppliers.value = await api.get<Supplier[]>("/api/suppliers");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not load suppliers";
  } finally {
    loading.value = false;
  }
};

const websiteHref = (url: string) => (/^https?:\/\//i.test(url) ? url : `https://${url}`);

const remove = async (supplier: Supplier) => {
  if (!(await askConfirm(`Delete supplier "${supplier.name}"?`, { title: "Delete supplier" }))) return;
  error.value = "";
  try {
    await api.del(`/api/suppliers/${supplier.id}`);
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not delete supplier";
  }
};

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="grid">
    <div class="page-head">
      <h1>Suppliers</h1>
      <button class="btn gold" type="button" @click="editing = null">Add supplier</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <label>Search
        <input v-model="search" type="search" placeholder="Name, email, city" />
      </label>
      <div class="table-scroll desktop-only">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Website</th>
              <th>City</th>
              <th>Country</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>{{ row.name }}</td>
              <td>{{ row.email || "—" }}</td>
              <td>{{ row.phone || "—" }}</td>
              <td>
                <a v-if="row.website" :href="websiteHref(row.website)" target="_blank" rel="noopener noreferrer">{{ row.website }}</a>
                <span v-else>—</span>
              </td>
              <td>{{ row.city || "—" }}</td>
              <td>{{ countryLabel(row.country) || "—" }}</td>
              <td>
                <button class="btn secondary compact" type="button" @click="editing = row">Edit</button>
                <button class="btn danger compact" type="button" @click="remove(row)">Delete</button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="7" class="muted">{{ loading ? "Loading…" : "No suppliers yet." }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="list-cards mobile-only">
        <article v-for="row in visible" :key="row.id" class="list-card">
          <h3>{{ row.name }}</h3>
          <p class="muted">{{ [row.email, row.phone, row.city, countryLabel(row.country)].filter(Boolean).join(" · ") || "—" }}</p>
          <div class="list-card-actions">
            <button class="btn secondary compact" type="button" @click="editing = row">Edit</button>
            <button class="btn danger compact" type="button" @click="remove(row)">Delete</button>
          </div>
        </article>
        <p v-if="!visible.length" class="muted">{{ loading ? "Loading…" : "No suppliers yet." }}</p>
      </div>
    </div>
    <SupplierModal
      v-if="editing !== undefined"
      :supplier="editing"
      @close="editing = undefined"
      @saved="editing = undefined; load()"
    />
  </div>
</template>
