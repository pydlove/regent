import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/useAuthStore'
import { resolveRouterScroll } from '@/utils/routerScroll'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, _from, savedPosition) {
    return resolveRouterScroll(savedPosition, to.hash)
  },
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
      path: '/download',
      name: 'download',
      component: () => import('@/views/DownloadView.vue'),
      // 下载页是公开页面，但和登录页一样使用独立布局，不套管理端侧边栏
      meta: { title: '下载', layout: 'auth', public: true },
    },
    {
      path: '/download/app',
      name: 'mobile-download',
      component: () => import('@/views/MobileAppDownloadView.vue'),
      meta: { title: '下载手机 App', layout: 'auth', public: true },
    },
    {
      path: '/invite/:token',
      name: 'organization-invitation',
      component: () => import('@/views/InvitationAcceptView.vue'),
      meta: { title: '加入组织', layout: 'auth', public: true },
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('@/views/ChangePasswordView.vue'),
      meta: { title: '修改密码', layout: 'auth', allowAuthenticated: true },
    },
    {
      path: '/',
      redirect: '/dashboard',
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
      component: () => import('@/views/TasksWorkspaceView.vue'),
      meta: { title: '任务' },
    },
    {
      path: '/plans',
      redirect: (to) => ({ path: '/tasks', query: { ...to.query, view: 'plans' } }),
    },
    {
      path: '/deployments',
      redirect: (to) => ({ path: '/delivery', query: { ...to.query, view: 'deployments' } }),
    },
    {
      path: '/delivery',
      name: 'delivery',
      component: () => import('@/views/DeliveryWorkspaceView.vue'),
      meta: { title: '交付' },
    },
    {
      path: '/servers',
      redirect: (to) => ({ path: '/projects', query: { ...to.query, view: 'servers' } }),
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
      component: () => import('@/views/ProjectsWorkspaceView.vue'),
      meta: { title: '项目' },
    },
    {
      path: '/organizations/workflow',
      name: 'workflow-templates',
      component: () => import('@/views/WorkflowTemplatesSettings.vue'),
      meta: { title: '研发流程' },
    },
    {
      path: '/workflow-templates',
      redirect: '/organizations/workflow',
    },
    {
      path: '/settings/workflow',
      redirect: '/organizations/workflow',
    },
    {
      path: '/organizations',
      name: 'organizations',
      component: () => import('@/views/OrganizationManagementView.vue'),
      meta: { title: '组织管理' },
    },
    {
      path: '/environments',
      redirect: (to) => ({ path: '/projects', query: { ...to.query, view: 'environments' } }),
    },
    {
      path: '/plugins',
      redirect: (to) => ({ path: '/projects', query: { ...to.query, view: 'plugins' } }),
    },
    {
      path: '/test-cases',
      redirect: (to) => ({ path: '/delivery', query: { ...to.query, view: 'tests' } }),
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
  const publicPage = to.meta.public === true
  if (
    authPage
    && auth.isAuthenticated
    && !publicPage
    && to.meta.allowAuthenticated !== true
  ) {
    return { name: 'tasks' }
  }
  if (!authPage && !publicPage && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }
  if (to.name === 'change-password' && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (
    auth.isAuthenticated
    && auth.user?.mustChangePassword
    && to.name !== 'change-password'
    && !publicPage
  ) {
    return {
      name: 'change-password',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }
})

export default router
