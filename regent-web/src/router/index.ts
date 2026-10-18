import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/useAuthStore'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/AuthView.vue'),
      props: { mode: 'login' },
      meta: { title: '登录', layout: 'auth' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/AuthView.vue'),
      props: { mode: 'register' },
      meta: { title: '注册', layout: 'auth' },
    },
    {
      path: '/',
      // 默认落地在任务页：样式基准页只是设置里的展示分区，不当首屏
      redirect: '/tasks',
    },
    {
      path: '/baseline',
      redirect: '/settings/baseline',
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { title: '工作台' },
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('@/views/TasksView.vue'),
      meta: { title: '任务' },
    },
    {
      path: '/plans',
      name: 'plans',
      component: () => import('@/views/PlansView.vue'),
      meta: { title: '计划' },
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
      path: '/plugins',
      name: 'plugins',
      component: () => import('@/views/PluginsView.vue'),
      meta: { title: '插件' },
    },
    {
      path: '/test-cases',
      name: 'test-cases',
      component: () => import('@/views/TestCasesView.vue'),
      meta: { title: '测试用例' },
    },
    {
      path: '/help',
      name: 'help',
      component: () => import('@/views/HelpView.vue'),
      meta: { title: '使用说明' },
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

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.initialized) {
    await auth.loadCurrentUser()
  }

  const authPage = to.meta.layout === 'auth'
  if (authPage && auth.isAuthenticated) {
    return { name: 'tasks' }
  }
  if (!authPage && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }
})

export default router
