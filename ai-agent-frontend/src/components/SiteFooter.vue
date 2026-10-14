<template>
  <footer class="site-footer" role="contentinfo">
    <p>Copyright © {{ year }} AI Agent · 健康信息仅供参考，不能代替诊疗</p>
  </footer>
</template>

<script setup>
const year = new Date().getFullYear()
</script>

<style scoped>
.site-footer {
  flex-shrink: 0;
  border-top: 1px solid var(--border);
  background: rgba(255, 253, 249, 0.8);
  padding:
    12px max(16px, var(--safe-right))
    calc(12px + var(--safe-bottom))
    max(16px, var(--safe-left));
  text-align: center;
  font-size: 0.78rem;
  color: var(--text-muted);
}
</style>
