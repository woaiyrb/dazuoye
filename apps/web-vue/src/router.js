import { createRouter, createWebHistory } from "vue-router";
import AppShell from "./layouts/AppShell.vue";
import AuthorView from "./views/AuthorView.vue";
import ClassesView from "./views/ClassesView.vue";
import CorrectionsView from "./views/CorrectionsView.vue";
import DashboardView from "./views/DashboardView.vue";
import GradingView from "./views/GradingView.vue";
import GeneratorView from "./views/GeneratorView.vue";
import KnowledgeView from "./views/KnowledgeView.vue";
import LearnerView from "./views/LearnerView.vue";
import LoginView from "./views/LoginView.vue";
import NotFoundView from "./views/NotFoundView.vue";
import ReviewView from "./views/ReviewView.vue";
import QuestionEditorView from "./views/QuestionEditorView.vue";
import SettingsView from "./views/SettingsView.vue";
import StudentsView from "./views/StudentsView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/dashboard" },
    { path: "/login", name: "login", component: LoginView },
    {
      path: "/",
      component: AppShell,
      children: [
        {
          path: "dashboard",
          name: "dashboard",
          component: DashboardView,
          meta: { title: "工作台" },
        },
        {
          path: "author/:assignmentId",
          name: "author",
          component: AuthorView,
          meta: { title: "作业编辑" },
        },
        {
          path: "students",
          name: "students",
          component: StudentsView,
          meta: { title: "学生" },
        },
        {
          path: "grading/:assignmentId",
          name: "grading",
          component: GradingView,
          meta: { title: "批改统计" },
        },
        {
          path: "author/:assignmentId/generate",
          name: "generator",
          component: GeneratorView,
          meta: { title: "批量生成题目" },
        },
        {
          path: "author/:assignmentId/questions",
          name: "question-editor",
          component: QuestionEditorView,
          meta: { title: "题目编辑器" },
        },
        {
          path: "review/:assignmentId",
          name: "review",
          component: ReviewView,
          meta: { title: "AI 讲评审核" },
        },
        {
          path: "corrections",
          name: "corrections",
          component: CorrectionsView,
          meta: { title: "订正跟踪" },
        },
        {
          path: "classes",
          name: "classes",
          component: ClassesView,
          meta: { title: "班级" },
        },
        {
          path: "knowledge-points",
          name: "knowledge",
          component: KnowledgeView,
          meta: { title: "知识点" },
        },
        {
          path: "settings",
          name: "settings",
          component: SettingsView,
          meta: { title: "设置" },
        },
      ],
    },
    {
      path: "/learner/:assignmentId",
      name: "learner",
      component: LearnerView,
      meta: { title: "学生作答" },
    },
    { path: "/:pathMatch(.*)*", component: NotFoundView },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

router.afterEach((to) => {
  document.title = `${to.meta.title || "Mark"} · 作业批改与学情分析`;
});

export default router;
