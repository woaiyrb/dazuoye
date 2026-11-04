<script setup>
import {
  Bell,
  BrainCircuit,
  BookOpen,
  ChartNoAxesCombined,
  ChevronRight,
  Home,
  Menu,
  RotateCcw,
  Search,
  Settings,
  Sigma,
  Sparkles,
  Users,
  X,
} from "@lucide/vue";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { getAssignments, getUser } from "../services/api";

const route = useRoute();
const router = useRouter();
const user = ref(null);
const javaStatus = ref(null);
const sidebarOpen = ref(false);
const isMobile = ref(false);
const searchQuery = ref("");
const searchFocused = ref(false);
const assignments = ref([]);
const selectedAssignmentId = ref(null);

const assignmentId = computed(
  () =>
    selectedAssignmentId.value ||
    user.value?.assignmentId ||
    assignments.value[0]?.id ||
    1,
);
const displayName = computed(() => {
  const raw = user.value?.userId || "教师用户";
  const local = raw.split("@")[0];
  if (["user", "admin", "teacher"].includes(local.toLowerCase())) {
    return local.toLowerCase() === "admin" ? "管理员" : "教师用户";
  }
  return local;
});
const initials = computed(() => displayName.value.slice(0, 1));
const roleLabel = computed(() => {
  const role = String(user.value?.role || "").toLowerCase();
  if (role === "author" || role === "teacher") return "教师账号";
  if (role === "admin") return "管理账号";
  return user.value ? "学习账号" : "正在识别身份";
});
const aiOnline = computed(() => Boolean(javaStatus.value?.deepTutorReady));
const pageTitle = computed(() => route.meta.title || "工作台");

const navigation = computed(() => [
  { label: "工作台", to: "/dashboard", icon: Home },
  { label: "作业编辑", to: `/author/${assignmentId.value}`, icon: BookOpen },
  { label: "批量出题", to: `/author/${assignmentId.value}/generate`, icon: Sparkles },
  { label: "批改统计", to: `/grading/${assignmentId.value}`, icon: ChartNoAxesCombined },
  { label: "讲评审核", to: `/review/${assignmentId.value}`, icon: BrainCircuit },
  { label: "订正跟踪", to: "/corrections", icon: RotateCcw },
  { label: "班级学生", to: "/students", icon: Users },
  { label: "知识点", to: "/knowledge-points", icon: Sparkles },
  { label: "系统设置", to: "/settings", icon: Settings },
]);

const searchResults = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return [];
  return navigation.value.filter((item) =>
    item.label.toLowerCase().includes(query),
  );
});

function isActive(path) {
  if (path === "/dashboard") return route.path === "/dashboard";
  return route.path.startsWith(path);
}

async function changeAssignment(event) {
  const nextId = Number(event.target.value);
  if (!nextId) return;
  selectedAssignmentId.value = nextId;
  if (route.path.startsWith("/author/")) {
    const suffix = route.path.endsWith("/generate") ? "/generate" : "/questions";
    await router.push(`/author/${nextId}${suffix}`);
  } else if (route.path.startsWith("/grading/")) {
    await router.push(`/grading/${nextId}`);
  } else if (route.path.startsWith("/review/")) {
    await router.push(`/review/${nextId}`);
  }
}

function submitSearch() {
  const target = searchResults.value[0];
  if (!target) return;
  router.push(target.to);
  searchQuery.value = "";
  searchFocused.value = false;
}

function hideSearchSoon() {
  window.setTimeout(() => {
    searchFocused.value = false;
  }, 120);
}

function syncViewport() {
  isMobile.value = window.innerWidth <= 1040;
  if (!isMobile.value) sidebarOpen.value = false;
}

onMounted(async () => {
  syncViewport();
  window.addEventListener("resize", syncViewport);
  const [session, statusFromJava, assignmentRows] = await Promise.all([
    getUser().catch(() => null),
    fetch("/java-api/ai/status", { credentials: "include" })
      .then((response) => (response.ok ? response.json() : null))
      .catch(() => null),
    getAssignments().catch(() => []),
  ]);
  user.value = session;
  javaStatus.value = statusFromJava;
  assignments.value = assignmentRows;
  selectedAssignmentId.value = session?.assignmentId || assignmentRows[0]?.id || 1;
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", syncViewport);
});
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="app-shell" data-role="teacher">
    <div
      v-if="sidebarOpen"
      class="mobile-scrim"
      aria-hidden="true"
      @click="sidebarOpen = false"
    ></div>

    <aside
      class="sidebar"
      :class="{ 'is-open': sidebarOpen }"
      :aria-hidden="isMobile && !sidebarOpen ? 'true' : undefined"
      :inert="isMobile && !sidebarOpen ? '' : undefined"
    >
      <div class="brand-row">
        <div class="brand-mark" aria-hidden="true">
          <Sigma :size="22" :stroke-width="2" />
        </div>
        <div class="brand-copy">
          <strong>Mark</strong>
          <span>作业批改与学情分析</span>
        </div>
        <button
          class="icon-button sidebar-close"
          type="button"
          aria-label="关闭导航"
          @click="sidebarOpen = false"
        >
          <X :size="20" />
        </button>
      </div>

      <div class="portal-chip">
        <span class="portal-dot"></span>
        教师工作台
        <small>TEACHER</small>
      </div>

      <nav class="side-nav" aria-label="主要导航">
        <p class="nav-caption">教学工作区</p>
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item.to) }"
          @click="sidebarOpen = false"
        >
          <span class="nav-icon">
            <component :is="item.icon" :size="18" :stroke-width="1.8" />
          </span>
          <span>{{ item.label }}</span>
          <ChevronRight v-if="isActive(item.to)" class="nav-chevron" :size="14" />
        </RouterLink>
      </nav>

      <div class="assignment-switcher">
        <div class="switcher-head">
          <span>当前作业</span>
          <RouterLink to="/dashboard">全部</RouterLink>
        </div>
        <select
          :value="assignmentId"
          aria-label="切换当前作业"
          @change="changeAssignment"
        >
          <option v-for="item in assignments" :key="item.id" :value="item.id">
            {{ item.name }}
          </option>
          <option v-if="!assignments.length" :value="assignmentId">当前作业</option>
        </select>
        <p>
          <span>选择后同步到编辑、批改与讲评页面</span>
          <span class="switcher-status">{{ assignments.length }} 份</span>
        </p>
      </div>

      <div class="side-insight">
        <div class="insight-heading">
          <span class="insight-icon"><Sparkles :size="15" /></span>
          <span>AI 教学草稿</span>
        </div>
        <p>
          {{ aiOnline ? "DeepTutor 已连接，可生成题目和讲评草稿。" : "DeepTutor 暂未就绪，教师仍可手工完成。" }}
        </p>
        <div class="status-line">
          <span :class="{ online: aiOnline }"></span>
          {{ aiOnline ? "服务正常" : "等待连接" }}
        </div>
      </div>

      <div class="account-card">
        <div class="avatar">{{ initials }}</div>
        <div class="account-copy">
          <strong>{{ displayName }}</strong>
          <span>{{ roleLabel }}</span>
        </div>
        <RouterLink to="/settings" class="account-link" aria-label="账号设置">
          <Settings :size="16" />
        </RouterLink>
      </div>
    </aside>

    <div class="app-main">
      <header class="topbar">
        <div class="topbar-left">
          <button
            class="icon-button mobile-menu"
            type="button"
            aria-label="打开导航"
            @click="sidebarOpen = true"
          >
            <Menu :size="21" />
          </button>
          <div class="crumb-copy">
            <span>Mark 教学中心</span>
            <strong>{{ pageTitle }}</strong>
          </div>
        </div>

        <div class="topbar-actions">
          <div class="search-wrap">
            <Search class="search-icon" :size="16" />
            <input
              v-model="searchQuery"
              class="topbar-search"
              type="search"
              placeholder="搜索页面或功能"
              aria-label="搜索页面或功能"
              @focus="searchFocused = true"
              @blur="hideSearchSoon"
              @keyup.enter="submitSearch"
            />
            <div v-if="searchFocused && searchResults.length" class="search-popover">
              <button
                v-for="result in searchResults"
                :key="result.to"
                type="button"
                @click="router.push(result.to)"
              >
                <component :is="result.icon" :size="15" />
                {{ result.label }}
              </button>
            </div>
          </div>
          <span class="ai-status" :class="{ online: aiOnline }">
            <span></span>
            AI {{ aiOnline ? "正常" : "未连接" }}
          </span>
          <button class="icon-button" type="button" aria-label="通知">
            <Bell :size="18" />
          </button>
          <RouterLink
            :to="'/author/' + assignmentId + '/questions'"
            class="button button-primary topbar-primary"
          >
            <BookOpen :size="16" />
            编辑作业
          </RouterLink>
        </div>
      </header>

      <main id="main-content" class="content" tabindex="-1">
        <RouterView />
      </main>

      <footer class="app-footer">
        <span>Mark · 初中数学作业批改与讲评</span>
        <span>教师确认后发布 · 学生答案分离保存</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100dvh;
}

.sidebar {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  width: var(--sidebar-width);
  height: 100dvh;
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  border-right: 1px solid rgb(219 233 227 / 0.88);
  background:
    radial-gradient(circle at 0 0, rgb(47 184 173 / 0.08), transparent 38%),
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(248 252 250 / 0.97));
  padding: 20px 14px 15px;
  backdrop-filter: blur(18px);
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 7px 16px;
}

.brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  border: 1px solid rgb(47 184 173 / 0.18);
  border-radius: 15px;
  background: linear-gradient(145deg, #f7fffd, #e2f7f2);
  color: var(--mint-deep);
  box-shadow: 0 8px 20px -14px rgb(23 127 120 / 0.65);
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.brand-copy strong {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 400;
  line-height: 1.2;
  letter-spacing: 0.06em;
}

.brand-copy span {
  overflow: hidden;
  color: var(--ink-3);
  font-size: 9.5px;
  letter-spacing: 0.05em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-close {
  display: none;
  margin-left: auto;
}

.portal-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 5px 15px;
  border: 1px solid rgb(47 184 173 / 0.13);
  border-radius: 11px;
  background: var(--mint-wash);
  padding: 7px 10px;
  color: var(--mint-deep);
  font-size: 11px;
  font-weight: 600;
}

.portal-chip small {
  margin-left: auto;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
}

.portal-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--mint);
  box-shadow: 0 0 0 3px rgb(47 184 173 / 0.12);
}

.side-nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav-caption {
  padding: 3px 12px 6px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.13em;
}

.nav-item {
  position: relative;
  display: flex;
  min-height: 40px;
  align-items: center;
  gap: 9px;
  border-radius: 12px;
  padding: 7px 11px;
  color: var(--ink-2);
  font-size: 12.5px;
  font-weight: 500;
  transition: color 180ms, background-color 180ms;
}

.nav-item::before {
  position: absolute;
  top: 50%;
  left: -14px;
  width: 3px;
  height: 17px;
  border-radius: 0 3px 3px 0;
  background: var(--role);
  content: "";
  opacity: 0;
  transform: translateY(-50%) scaleY(0.4);
  transition: 220ms var(--ease-out);
}

.nav-item:hover {
  background: #f5faf8;
  color: var(--ink);
}

.nav-item.active {
  background: var(--mint-soft);
  color: var(--mint-deep);
  font-weight: 700;
}

.nav-item.active::before {
  opacity: 1;
  transform: translateY(-50%) scaleY(1);
}

.nav-icon {
  display: grid;
  width: 20px;
  place-items: center;
  color: var(--ink-3);
}

.nav-item.active .nav-icon {
  color: var(--mint-deep);
}

.nav-chevron {
  margin-left: auto;
}

.assignment-switcher {
  margin: 14px 5px 0;
  border: 1px dashed rgb(47 184 173 / 0.22);
  border-radius: 13px;
  background: rgb(255 255 255 / 0.58);
  padding: 10px;
}

.switcher-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-3);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.switcher-head a {
  color: var(--mint-deep);
  font-weight: 700;
  letter-spacing: 0;
}

.assignment-switcher select {
  width: 100%;
  margin: 5px 0 6px;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 11px;
  font-weight: 650;
  outline: none;
  text-overflow: ellipsis;
  cursor: pointer;
}

.assignment-switcher p {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.5;
}

.switcher-status {
  flex: none;
  color: var(--mint-deep);
  font-family: var(--font-mono);
}

.side-insight {
  margin-top: auto;
  border: 1px solid rgb(149 131 221 / 0.16);
  border-radius: 15px;
  background: linear-gradient(145deg, #faf8ff, #f2effc);
  padding: 12px;
}

.insight-heading {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--lav-deep);
  font-size: 11px;
  font-weight: 700;
}

.insight-icon {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  border-radius: 9px;
  background: white;
  color: var(--lav);
  box-shadow: var(--shadow-xs);
}

.side-insight p {
  margin-top: 7px;
  color: var(--ink-2);
  font-size: 9.5px;
  line-height: 1.65;
}

.status-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: var(--ink-3);
  font-size: 9.5px;
}

.status-line span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ink-4);
}

.status-line span.online {
  background: var(--success);
  box-shadow: 0 0 0 3px var(--success-soft);
}

.account-card {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 10px;
  border-top: 1px solid var(--line);
  padding: 11px 6px 0;
}

.avatar {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 1px solid rgb(47 184 173 / 0.16);
  border-radius: 50%;
  background: var(--mint-soft);
  color: var(--mint-deep);
  font-family: var(--font-display);
  font-size: 15px;
}

.account-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.account-copy strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-copy span {
  color: var(--ink-3);
  font-size: 9.5px;
}

.account-link {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  color: var(--ink-3);
}

.account-link:hover {
  background: var(--surface-soft);
  color: var(--ink);
}

.app-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  min-height: 66px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid rgb(225 237 231 / 0.82);
  background: rgb(243 248 245 / 0.84);
  padding: 10px clamp(20px, 3.2vw, 42px);
  backdrop-filter: blur(18px);
}

.topbar-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
}

.crumb-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.crumb-copy span {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.crumb-copy strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 7px;
}

.mobile-menu {
  display: none;
}

.search-wrap {
  position: relative;
  margin-right: 2px;
}

.search-icon {
  position: absolute;
  top: 50%;
  left: 12px;
  color: var(--ink-3);
  transform: translateY(-50%);
}

.topbar-search {
  width: 225px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgb(255 255 255 / 0.76);
  padding: 0 13px 0 36px;
  color: var(--ink);
  font-size: 11.5px;
  outline: none;
}

.topbar-search:focus {
  border-color: color-mix(in srgb, var(--mint) 52%, var(--line));
  background: #fff;
  box-shadow: 0 0 0 4px rgb(47 184 173 / 0.09);
}

.search-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  left: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgb(255 255 255 / 0.98);
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(16px);
}

.search-popover button {
  display: flex;
  min-height: 40px;
  width: 100%;
  align-items: center;
  gap: 9px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: #fff;
  padding: 8px 12px;
  color: var(--ink-2);
  font-size: 11.5px;
  text-align: left;
}

.search-popover button:hover {
  background: var(--mint-wash);
  color: var(--mint-deep);
}

.ai-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 29px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgb(255 255 255 / 0.68);
  padding: 0 10px;
  color: var(--ink-3);
  font-size: 9.5px;
}

.ai-status > span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ink-4);
}

.ai-status.online {
  border-color: rgb(58 151 123 / 0.18);
  background: var(--success-soft);
  color: var(--success);
}

.ai-status.online > span {
  background: var(--success);
}

.topbar-primary {
  min-height: 38px;
  margin-left: 2px;
  padding: 7px 14px;
  font-size: 11.5px;
}

.content {
  min-width: 0;
  flex: 1;
  outline: none;
}

.app-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 0 clamp(20px, 3.2vw, 42px) 22px;
  color: var(--ink-4);
  font-size: 9px;
}

.mobile-scrim {
  display: none;
}

@media (max-width: 1180px) {
  .ai-status {
    display: none;
  }
}

@media (max-width: 1040px) {
  .sidebar {
    position: fixed;
    left: 0;
    transform: translateX(-105%);
    transition: transform 320ms var(--ease-out);
  }

  .sidebar.is-open {
    transform: translateX(0);
    box-shadow: 28px 0 80px -44px rgb(25 55 45 / 0.5);
  }

  .sidebar-close,
  .mobile-menu {
    display: inline-flex;
    width: 40px;
    height: 40px;
  }

  .mobile-scrim {
    position: fixed;
    inset: 0;
    z-index: 35;
    display: block;
    background: rgb(28 48 41 / 0.32);
    backdrop-filter: blur(3px);
  }
}

@media (max-width: 760px) {
  .topbar {
    min-height: 62px;
    padding: 9px 16px;
  }

  .search-wrap,
  .topbar-actions > .icon-button,
  .app-footer {
    display: none;
  }

  .topbar-primary {
    width: 40px;
    padding: 0;
    font-size: 0;
  }

  .topbar-primary .icon {
    margin: 0;
  }

  .content :deep(.page) {
    padding: 20px 16px 38px;
  }
}
</style>
