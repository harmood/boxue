<template>
  <div>
    <h2>系统概览</h2>
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">用户</div><div class="value">0</div></el-card></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">题库</div><div class="value">0</div></el-card></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">同步成功率</div><div class="value">--</div></el-card></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">API P95</div><div class="value">--</div></el-card></el-col>
    </el-row>
    <el-card class="panel">
      <template #header>监控接入</template>
      <p>后端接入 Spring Boot Actuator 和 Prometheus 后，这里将展示同步、任务队列、数据库和存储指标。</p>
    </el-card>
  </div>
</template>

<style scoped>
h2 { margin-top: 0; }
.label { color: #64748b; font-size: 13px; }
.value { margin-top: 8px; font-size: 30px; font-weight: 800; }
.panel { margin-top: 16px; }
</style>