import { test, expect, type Page } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const ROLE_CONFIG = {
  教师: { account: 'teacher01', path: '/teacher' },
  学生: { account: 'S2024013', path: '/student' },
  教研: { account: 'research01', path: '/research' },
} as const

const screenshotDir = path.resolve(process.cwd(), '../docs/screenshots')

async function login(page: Page, role: keyof typeof ROLE_CONFIG): Promise<void> {
  const config = ROLE_CONFIG[role]
  await page.goto('/login')
  await page.getByRole('tab', { name: role }).click()
  await page.getByRole('button', { name: new RegExp(`${role} ${config.account}`) }).click()
  await page.getByRole('button', { name: new RegExp(`进入${role}工作台`) }).click()
  await expect(page).toHaveURL(new RegExp(`${config.path}$`))
}

async function snapshot(page: Page, name: string): Promise<void> {
  fs.mkdirSync(screenshotDir, { recursive: true })
  await page.screenshot({ path: path.join(screenshotDir, `${name}.png`), fullPage: true })
}

test('teacher can open classes, templates, review versions and question corrections', async ({ page }) => {
  await login(page, '教师')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await snapshot(page, 'teacher-dashboard')

  await page.goto('/teacher/classes')
  await expect(page.getByRole('heading', { name: '班级与学生' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '学生名单' })).toBeVisible()
  await snapshot(page, 'teacher-classes')

  await page.goto('/teacher/templates')
  await expect(page.getByRole('heading', { name: '讲评模板' })).toBeVisible()
  await expect(page.getByText('可用模板')).toBeVisible()
  await snapshot(page, 'teacher-templates')

  await page.goto('/teacher/homeworks/5/review')
  await expect(page.getByRole('heading', { name: '讲评审核台' })).toBeVisible()
  await page.getByRole('button', { name: /版本 \d+/ }).first().click()
  await expect(page.getByRole('dialog', { name: '讲评版本记录' })).toBeVisible()
  await snapshot(page, 'teacher-review-versions')
  await page.getByRole('button', { name: '关闭' }).click()

  await page.goto('/teacher/corrections?homework=2')
  await page.getByRole('button', { name: /按题目/ }).click()
  await expect(page.getByText('按题目查看订正')).toBeVisible()
  await snapshot(page, 'teacher-question-corrections')
})

test('teacher can create a class and submit a template with the real backend', async ({ page }) => {
  const suffix = Date.now()
  const className = `E2E 测试班-${suffix}`
  const templateTitle = `E2E 测试模板-${suffix}`
  await login(page, '教师')

  await page.goto('/teacher/classes')
  await page.getByRole('button', { name: '新建班级' }).click()
  const classDialog = page.getByRole('dialog', { name: '新建班级' })
  await classDialog.getByLabel('班级名称').fill(className)
  await classDialog.getByLabel('年级').fill('初二')
  await classDialog.getByRole('button', { name: '创建班级' }).click()
  await expect(page.getByText('班级已创建')).toBeVisible()
  await expect(page.getByText(className, { exact: true })).toBeVisible()
  await snapshot(page, 'teacher-class-created')

  await page.goto('/teacher/templates')
  const title = page.getByLabel('模板标题')
  const content = page.getByLabel('模板说明')
  const sections = page.getByLabel('结构槽位（每行一个）')
  await title.fill(templateTitle)
  await content.fill('先定位错误，再引用原题说明正确思路。')
  await sections.fill('错误定位\n原题依据\n正确思路')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByText('模板已提交')).toBeVisible()
  await expect(page.getByText(templateTitle, { exact: true })).toBeVisible()
})

test('teacher can create a student account and transfer the student', async ({ page }) => {
  const suffix = Date.now()
  const sourceClass = `转班源班-${suffix}`
  const targetClass = `转班目标班-${suffix}`
  const studentNo = `E2E-TRANSFER-${suffix}`
  await login(page, '教师')
  await page.goto('/teacher/classes')

  for (const className of [sourceClass, targetClass]) {
    await page.getByRole('button', { name: '新建班级' }).click()
    const dialog = page.getByRole('dialog', { name: '新建班级' })
    await dialog.getByLabel('班级名称').fill(className)
    await dialog.getByLabel('年级').fill('七年级')
    await dialog.getByRole('button', { name: '创建班级' }).click()
    await expect(page.getByText(className, { exact: true })).toBeVisible()
  }

  const classSelect = page.locator('.class-select select')
  const sourceValue = await classSelect
    .locator('option')
    .filter({ hasText: sourceClass })
    .getAttribute('value')
  const targetValue = await classSelect
    .locator('option')
    .filter({ hasText: targetClass })
    .getAttribute('value')
  await classSelect.selectOption(sourceValue!)

  await page.locator('.student-actions').getByRole('button', { name: '添加学生' }).click()
  const accountDialog = page.getByRole('dialog', { name: '添加学生账号' })
  await accountDialog.getByLabel('学号').fill(studentNo)
  await accountDialog.getByLabel('姓名').fill('E2E 转班学生')
  await accountDialog.getByRole('button', { name: '创建账号' }).click()
  await expect(page.getByText('学生已添加')).toBeVisible()

  const studentRow = page.locator('.student-table-row').filter({ hasText: studentNo })
  await expect(studentRow).toBeVisible()
  await studentRow.getByRole('button', { name: '转班' }).click()
  const transferDialog = page.getByRole('dialog', { name: '学生转班' })
  await transferDialog.getByLabel('转入班级').selectOption(targetValue!)
  await transferDialog.getByRole('button', { name: '确认转班' }).click()
  await expect(page.getByText('学生已转班')).toBeVisible()
  await expect(classSelect).toHaveValue(targetValue!)
  await expect(page.locator('.student-table-row').filter({ hasText: studentNo })).toBeVisible()
})

test('student sees own handouts and research opens audit logs', async ({ page }) => {
  await login(page, '学生')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await page.goto('/student/homeworks')
  await expect(page.getByRole('heading', { name: '作业与成绩' })).toBeVisible()
  await expect(page.locator('.homework-card').first()).toBeVisible()
  await snapshot(page, 'student-homeworks')

  await page.getByRole('button', { name: /查看结果 \/ 再练|开始作答/ }).first().click()
  await expect(page.getByRole('heading', { name: /数据库五步验收|AI功能全面测试/ })).toBeVisible()
  await expect(page.locator('.result-hero, .option-row').first()).toBeVisible()
  if ((await page.locator('.result-hero').count()) === 0) {
    await page.locator('.option-row').first().click()
    await expect(page.getByRole('button', { name: '提交并查看成绩' })).toBeEnabled()
    await page.getByRole('button', { name: '提交并查看成绩' }).click()
  }
  await expect(page.getByText(/本次完成得不错|再回看一遍错题/)).toBeVisible()
  await snapshot(page, 'student-homework-result')

  await page.goto('/student/wrong-book')
  await expect(page.getByRole('heading', { name: '我的错题本' })).toBeVisible()
  await expect(page.locator('.wrong-card').first()).toBeVisible()
  await snapshot(page, 'student-wrong-book')
  await page.setViewportSize({ width: 375, height: 812 })
  await page.reload()
  await expect(page.getByRole('heading', { name: '我的错题本' })).toBeVisible()
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 1,
  )
  expect(hasHorizontalOverflow).toBe(false)
  await snapshot(page, 'student-wrong-book-mobile')
  await page.setViewportSize({ width: 1280, height: 720 })

  await page.goto('/student/handouts')
  await expect(page.getByRole('heading', { name: '我的错题讲评' })).toBeVisible()
  await snapshot(page, 'student-handouts')

  await page.getByRole('button', { name: '退出登录' }).click()
  await page.getByRole('tab', { name: '教研' }).click()
  await page.getByRole('button', { name: /教研 research01/ }).click()
  await page.getByRole('button', { name: /进入教研工作台/ }).click()
  await expect(page).toHaveURL(/\/research$/)
  await page.goto('/research/logs')
  await expect(page.getByRole('heading', { name: '操作日志' })).toBeVisible()
  await snapshot(page, 'research-logs')
})
