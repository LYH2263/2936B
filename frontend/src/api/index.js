import axios from 'axios';
import { message } from 'ant-design-vue';

const api = axios.create({
    baseURL: '/api',
    timeout: 5000,
    withCredentials: true,
    xsrfCookieName: 'XSRF-TOKEN',
    xsrfHeaderName: 'X-XSRF-TOKEN',
});

// Request interceptor
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response interceptor
api.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        if (error.response) {
            const { status, data } = error.response;
            if (status === 401) {
                message.error('登录已过期，请重新登录');
                localStorage.removeItem('token');
                window.location.href = '/login';
            } else if (status === 403) {
                message.error('权限不足，拒绝访问');
            } else if (status === 404) {
                message.error('未找到请求的资源');
            } else if (status === 500) {
                message.error('服务器内部错误，请稍后再试');
            } else {
                message.error(data.message || '请求执行失败');
            }
        } else if (error.request) {
            message.error('网络连接超时或服务器无响应');
        } else {
            message.error('请求配置异常');
        }
        return Promise.reject(error);
    }
);

export const login = (data) => api.post('/auth/login', data);
export const register = (data) => api.post('/auth/register', data);
export const updateProfile = (data) => api.put('/auth/profile', data);

// System Config
export const getSystemConfig = () => api.get('/config');
export const updateSystemConfig = (data) => api.post('/config', data);

// Exams
export const getExams = () => api.get('/exams');
export const getMySubmissions = () => api.get('/submissions/my');
export const createExam = (data) => api.post('/exams', data);
export const createQuestion = (data) => api.post('/exams/questions', data);
export const addQuestionToExam = (examId, data) => api.post(`/exams/${examId}/questions`, data);
export const getExam = (id) => api.get(`/exams/${id}`);
export const submitExam = (examId, data) => api.post(`/submissions/${examId}`, data);
export const getStudentStats = () => api.get('/submissions/stats');
export const getTeacherStats = () => api.get('/submissions/teacher-stats');
export const getExamSubmissions = (examId) => api.get(`/submissions/exam/${examId}`);
export const getSubmission = (id) => api.get(`/submissions/${id}`);
export const getExamQuestions = (examId) => api.get(`/exams/${examId}/questions`);
export const getAllQuestions = () => api.get('/exams/questions');
export const publishExam = (examId, data) => api.post(`/exams/${examId}/publish`, data);
export const deleteExam = (examId) => api.delete(`/exams/${examId}`);
export const getStatistics = (examId) => api.get(`/exams/${examId}/statistics`);
export const exportExamStatistics = (examId) => api.get(`/exams/${examId}/export`, { responseType: 'blob' });
export const gradeSubmission = (id, data) => api.post(`/submissions/${id}/grade`, data);
export const autoGenerateExam = (examId, strategy) => api.post(`/exams/${examId}/auto-generate`, strategy);
export const updateExamQuestion = (examId, questionId, data) => api.put(`/exams/${examId}/questions/${questionId}`, data);
export const removeQuestionFromExam = (examId, questionId) => api.delete(`/exams/${examId}/questions/${questionId}`);
export const recordCheating = (examId, data) => api.post(`/exams/${examId}/record-cheating`, data);

export const getNotifications = () => api.get('/notifications');
export const markNotificationRead = (id) => api.post(`/notifications/${id}/read`);

// User Management
export const getUsers = (params) => api.get('/users', { params });
export const createUser = (data) => api.post('/users', data);
export const updateUser = (id, data) => api.put(`/users/${id}`, data);
export const deleteUser = (id) => api.delete(`/users/${id}`);
export const importUsers = (formData) => api.post('/users/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
});
export const exportUsers = () => api.get('/users/export', { responseType: 'blob' });
export const resetUserPassword = (id, password) => api.post(`/users/${id}/reset-password`, { password });

export default api;
