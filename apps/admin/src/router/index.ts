import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import UsersView from '../views/UsersView.vue'
import BanksView from '../views/BanksView.vue'
import RolesView from '../views/RolesView.vue'
import AuditLogsView from '../views/AuditLogsView.vue'
import { useAdminAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: DashboardView },
        { path: 'users', name: 'users', component: UsersView },
        { path: 'banks', name: 'banks', component: BanksView },
        { path: 'roles', name: 'roles', component: RolesView },
        { path: 'audit-logs', name: 'audit-logs', component: AuditLogsView }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAdminAuthStore()

  if (to.meta.public) {
    if (to.name === 'login' && auth.accessToken) {
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
