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

// Reservations
export const createReservation = (data) => api.post('/reservations', data);
export const cancelReservation = (examId) => api.delete(`/reservations/${examId}`);
export const getQueuePosition = (examId) => api.get(`/reservations/${examId}/position`);
export const getTimeSlots = (examId) => api.get(`/reservations/${examId}/timeslots`);
export const getQueueSnapshot = (examId) => api.get(`/reservations/${examId}/snapshot`);
export const admitStudent = (examId) => api.post(`/reservations/${examId}/admit`);
export const canEnterExam = (examId) => api.get(`/reservations/${examId}/can-enter`);
export const completeReservation = (examId) => api.post(`/reservations/${examId}/complete`);

export const getNotifications = () => api.get('/notifications');
export const markNotificationRead = (id) => api.post(`/notifications/${id}/read`);

// Wrong Question Book
export const getWrongQuestions = (params) => api.get('/wrong-book', { params });
export const getWrongBookStats = () => api.get('/wrong-book/stats');
export const getWrongBookSubjects = () => api.get('/wrong-book/subjects');
export const getWrongBookKnowledgePoints = () => api.get('/wrong-book/knowledge-points');
export const getPracticeQuestions = (count = 10) => api.get('/wrong-book/practice', { params: { count } });
export const submitPracticeResult = (data) => api.post('/wrong-book/practice/submit', data);
export const removeFromWrongBook = (questionId) => api.delete(`/wrong-book/${questionId}`);
export const markQuestionMastered = (questionId) => api.put(`/wrong-book/${questionId}/mastered`);

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

// Exam Templates
export const getTemplates = () => api.get('/templates');
export const searchTemplates = (params) => api.get('/templates/search', { params });
export const getTemplate = (id) => api.get(`/templates/${id}`);
export const getTemplateQuestions = (id) => api.get(`/templates/${id}/questions`);
export const getMyTemplates = () => api.get('/templates/my');
export const saveAsTemplate = (examId, data) => api.post(`/templates/${examId}/save`, data);
export const createExamFromTemplate = (templateId, data) => api.post(`/templates/${templateId}/create-exam`, data);
export const updateTemplate = (id, data) => api.put(`/templates/${id}`, data);
export const deleteTemplate = (id) => api.delete(`/templates/${id}`);
export const getPendingTemplates = () => api.get('/templates/pending');
export const reviewTemplate = (id, data) => api.post(`/templates/${id}/review`, data);

// Learning Alerts
export const scanLearningAlerts = (params) => api.post('/learning-alerts/scan', params || {});
export const getLearningAlerts = (params) => api.get('/learning-alerts', { params });
export const getAlertStats = () => api.get('/learning-alerts/stats');
export const getAlertDetail = (id) => api.get(`/learning-alerts/${id}`);
export const resolveAlert = (id) => api.post(`/learning-alerts/${id}/resolve`);
export const resolveAlertsBatch = (ids) => api.post('/learning-alerts/resolve/batch', { ids });
export const exportLearningAlerts = (params) => api.get('/learning-alerts/export', {
    params,
    responseType: 'blob'
});
export const getMyAlerts = () => api.get('/learning-alerts/my');

export default api;
