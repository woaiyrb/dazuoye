<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, errorMessage } from '@/api/errors'
import { isMockMode } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { PortalRole } from '@/types/models'
import AppIcon from '@/components/AppIcon.vue'
import BrandMark from '@/components/BrandMark.vue'
import AppModal from '@/components/AppModal.vue'

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const selectedRole = ref<PortalRole>('TEACHER')
const username = ref(auth.rememberedUsername)
const password = ref('')
const remember = ref(Boolean(auth.rememberedUsername))
const showPassword = ref(false)
const errorText = ref('')
const attemptsRemaining = ref<number | null>(null)
const failureCount = ref(0)
const captchaRequired = computed(() => failureCount.value >= 3)
const captchaInput = ref('')
const captchaCode = ref('')
const lockedSeconds = ref(0)
const forgotOpen = ref(false)
const submitting = ref(false)
let countdownTimer: number | undefined

const roleOptions: Array<{
  value: PortalRole
  label: string
  hint: string
  icon: 'graduation' | 'book-open' | 'compass'
  account: string
  name: string
}> = [
  {
    value: 'TEACHER',
    label: '教师',
    hint: '批改 · 讲评 · 发布',
    icon: 'graduation',
    account: 'teacher01',
    name: '王老师',
  },
  {
    value: 'STUDENT',
    label: '学生',
    hint: '讲解 · 订正 · 疑问',
    icon: 'book-open',
    account: 'S2024013',
    name: '小李',
  },
  {
    value: 'RESEARCH',
    label: '教研',
    hint: '知识点 · 模板',
    icon: 'compass',
    account: 'research01',
    name: '陈老师',
  },
]

const currentRole = computed(() => roleOptions.find((item) => item.value === selectedRole.value)!)
const isLocked = computed(() => lockedSeconds.value > 0)
const loginDisabled = computed(() => submitting.value || auth.loading || isLocked.value)

function randomCaptcha(): string {
  const alphabet = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'
  return Array.from({ length: 4 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join('')
}

function refreshCaptcha(): void {
  captchaCode.value = randomCaptcha()
  captchaInput.value = ''
}

function selectRole(role: PortalRole): void {
  selectedRole.value = role
  errorText.value = ''
  attemptsRemaining.value = null
}

function fillDemo(account: string, role: PortalRole): void {
  selectedRole.value = role
  username.value = account
  password.value = '123456'
  errorText.value = ''
}

function remainingLockSeconds(payload?: {
  retryAfterSeconds?: number
  lockedUntil?: string
}): number {
  if (payload?.lockedUntil) {
    const until = new Date(payload.lockedUntil).getTime()
    if (Number.isFinite(until)) {
      return Math.max(1, Math.ceil((until - Date.now()) / 1000))
    }
  }
  return payload?.retryAfterSeconds ?? 60
}

function startCountdown(seconds: number): void {
  lockedSeconds.value = Math.max(1, Math.ceil(seconds))
  if (countdownTimer) window.clearInterval(countdownTimer)
  countdownTimer = window.setInterval(() => {
    if (lockedSeconds.value <= 1) {
      lockedSeconds.value = 0
      failureCount.value = 0
      attemptsRemaining.value = null
      errorText.value = ''
      if (countdownTimer) window.clearInterval(countdownTimer)
      return
    }
    lockedSeconds.value -= 1
  }, 1000)
}

async function submit(): Promise<void> {
  if (loginDisabled.value) return
  errorText.value = ''
  attemptsRemaining.value = null

  if (!username.value.trim() || !password.value) {
    errorText.value = '请输入账号与密码。'
    return
  }
  if (password.value.length < 6) {
    errorText.value = '密码至少 6 位。'
    return
  }
  if (captchaRequired.value && captchaInput.value.trim().toUpperCase() !== captchaCode.value) {
    errorText.value = '图形验证码不正确，请重新输入。'
    refreshCaptcha()
    return
  }

  submitting.value = true
  try {
    await auth.login(username.value.trim(), password.value, selectedRole.value)
    auth.rememberUsername(username.value.trim(), remember.value)
    ui.toast('success', `欢迎回来，${auth.displayName}`, '已按责任范围加载你的工作区。')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = redirect.startsWith(`/${selectedRole.value.toLowerCase()}`)
      ? redirect
      : auth.homePath
    await router.replace(target)
  } catch (cause) {
    const apiError = cause instanceof ApiError ? cause : null
    failureCount.value += 1
    errorText.value = errorMessage(cause)
    const payload = apiError?.data as
      | { attemptsRemaining?: number; retryAfterSeconds?: number; lockedUntil?: string }
      | undefined
    if (payload?.attemptsRemaining !== undefined) {
      attemptsRemaining.value = payload.attemptsRemaining
    }
    if (apiError?.code === 'AUTH_ACCOUNT_LOCKED') {
      failureCount.value = 5
      startCountdown(remainingLockSeconds(payload))
    } else if (captchaRequired.value) {
      refreshCaptcha()
    }
  } finally {
    submitting.value = false
  }
}

function handleForgot(): void {
  forgotOpen.value = true
}

function closeForgot(): void {
  forgotOpen.value = false
}

function submitForgot(): void {
  forgotOpen.value = false
  ui.toast('info', '已记录找回请求（演示）', '按数据最小化约定，请携带学生证/工牌联系管理员线下重置。')
}

onMounted(() => {
  refreshCaptcha()
  if (!username.value) username.value = ''
})

onBeforeUnmount(() => {
  if (countdownTimer) window.clearInterval(countdownTimer)
})
</script>

<template>
  <div class="login-view" :data-role="selectedRole.toLowerCase()">
    <div class="login-canvas">
      <div class="canvas-grid" aria-hidden="true" />
      <div class="canvas-glow canvas-glow--accent" aria-hidden="true" />
      <div class="canvas-glow canvas-glow--peri" aria-hidden="true" />
      <span class="canvas-formula canvas-formula--sigma" aria-hidden="true">Σ</span>
      <span class="canvas-formula canvas-formula--fraction" aria-hidden="true">x / 60</span>

      <header class="login-brand">
        <BrandMark :size="46" />
        <div>
          <strong>错题星球</strong>
          <span>数学讲评与错题跟踪助手</span>
        </div>
        <span class="login-brand__chapter">初一 · 数学</span>
      </header>

      <section class="login-hero">
        <span class="hero-kicker">MISTAKE → UNDERSTANDING</span>
        <h1>让每一道错题，<br /><em>都有回音。</em></h1>
        <p>
          从批改数据、错误分布，到有依据的 AI 讲评草稿；<br />
          老师确认之后，才成为学生看得到的那一页讲解。
        </p>
        <div class="hero-principles">
          <span><AppIcon name="shield" :size="13" /> AI 不改分</span>
          <span><AppIcon name="check-double" :size="13" /> 教师终审</span>
          <span><AppIcon name="user" :size="13" /> 学生只看自己</span>
        </div>
      </section>

      <div class="proof-desk" aria-hidden="true">
        <div class="paper-sheet">
          <div class="paper-sheet__margin" />
          <div class="paper-sheet__head">
            <span>初一(3)班 · 数学</span>
            <span>第 5 题 / 8</span>
          </div>
          <p class="paper-sheet__label">典型错误</p>
          <p class="paper-sheet__formula">
            2(x−3) = x+1
            <span class="paper-sheet__wrong">2x−3 = x+1</span>
            <span class="paper-sheet__arrow">→</span>
            2x−6 = x+1
          </p>
          <p class="paper-sheet__note">
            括号前是负号，括号内<b>每一项</b>都要变号。
          </p>
          <div class="paper-sheet__answer">
            <span>正确答案</span>
            <b>A. x = 7</b>
          </div>
          <div class="confirm-stamp">教师已确认<br /><span>REVIEWED</span></div>
          <div class="paper-sheet__holes">
            <i /><i /><i />
          </div>
        </div>

        <div class="float-card float-card--rate">
          <span class="float-card__eyebrow">本题错误率</span>
          <div class="float-card__value">62.2<span>%</span></div>
          <div class="rate-bars">
            <i style="--w: 28%" /><i style="--w: 62%" /><i style="--w: 44%" /><i style="--w: 53%" />
          </div>
          <span class="float-card__foot">28 / 45 人答错 · 重点讲评</span>
        </div>

        <div class="float-card float-card--ai">
          <div class="float-card__head">
            <span class="ai-orb"><AppIcon name="sparkles" :size="15" /></span>
            <div>
              <b>AI 讲评草稿</b>
              <span>依据快照 4 条</span>
            </div>
            <span class="ai-status">候选</span>
          </div>
          <p>“错选 B 的学生，多因去括号时只对第一项变号。”</p>
          <div class="evidence-dots">
            <span>原题</span><span>答案</span><span>解析</span><span>统计</span>
          </div>
        </div>

        <div class="float-card float-card--progress">
          <div class="progress-ring">
            <svg viewBox="0 0 44 44">
              <circle cx="22" cy="22" r="18" fill="none" stroke="var(--role-soft)" stroke-width="4" />
              <circle cx="22" cy="22" r="18" fill="none" stroke="#6b8cf5" stroke-width="4" stroke-linecap="round" stroke-dasharray="113" stroke-dashoffset="38" transform="rotate(-90 22 22)" />
            </svg>
            <b>2/3</b>
          </div>
          <div>
            <span>我的订正</span>
            <b>继续加油</b>
          </div>
        </div>
      </div>

      <footer class="login-canvas__footer">
        <span>© 2026 错题星球 · 课程实训项目</span>
        <span class="footer-sep" />
        <span>全部学生、成绩与讲评均为虚构演示数据</span>
      </footer>
    </div>

    <div class="login-side">
      <div class="login-side__top">
        <span class="side-kicker">{{ isMockMode ? 'DEMO WORKSPACE' : 'SPRING BOOT API' }}</span>
        <span class="side-version">v0.1</span>
      </div>

      <div class="login-card">
        <header class="login-card__head">
          <span class="login-card__icon"><AppIcon name="key" :size="18" /></span>
          <div>
            <h2>登录工作台</h2>
            <p>先选择身份，系统将按责任范围授权。</p>
          </div>
        </header>

        <div class="role-switch" role="tablist" aria-label="选择登录身份">
          <button
            v-for="option in roleOptions"
            :key="option.value"
            type="button"
            role="tab"
            :aria-selected="selectedRole === option.value"
            :class="{ 'is-active': selectedRole === option.value }"
            @click="selectRole(option.value)"
          >
            <span class="role-switch__icon"><AppIcon :name="option.icon" :size="17" /></span>
            <b>{{ option.label }}</b>
            <small>{{ option.hint }}</small>
          </button>
        </div>

        <Transition name="fade">
          <div v-if="errorText" class="login-error" role="alert">
            <AppIcon name="alert-circle" :size="16" />
            <div>
              <span>{{ errorText }}</span>
              <small v-if="attemptsRemaining !== null && !isLocked">
                还可尝试 {{ attemptsRemaining }} 次。失败提示不区分账号是否存在。
              </small>
            </div>
          </div>
        </Transition>

        <form class="login-form" @submit.prevent="submit">
          <div class="field">
            <label for="login-username">账号</label>
            <div class="input-icon">
              <AppIcon name="user" :size="17" />
              <input
                id="login-username"
                v-model="username"
                class="input"
                type="text"
                autocomplete="username"
                placeholder="教师工号 / 学号 / 教研账号"
              />
            </div>
          </div>

          <div class="field">
            <label for="login-password">密码</label>
            <div class="input-icon">
              <AppIcon name="lock" :size="17" />
              <input
                id="login-password"
                v-model="password"
                class="input"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="17" />
              </button>
            </div>
          </div>

          <div v-if="captchaRequired" class="captcha-row">
            <div class="field">
              <label for="login-captcha">图形验证码</label>
              <input
                id="login-captcha"
                v-model="captchaInput"
                class="input"
                type="text"
                maxlength="4"
                autocomplete="off"
                placeholder="输入 4 位字符"
              />
            </div>
            <button class="captcha-box" type="button" title="点击刷新验证码" @click="refreshCaptcha">
              <span>{{ captchaCode }}</span>
              <i />
              <i />
              <em>换一个</em>
            </button>
          </div>

          <div class="login-options">
            <label class="check">
              <input v-model="remember" type="checkbox" />
              记住账号
            </label>
            <button type="button" class="forgot-link" @click="handleForgot">忘记密码？</button>
          </div>

          <button
            class="btn btn--primary btn--lg btn--block login-submit"
            :class="{ 'is-loading': submitting || auth.loading }"
            type="submit"
            :disabled="loginDisabled"
          >
            <AppIcon v-if="submitting || auth.loading" name="refresh" :size="17" />
            <span>{{ isLocked ? `锁定中 ${lockedSeconds}s` : `进入${currentRole.label}工作台` }}</span>
            <AppIcon v-if="!submitting && !auth.loading" name="arrow-right" :size="17" />
          </button>

          <div v-if="isLocked" class="lock-banner">
            <AppIcon name="lock" :size="15" />
            连续失败次数过多，账号已临时锁定，请 <b>{{ lockedSeconds }}</b> 秒后重试。
          </div>
        </form>

        <div class="demo-accounts">
          <div class="demo-accounts__title">
            <span>一键填入演示账号</span>
            <span class="kbd">密码 123456</span>
          </div>
          <div class="demo-accounts__list">
            <button
              v-for="option in roleOptions"
              :key="option.account"
              type="button"
              @click="fillDemo(option.account, option.value)"
            >
              <span>{{ option.label }}</span>
              <b>{{ option.account }}</b>
            </button>
          </div>
        </div>

        <p class="login-security">
          <AppIcon name="shield" :size="13" />
          统一错误文案、渐进验证码与后端锁定策略，避免账号枚举与暴力尝试。
        </p>
      </div>

      <footer class="login-side__footer">
        <span>{{ isMockMode ? '当前为 Mock 演示模式，无需启动后端' : '已连接 Spring Boot API' }}</span>
        <span>数据最小化 · 不收集手机号与邮箱</span>
      </footer>
    </div>

    <AppModal v-model="forgotOpen" title="找回密码" subtitle="本版遵循数据最小化原则，不收集手机号或邮箱" size="sm">
      <div class="forgot-body">
        <div class="forgot-step">
          <span>1</span>
          <p>携带学生证或教师工牌，联系校内系统管理员当面核验身份。</p>
        </div>
        <div class="forgot-step">
          <span>2</span>
          <p>管理员在后台执行「重置为初始密码」，并写入操作日志。</p>
        </div>
        <div class="forgot-step">
          <span>3</span>
          <p>首次登录后请立即修改密码；演示环境可直接使用统一密码。</p>
        </div>
        <div class="info-banner info-banner--warm">
          <AppIcon name="info" :size="16" />
          若后续评审决定收集验证方式，再启用一次性限时重置链接与发送冷却。
        </div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="closeForgot">关闭</button>
        <button class="btn btn--primary" type="button" @click="submitForgot">我知道了</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.login-view {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
  min-height: 100vh;
  --role: var(--mint);
  --role-soft: var(--mint-soft);
  --role-deep: var(--mint-deep);
}

.login-view[data-role='student'] {
  --role: var(--peri);
  --role-soft: var(--peri-soft);
  --role-deep: var(--peri-deep);
}

.login-view[data-role='research'] {
  --role: var(--apricot);
  --role-soft: var(--apricot-soft);
  --role-deep: var(--apricot-deep);
}

.login-canvas {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: clamp(28px, 4.4vw, 54px) clamp(28px, 5vw, 76px) 26px;
  overflow: hidden;
  border-right: 1px solid color-mix(in srgb, var(--role) 14%, #e4eee9);
  background:
    linear-gradient(150deg, color-mix(in srgb, var(--role) 7%, #fbfdfe) 0%, rgba(244, 249, 255, 0.97) 56%, rgba(252, 247, 239, 0.94) 100%);
}

.canvas-grid {
  position: absolute;
  inset: 0;
  opacity: 0.55;
  background-image:
    linear-gradient(color-mix(in srgb, var(--role) 5%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--role) 5%, transparent) 1px, transparent 1px);
  background-size: 30px 30px;
  mask-image: linear-gradient(to bottom, #000 0%, transparent 92%);
}

.canvas-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(4px);
  pointer-events: none;
}

.canvas-glow--accent {
  top: -16%;
  left: -12%;
  width: 55vw;
  height: 55vw;
  max-width: 720px;
  max-height: 720px;
  background: radial-gradient(circle, color-mix(in srgb, var(--role) 17%, transparent), transparent 66%);
}

.canvas-glow--peri {
  right: -18%;
  bottom: -22%;
  width: 46vw;
  height: 46vw;
  max-width: 620px;
  max-height: 620px;
  background: radial-gradient(circle, rgba(133, 152, 240, 0.14), transparent 68%);
}

.canvas-formula {
  position: absolute;
  color: color-mix(in srgb, var(--role-deep) 6%, transparent);
  font-family: var(--font-numbers);
  pointer-events: none;
  user-select: none;
}

.canvas-formula--sigma {
  top: 8%;
  right: 4%;
  font-size: clamp(180px, 24vw, 370px);
  font-weight: 300;
  line-height: 1;
}

.canvas-formula--fraction {
  bottom: 18%;
  left: 2%;
  font-family: var(--font-serif);
  font-size: clamp(32px, 4.4vw, 62px);
  letter-spacing: 0.04em;
  transform: rotate(-8deg);
}

.login-brand {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-brand > div {
  display: flex;
  flex-direction: column;
}

.login-brand strong {
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 400;
  letter-spacing: 0.08em;
  line-height: 1.2;
}

.login-brand > div span {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.login-brand__chapter {
  margin-left: auto !important;
  padding: 5px 12px;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 999px;
  color: var(--ink-3) !important;
  background: rgba(255, 255, 255, 0.56);
  font-family: var(--font-serif);
  font-size: 10px !important;
  backdrop-filter: blur(10px);
}

.login-hero {
  position: relative;
  z-index: 2;
  max-width: 600px;
  margin-top: clamp(36px, 6vh, 78px);
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--role-deep);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.2em;
}

.hero-kicker::before {
  content: '';
  width: 26px;
  height: 1px;
  background: currentColor;
  opacity: 0.5;
}

.login-hero h1 {
  margin-top: 14px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(42px, 4.2vw, 66px);
  font-weight: 400;
  letter-spacing: 0.015em;
  line-height: 1.24;
}

.login-hero h1 em {
  position: relative;
  color: var(--role-deep);
  font-style: normal;
}

.login-hero h1 em::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 6px;
  left: 2px;
  z-index: -1;
  height: 12px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--role) 16%, transparent);
  transform: rotate(-1.5deg);
}

.login-hero p {
  margin-top: 16px;
  color: color-mix(in srgb, var(--role) 18%, var(--ink-2));
  font-family: var(--font-serif);
  font-size: 13.5px;
  line-height: 2;
}

.hero-principles {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.hero-principles span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 999px;
  color: color-mix(in srgb, var(--role) 18%, var(--ink-2));
  background: rgba(255, 255, 255, 0.54);
  font-size: 10px;
  backdrop-filter: blur(10px);
}

.hero-principles .icon {
  color: var(--role);
}

/* 拼贴讲评台 */
.proof-desk {
  position: relative;
  z-index: 2;
  width: min(100%, 620px);
  height: clamp(280px, 37vh, 340px);
  margin-top: clamp(24px, 4vh, 46px);
}

.paper-sheet {
  position: absolute;
  top: 8px;
  left: 4%;
  width: 67%;
  height: 100%;
  padding: 22px 24px 20px 38px;
  border: 1px solid color-mix(in srgb, var(--role) 10%, #e4eaf2);
  border-radius: 4px 12px 10px 4px;
  background:
    repeating-linear-gradient(to bottom, transparent 0, transparent 29px, color-mix(in srgb, var(--role-deep) 9%, transparent) 30px),
    #fffefa;
  box-shadow: 0 24px 58px -32px color-mix(in srgb, var(--role-deep) 32%, transparent);
  transform: rotate(-2.2deg);
}

.paper-sheet__margin {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 24px;
  width: 1px;
  background: rgba(222, 147, 141, 0.24);
}

.paper-sheet__head {
  display: flex;
  justify-content: space-between;
  color: #96a1b0;
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.02em;
}

.paper-sheet__label {
  margin-top: 17px;
  color: #8b96a6;
  font-size: 10px;
  letter-spacing: 0.1em;
}

.paper-sheet__formula {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
  color: #29453c;
  font-family: var(--font-serif);
  font-size: clamp(15px, 1.4vw, 19px);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.paper-sheet__wrong {
  position: relative;
  color: #c87d77;
}

.paper-sheet__wrong::after {
  content: '';
  position: absolute;
  top: 55%;
  right: -2px;
  left: -2px;
  height: 1px;
  background: #d38c86;
  transform: rotate(-2deg);
}

.paper-sheet__arrow {
  color: #91a2b3;
  font-family: var(--font-body);
  font-weight: 400;
}

.paper-sheet__note {
  margin-top: 18px;
  padding: 10px 12px;
  border-left: 2px solid #e5b2a8;
  border-radius: 0 7px 7px 0;
  color: #8b615a;
  background: #fdf5f1;
  font-family: var(--font-hand);
  font-size: 13px;
  line-height: 1.7;
}

.paper-sheet__note b {
  color: #b56c63;
  font-weight: 400;
}

.paper-sheet__answer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 15px;
}

.paper-sheet__answer span {
  color: #96a1b0;
  font-size: 9.5px;
}

.paper-sheet__answer b {
  padding: 3px 10px;
  border-radius: 5px;
  color: var(--role-deep);
  background: var(--role-soft);
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 600;
}

.confirm-stamp {
  position: absolute;
  right: 22px;
  bottom: 18px;
  padding: 7px 11px;
  border: 1.5px solid rgba(180, 109, 101, 0.35);
  border-radius: 7px;
  color: rgba(169, 97, 90, 0.62);
  font-family: var(--font-serif);
  font-size: 10.5px;
  font-weight: 700;
  line-height: 1.4;
  text-align: center;
  transform: rotate(-7deg);
}

.confirm-stamp span {
  font-family: var(--font-mono);
  font-size: 6.5px;
  letter-spacing: 0.16em;
}

.paper-sheet__holes {
  position: absolute;
  top: 30px;
  left: -5px;
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.paper-sheet__holes i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--role) 8%, #f4f7fb);
  box-shadow: inset 0 1px 2px rgba(66, 102, 89, 0.08);
}

.float-card {
  position: absolute;
  border: 1px solid rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 20px 46px -30px color-mix(in srgb, var(--role-deep) 36%, transparent);
  backdrop-filter: blur(16px);
}

.float-card--rate {
  top: 0;
  right: 1%;
  width: 174px;
  padding: 14px;
  border-radius: 16px;
  transform: rotate(1.6deg);
}

.float-card__eyebrow {
  color: var(--ink-3);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.float-card__value {
  margin: 2px 0 5px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 32px;
  font-weight: 600;
  line-height: 1;
}

.float-card__value span {
  margin-left: 2px;
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 11px;
}

.rate-bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 26px;
}

.rate-bars i {
  width: 100%;
  height: var(--w);
  min-height: 4px;
  border-radius: 3px 3px 1px 1px;
  background: linear-gradient(to top, color-mix(in srgb, var(--role) 45%, #fff), var(--role));
}

.rate-bars i:nth-child(2) {
  background: linear-gradient(to top, #efc0a9, #df9682);
}

.float-card__foot {
  display: block;
  margin-top: 7px;
  color: var(--ink-3);
  font-size: 8.5px;
}

.float-card--ai {
  right: 6%;
  bottom: 33px;
  width: 236px;
  padding: 12px 13px;
  border-radius: 15px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.9), rgba(247, 243, 255, 0.87));
  transform: rotate(1.2deg);
}

.float-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-orb {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  color: var(--lav-deep);
  background: var(--lav-soft);
}

.float-card__head b {
  display: block;
  color: var(--ink);
  font-size: 10.5px;
}

.float-card__head span {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.float-card__head .ai-status {
  margin-left: auto;
  padding: 2px 6px;
  border-radius: 999px;
  color: var(--lav-deep);
  background: var(--lav-soft);
  font-size: 7.5px;
}

.float-card--ai > p {
  margin-top: 8px;
  color: #5f6e84;
  font-family: var(--font-serif);
  font-size: 9.5px;
  line-height: 1.65;
}

.evidence-dots {
  display: flex;
  gap: 4px;
  margin-top: 7px;
}

.evidence-dots span {
  padding: 2px 5px;
  border-radius: 4px;
  color: #7d88a1;
  background: rgba(255, 255, 255, 0.7);
  font-size: 7.5px;
}

.float-card--progress {
  display: flex;
  align-items: center;
  gap: 9px;
  bottom: -3px;
  left: 0;
  width: 154px;
  padding: 10px 12px;
  border-radius: 14px;
  transform: rotate(-1.5deg);
}

.progress-ring {
  position: relative;
  width: 40px;
  height: 40px;
  flex: none;
}

.progress-ring svg {
  width: 100%;
  height: 100%;
}

.progress-ring b {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--peri-deep);
  font-family: var(--font-numbers);
  font-size: 11px;
  font-weight: 600;
}

.float-card--progress > div:last-child span {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.float-card--progress > div:last-child b {
  display: block;
  margin-top: 1px;
  color: var(--ink);
  font-size: 10.5px;
}

.login-canvas__footer {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: auto;
  padding-top: 20px;
  color: #9aa6b3;
  font-size: 9px;
  letter-spacing: 0.02em;
}

.footer-sep {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
}

/* 登录侧 */
.login-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 30px clamp(24px, 4.2vw, 58px);
  background:
    radial-gradient(circle at 50% 0, rgba(255, 255, 255, 0.9), transparent 42%),
    rgba(248, 252, 250, 0.74);
  backdrop-filter: blur(10px);
}

.login-side__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(100%, 420px);
  margin-bottom: 12px;
}

.side-kicker,
.side-version {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.16em;
}

.login-card {
  width: min(100%, 420px);
  padding: clamp(22px, 3vw, 30px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 34px 86px -46px rgba(39, 84, 70, 0.4), 0 2px 8px rgba(39, 84, 70, 0.035);
  backdrop-filter: blur(20px);
}

.login-card__head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.login-card__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: 12px;
  color: var(--role-deep);
  background: var(--role-soft);
}

.login-card__head h2 {
  font-family: var(--font-display);
  font-size: 25px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.login-card__head p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 11px;
}

.role-switch {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
  margin: 20px 0;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #f3f8f5;
}

.role-switch button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 9px 5px 8px;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--ink-3);
  transition: 0.2s;
}

.role-switch button:hover {
  color: var(--ink-2);
  background: rgba(255, 255, 255, 0.55);
}

.role-switch button.is-active {
  border-color: rgba(255, 255, 255, 0.8);
  color: var(--role-deep);
  background: #fff;
  box-shadow: 0 3px 12px -4px rgba(43, 80, 67, 0.14);
}

.role-switch__icon {
  display: grid;
  place-items: center;
  margin-bottom: 1px;
}

.role-switch b {
  font-size: 11.5px;
  font-weight: 700;
}

.role-switch small {
  color: var(--ink-4);
  font-size: 8px;
  white-space: nowrap;
}

.role-switch button.is-active small {
  color: var(--role-deep);
  opacity: 0.62;
}

.login-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  padding: 9px 11px;
  border: 1px solid #f0d9d5;
  border-radius: 10px;
  color: #ac655e;
  background: #fdf2f0;
  font-size: 11.5px;
  line-height: 1.6;
  animation: shake 0.4s var(--ease-soft);
}

.login-error .icon {
  flex: none;
  margin-top: 2px;
}

.login-error small {
  display: block;
  margin-top: 2px;
  color: #bb8b86;
  font-size: 9.5px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.login-form .field {
  gap: 6px;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 6px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: var(--ink-4);
  transform: translateY(-50%);
}

.password-toggle:hover {
  color: var(--ink-2);
  background: var(--surface-soft);
}

.login-form .input-icon .input {
  padding-right: 40px;
}

.login-form .input-icon .password-toggle {
  right: 5px;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  align-items: end;
  gap: 9px;
}

.captcha-box {
  position: relative;
  display: grid;
  place-items: center;
  height: 41px;
  overflow: hidden;
  border: 1px dashed color-mix(in srgb, var(--role) 38%, var(--line));
  border-radius: 11px;
  background:
    repeating-linear-gradient(-38deg, #fff 0, #fff 6px, color-mix(in srgb, var(--role) 4%, #fff) 6px, color-mix(in srgb, var(--role) 4%, #fff) 12px);
}

.captcha-box span {
  z-index: 1;
  color: var(--role-deep);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-decoration: line-through;
  text-decoration-color: color-mix(in srgb, var(--role) 30%, transparent);
  transform: rotate(-2deg);
}

.captcha-box i {
  position: absolute;
  width: 140%;
  height: 1px;
  background: color-mix(in srgb, var(--role) 22%, transparent);
  transform: rotate(9deg);
}

.captcha-box i:nth-of-type(2) {
  transform: rotate(-6deg);
}

.captcha-box em {
  position: absolute;
  right: 4px;
  bottom: 2px;
  color: var(--ink-4);
  font-size: 7px;
  font-style: normal;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -2px;
}

.forgot-link {
  position: relative;
  color: var(--role-deep);
  font-size: 11px;
  font-weight: 600;
}

.forgot-link::after {
  content: '';
  position: absolute;
  inset: -9px -6px;
}

.forgot-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.login-submit {
  margin-top: 2px;
  letter-spacing: 0.04em;
}

.login-submit .icon {
  transition: transform 0.2s;
}

.login-submit:hover:not(:disabled) .icon:last-child {
  transform: translateX(3px);
}

.lock-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  border-radius: 9px;
  color: #a8645e;
  background: #fdf2f0;
  font-size: 10px;
}

.demo-accounts {
  margin-top: 17px;
  padding: 11px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: 12px;
  background: rgba(247, 251, 249, 0.7);
}

.demo-accounts__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-3);
  font-size: 9px;
  letter-spacing: 0.03em;
}

.demo-accounts__list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
  margin-top: 7px;
}

.demo-accounts__list button {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 6px 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  transition: 0.18s;
}

.demo-accounts__list button:hover {
  border-color: color-mix(in srgb, var(--role) 35%, var(--line));
  background: var(--role-soft);
}

.demo-accounts__list span {
  color: var(--ink-3);
  font-size: 8px;
}

.demo-accounts__list b {
  overflow: hidden;
  color: var(--ink-2);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 500;
  text-overflow: ellipsis;
}

.login-security {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 12px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.login-security .icon {
  flex: none;
  margin-top: 1px;
  color: var(--ink-3);
  opacity: 0.7;
}

.login-side__footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  margin-top: 14px;
  color: var(--ink-4);
  font-size: 8.5px;
  text-align: center;
}

.forgot-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.forgot-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.forgot-step > span {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  flex: none;
  border-radius: 50%;
  color: var(--role-deep);
  background: var(--role-soft);
  font-family: var(--font-numbers);
  font-size: 11px;
  font-weight: 600;
}

.forgot-step p {
  color: var(--ink-2);
  font-size: 12px;
  line-height: 1.8;
}

@keyframes shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-4px);
  }
  50% {
    transform: translateX(4px);
  }
  75% {
    transform: translateX(-2px);
  }
}

@media (max-width: 1220px) {
  .login-view {
    grid-template-columns: minmax(0, 1fr) minmax(400px, 0.9fr);
  }

  .proof-desk {
    transform: scale(0.92);
    transform-origin: left top;
  }
}

@media (max-width: 1024px) {
  .login-view {
    grid-template-columns: 1fr;
  }

  .login-canvas {
    min-height: auto;
    padding: 28px 30px 34px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .login-hero {
    margin-top: 34px;
  }

  .login-hero h1 {
    font-size: clamp(38px, 7vw, 52px);
  }

  .proof-desk {
    display: none;
  }

  .login-canvas__footer {
    margin-top: 22px;
  }

  .login-side {
    min-height: auto;
    padding: 28px 22px 38px;
  }

  .login-side__top {
    margin-bottom: 10px;
  }
}

@media (max-width: 560px) {
  .login-canvas {
    padding: 22px 20px 26px;
  }

  .login-brand {
    gap: 9px;
  }

  .login-brand strong {
    font-size: 18px;
  }

  .login-brand span {
    font-size: 8.5px;
  }

  .login-brand__chapter {
    display: none;
  }

  .login-hero {
    margin-top: 27px;
  }

  .hero-kicker {
    font-size: 8px;
  }

  .login-hero h1 {
    margin-top: 10px;
    font-size: 37px;
    line-height: 1.28;
  }

  .login-hero p {
    margin-top: 11px;
    font-size: 12px;
    line-height: 1.85;
  }

  .hero-principles {
    margin-top: 13px;
    gap: 5px;
  }

  .hero-principles span {
    padding: 4px 8px;
    font-size: 9px;
  }

  .login-canvas__footer {
    flex-wrap: wrap;
    font-size: 8px;
  }

  .login-canvas__footer .footer-sep,
  .login-canvas__footer span:last-child {
    display: none;
  }

  .login-side {
    padding: 22px 14px 34px;
  }

  .login-card {
    padding: 20px 17px;
    border-radius: 22px;
  }

  .login-card__head h2 {
    font-size: 22px;
  }

  .role-switch {
    margin: 16px 0;
  }

  .role-switch small {
    display: none;
  }

  .role-switch button {
    padding: 9px 4px;
  }

  .demo-accounts__list {
    gap: 4px;
  }

  .demo-accounts__list button {
    padding: 5px 6px;
  }

  .login-side__top {
    padding: 0 6px;
  }
}
</style>
