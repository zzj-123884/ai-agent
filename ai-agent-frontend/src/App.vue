<template>
  <div class="app-shell">
    <main class="app-main">
      <router-view />
    </main>
    <SiteFooter />
  </div>
</template>

<script setup>
import SiteFooter from './components/SiteFooter.vue'
</script>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  max-height: 100dvh;
  overflow: hidden;
}

.app-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
}
</style>
