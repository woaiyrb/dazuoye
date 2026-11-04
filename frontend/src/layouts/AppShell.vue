<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isMockMode } from '@/api/client'
import { devApi } from '@/api/dev'
import { teacherApi } from '@/api/teacher'
import { useAuthStore } from '@/stores/auth'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { useUiStore } from '@/stores/ui'
import { ROLE_META, ROLE_NAV_HINT } from '@/types/labels'
import { formatDay, formatWeekday } from '@/utils/format'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import BrandMark from '@/components/BrandMark.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useOutsideClick } from '@/composables/useOutsideClick'

const auth = useAuthStore()
const workspace = useTeacherWorkspace()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

interface NavItem {
  label: string
  icon: IconName
  to: string
  active: (path: string) => boolean
  dot?: boolean
}

const mobileOpen = ref(false)
const userMenuOpen = ref(false)
const aiMenuOpen = ref(false)
const userMenu = ref<HTMLElement | null>(null)
const aiMenu = ref<HTMLElement | null>(null)
const pendingDoubts = ref(0)

useOutsideClick(userMenu, () => (userMenuOpen.value = false))
useOutsideClick(aiMenu, () => (aiMenuOpen.value = false))

const role = computed(() => auth.role)
const roleMeta = computed(() => (role.value ? ROLE_META[role.value] : ROLE_META.TEACHER))
const roleHint = computed(() => (role.value ? ROLE_NAV_HINT[role.value] : ''))
const initials = computed(() => auth.displayName.slice(0, 1) || '师')
const today = `${formatDay(new Date())} · ${formatWeekday(new Date())}`
const isTeacher = computed(() => role.value === 'TEACHER')

const navItems = computed<NavItem[]>(() => {
  if (role.value === 'TEACHER') {
    return [
      { label: '总览', icon: 'home', to: '/teacher', active: (path) => path === '/teacher' },
      {
        label: '班级与学生',
        icon: 'users',
        to: '/teacher/classes',
        active: (path) => path.startsWith('/teacher/classes'),
      },
      {
        label: '讲评模板',
        icon: 'file',
        to: '/teacher/templates',
        active: (path) => path.startsWith('/teacher/templates'),
      },
      {
        label: '作业与批改',
        icon: 'clipboard',
        to: '/teacher/homeworks',
        active: (path) => path.startsWith('/teacher/homeworks') && !path.endsWith('/review'),
      },
      {
        label: '讲评审核',
        icon: 'sparkles',
        to: workspace.activeHomeworkId
          ? `/teacher/homeworks/${workspace.activeHomeworkId}/review`
          : '/teacher/homeworks',
        active: (path) => path.endsWith('/review'),
        dot: ['UNPUBLISHED', 'WITHDRAWN'].includes(workspace.activeHomework?.handoutStatus ?? ''),
      },
      {
        label: '订正跟踪',
        icon: 'clipboard-check',
        to: '/teacher/corrections',
        active: (path) => path.startsWith('/teacher/corrections'),
      },
      {
        label: '学生疑问',
        icon: 'message',
        to: '/teacher/doubts',
        active: (path) => path.startsWith('/teacher/doubts'),
        dot: pendingDoubts.value > 0,
      },
    ]
  }
  if (role.value === 'STUDENT') {
    return [
      { label: '我的主页', icon: 'home', to: '/student', active: (path) => path === '/student' },
      {
        label: '作业与成绩',
        icon: 'clipboard',
        to: '/student/homeworks',
        active: (path) => path.startsWith('/student/homeworks'),
      },
      {
        label: '错题讲评',
        icon: 'book-open',
        to: '/student/handouts',
        active: (path) => path.startsWith('/student/handouts'),
      },
      {
        label: '错题本',
        icon: 'target',
        to: '/student/wrong-book',
        active: (path) => path.startsWith('/student/wrong-book'),
      },
      {
        label: '我的订正',
        icon: 'check-circle',
        to: '/student/corrections',
        active: (path) => path.startsWith('/student/corrections'),
      },
      {
        label: '我的疑问',
        icon: 'message',
        to: '/student/doubts',
        active: (path) => path.startsWith('/student/doubts'),
      },
    ]
  }
  return [
    { label: '总览', icon: 'compass', to: '/research', active: (path) => path === '/research' },
    {
      label: '知识点体系',
      icon: 'layers',
      to: '/research/knowledge-points',
      active: (path) => path.startsWith('/research/knowledge-points'),
    },
    {
      label: '公共讲评模板',
      icon: 'file',
      to: '/research/templates',
      active: (path) => path.startsWith('/research/templates'),
    },
    {
      label: '操作日志',
      icon: 'list',
      to: '/research/logs',
      active: (path) => path.startsWith('/research/logs'),
    },
  ]
})

const currentTitle = computed(() => (route.meta.title as string | undefined) ?? roleMeta.value.portal)
const portalLabel = computed(() => {
  if (role.value === 'TEACHER') return '教师工作台'
  if (role.value === 'STUDENT') return '学生空间'
  return '教研控制台'
})

function handleHomeworkChange(event: Event): void {
  const id = Number((event.target as HTMLSelectElement).value)
  if (!id) return
  workspace.selectHomework(id)
  const path = route.path
  if (path.endsWith('/review') || path.endsWith('/grading') || path.endsWith('/statistics')) {
    const suffix = path.endsWith('/review') ? '/review' : path.endsWith('/grading') ? '/grading' : '/statistics'
    void router.push(`/teacher/homeworks/${id}${suffix}`)
  }
}

watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
    userMenuOpen.value = false
    aiMenuOpen.value = false
  },
)

watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (!authenticated && route.name !== 'login') {
      void router.replace({ name: 'login', query: { redirect: route.fullPath } })
    }
  },
)

async function handleResetDemo(): Promise<void> {
  const accepted = await ui.confirm({
    title: '重置演示数据',
    message: '将恢复作业、批改、讲评、订正和疑问的初始虚构数据。当前会话保留，页面会自动刷新。',
    confirmText: '重置并刷新',
    tone: 'danger',
  })
  if (!accepted) return
  await devApi.resetDemoData()
  workspace.reset()
  window.location.reload()
}

async function handleLogout(): Promise<void> {
  userMenuOpen.value = false
  workspace.reset()
  await auth.logout()
  await router.replace('/login')
}

onMounted(async () => {
  if (role.value === 'TEACHER') {
    await workspace.load()
    try {
      const page = await teacherApi.listDoubts({ status: 'PENDING', page: 1, pageSize: 1 })
      pendingDoubts.value = page.total
    } catch {
      pendingDoubts.value = 0
    }
  }
})
</script>

<template>
  <div class="shell" :data-role="role?.toLowerCase()">
    <button
      v-if="mobileOpen"
      class="nav-backdrop"
      type="button"
      aria-label="关闭导航"
      @click="mobileOpen = false"
    />

    <aside class="sidebar" :class="{ 'is-open': mobileOpen }">
      <div class="sidebar__brand">
        <BrandMark :size="42" />
        <div class="sidebar__brand-copy">
          <strong>错题星球</strong>
          <span>让每道错题都有回音</span>
        </div>
        <button class="sidebar__close icon-btn" type="button" aria-label="关闭导航" @click="mobileOpen = false">
          <AppIcon name="x" :size="18" />
        </button>
      </div>

      <div class="portal-chip">
        <span class="portal-chip__dot" />
        {{ portalLabel }}
        <span class="portal-chip__role">{{ roleMeta.label }}</span>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <p class="side-nav__label">工作区</p>
        <RouterLink
          v-for="item in navItems"
          :key="item.label"
          :to="item.to"
          class="nav-link"
          :class="{ 'is-active': item.active(route.path) }"
          @click="mobileOpen = false"
        >
          <span class="nav-link__icon"><AppIcon :name="item.icon" :size="18" /></span>
          <span>{{ item.label }}</span>
          <span v-if="item.dot" class="nav-link__dot" aria-label="有待处理" />
        </RouterLink>
      </nav>

      <div v-if="isTeacher && workspace.homeworks.length" class="current-homework">
        <p class="current-homework__label">
          <span>当前作业</span>
          <RouterLink to="/teacher/homeworks" class="current-homework__all">全部</RouterLink>
        </p>
        <select
          class="current-homework__select"
          :value="workspace.activeHomeworkId ?? ''"
          aria-label="切换当前作业"
          @change="handleHomeworkChange"
        >
          <option v-for="homework in workspace.homeworks" :key="homework.id" :value="homework.id">
            {{ homework.title }}
          </option>
        </select>
        <div class="current-homework__meta">
          <span>{{ workspace.activeHomework?.className }}</span>
          <StatusBadge
            v-if="workspace.activeHomework?.handoutStatus"
            :status="workspace.activeHomework.handoutStatus"
            plain
          />
        </div>
      </div>

      <div class="boundary-note">
        <AppIcon name="shield" :size="15" />
        <p>{{ roleHint }}</p>
      </div>

      <div class="sidebar__footer">
        <div class="side-user">
          <span class="avatar">{{ initials }}</span>
          <div class="side-user__copy">
            <strong>{{ auth.displayName }}</strong>
            <span>{{ auth.user?.username }}</span>
          </div>
          <button class="icon-btn side-user__logout" type="button" aria-label="退出登录" @click="handleLogout">
            <AppIcon name="logout" :size="16" />
          </button>
        </div>
        <p class="sidebar__legal">仅使用脱敏虚构数据 · v0.1</p>
      </div>
    </aside>

    <div class="main-column">
      <header class="topbar">
        <div class="topbar__left">
          <button class="menu-button" type="button" aria-label="打开导航" @click="mobileOpen = true">
            <AppIcon name="menu" :size="20" />
          </button>
          <div class="crumbs">
            <span>{{ portalLabel }}</span>
            <AppIcon name="chevron-right" :size="12" />
            <strong>{{ currentTitle }}</strong>
          </div>
        </div>

        <div class="topbar__right">
          <span class="today-label">{{ today }}</span>

          <span v-if="isMockMode" class="mode-chip" title="当前使用内置 Mock 数据，无需启动后端">
            <span class="mode-chip__pulse" />
            Mock 数据
          </span>

          <div v-if="isMockMode" ref="aiMenu" class="popover-anchor">
            <button
              class="ai-chip"
              type="button"
              :class="{ 'is-offline': !ui.aiOnline }"
              :aria-expanded="aiMenuOpen"
              @click="aiMenuOpen = !aiMenuOpen"
            >
              <span class="ai-chip__dot" />
              <span>AI {{ ui.aiOnline ? '服务正常' : '已模拟离线' }}</span>
              <AppIcon name="chevron-down" :size="13" />
            </button>
            <Transition name="pop">
              <div v-if="aiMenuOpen" class="menu ai-popover">
                <p class="menu__label">AI 服务状态</p>
                <div class="ai-popover__head">
                  <span class="ai-popover__icon"><AppIcon name="sparkles" :size="18" /></span>
                  <div>
                    <strong>{{ ui.aiOnline ? '草稿生成正常' : '已降级到手工模式' }}</strong>
                    <p>AI 只生成候选草稿，不能改分或直接发布。</p>
                  </div>
                </div>
                <div class="ai-popover__switch">
                  <div>
                    <b>模拟 AI 服务</b>
                    <span>演示 30 秒超时 / 服务不可用降级</span>
                  </div>
                  <label class="switch" :class="{ 'is-on': ui.aiOnline }">
                    <input
                      type="checkbox"
                      class="visually-hidden"
                      :checked="ui.aiOnline"
                      @change="ui.setAiOnline(!ui.aiOnline)"
                    />
                    <span />
                  </label>
                </div>
                <p class="ai-popover__foot">该开关仅在 Mock 模式生效，真实模式以 Spring AI 返回为准。</p>
              </div>
            </Transition>
          </div>

          <div ref="userMenu" class="popover-anchor">
            <button
              class="user-chip"
              type="button"
              :aria-expanded="userMenuOpen"
              @click="userMenuOpen = !userMenuOpen"
            >
              <span class="avatar avatar--sm">{{ initials }}</span>
              <span class="user-chip__name">{{ auth.displayName }}</span>
              <AppIcon name="chevron-down" :size="13" />
            </button>
            <Transition name="pop">
              <div v-if="userMenuOpen" class="menu user-menu">
                <div class="user-menu__head">
                  <span class="avatar">{{ initials }}</span>
                  <div>
                    <strong>{{ auth.displayName }}</strong>
                    <span>{{ roleMeta.portal }} · {{ auth.user?.username }}</span>
                  </div>
                </div>
                <div class="menu__sep" />
                <RouterLink :to="roleMeta.home" class="menu__item">
                  <AppIcon name="home" :size="15" />
                  返回{{ roleMeta.portal }}首页
                </RouterLink>
                <button v-if="isMockMode" class="menu__item" type="button" @click="handleResetDemo">
                  <AppIcon name="refresh" :size="15" />
                  重置演示数据
                </button>
                <div class="menu__sep" />
                <button class="menu__item is-danger" type="button" @click="handleLogout">
                  <AppIcon name="logout" :size="15" />
                  退出登录
                </button>
              </div>
            </Transition>
          </div>
        </div>
      </header>

      <main class="content">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="String(route.params.id ?? '')" />
          </Transition>
        </RouterView>
      </main>

      <footer class="app-footer">
        <span>错题星球 · 数学讲评与错题跟踪助手</span>
        <span class="app-footer__dot" />
        <span>四层数据隔离：原始数据 / 统计 / AI 草稿 / 教师结论</span>
        <span class="app-footer__spacer" />
        <span class="mono">{{ isMockMode ? 'MOCK DATA' : 'SPRING BOOT API' }}</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  --page-base: var(--bg);
  --page-glow-a: rgba(87, 203, 185, 0.16);
  --page-glow-b: rgba(123, 143, 238, 0.11);
  --page-dot: rgba(64, 108, 94, 0.055);
  background-color: var(--page-base);
  background-image:
    radial-gradient(circle at 4% -6%, var(--page-glow-a), transparent 32%),
    radial-gradient(circle at 94% 104%, var(--page-glow-b), transparent 34%),
    radial-gradient(var(--page-dot) 0.75px, transparent 0.75px);
  background-size: auto, auto, 22px 22px;
  background-attachment: fixed;
}

.shell[data-role='student'] {
  --page-base: #f4f6ff;
  --page-glow-a: rgba(107, 140, 245, 0.16);
  --page-glow-b: rgba(149, 131, 221, 0.12);
  --page-dot: rgba(92, 108, 176, 0.05);
}

.shell[data-role='research'] {
  --page-base: #fcf8f1;
  --page-glow-a: rgba(214, 160, 95, 0.18);
  --page-glow-b: rgba(214, 160, 95, 0.1);
  --page-dot: rgba(146, 111, 60, 0.05);
}

.sidebar {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  height: 100vh;
  flex: none;
  padding: 22px 15px 16px;
  border-right: 1px solid color-mix(in srgb, var(--role) 8%, var(--line));
  background:
    radial-gradient(circle at 0 0, color-mix(in srgb, var(--role) 9%, transparent), transparent 38%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), color-mix(in srgb, var(--role) 4%, #fff));
  backdrop-filter: blur(18px);
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 6px 18px;
}

.sidebar__brand-copy {
  min-width: 0;
  flex: 1;
}

.sidebar__brand-copy strong {
  display: block;
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 400;
  letter-spacing: 0.06em;
  line-height: 1.2;
}

.sidebar__brand-copy span {
  display: block;
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9.5px;
  letter-spacing: 0.08em;
}

.sidebar__close {
  display: none;
}

.portal-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 4px 18px;
  padding: 7px 10px;
  border: 1px solid color-mix(in srgb, var(--role) 12%, var(--line));
  border-radius: 10px;
  color: var(--role-deep);
  background: color-mix(in srgb, var(--role) 6%, #fff);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.portal-chip__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--role);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--role) 12%, transparent);
}

.portal-chip__role {
  margin-left: auto;
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
}

.side-nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.side-nav__label {
  padding: 0 12px 7px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.nav-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 12px;
  border-radius: 12px;
  color: var(--ink-2);
  font-size: 13px;
  font-weight: 500;
  transition: color 0.18s, background-color 0.18s;
}

.nav-link::before {
  content: '';
  position: absolute;
  top: 50%;
  left: -15px;
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--role);
  opacity: 0;
  transform: translateY(-50%) scaleY(0.4);
  transition: 0.24s var(--ease-out);
}

.nav-link:hover {
  color: var(--ink);
  background: color-mix(in srgb, var(--role) 4%, #f8fbfa);
}

.nav-link.is-active {
  color: var(--role-deep);
  background: color-mix(in srgb, var(--role) 10%, #fff);
  font-weight: 700;
}

.nav-link.is-active::before {
  opacity: 1;
  transform: translateY(-50%) scaleY(1);
}

.nav-link__icon {
  display: grid;
  place-items: center;
  width: 20px;
  color: var(--ink-3);
  transition: color 0.18s;
}

.nav-link.is-active .nav-link__icon {
  color: var(--role-deep);
}

.nav-link__dot {
  width: 6px;
  height: 6px;
  margin-left: auto;
  border-radius: 50%;
  background: var(--apricot);
  box-shadow: 0 0 0 3px var(--apricot-soft);
}

.current-homework {
  margin: 18px 4px 0;
  padding: 11px;
  border: 1px dashed color-mix(in srgb, var(--role) 20%, var(--line));
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.6);
}

.current-homework__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-3);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.current-homework__all {
  color: var(--role-deep);
  letter-spacing: 0;
  text-transform: none;
}

.current-homework__select {
  width: 100%;
  margin: 6px 0 7px;
  padding: 0;
  overflow: hidden;
  border: 0;
  color: var(--ink);
  background: transparent;
  font-size: 11.5px;
  font-weight: 600;
  line-height: 1.6;
  outline: none;
  text-overflow: ellipsis;
  cursor: pointer;
}

.current-homework__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: var(--ink-3);
  font-size: 10px;
}

.current-homework__meta :deep(.badge) {
  min-height: 19px;
  padding: 0 6px;
  font-size: 9.5px;
}

.boundary-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: auto 5px 12px;
  padding: 10px 11px;
  border-radius: 11px;
  color: var(--ink-3);
  background: color-mix(in srgb, var(--role) 5%, #f8fbf9);
  font-size: 10px;
  line-height: 1.65;
}

.boundary-note .icon {
  flex: none;
  margin-top: 2px;
  color: var(--role);
  opacity: 0.72;
}

.sidebar__footer {
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.side-user {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 6px;
}

.side-user__copy {
  min-width: 0;
  flex: 1;
}

.side-user__copy strong {
  display: block;
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-user__copy span {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9.5px;
}

.side-user__logout {
  width: 28px;
  height: 28px;
}

.side-user__logout:hover {
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.sidebar__legal {
  padding: 4px 8px 0;
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.02em;
}

.main-column {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  height: 66px;
  padding: 0 clamp(20px, 3.4vw, 46px);
  border-bottom: 1px solid color-mix(in srgb, var(--page-base) 55%, var(--line));
  background: color-mix(in srgb, var(--page-base) 82%, transparent);
  backdrop-filter: blur(18px);
}

.topbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.menu-button {
  display: none;
  width: 37px;
  height: 37px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 11px;
  color: var(--ink-2);
  background: #fff;
}

.crumbs {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--ink-3);
  font-size: 11.5px;
}

.crumbs strong {
  overflow: hidden;
  color: var(--ink);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.crumbs .icon {
  color: var(--ink-4);
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: 9px;
}

.today-label {
  margin-right: 3px;
  color: var(--ink-3);
  font-family: var(--font-serif);
  font-size: 11px;
  letter-spacing: 0.03em;
}

.mode-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  color: var(--ink-3);
  background: rgba(255, 255, 255, 0.68);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.03em;
}

.mode-chip__pulse {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--peri);
}

.ai-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 31px;
  padding: 0 11px;
  border: 1px solid color-mix(in srgb, var(--lav) 18%, var(--line));
  border-radius: var(--r-pill);
  color: var(--lav-deep);
  background: color-mix(in srgb, var(--lav) 7%, #fff);
  font-size: 10.5px;
  font-weight: 600;
  transition: 0.18s;
}

.ai-chip:hover {
  background: color-mix(in srgb, var(--lav) 11%, #fff);
}

.ai-chip.is-offline {
  border-color: #f0dcd6;
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.ai-chip__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lav);
  box-shadow: 0 0 0 3px rgba(149, 131, 221, 0.12);
}

.ai-chip.is-offline .ai-chip__dot {
  background: var(--coral);
  box-shadow: 0 0 0 3px rgba(217, 133, 129, 0.12);
}

.popover-anchor {
  position: relative;
}

.ai-popover,
.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 60;
}

.ai-popover {
  width: 302px;
  padding: 13px;
}

.ai-popover__head {
  display: flex;
  gap: 10px;
  padding: 4px 5px 11px;
}

.ai-popover__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 10px;
  color: var(--lav-deep);
  background: var(--lav-soft);
}

.ai-popover__head strong {
  display: block;
  font-size: 12px;
}

.ai-popover__head p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 10.5px;
  line-height: 1.6;
}

.ai-popover__switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  border-radius: 10px;
  background: var(--surface-soft);
}

.ai-popover__switch b {
  display: block;
  font-size: 11.5px;
}

.ai-popover__switch span {
  display: block;
  margin-top: 1px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.5;
}

.ai-popover__foot {
  padding: 8px 5px 0;
  color: var(--ink-4);
  font-size: 9.5px;
  line-height: 1.6;
}

.switch {
  position: relative;
  display: inline-flex;
  width: 34px;
  height: 20px;
  flex: none;
  border-radius: 999px;
  background: #d9e4df;
  transition: background 0.2s;
  cursor: pointer;
}

.switch span {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(30, 60, 50, 0.2);
  transition: transform 0.22s var(--ease-out);
}

.switch.is-on {
  background: var(--mint);
}

.switch.is-on span {
  transform: translateX(14px);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 37px;
  padding: 0 8px 0 5px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  color: var(--ink-2);
  background: rgba(255, 255, 255, 0.72);
  transition: 0.18s;
}

.user-chip:hover {
  background: #fff;
}

.user-chip__name {
  max-width: 76px;
  overflow: hidden;
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu {
  width: 226px;
  padding: 8px;
}

.user-menu__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 10px;
}

.user-menu__head strong {
  display: block;
  font-size: 12px;
}

.user-menu__head span {
  display: block;
  margin-top: 1px;
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 9.5px;
}

.content {
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: clamp(22px, 3.2vw, 42px) clamp(20px, 3.4vw, 46px) 38px;
  flex: 1;
}

.app-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: var(--content-max);
  width: 100%;
  margin: 0 auto;
  padding: 0 clamp(20px, 3.4vw, 46px) 26px;
  color: var(--ink-4);
  font-size: 9.5px;
  letter-spacing: 0.02em;
}

.app-footer__dot {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
}

.app-footer__spacer {
  flex: 1;
}

.nav-backdrop {
  position: fixed;
  inset: 0;
  z-index: 39;
  background: rgba(28, 48, 41, 0.32);
  backdrop-filter: blur(3px);
}

.pop-enter-active,
.pop-leave-active {
  transition: opacity 0.18s var(--ease-soft), transform 0.22s var(--ease-out);
  transform-origin: top right;
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: scale(0.97) translateY(-4px);
}

@media (max-width: 1180px) {
  .today-label {
    display: none;
  }
}

@media (max-width: 1024px) {
  .sidebar {
    position: fixed;
    left: 0;
    transform: translateX(-105%);
    transition: transform 0.32s var(--ease-out);
    box-shadow: 24px 0 70px -40px rgba(25, 55, 45, 0.5);
  }

  .sidebar.is-open {
    transform: translateX(0);
  }

  .sidebar__close {
    display: inline-flex;
    width: 40px;
    height: 40px;
  }

  .side-user__logout {
    width: 40px;
    height: 40px;
  }

  .menu-button {
    display: grid;
    width: 40px;
    height: 40px;
  }

  .topbar {
    padding-right: 20px;
    padding-left: 16px;
  }
}

@media (max-width: 760px) {
  .crumbs > span:first-child,
  .crumbs > .icon {
    display: none;
  }

  .mode-chip,
  .user-chip__name,
  .user-chip > .icon,
  .ai-chip > span:not(.ai-chip__dot) {
    display: none;
  }

  .ai-chip {
    width: 40px;
    height: 40px;
    padding: 0;
    justify-content: center;
  }

  .user-chip {
    width: 40px;
    height: 40px;
    padding: 0;
    justify-content: center;
  }

  .user-chip .avatar {
    width: 30px;
    height: 30px;
    font-size: 11px;
  }

  .content {
    padding-top: 22px;
    padding-right: 16px;
    padding-left: 16px;
  }

  .app-footer {
    padding-right: 16px;
    padding-left: 16px;
    flex-wrap: wrap;
  }

  .app-footer__dot,
  .app-footer__spacer,
  .app-footer > span:nth-child(3) {
    display: none;
  }

  .ai-popover {
    right: -42px;
    width: min(302px, calc(100vw - 24px));
  }
}
</style>
