import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import VerifyEmailView from '../views/VerifyEmailView.vue'
import ForgotPasswordView from '../views/ForgotPasswordView.vue'
import ResetPasswordView from '../views/ResetPasswordView.vue'
import DashboardView from '../views/DashboardView.vue'
import BankListView from '../views/BankListView.vue'
const QuestionListView = () => import('../views/QuestionListView.vue')
const QuestionEditorView = () => import('../views/QuestionEditorView.vue')
import PracticeView from '../views/PracticeView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/verify-email', name: 'verify-email', component: VerifyEmailView, meta: { public: true } },
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView, meta: { public: true } },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordView, meta: { public: true } },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: DashboardView },
        { path: 'banks', name: 'banks', component: BankListView },
        { path: 'banks/:bankId/questions', name: 'bank-questions', component: QuestionListView },
        { path: 'banks/:bankId/questions/new', name: 'question-create', component: QuestionEditorView },
        { path: 'banks/:bankId/questions/:questionId/edit', name: 'question-edit', component: QuestionEditorView },
        { path: 'practice', name: 'practice', component: PracticeView }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if ((to.name === 'login' || to.name === 'register') && auth.accessToken) {
      const restored = await auth.restore()
      if (restored) {
        return { name: 'dashboard' }
      }
    }
    return true
  }

  const restored = await auth.restore()
  if (!restored) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
