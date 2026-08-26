<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { COUNTRIES, countryFlag } from "../countries";

const country = defineModel<string>("country", { default: "" });
const query = ref("");

watch(country, (code) => {
  if (!code) return;
  const match = COUNTRIES.find((row) => row.code === code);
  if (match && query.value !== match.name) {
    query.value = "";
  }
});

const filtered = computed(() => {
  const needle = query.value.trim().toLowerCase();
  const list = !needle
    ? COUNTRIES
    : COUNTRIES.filter((row) =>
        row.name.toLowerCase().includes(needle) || row.code.toLowerCase().includes(needle)
      );
  if (country.value && !list.some((row) => row.code === country.value)) {
    const selected = COUNTRIES.find((row) => row.code === country.value);
    if (selected) return [selected, ...list];
  }
  return list;
});
</script>

<template>
  <label>Country
    <input v-model="query" type="search" placeholder="Search countries" />
    <select v-model="country">
      <option value="">Select a country</option>
      <option v-for="row in filtered" :key="row.code" :value="row.code">
        {{ countryFlag(row.code) }} {{ row.name }}
      </option>
    </select>
  </label>
</template>
