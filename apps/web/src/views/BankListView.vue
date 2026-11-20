<script setup lang="ts">
import { ref } from 'vue'

const banks = ref([
  { id: 'demo-1', name: '高等数学', questionCount: 0, accuracy: '--', updatedAt: '尚未导入' }
])
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2>我的题库</h2>
        <p class="muted">创建、导入和管理自己的题库。</p>
      </div>
      <el-button type="primary">新建题库</el-button>
    </div>
    <el-table :data="banks" border>
      <el-table-column prop="name" label="题库" min-width="180" />
      <el-table-column prop="questionCount" label="题目数" width="100" />
      <el-table-column prop="accuracy" label="正确率" width="100" />
      <el-table-column prop="updatedAt" label="最近更新" min-width="160" />
    </el-table>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
h2 { margin: 0 0 4px; }
.muted { margin: 0 0 16px; color: #64748b; }
</style>