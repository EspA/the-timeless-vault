<script setup lang="ts">
import { nextTick, onUnmounted, ref } from "vue";

const props = defineProps<{
  disabled?: boolean;
}>();

const emit = defineEmits<{
  files: [files: File[]];
}>();

const libraryInput = ref<HTMLInputElement | null>(null);
const cameraInput = ref<HTMLInputElement | null>(null);
const video = ref<HTMLVideoElement | null>(null);
const cameraOpen = ref(false);
const cameraError = ref("");
const facingMode = ref<"environment" | "user">("environment");
const shots = ref<{ file: File; url: string }[]>([]);
let stream: MediaStream | null = null;

const canUseLiveCamera = () =>
  typeof navigator !== "undefined"
  && !!navigator.mediaDevices?.getUserMedia
  && (window.isSecureContext || location.hostname === "localhost");

const stopCamera = () => {
  stream?.getTracks().forEach((track) => track.stop());
  stream = null;
  if (video.value) {
    video.value.srcObject = null;
  }
};

const clearShots = () => {
  for (const shot of shots.value) {
    URL.revokeObjectURL(shot.url);
  }
  shots.value = [];
};

const closeCamera = () => {
  stopCamera();
  clearShots();
  cameraOpen.value = false;
  cameraError.value = "";
};

const startCamera = async () => {
  stopCamera();
  cameraError.value = "";
  stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      facingMode: { ideal: facingMode.value },
      width: { ideal: 1920 },
      height: { ideal: 1440 },
    },
  });
  await nextTick();
  if (!video.value) {
    return;
  }
  video.value.srcObject = stream;
  await video.value.play();
};

const openCamera = async () => {
  if (props.disabled) return;
  if (!canUseLiveCamera()) {
    cameraInput.value?.click();
    return;
  }
  cameraOpen.value = true;
  try {
    await startCamera();
  } catch {
    closeCamera();
    cameraInput.value?.click();
  }
};

const flipCamera = async () => {
  facingMode.value = facingMode.value === "environment" ? "user" : "environment";
  try {
    await startCamera();
  } catch (e) {
    cameraError.value = e instanceof Error ? e.message : "Could not switch camera";
  }
};

const capture = async () => {
  const node = video.value;
  if (!node || !node.videoWidth) return;
  const canvas = document.createElement("canvas");
  canvas.width = node.videoWidth;
  canvas.height = node.videoHeight;
  const context = canvas.getContext("2d");
  if (!context) return;
  context.drawImage(node, 0, 0);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.92));
  if (!blob) return;
  const file = new File([blob], `camera-${Date.now()}.jpg`, { type: "image/jpeg" });
  shots.value.push({ file, url: URL.createObjectURL(blob) });
};

const finishCamera = () => {
  const files = shots.value.map((shot) => shot.file);
  stopCamera();
  clearShots();
  cameraOpen.value = false;
  cameraError.value = "";
  if (files.length) {
    emit("files", files);
  }
};

const onInput = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];
  input.value = "";
  if (files.length) {
    emit("files", files);
  }
};

onUnmounted(() => {
  stopCamera();
  clearShots();
});
</script>

<template>
  <div class="photo-actions">
    <button class="btn gold" type="button" :disabled="disabled" @click="openCamera">Take photo</button>
    <button class="btn secondary" type="button" :disabled="disabled" @click="libraryInput?.click()">
      Choose from library
    </button>
    <input
      ref="cameraInput"
      class="photo-file-input"
      type="file"
      accept="image/*"
      capture="environment"
      :disabled="disabled"
      @change="onInput"
    />
    <input
      ref="libraryInput"
      class="photo-file-input"
      type="file"
      accept="image/*"
      multiple
      :disabled="disabled"
      @change="onInput"
    />
  </div>

  <Teleport to="body">
    <div v-if="cameraOpen" class="camera-overlay" role="dialog" aria-modal="true" aria-label="Take photo">
      <video ref="video" class="camera-video" autoplay playsinline muted></video>
      <div class="camera-top">
        <button class="btn secondary compact" type="button" @click="closeCamera">Cancel</button>
        <button class="btn secondary compact" type="button" @click="flipCamera">Flip</button>
      </div>
      <p v-if="cameraError" class="camera-error">{{ cameraError }}</p>
      <div class="camera-shots" v-if="shots.length">
        <img v-for="shot in shots" :key="shot.url" :src="shot.url" alt="Captured photo" />
      </div>
      <div class="camera-bottom">
        <button class="camera-shutter" type="button" aria-label="Capture photo" @click="capture"></button>
        <button class="btn gold" type="button" :disabled="!shots.length" @click="finishCamera">
          {{ shots.length ? `Use ${shots.length} photo${shots.length === 1 ? "" : "s"}` : "Use photos" }}
        </button>
      </div>
    </div>
  </Teleport>
</template>
