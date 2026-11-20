<script setup lang="ts">
import { ref, watch } from 'vue'
import { getMediaObjectUrl } from '../api/media'

const props = defineProps<{
  mediaId: string
  alt?: string
}>()

const src = ref('')

watch(
  () => props.mediaId,
  async (mediaId) => {
    if (!mediaId) {
      src.value = ''
      return
    }
    try {
      src.value = await getMediaObjectUrl(mediaId)
    } catch {
      src.value = ''
    }
  },
  { immediate: true }
)
</script>

<template>
  <img v-if="src" :src="src" :alt="alt || ''" class="media-image" />
  <span v-else class="media-placeholder">图片加载中…</span>
</template>

<style scoped>
.media-image { max-width: 100%; border-radius: 8px; display: block; }
.media-placeholder { display: inline-block; padding: 8px 12px; color: #94a3b8; background: #f8fafc; border-radius: 8px; font-size: 12px; }
</style>
