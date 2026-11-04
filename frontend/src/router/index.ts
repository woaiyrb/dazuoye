import { createRouter, createWebHistory } from 'vue-router'
import type { RoleCode } from '@/types/models'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    roles?: RoleCode[]
    public?: boolean
    redirectAfterLogin?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth', top: 88 }
    if (to.path !== from.path) return { top: 0 }
    return undefined
  },
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/teacher',
      component: () => import('@/layouts/AppShell.vue'),
      meta: { roles: ['TEACHER'] },
      children: [
        {
          path: '',
          name: 'teacher-home',
          component: () => import('@/views/teacher/TeacherDashboard.vue'),
          meta: { title: '总览', roles: ['TEACHER'] },
        },
        {
          path: 'classes',
          name: 'teacher-classes',
          component: () => import('@/views/teacher/ClassesView.vue'),
          meta: { title: '班级与学生', roles: ['TEACHER'] },
        },
        {
          path: 'templates',
          name: 'teacher-templates',
          component: () => import('@/views/teacher/TemplatesView.vue'),
          meta: { title: '讲评模板', roles: ['TEACHER'] },
        },
        {
          path: 'homeworks',
          name: 'teacher-homeworks',
          component: () => import('@/views/teacher/HomeworkListView.vue'),
          meta: { title: '作业与批改', roles: ['TEACHER'] },
        },
        {
          path: 'homeworks/:id',
          name: 'teacher-homework-detail',
          component: () => import('@/views/teacher/HomeworkDetailView.vue'),
          meta: { title: '作业详情', roles: ['TEACHER'] },
        },
        {
          path: 'homeworks/:id/grading',
          name: 'teacher-grading',
          component: () => import('@/views/teacher/GradingView.vue'),
          meta: { title: '批改与统计', roles: ['TEACHER'] },
        },
        {
          path: 'homeworks/:id/grading/manual',
          name: 'teacher-manual-grading',
          component: () => import('@/views/teacher/ManualGradingView.vue'),
          meta: { title: '手工录入批改', roles: ['TEACHER'] },
        },
        {
          path: 'homeworks/:id/statistics',
          name: 'teacher-statistics',
          component: () => import('@/views/teacher/StatisticsView.vue'),
          meta: { title: '错误统计', roles: ['TEACHER'] },
        },
        {
          path: 'homeworks/:id/review',
          name: 'teacher-review',
          component: () => import('@/views/teacher/ReviewView.vue'),
          meta: { title: '讲评审核', roles: ['TEACHER'] },
        },
        {
          path: 'corrections',
          name: 'teacher-corrections',
          component: () => import('@/views/teacher/CorrectionsView.vue'),
          meta: { title: '订正跟踪', roles: ['TEACHER'] },
        },
        {
          path: 'doubts',
          name: 'teacher-doubts',
          component: () => import('@/views/teacher/DoubtsView.vue'),
          meta: { title: '学生疑问', roles: ['TEACHER'] },
        },
      ],
    },
    {
      path: '/student',
      component: () => import('@/layouts/AppShell.vue'),
      meta: { roles: ['STUDENT'] },
      children: [
        {
          path: '',
          name: 'student-home',
          component: () => import('@/views/student/StudentDashboard.vue'),
          meta: { title: '我的主页', roles: ['STUDENT'] },
        },
        {
          path: 'homeworks',
          name: 'student-homeworks',
          component: () => import('@/views/student/HomeworkListView.vue'),
          meta: { title: '作业与成绩', roles: ['STUDENT'] },
        },
        {
          path: 'homeworks/:id',
          name: 'student-homework-answer',
          component: () => import('@/views/student/HomeworkAnswerView.vue'),
          meta: { title: '作业作答', roles: ['STUDENT'] },
        },
        {
          path: 'handouts',
          name: 'student-handouts',
          component: () => import('@/views/student/HandoutListView.vue'),
          meta: { title: '错题讲评', roles: ['STUDENT'] },
        },
        {
          path: 'handouts/:id',
          name: 'student-handout-detail',
          component: () => import('@/views/student/HandoutDetailView.vue'),
          meta: { title: '讲评详情', roles: ['STUDENT'] },
        },
        {
          path: 'corrections',
          name: 'student-corrections',
          component: () => import('@/views/student/CorrectionsView.vue'),
          meta: { title: '我的订正', roles: ['STUDENT'] },
        },
        {
          path: 'wrong-book',
          name: 'student-wrong-book',
          component: () => import('@/views/student/WrongBookView.vue'),
          meta: { title: '我的错题本', roles: ['STUDENT'] },
        },
        {
          path: 'doubts',
          name: 'student-doubts',
          component: () => import('@/views/student/DoubtsView.vue'),
          meta: { title: '我的疑问', roles: ['STUDENT'] },
        },
      ],
    },
    {
      path: '/research',
      component: () => import('@/layouts/AppShell.vue'),
      meta: { roles: ['RESEARCH'] },
      children: [
        {
          path: '',
          name: 'research-home',
          component: () => import('@/views/research/ResearchDashboard.vue'),
          meta: { title: '总览', roles: ['RESEARCH'] },
        },
        {
          path: 'knowledge-points',
          name: 'research-knowledge',
          component: () => import('@/views/research/KnowledgePointsView.vue'),
          meta: { title: '知识点体系', roles: ['RESEARCH'] },
        },
        {
          path: 'templates',
          name: 'research-templates',
          component: () => import('@/views/research/TemplatesView.vue'),
          meta: { title: '公共讲评模板', roles: ['RESEARCH'] },
        },
        {
          path: 'logs',
          name: 'research-logs',
          component: () => import('@/views/research/OperationLogsView.vue'),
          meta: { title: '操作日志', roles: ['RESEARCH'] },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面未找到', public: true },
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (to.name === 'login' && auth.isAuthenticated) return auth.homePath
    return true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles?.length && (!auth.role || !to.meta.roles.includes(auth.role))) {
    return auth.homePath
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · 错题星球` : '错题星球 · 数学讲评助手'
})

export default router
