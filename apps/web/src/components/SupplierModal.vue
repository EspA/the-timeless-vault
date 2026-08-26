<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from "vue";
import { api, type Supplier } from "../api";
import CountrySelect from "./CountrySelect.vue";

const props = defineProps<{
  supplier?: Supplier | null;
}>();

const emit = defineEmits<{
  close: [];
  saved: [supplier: Supplier];
}>();

const error = ref("");
const saving = ref(false);
const form = reactive({
  name: props.supplier?.name || "",
  email: props.supplier?.email || "",
  phone: props.supplier?.phone || "",
  website: props.supplier?.website || "",
  street: props.supplier?.street || "",
  city: props.supplier?.city || "",
  zip: props.supplier?.zip || "",
  country: props.supplier?.country || "",
});

const onKey = (event: KeyboardEvent) => {
  if (event.key === "Escape" && !saving.value) emit("close");
};

onMounted(() => {
  document.body.style.overflow = "hidden";
  window.addEventListener("keydown", onKey);
});

onUnmounted(() => {
  document.body.style.overflow = "";
  window.removeEventListener("keydown", onKey);
});

const save = async () => {
  error.value = "";
  saving.value = true;
  try {
    const body = { ...form };
    const saved = props.supplier
      ? await api.put<Supplier>(`/api/suppliers/${props.supplier.id}`, body)
      : await api.post<Supplier>("/api/suppliers", body);
    emit("saved", saved);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Could not save supplier";
  } finally {
    saving.value = false;
  }
};
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop">
      <form class="modal form-modal card" role="dialog" aria-modal="true" @submit.prevent="save">
        <div class="form-modal-head">
          <h2>{{ supplier ? "Edit supplier" : "New supplier" }}</h2>
          <button class="btn secondary compact" type="button" :disabled="saving" @click="emit('close')">Cancel</button>
        </div>
        <div class="form-modal-body grid">
          <p v-if="error" class="error">{{ error }}</p>
          <label>Name
            <input v-model="form.name" required maxlength="255" />
          </label>
          <div class="grid two">
            <label>Email
              <input v-model="form.email" type="email" />
            </label>
            <label>Phone
              <input v-model="form.phone" />
            </label>
          </div>
          <label>Website
            <input v-model="form.website" placeholder="https://" />
          </label>
          <label>Street
            <input v-model="form.street" />
          </label>
          <div class="grid two">
            <label>City
              <input v-model="form.city" />
            </label>
            <label>Zip code
              <input v-model="form.zip" />
            </label>
          </div>
          <CountrySelect v-model:country="form.country" />
        </div>
        <div class="form-modal-foot">
          <button class="btn gold" type="submit" :disabled="saving">{{ saving ? "Saving…" : "Save" }}</button>
        </div>
      </form>
    </div>
  </Teleport>
</template>
