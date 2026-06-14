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
      path: '/love-master',
      name: 'love-master',
      component: ChatView,
      props: { appType: 'love' },
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
