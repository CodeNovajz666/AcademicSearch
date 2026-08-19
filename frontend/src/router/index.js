import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/upload',
    name: 'Upload',
    component: () => import('../views/Upload.vue')
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('../views/Search.vue')
  },
  {
    path: '/rag',
    name: 'Rag',
    component: () => import('../views/Rag.vue')
  },
  {
    path: '/literature/:id',
    name: 'LiteratureDetail',
    component: () => import('../views/LiteratureDetail.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router