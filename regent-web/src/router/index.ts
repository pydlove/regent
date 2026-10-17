import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      // 默认落地在任务页：样式基准页只是展示用，放在侧边栏底部，不当首屏
      redirect: '/tasks',
    },
    {
      path: '/baseline',
      name: 'baseline',
      component: () => import('@/views/BaselineView.vue'),
      meta: { title: '样式基准页' },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { title: '看板' },
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('@/views/TasksView.vue'),
      meta: { title: '任务' },
    },
    {
      path: '/decisions',
      name: 'decisions',
      component: () => import('@/views/DecisionsView.vue'),
      meta: { title: '决策' },
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/ProjectsView.vue'),
      meta: { title: '项目' },
    },
    {
      path: '/environments',
      name: 'environments',
      component: () => import('@/views/EnvironmentsView.vue'),
      meta: { title: '环境' },
    },
    {
      path: '/bugs',
      redirect: (to) => ({ name: 'tasks', query: { ...to.query, view: 'bugs' } }),
    },
    {
      path: '/settings/:section?',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
      meta: { title: '设置' },
    },
  ],
})

export default router
