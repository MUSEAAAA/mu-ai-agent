<script setup>
import { RouterLink } from 'vue-router'
import AppLogo from '../components/AppLogo.vue'
import ArrowIcon from '../components/ArrowIcon.vue'

const applications = [
  {
    name: 'AI 恋爱大师',
    eyebrow: 'LOVE MASTER',
    description: '倾听你的困惑，读懂关系中的微妙信号，给出温柔而清醒的建议。',
    route: '/love-master',
    theme: 'rose',
    tags: ['关系分析', '情绪陪伴'],
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
        AI 服务中心
      </div>
    </nav>

    <section class="hero">
      <div class="hero__label"><span></span> YOUR AI WORKSPACE</div>
      <h1>
        一点灵感，<br />
        <em>无限可能。</em>
      </h1>
      <p>选择一个智能应用，开启属于你的 AI 对话。</p>
    </section>

    <section class="app-grid" aria-label="AI 应用列表">
      <RouterLink
        v-for="(app, index) in applications"
        :key="app.route"
        :to="app.route"
        class="app-card"
        :class="`app-card--${app.theme}`"
      >
        <div class="app-card__top">
          <span class="app-card__number">0{{ index + 1 }}</span>
          <span class="app-card__eyebrow">{{ app.eyebrow }}</span>
        </div>

        <div class="app-card__art" aria-hidden="true">
          <div v-if="app.theme === 'rose'" class="heart-art">
            <svg viewBox="0 0 180 150" fill="none">
              <path d="M90 132S22 94 22 48C22 20 57 11 76 35L90 54l14-19c19-24 54-15 54 13 0 46-68 84-68 84Z" fill="currentColor" />
              <path d="M51 50c5-13 17-17 26-9" stroke="white" stroke-width="5" stroke-linecap="round" opacity=".65" />
            </svg>
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
      <span>MU AI LAB · 2026</span>
      <span>让智能，更贴近每一个具体的问题</span>
    </footer>
  </main>
</template>
