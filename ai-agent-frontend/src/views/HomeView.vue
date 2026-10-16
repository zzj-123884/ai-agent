<template>
  <div class="home">
    <div class="home-inner">
      <header class="hero">
        <p class="kicker">临床参考 · 任务执行</p>
        <h1>把问题交给<br />合适的助手</h1>
        <p class="lede">
          医学助手依据公开资料解释健康问题。超级智能体按步骤调用工具，把复杂任务做完。
        </p>
      </header>

      <nav class="choices" aria-label="应用列表">
        <router-link to="/medical" class="choice med">
          <div class="choice-body">
            <p class="label">健康咨询</p>
            <h2>医学助手</h2>
            <p class="summary">用中文解释健康问题，并给出资料出处。</p>
          </div>
          <ul class="points">
            <li>症状与用药</li>
            <li>附带来源</li>
          </ul>
          <span class="go">开始咨询 <span aria-hidden="true">→</span></span>
        </router-link>

        <router-link to="/manus-app" class="choice manus">
          <div class="choice-body">
            <p class="label">任务执行</p>
            <h2>超级智能体</h2>
            <p class="summary">分步调用工具，把一件具体的事做完。</p>
          </div>
          <ul class="points">
            <li>搜索资料</li>
            <li>读写文件</li>
            <li>展示每一步</li>
          </ul>
          <span class="go">布置任务 <span aria-hidden="true">→</span></span>
        </router-link>
      </nav>

      <p class="extra">
        <router-link to="/api-key">管理 API Key</router-link>
        <span>签发后可让第三方应用调用医学助手</span>
      </p>
    </div>
  </div>
</template>

<style scoped>
.home {
  flex: 1;
  display: flex;
  align-items: center;
  min-height: 0;
  padding:
    calc(40px + var(--safe-top))
    max(24px, var(--safe-right))
    calc(40px + var(--safe-bottom))
    max(24px, var(--safe-left));
}

.home-inner {
  width: 100%;
  max-width: 980px;
  margin: 0 auto;
}

.kicker {
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--accent-cyan);
  font-weight: 600;
}

.hero h1 {
  margin-top: 12px;
  font-family: var(--font-display);
  font-size: clamp(2.4rem, 6vw, 4.2rem);
  font-weight: 560;
  line-height: 1.12;
  letter-spacing: -0.03em;
  color: var(--text);
}

.lede {
  max-width: 34em;
  margin-top: 18px;
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--text-muted);
}

.choices {
  display: grid;
  gap: 18px;
  margin-top: 36px;
}

.choice {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: 20px;
  text-decoration: none;
  color: inherit;
  overflow: hidden;
  border: 1px solid rgba(30, 42, 38, 0.08);
  box-shadow: var(--shadow-sm);
  transition:
    transform var(--duration) var(--ease),
    box-shadow var(--duration) var(--ease);
}

.choice.med {
  background: linear-gradient(155deg, #cfe8dc 0%, #e7f5ee 46%, #fffdf9 100%);
}

.choice.manus {
  background: linear-gradient(155deg, #d5e3f1 0%, #eaf1f7 46%, #fffdf9 100%);
}

.choice:hover,
.choice:focus-visible {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
  outline: none;
}

.choice-body {
  padding: 22px 22px 0;
}

.label {
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.choice.med .label,
.choice.med .go {
  color: var(--accent-cyan);
}

.choice.manus .label,
.choice.manus .go {
  color: var(--accent-ink);
}

.choice h2 {
  margin-top: 6px;
  font-family: var(--font-display);
  font-size: clamp(2rem, 3vw, 2.35rem);
  font-weight: 640;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: #14211c;
}

.summary {
  margin-top: 8px;
  max-width: 16em;
  line-height: 1.6;
  color: var(--text-muted);
  word-break: keep-all;
}

.points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 0;
  padding: 0 22px 18px;
  list-style: none;
}

.points li {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 253, 249, 0.55);
  border: 1px solid rgba(30, 42, 38, 0.08);
  font-size: 0.78rem;
  color: var(--text);
  word-break: keep-all;
}

.go {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding: 14px 22px;
  border-top: 1px solid rgba(30, 42, 38, 0.08);
  background: rgba(255, 253, 249, 0.35);
  font-size: 0.95rem;
  font-weight: 600;
}

@media (min-width: 800px) {
  .choices {
    grid-template-columns: 1fr 1fr;
  }
}

.extra {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px 14px;
  margin-top: 22px;
  color: var(--text-muted);
  font-size: 0.92rem;
}

.extra a {
  color: var(--accent-cyan);
  font-weight: 600;
}
</style>
