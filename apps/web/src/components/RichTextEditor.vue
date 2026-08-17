<script setup lang="ts">
import { EditorContent, useEditor } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import Link from "@tiptap/extension-link";
import { watch } from "vue";

const props = defineProps<{ modelValue?: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

const editor = useEditor({
  content: props.modelValue || "",
  extensions: [
    StarterKit.configure({ heading: { levels: [2, 3] } }),
    Underline,
    Link.configure({ openOnClick: false, autolink: true }),
  ],
  editorProps: {
    attributes: { class: "rich-editor-content" },
  },
  onUpdate: ({ editor: instance }) => {
    emit("update:modelValue", instance.getHTML());
  },
});

watch(
  () => props.modelValue,
  (value) => {
    if (!editor.value) return;
    const next = value || "";
    if (next !== editor.value.getHTML()) {
      editor.value.commands.setContent(next, { emitUpdate: false });
    }
  }
);

const setLink = () => {
  if (!editor.value) return;
  const previous = editor.value.getAttributes("link").href as string | undefined;
  const url = window.prompt("Link URL", previous || "https://");
  if (url === null) return;
  if (url === "") {
    editor.value.chain().focus().unsetLink().run();
    return;
  }
  editor.value.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
};
</script>

<template>
  <div class="rich-editor" v-if="editor">
    <div class="rich-toolbar">
      <button type="button" :class="{ on: editor.isActive('bold') }" @click="editor.chain().focus().toggleBold().run()">B</button>
      <button type="button" :class="{ on: editor.isActive('italic') }" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button>
      <button type="button" :class="{ on: editor.isActive('underline') }" @click="editor.chain().focus().toggleUnderline().run()"><u>U</u></button>
      <button type="button" :class="{ on: editor.isActive('heading', { level: 2 }) }" @click="editor.chain().focus().toggleHeading({ level: 2 }).run()">H2</button>
      <button type="button" :class="{ on: editor.isActive('bulletList') }" @click="editor.chain().focus().toggleBulletList().run()">• List</button>
      <button type="button" :class="{ on: editor.isActive('orderedList') }" @click="editor.chain().focus().toggleOrderedList().run()">1. List</button>
      <button type="button" :class="{ on: editor.isActive('link') }" @click="setLink">Link</button>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>
