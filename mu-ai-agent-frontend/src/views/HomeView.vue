<script setup>
import { RouterLink } from 'vue-router'
import AppLogo from '../components/AppLogo.vue'
import ArrowIcon from '../components/ArrowIcon.vue'

const applications = [
  {
    name: 'AI 知识经济顾问',
    eyebrow: 'KNOWLEDGE ECONOMY',
    description: '解答知识经济领域的问题，梳理核心概念、发展趋势与实际应用。',
    route: '/knowledge-economy',
    theme: 'knowledge',
    tags: ['知识经济', '趋势分析'],
    featured: true,
  },
  {
    name: 'AI 超级智能体',
    eyebrow: 'SUPER AGENT',
    description: '调用多种工具完成复杂任务，从信息检索到方案执行，让想法真正落地。',
    route: '/super-agent',
    theme: 'blue',
    tags: ['自主规划', '工具调用'],
  },
  {
    name: 'AI 图片解析',
    eyebrow: 'VISION ANALYST',
    description: '上传一张图片并提出问题，让多模态 AI 识别画面内容、细节与潜在信息。',
    route: '/image-explain',
    theme: 'violet',
    tags: ['图像识别', '多模态分析'],
  },
]
</script>

<template>
  <main class="home-page">
    <nav class="home-nav">
      <AppLogo />
      <div class="home-nav__status">
        <span class="status-dot"></span>
        KNOWLEDGE SYSTEM ONLINE
      </div>
    </nav>

    <section class="hero">
      <div class="hero__copy">
        <div class="hero__label"><span></span> INTELLIGENCE FOR THE KNOWLEDGE ECONOMY</div>
        <h1>
          让知识，成为<br />
          <em>增长的基础设施。</em>
        </h1>
        <p>
          从概念理解到趋势洞察，把分散的信息组织成可行动的认知。
          与你的 AI 研究伙伴一起，探索知识驱动的新经济。
        </p>
        <div class="hero__actions">
          <RouterLink to="/knowledge-economy" class="hero__primary">
            开始研究
            <ArrowIcon />
          </RouterLink>
          <span class="hero__note">面向学习、研究与战略思考</span>
        </div>
        <div class="hero__stats" aria-label="平台能力">
          <div><strong>03</strong><span>智能应用</span></div>
          <div><strong>24/7</strong><span>持续响应</span></div>
          <div><strong>SSE</strong><span>实时生成</span></div>
        </div>
      </div>

      <div class="hero__visual" aria-hidden="true">
        <div class="knowledge-orbit knowledge-orbit--outer"></div>
        <div class="knowledge-orbit knowledge-orbit--inner"></div>
        <span class="knowledge-node knowledge-node--capital">资本</span>
        <span class="knowledge-node knowledge-node--data">数据</span>
        <span class="knowledge-node knowledge-node--talent">人才</span>
        <span class="knowledge-node knowledge-node--innovation">创新</span>
        <div class="knowledge-core">
          <span>KNOWLEDGE</span>
          <strong>知识经济</strong>
          <small>VALUE NETWORK</small>
        </div>
        <div class="knowledge-index">
          <span>KNOWLEDGE INDEX</span>
          <strong>87.4</strong>
          <i>+12.8%</i>
        </div>
      </div>
    </section>

    <section class="app-grid" aria-label="AI 应用列表">
      <RouterLink
        v-for="(app, index) in applications"
        :key="app.route"
        :to="app.route"
        class="app-card"
        :class="[`app-card--${app.theme}`, { 'app-card--featured': app.featured }]"
      >
        <div class="app-card__top">
          <span class="app-card__number">0{{ index + 1 }}</span>
          <span class="app-card__eyebrow">{{ app.eyebrow }}</span>
        </div>

        <div class="app-card__art" aria-hidden="true">
          <div v-if="app.theme === 'knowledge'" class="knowledge-card-art">
            <span class="knowledge-card-art__ring"></span>
            <span class="knowledge-card-art__axis knowledge-card-art__axis--one"></span>
            <span class="knowledge-card-art__axis knowledge-card-art__axis--two"></span>
            <span class="knowledge-card-art__node knowledge-card-art__node--one"></span>
            <span class="knowledge-card-art__node knowledge-card-art__node--two"></span>
            <span class="knowledge-card-art__node knowledge-card-art__node--three"></span>
            <strong>K</strong>
          </div>
          <div v-else-if="app.theme === 'blue'" class="agent-art">
            <span class="agent-orbit agent-orbit--one"></span>
            <span class="agent-orbit agent-orbit--two"></span>
            <span class="agent-core">
              <svg viewBox="0 0 48 48" fill="none">
                <path d="M15 29V19l9 7 9-7v10" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
                <circle cx="24" cy="13" r="3" fill="currentColor" />
              </svg>
            </span>
          </div>
          <div v-else class="vision-art">
            <span class="vision-art__frame">
              <svg viewBox="0 0 80 80" fill="none">
                <rect x="13" y="17" width="54" height="46" rx="8" stroke="currentColor" stroke-width="2.5" />
                <circle cx="52" cy="31" r="5" fill="currentColor" opacity=".58" />
                <path d="m20 55 14-14 10 9 7-6 10 11" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
            <span class="vision-art__scan"></span>
          </div>
        </div>

        <div class="app-card__content">
          <h2>{{ app.name }}</h2>
          <p>{{ app.description }}</p>
          <div class="app-card__footer">
            <div class="tag-list">
              <span v-for="tag in app.tags" :key="tag">{{ tag }}</span>
            </div>
            <span class="round-arrow"><ArrowIcon /></span>
          </div>
        </div>
      </RouterLink>
    </section>

    <footer class="home-footer">
      <span>MU INTELLIGENCE LAB · 2026</span>
      <span>KNOWLEDGE · INSIGHT · ACTION</span>
    </footer>
  </main>
</template>
