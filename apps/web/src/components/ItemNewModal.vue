<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { api, applyCatalogToDescription, brickLinkShortDescriptionFromHtml, channelPricesFromCost, CONDITIONS, defaultDescriptionHtml, defaultListingTitle, LISTING_TITLE_MAX, minimumOfferFromEbayPrice, type Catalog, type InventoryItem } from "../api";
import RichTextEditor from "./RichTextEditor.vue";
import ShopifyCollectionsField from "./ShopifyCollectionsField.vue";
import EbayStoreCategoryField from "./EbayStoreCategoryField.vue";

const emit = defineEmits<{
  close: [];
  saved: [item: InventoryItem];
}>();

const setNumber = ref("");
const catalog = ref<Catalog | null>(null);
const error = ref("");
const saving = ref(false);
const editorKey = ref(0);
const pendingPhotos = ref<{ file: File; url: string }[]>([]);

const addPhotos = (event: Event) => {
  const files = (event.target as HTMLInputElement).files;
  if (!files) return;
  for (const file of Array.from(files)) {
    pendingPhotos.value.push({ file, url: URL.createObjectURL(file) });
  }
  (event.target as HTMLInputElement).value = "";
};

const removePhoto = (index: number) => {
  URL.revokeObjectURL(pendingPhotos.value[index].url);
  pendingPhotos.value.splice(index, 1);
};

const form = reactive({
  title: "",
  description: defaultDescriptionHtml(),
  shortDescription: brickLinkShortDescriptionFromHtml(defaultDescriptionHtml()),
  ebayPrice: "",
  bricklinkPrice: "",
  shopifyPrice: "",
  quantity: 1,
  cost: "",
  itemType: "SET",
  condition: "NEW_SEALED",
  shopifyCollectionIds: [] as string[],
  ebayStoreCategory: "Other",
  minimumOffer: "",
  packageLbs: 0,
  packageOz: 0,
  packageLength: "",
  packageWidth: "",
  packageHeight: "",
  notes: "",
});

let lastGeneratedShortDescription = form.shortDescription;

watch(
  () => form.description,
  (html) => {
    const generated = brickLinkShortDescriptionFromHtml(html);
    if (form.shortDescription === lastGeneratedShortDescription || form.shortDescription === "") {
      form.shortDescription = generated;
    }
    lastGeneratedShortDescription = generated;
  }
);

watch(
  () => form.cost,
  (cost) => {
    const generated = channelPricesFromCost(cost);
    form.ebayPrice = generated.ebayPrice;
    form.bricklinkPrice = generated.bricklinkPrice;
    form.shopifyPrice = generated.shopifyPrice;
  }
);

watch(
  () => form.ebayPrice,
  (price) => {
    form.minimumOffer = minimumOfferFromEbayPrice(price);
  }
);

const lookup = async (refresh = false) => {
  error.value = "";
  try {
    catalog.value = await api.get<Catalog>(`/api/catalog/lookup?setNumber=${encodeURIComponent(setNumber.value)}&refresh=${refresh}`);
    form.title = defaultListingTitle(catalog.value);
    form.description = applyCatalogToDescription(form.description, catalog.value);
    editorKey.value += 1;
    form.ebayStoreCategory = catalog.value.suggestedEbayStoreCategory;
    const pkg = catalog.value.bricklinkPackage?.shipping;
    if (pkg) {
      form.packageLbs = pkg.lbs ?? 0;
      form.packageOz = 0;
      form.packageLength = pkg.length != null ? String(pkg.length) : "";
      form.packageWidth = pkg.width != null ? String(pkg.width) : "";
      form.packageHeight = pkg.height != null ? String(pkg.height) : "";
    }
  } catch (e) {
    error.value = (e as Error).message;
  }
};

const save = async () => {
  error.value = "";
  saving.value = true;
  try {
    const created = await api.post<InventoryItem>("/api/inventory", {
      setNumber: setNumber.value,
      title: form.title,
      description: form.description,
      shortDescription: form.shortDescription,
      ebayPrice: Number(form.ebayPrice),
      bricklinkPrice: Number(form.bricklinkPrice),
      shopifyPrice: Number(form.shopifyPrice),
      quantity: Number(form.quantity),
      cost: form.cost ? Number(form.cost) : null,
      itemType: form.itemType,
      condition: form.condition,
      shopifyCollectionIds: form.shopifyCollectionIds,
      ebayStoreCategory: form.ebayStoreCategory,
      minimumOffer: form.minimumOffer ? Number(form.minimumOffer) : null,
      packageLbs: Number(form.packageLbs),
      packageOz: Number(form.packageOz),
      packageLength: form.packageLength ? Number(form.packageLength) : null,
      packageWidth: form.packageWidth ? Number(form.packageWidth) : null,
      packageHeight: form.packageHeight ? Number(form.packageHeight) : null,
      notes: form.notes,
    });
    for (const photo of pendingPhotos.value) {
      const data = new FormData();
      data.append("file", photo.file);
      await api.post(`/api/inventory/${created.id}/photos`, data);
    }
    emit("saved", created);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
};

const onKey = (event: KeyboardEvent) => {
  if (event.key === "Escape" && !saving.value) {
    event.preventDefault();
    emit("close");
  }
};

onMounted(() => {
  document.body.style.overflow = "hidden";
  window.addEventListener("keydown", onKey);
});

onUnmounted(() => {
  document.body.style.overflow = "";
  window.removeEventListener("keydown", onKey);
  for (const photo of pendingPhotos.value) {
    URL.revokeObjectURL(photo.url);
  }
});
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop">
      <form
        class="modal form-modal card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="item-new-title"
        @submit.prevent="save"
      >
        <div class="form-modal-head">
          <h2 id="item-new-title">New inventory item</h2>
          <button class="btn secondary compact" type="button" :disabled="saving" @click="emit('close')">
            Cancel
          </button>
        </div>

        <div class="form-modal-body grid">
          <div class="card grid">
            <label>LEGO set number
              <div class="form-modal-lookup">
                <input v-model="setNumber" placeholder="10236-1" />
                <button class="btn gold" type="button" @click="lookup(false)">Look up</button>
                <button class="btn secondary" type="button" @click="lookup(true)">Refresh</button>
              </div>
            </label>
            <p v-if="error" class="error">{{ error }}</p>
          </div>

          <div class="card grid">
            <label>Title
              <input v-model="form.title" required :maxlength="LISTING_TITLE_MAX" />
              <span class="muted">{{ form.title.length }}/{{ LISTING_TITLE_MAX }}</span>
            </label>
            <label>Description
              <RichTextEditor :key="editorKey" v-model="form.description" />
            </label>
            <label>Short description (BrickLink)
              <textarea class="short-description" v-model="form.shortDescription" maxlength="255" rows="2" />
              <span class="muted">{{ form.shortDescription.length }}/255</span>
            </label>
          </div>

          <div class="card grid">
            <div class="grid three">
              <label>eBay price (default 45% margin) <input v-model="form.ebayPrice" type="number" step="0.01" required /></label>
              <label>BrickLink price (default 40% margin) <input v-model="form.bricklinkPrice" type="number" step="0.01" required /></label>
              <label>Shopify price (default 32% margin) <input v-model="form.shopifyPrice" type="number" step="0.01" required /></label>
            </div>
            <div class="grid three">
              <label>Cost <input v-model="form.cost" type="number" step="0.01" /></label>
              <label>Quantity <input v-model="form.quantity" type="number" min="1" /></label>
              <label>eBay Minimum offer (default 90% eBay price) <input v-model="form.minimumOffer" type="number" step="0.01" /></label>
            </div>
            <div class="grid four">
              <label>Condition
                <select v-model="form.condition">
                  <option v-for="c in CONDITIONS" :key="c" :value="c">{{ c }}</option>
                </select>
              </label>
              <label>Shopify Product Type
                <select v-model="form.itemType">
                  <option>SET</option>
                  <option>POLYBAG</option>
                </select>
              </label>
              <EbayStoreCategoryField v-model="form.ebayStoreCategory" />
              <ShopifyCollectionsField v-model="form.shopifyCollectionIds" default-title="The collection" />
            </div>
          </div>

          <div class="card grid">
            <h3>Shipping Dimensions and Weight</h3>
            <p v-if="catalog?.bricklinkPackage?.original" class="muted">
              BrickLink original:
              {{ catalog.bricklinkPackage.original.lbs }} lb
              {{ catalog.bricklinkPackage.original.oz }} oz
              ·
              {{ catalog.bricklinkPackage.original.length }} ×
              {{ catalog.bricklinkPackage.original.width }} ×
              {{ catalog.bricklinkPackage.original.height }} in
            </p>
            <div class="grid five">
              <label>lbs <input v-model="form.packageLbs" type="number" /></label>
              <label>oz <input v-model="form.packageOz" type="number" /></label>
              <label>L <input v-model="form.packageLength" type="number" step="0.1" /></label>
              <label>W <input v-model="form.packageWidth" type="number" step="0.1" /></label>
              <label>H <input v-model="form.packageHeight" type="number" step="0.1" /></label>
            </div>
          </div>

          <div class="card grid">
            <h3>Photos</h3>
            <p class="muted">Add listing photos now. The first one is used as the BrickLink photo.</p>
            <input type="file" accept="image/*" multiple @change="addPhotos" />
            <div v-if="pendingPhotos.length" class="photos" style="margin-top:0.75rem">
              <div v-for="(photo, index) in pendingPhotos" :key="photo.url" class="photo-tile">
                <img
                  :src="photo.url"
                  :alt="photo.file.name"
                  :class="{ primary: index === 0 }"
                />
                <button
                  class="photo-delete"
                  type="button"
                  title="Remove photo"
                  @click="removePhoto(index)"
                >
                  ×
                </button>
              </div>
            </div>
            <p v-if="pendingPhotos.length" class="muted">The first photo is used as the BrickLink photo. Use × to remove one.</p>
          </div>

          <div class="card grid">
            <label>Notes <textarea v-model="form.notes" /></label>
          </div>
        </div>

        <div class="form-modal-foot">
          <button class="btn secondary" type="button" :disabled="saving" @click="emit('close')">Cancel</button>
          <button class="btn gold" type="submit" :disabled="!catalog || saving">
            {{ saving ? "Saving…" : "Save to vault" }}
          </button>
        </div>
      </form>
    </div>
  </Teleport>
</template>
