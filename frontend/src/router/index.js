import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ExamView from '@/views/ExamView.vue'
import ExamDetailView from '@/views/ExamDetailView.vue'
import ScoreDetailView from '@/views/ScoreDetailView.vue'
import ExamAssembleView from '@/views/ExamAssembleView.vue'
import ExamPublishView from '@/views/ExamPublishView.vue'
import ExamAnalysisView from '@/views/ExamAnalysisView.vue'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/login',
            name: 'login',
            component: LoginView
        },
        {
            path: '/',
            redirect: '/dashboard'
        },
        {
            path: '/dashboard',
            name: 'dashboard',
            component: DashboardView,
            meta: { requiresAuth: true }
        },
        {
            path: '/exam/:id',
            name: 'exam',
            component: ExamView,
            meta: { requiresAuth: true }
        },
        {
            path: '/exam/:id/detail',
            name: 'exam-detail',
            component: ExamDetailView,
            meta: { requiresAuth: true }
        },
        {
            path: '/score/:id',
            name: 'score-detail',
            component: ScoreDetailView,
            meta: { requiresAuth: true }
        },
        {
            path: '/exam/:id/assemble',
            name: 'exam-assemble',
            component: ExamAssembleView,
            meta: { requiresAuth: true }
        },
        {
            path: '/exam/:id/publish',
            name: 'exam-publish',
            component: ExamPublishView,
            meta: { requiresAuth: true }
        },
        {
            path: '/exam/:id/analysis',
            name: 'exam-analysis',
            component: ExamAnalysisView,
            meta: { requiresAuth: true }
        }
    ]
})

router.beforeEach((to, from, next) => {
    const authStore = useAuthStore()
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
        next('/login')
    } else {
        next()
    }
})

export default router
