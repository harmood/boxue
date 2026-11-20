<template>
  <div>
    <h2>学习概览</h2>
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card><div class="metric-label">题库</div><div class="metric">0</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card><div class="metric-label">今日已练</div><div class="metric">0</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card><div class="metric-label">正确率</div><div class="metric">--</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card><div class="metric-label">待复习</div><div class="metric">0</div></el-card>
      </el-col>
    </el-row>
    <el-card class="panel">
      <template #header>下一步</template>
      <p>先创建题库并添加一道带公式和图片的题目，然后开始第一次练习。</p>
      <el-button type="primary" @click="$router.push('/banks')">去题库</el-button>
    </el-card>
  </div>
</template>

<style scoped>
h2 { margin-top: 0; }
.metric-label { color: #64748b; font-size: 13px; }
.metric { margin-top: 8px; font-size: 30px; font-weight: 800; color: #0f172a; }
.panel { margin-top: 16px; }
</style>