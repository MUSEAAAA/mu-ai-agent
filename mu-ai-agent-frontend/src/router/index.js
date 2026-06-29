import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import ChatView from '../views/ChatView.vue'
import ImageExplainView from '../views/ImageExplainView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/knowledge-economy',
      name: 'knowledge-economy',
      component: ChatView,
      props: { appType: 'knowledgeEconomy' },
    },
{
      path: '/super-agent',
      name: 'super-agent',
      component: ChatView,
      props: { appType: 'manus' },
    },
    {
      path: '/image-explain',
      name: 'image-explain',
      component: ImageExplainView,
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
