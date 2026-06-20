import { defineStore } from 'pinia';
import { login as loginApi } from '@/api';

export const useAuthStore = defineStore('auth', {
    state: () => ({
        user: JSON.parse(localStorage.getItem('user')) || null,
        token: localStorage.getItem('token') || null,
    }),
    getters: {
        isAuthenticated: (state) => !!state.token,
        isTeacher: (state) => state.user?.role === 'TEACHER' || state.user?.role === 'ADMIN',
        isAdmin: (state) => state.user?.role === 'ADMIN',
    },
    actions: {
        async login(credentials) {
            try {
                const response = await loginApi(credentials);
                const { token, ...userData } = response.data;

                this.token = token;
                this.user = userData; // Includes role and username

                localStorage.setItem('token', token);
                localStorage.setItem('user', JSON.stringify(userData));

                return true;
            } catch (error) {
                console.error('Login failed', error);
                return false;
            }
        },
        logout() {
            this.token = null;
            this.user = null;
            localStorage.removeItem('token');
            localStorage.removeItem('user');
        }
    }
});
