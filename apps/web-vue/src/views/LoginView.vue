<script setup>
import {
  ArrowRight,
  BarChart3,
  BookOpenCheck,
  CheckCircle2,
  Eye,
  EyeOff,
  GraduationCap,
  LockKeyhole,
  ShieldCheck,
  Sigma,
  Sparkles,
  UserRound,
} from "@lucide/vue";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { getUser } from "../services/api";

const router = useRouter();
const role = ref("teacher");
const account = ref("");
const password = ref("");
const showPassword = ref(false);
const loading = ref(false);
const session = ref(null);
const error = ref("");

const roles = [
  { id: "teacher", label: "教师端", icon: BookOpenCheck },
  { id: "learner", label: "学生端", icon: GraduationCap },
  { id: "research", label: "教研端", icon: ShieldCheck },
];

async function enterWorkspace() {
  loading.value = true;
  error.value = "";
  try {
    session.value = await getUser().catch(() => null);
    if (role.value === "learner") {
      const assignmentId = session.value?.assignmentId || 1;
      await router.push(`/learner/${assignmentId}`);
      return;
    }
    if (role.value === "research") {
      const assignmentId = session.value?.assignmentId || 1;
      await router.push(`/review/${assignmentId}`);
      return;
    }
    await router.push("/dashboard");
  } catch (requestError) {
    error.value = requestError?.message || "暂时无法连接服务，请稍后重试。";
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  session.value = await getUser().catch(() => null);
});
</script>

<template>
  <main class="login-page">
    <section class="login-brand">
      <div class="brand-header">
        <span class="brand-logo"><Sigma :size="23" /></span>
        <div><strong>Mark</strong><span>作业批改与学情分析</span></div>
      </div>

      <div class="brand-copy">
        <p class="brand-eyebrow">让每一次批改都成为学习反馈</p>
        <h1>把出题、批改和讲评，<br /><em>放进同一条清晰路径。</em></h1>
        <p class="lead">
          面向初中数学教学，用真实作答数据发现有价值的问题，把时间留给学生和讲评。
        </p>
      </div>

      <div class="feature-grid">
        <article>
          <span><Sparkles :size="18" /></span>
          <div><strong>AI 草稿</strong><p>只生成可审核的分析与讲评</p></div>
        </article>
        <article>
          <span><BarChart3 :size="18" /></span>
          <div><strong>学情洞察</strong><p>快速定位薄弱题目与知识点</p></div>
        </article>
        <article>
          <span><CheckCircle2 :size="18" /></span>
          <div><strong>教学闭环</strong><p>出题、发布、作答、订正与讲评</p></div>
        </article>
      </div>

      <p class="brand-footnote">Mark · 教学数据仅在当前部署环境内处理</p>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="mobile-brand">
          <span class="brand-logo"><Sigma :size="21" /></span>
          <strong>Mark</strong>
        </div>
        <p class="login-eyebrow">欢迎回来</p>
        <h2>登录教学工作台</h2>
        <p class="login-description">选择身份后进入对应的教学或学习空间。</p>

        <div class="role-tabs" role="tablist" aria-label="登录身份">
          <button
            v-for="item in roles"
            :key="item.id"
            type="button"
            role="tab"
            :aria-selected="role === item.id"
            :class="{ active: role === item.id }"
            @click="role = item.id"
          >
            <component :is="item.icon" :size="16" />
            {{ item.label }}
          </button>
        </div>

        <div v-if="session" class="session-hint">
          <CheckCircle2 :size="15" />
          已检测到平台会话：{{ session.userId }}
        </div>

        <form class="login-form" @submit.prevent="enterWorkspace">
          <label>
            <span class="field-label">账号</span>
            <span class="input-wrap">
              <UserRound :size="16" />
              <input
                v-model="account"
                class="input login-input"
                type="text"
                autocomplete="username"
                placeholder="请输入账号或邮箱"
              />
            </span>
          </label>
          <label>
            <span class="field-label">密码</span>
            <span class="input-wrap">
              <LockKeyhole :size="16" />
              <input
                v-model="password"
                class="input login-input"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
              <button
                type="button"
                class="password-toggle"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" :size="16" />
                <Eye v-else :size="16" />
              </button>
            </span>
          </label>

          <div class="login-options">
            <label class="remember"><input type="checkbox" /> 记住登录状态</label>
            <button type="button">忘记密码？</button>
          </div>

          <p v-if="error" class="alert alert-danger" role="alert">{{ error }}</p>

          <button
            class="button button-primary login-submit"
            type="submit"
            :disabled="loading"
          >
            {{ loading ? "正在进入…" : "进入工作台" }}
            <ArrowRight v-if="!loading" :size="17" />
          </button>
        </form>

        <p class="login-note">
          本地部署已关闭登录校验时，可直接进入工作台。正式环境请通过学校平台单点登录或教研账号验证。
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100dvh;
  grid-template-columns: minmax(500px, 1.08fr) minmax(440px, 0.92fr);
  background:
    radial-gradient(circle at 8% 0%, rgb(47 184 173 / 0.17), transparent 34%),
    radial-gradient(rgb(64 108 94 / 0.055) 0.75px, transparent 0.75px),
    var(--bg);
  background-size: auto, 22px 22px, auto;
}

.login-brand {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(32px, 5vw, 66px);
}

.login-brand::before,
.login-brand::after {
  position: absolute;
  border-radius: 50%;
  content: "";
  pointer-events: none;
}

.login-brand::before {
  right: -120px;
  bottom: -170px;
  width: 440px;
  height: 440px;
  border: 1px solid rgb(47 184 173 / 0.16);
  box-shadow:
    0 0 0 64px rgb(47 184 173 / 0.035),
    0 0 0 140px rgb(107 140 245 / 0.025);
}

.login-brand::after {
  top: 18%;
  right: 9%;
  width: 12px;
  height: 12px;
  background: var(--apricot);
  box-shadow:
    -126px 106px 0 -3px var(--peri),
    88px 144px 0 -4px var(--mint);
  opacity: 0.55;
}

.brand-header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 11px;
}

.brand-logo {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border: 1px solid rgb(47 184 173 / 0.2);
  border-radius: 15px;
  background: rgb(255 255 255 / 0.68);
  color: var(--mint-deep);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(12px);
}

.brand-header div {
  display: flex;
  flex-direction: column;
}

.brand-header strong {
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 400;
  letter-spacing: 0.07em;
}

.brand-header span {
  color: var(--ink-3);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.brand-copy {
  position: relative;
  z-index: 1;
  max-width: 690px;
  margin: auto 0;
  padding: 48px 0;
}

.brand-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 17px;
  color: var(--mint-deep);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.15em;
}

.brand-eyebrow::before {
  width: 20px;
  height: 1px;
  background: currentColor;
  content: "";
  opacity: 0.6;
}

.brand-copy h1 {
  max-width: 650px;
  font-family: var(--font-display);
  font-size: clamp(38px, 4.5vw, 62px);
  font-weight: 400;
  line-height: 1.34;
  letter-spacing: 0.02em;
}

.brand-copy h1 em {
  color: var(--mint-deep);
  font-style: normal;
}

.brand-copy .lead {
  max-width: 550px;
  margin-top: 20px;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.9;
}

.feature-grid {
  position: relative;
  z-index: 1;
  display: grid;
  max-width: 690px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.feature-grid article {
  display: flex;
  gap: 10px;
  border: 1px solid rgb(210 227 220 / 0.8);
  border-radius: 16px;
  background: rgb(255 255 255 / 0.52);
  padding: 13px;
  backdrop-filter: blur(12px);
}

.feature-grid article > span {
  display: grid;
  width: 31px;
  height: 31px;
  flex: 0 0 31px;
  place-items: center;
  border-radius: 10px;
  background: var(--mint-soft);
  color: var(--mint-deep);
}

.feature-grid strong {
  font-size: 11px;
}

.feature-grid p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.55;
}

.brand-footnote {
  position: relative;
  z-index: 1;
  margin-top: 28px;
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.login-panel {
  display: grid;
  place-items: center;
  padding: 38px;
}

.login-card {
  width: min(100%, 430px);
  border: 1px solid rgb(228 238 233 / 0.9);
  border-radius: 26px;
  background: rgb(255 255 255 / 0.84);
  padding: 34px;
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(18px);
}

.mobile-brand {
  display: none;
}

.login-eyebrow {
  color: var(--mint-deep);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.login-card h2 {
  margin-top: 6px;
  font-family: var(--font-display);
  font-size: 31px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.login-description {
  margin-top: 6px;
  color: var(--ink-2);
  font-size: 12px;
}

.role-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  margin-top: 24px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgb(247 251 249 / 0.9);
  padding: 4px;
}

.role-tabs button {
  display: flex;
  min-height: 38px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border-radius: 999px;
  background: transparent;
  color: var(--ink-3);
  font-size: 10.5px;
  font-weight: 650;
}

.role-tabs button.active {
  background: white;
  color: var(--mint-deep);
  box-shadow: var(--shadow-sm);
}

.session-hint {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 13px;
  border: 1px solid #d8eee6;
  border-radius: 11px;
  background: var(--success-soft);
  padding: 9px 11px;
  color: var(--success);
  font-size: 10.5px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 21px;
}

.input-wrap {
  position: relative;
  display: block;
}

.input-wrap > svg {
  position: absolute;
  top: 50%;
  left: 13px;
  color: var(--ink-3);
  transform: translateY(-50%);
}

.login-input {
  height: 44px;
  padding-right: 42px;
  padding-left: 40px;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 5px;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 50%;
  background: transparent;
  color: var(--ink-3);
  transform: translateY(-50%);
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 10.5px;
}

.remember {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--ink-2);
}

.remember input {
  accent-color: var(--mint);
}

.login-options button {
  border: 0;
  background: transparent;
  padding: 4px;
  color: var(--mint-deep);
}

.login-submit {
  width: 100%;
  margin-top: 2px;
}

.login-note {
  margin-top: 18px;
  border-top: 1px solid var(--line);
  padding-top: 14px;
  color: var(--ink-4);
  font-size: 9.5px;
  line-height: 1.7;
  text-align: center;
}

@media (max-width: 1020px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr);
  }

  .login-brand {
    display: none;
  }

  .login-panel {
    min-height: 100dvh;
    padding: 24px;
  }
}

@media (max-width: 520px) {
  .login-panel {
    padding: 0;
  }

  .login-card {
    min-height: 100dvh;
    border: 0;
    border-radius: 0;
    padding: 28px 20px;
    box-shadow: none;
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 9px;
    margin-bottom: 28px;
  }

  .mobile-brand .brand-logo {
    width: 38px;
    height: 38px;
  }

  .role-tabs button {
    min-height: 48px;
    flex-direction: column;
  }
}
</style>
