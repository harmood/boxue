<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<template>
  <div class="boxue-brand">
    <span class="mark">博</span>
    <span v-if="!compact" class="name">博学</span>
  </div>
</template>

<style scoped>
.boxue-brand { display: inline-flex; align-items: center; gap: 10px; font-weight: 700; }
.mark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: #409eff; color: #fff; }
.name { font-size: 20px; }
</style>