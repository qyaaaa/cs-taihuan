import { createRouter, createWebHistory } from 'vue-router'

// 路由仅用于把当前 tab 同步到 URL（刷新 / 前进后退 / 深链）；页面仍由 App.vue 条件渲染，
// 没有 router-view，因此每个路由挂一个空占位组件即可。
const Empty = { render: () => null }

const routes = [
  { path: '/', name: 'overview', component: Empty },
  { path: '/inventory', name: 'inventory', component: Empty },
  { path: '/plans', name: 'plans', component: Empty },
  { path: '/float', name: 'float', component: Empty },
  { path: '/odds', name: 'odds', component: Empty },
  { path: '/collections', name: 'collections', component: Empty },
  // 未知路径回退到总览。
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
