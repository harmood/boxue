<script setup lang="ts">
const platform = window.boxue?.platform ?? 'browser'
const electronVersion = window.boxue?.electronVersion ?? 'dev'
</script>

<template>
  <div class="shell">
    <aside>
      <div class="brand">博学</div>
      <nav>
        <a class="active">学习概览</a>
        <a>我的题库</a>
        <a>开始练习</a>
      </nav>
    </aside>
    <main>
      <header>
        <div>
          <h1>博学桌面端</h1>
          <p>本地题库、离线练习和多端同步的桌面入口。</p>
        </div>
        <span class="badge">Electron {{ electronVersion }} · {{ platform }}</span>
      </header>
      <section class="cards">
        <div class="card"><span>题库</span><strong>0</strong></div>
        <div class="card"><span>今日已练</span><strong>0</strong></div>
        <div class="card"><span>待复习</span><strong>0</strong></div>
      </section>
      <section class="panel">
        <h2>骨架状态</h2>
        <p>Electron 主进程、preload 安全桥和 Vue 渲染层已经就绪。</p>
        <p>下一步接入 SQLite、同步队列和练习引擎。</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.shell { display: grid; grid-template-columns: 220px 1fr; min-height: 100vh; }
aside { background: #111827; color: #fff; padding: 24px 16px; }
.brand { font-size: 24px; font-weight: 800; padding: 0 12px 28px; }
nav { display: grid; gap: 6px; }
nav a { padding: 10px 12px; border-radius: 8px; color: #cbd5e1; cursor: pointer; }
nav a.active { background: #1f2937; color: #fff; }
main { padding: 32px; }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
h1 { margin: 0 0 6px; }
p { color: #64748b; }
.badge { padding: 6px 10px; border-radius: 999px; background: #e0f2fe; color: #0369a1; font-size: 12px; }
.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 24px 0; }
.card { padding: 20px; border-radius: 14px; background: #fff; box-shadow: 0 8px 30px rgba(15, 23, 42, .08); }
.card span { display: block; color: #64748b; font-size: 13px; }
.card strong { display: block; margin-top: 8px; font-size: 28px; }
.panel { padding: 24px; border-radius: 14px; background: #fff; box-shadow: 0 8px 30px rgba(15, 23, 42, .08); }
</style>