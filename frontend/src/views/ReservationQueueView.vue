<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message, Modal } from 'ant-design-vue';
import {
  ClockCircleOutlined,
  UserOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  HourglassOutlined,
  ArrowLeftOutlined,
  PlayCircleOutlined,
  WarningOutlined
} from '@ant-design/icons-vue';
import {
  getQueuePosition,
  getQueueSnapshot,
  createReservation,
  cancelReservation,
  admitStudent,
  getExam,
  getTimeSlots
} from '@/api';
import { useAuthStore } from '@/stores/auth';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const examId = route.params.id;

const exam = ref(null);
const queuePosition = ref(null);
const queueSnapshot = ref(null);
const timeSlots = ref([]);
const selectedTimeSlot = ref(null);
const loading = ref(true);
const secondsLeft = ref(0);
const timerInterval = ref(null);
const pollInterval = ref(null);
const stompClient = ref(null);
const connected = ref(false);

const isPending = computed(() => queuePosition.value?.status === 'PENDING');
const isConfirmed = computed(() => queuePosition.value?.status === 'CONFIRMED');
const isAdmitted = computed(() => queuePosition.value?.status === 'ADMITTED');
const isExpired = computed(() => queuePosition.value?.status === 'EXPIRED' || queuePosition.value?.status === 'CANCELLED');

const formatTime = (seconds) => {
  if (!seconds || seconds <= 0) return '00:00';
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const fetchData = async () => {
  try {
    const [examRes, posRes, snapshotRes, slotsRes] = await Promise.all([
      getExam(examId),
      getQueuePosition(examId).catch(() => ({ data: null })),
      getQueueSnapshot(examId).catch(() => ({ data: null })),
      getTimeSlots(examId).catch(() => ({ data: [] }))
    ]);

    exam.value = examRes.data;
    queuePosition.value = posRes.data;
    queueSnapshot.value = snapshotRes.data;
    timeSlots.value = slotsRes.data;

    if (queuePosition.value?.secondsUntilExpiry) {
      secondsLeft.value = queuePosition.value.secondsUntilExpiry;
    }
  } catch (e) {
    console.error('Failed to fetch data:', e);
    message.error('加载数据失败');
  } finally {
    loading.value = false;
  }
};

const connectWebSocket = () => {
  try {
    const token = localStorage.getItem('token');
    const socket = new SockJS('/ws');
    stompClient.value = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      onConnect: () => {
        connected.value = true;
        console.log('WebSocket connected');

        stompClient.value.subscribe(`/topic/exam/${examId}/queue`, (message) => {
          const data = JSON.parse(message.body);
          queueSnapshot.value = data;
        });

        stompClient.value.subscribe(`/user/queue/reservation`, (message) => {
          const data = JSON.parse(message.body);
          queuePosition.value = data;
          if (data.status === 'CONFIRMED') {
            message.success('恭喜！您已获得入场资格，请在倒计时结束前进入考场');
            secondsLeft.value = data.secondsUntilExpiry || 900;
            startCountdown();
          }
        });
      },
      onDisconnect: () => {
        connected.value = false;
        console.log('WebSocket disconnected');
      },
      onStompError: (error) => {
        console.error('WebSocket error:', error);
        connected.value = false;
      }
    });

    stompClient.value.activate();
  } catch (e) {
    console.error('Failed to connect WebSocket, falling back to polling:', e);
    startPolling();
  }
};

const startPolling = () => {
  if (pollInterval.value) clearInterval(pollInterval.value);
  pollInterval.value = setInterval(async () => {
    try {
      const posRes = await getQueuePosition(examId);
      const snapshotRes = await getQueueSnapshot(examId);

      if (posRes.data && queuePosition.value?.status !== posRes.data.status) {
        if (posRes.data.status === 'CONFIRMED' && queuePosition.value?.status === 'PENDING') {
          message.success('恭喜！您已获得入场资格，请在倒计时结束前进入考场');
          secondsLeft.value = posRes.data.secondsUntilExpiry || 900;
          startCountdown();
        }
      }

      queuePosition.value = posRes.data;
      queueSnapshot.value = snapshotRes.data;
    } catch (e) {
      console.error('Polling error:', e);
    }
  }, 3000);
};

const startCountdown = () => {
  if (timerInterval.value) clearInterval(timerInterval.value);
  timerInterval.value = setInterval(() => {
    if (secondsLeft.value > 0) {
      secondsLeft.value--;
    } else {
      clearInterval(timerInterval.value);
      fetchData();
    }
  }, 1000);
};

const handleReserve = async () => {
  try {
    loading.value = true;
    const data = { examId: parseInt(examId) };
    if (selectedTimeSlot.value) {
      data.timeSlotId = selectedTimeSlot.value;
    }
    await createReservation(data);
    message.success('预约成功，已加入排队');
    await fetchData();
    connectWebSocket();
  } catch (e) {
    console.error(e);
    message.error(e.response?.data?.message || '预约失败');
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  Modal.confirm({
    title: '确认取消预约',
    content: '取消后将失去当前排队位置，确定要取消吗？',
    okText: '确认取消',
    cancelText: '继续等待',
    okType: 'danger',
    onOk: async () => {
      try {
        await cancelReservation(examId);
        message.success('已取消预约');
        await fetchData();
      } catch (e) {
        console.error(e);
        message.error('取消失败');
      }
    }
  });
};

const handleEnterExam = async () => {
  try {
    await admitStudent(examId);
    router.push(`/exam/${examId}`);
  } catch (e) {
    console.error(e);
    message.error(e.response?.data?.message || '入场失败');
    fetchData();
  }
};

const goBack = () => {
  router.push('/dashboard');
};

onMounted(async () => {
  await fetchData();

  if (queuePosition.value && !isExpired.value) {
    connectWebSocket();
    if (isConfirmed.value && queuePosition.value.secondsUntilExpiry > 0) {
      secondsLeft.value = queuePosition.value.secondsUntilExpiry;
      startCountdown();
    }
  }
});

onUnmounted(() => {
  if (timerInterval.value) clearInterval(timerInterval.value);
  if (pollInterval.value) clearInterval(pollInterval.value);
  if (stompClient.value) {
    stompClient.value.deactivate();
  }
});
</script>

<template>
  <div class="queue-page-container">
    <div class="queue-wrapper">
      <a-button type="text" class="back-btn" @click="goBack">
        <ArrowLeftOutlined /> 返回大厅
      </a-button>

      <div v-if="loading" class="loading-container">
        <a-spin size="large" tip="加载中..." />
      </div>

      <div v-else-if="!exam" class="error-container">
        <a-result status="404" title="考试不存在" sub-title="请检查链接是否正确">
          <template #extra>
            <a-button type="primary" @click="goBack">返回首页</a-button>
          </template>
        </a-result>
      </div>

      <div v-else>
        <div class="exam-header">
          <div class="exam-info">
            <h1 class="exam-title">{{ exam.title }}</h1>
            <a-tag color="blue">{{ exam.course }}</a-tag>
            <span class="exam-duration">
              <ClockCircleOutlined /> {{ exam.duration }} 分钟
            </span>
          </div>
          <div class="connection-status" v-if="queuePosition">
            <span :class="['status-dot', connected ? 'online' : 'offline']"></span>
            <span>{{ connected ? '实时连接' : '轮询模式' }}</span>
          </div>
        </div>

        <div v-if="!queuePosition || isExpired" class="reserve-section">
          <a-card class="reserve-card">
            <template #title>
              <div class="card-title">
                <TeamOutlined /> 预约考试
              </div>
            </template>

            <div v-if="isExpired" class="expired-notice">
              <a-alert
                type="warning"
                show-icon
                :message="queuePosition?.status === 'EXPIRED' ? '预约已超时' : '预约已取消'"
                :description="queuePosition?.status === 'EXPIRED' ? '您未在规定时间内进入考场，资格已作废，请重新预约。' : '您已取消本次预约，可重新预约。'"
              />
            </div>

            <div class="reserve-info">
              <div class="info-row">
                <span class="label">最大同时在线</span>
                <span class="value">{{ exam.maxConcurrentUsers || 50 }} 人</span>
              </div>
              <div class="info-row">
                <span class="label">入场超时时间</span>
                <span class="value">{{ exam.admissionTimeout || 15 }} 分钟</span>
              </div>
              <div class="info-row" v-if="queueSnapshot">
                <span class="label">当前在线</span>
                <span class="value">{{ queueSnapshot.activeCount }} 人</span>
              </div>
              <div class="info-row" v-if="queueSnapshot">
                <span class="label">排队人数</span>
                <span class="value">{{ queueSnapshot.pendingCount }} 人</span>
              </div>
            </div>

            <div v-if="timeSlots.length > 0" class="time-slot-section">
              <h4>选择时段 (可选)</h4>
              <a-radio-group v-model:value="selectedTimeSlot" class="time-slot-group">
                <a-radio
                  v-for="slot in timeSlots"
                  :key="slot.id"
                  :value="slot.id"
                  :disabled="!slot.available"
                >
                  <div class="slot-item" :class="{ full: !slot.available }">
                    <span class="slot-time">
                      {{ formatDateTime(slot.startTime) }} - {{ formatDateTime(slot.endTime) }}
                    </span>
                    <span class="slot-count">
                      {{ slot.reservedCount }}/{{ slot.capacity }}
                    </span>
                    <a-tag v-if="!slot.available" color="red">已满</a-tag>
                  </div>
                </a-radio>
              </a-radio-group>
            </div>

            <a-button
              type="primary"
              size="large"
              block
              class="reserve-btn"
              :loading="loading"
              @click="handleReserve"
            >
              {{ selectedTimeSlot ? '预约选定时段' : '加入排队预约' }}
            </a-button>
          </a-card>
        </div>

        <div v-else-if="isPending" class="queue-section">
          <a-card class="queue-card">
            <div class="queue-header">
              <HourglassOutlined class="queue-icon pending" />
              <div>
                <h2 class="queue-title">排队等待中</h2>
                <p class="queue-subtitle">请耐心等待，有考生离场后将自动递补</p>
              </div>
            </div>

            <div class="queue-stats">
              <div class="stat-item">
                <div class="stat-value primary">{{ queuePosition.position }}</div>
                <div class="stat-label">您的排队序号</div>
              </div>
              <div class="stat-divider"></div>
              <div class="stat-item">
                <div class="stat-value">{{ queuePosition.totalWaiting }}</div>
                <div class="stat-label">总等待人数</div>
              </div>
              <div class="stat-divider"></div>
              <div class="stat-item">
                <div class="stat-value">{{ queuePosition.estimatedWaitMinutes }}</div>
                <div class="stat-label">预计等待 (分钟)</div>
              </div>
            </div>

            <div class="queue-progress">
              <div class="progress-label">
                <span>排队进度</span>
                <span>{{ queuePosition.position }} / {{ queuePosition.totalWaiting || queuePosition.position }}</span>
              </div>
              <a-progress
                :percent="Math.round(((queueSnapshot?.availableSlots || 0) + queuePosition.totalWaiting - queuePosition.position + 1) / (queuePosition.totalWaiting + 1) * 100)"
                :show-info="false"
                stroke-color="#faad14"
              />
            </div>

            <div class="live-stats" v-if="queueSnapshot">
              <div class="live-stat">
                <UserOutlined />
                <span>当前在线: {{ queueSnapshot.activeCount }} / {{ queueSnapshot.maxConcurrent }}</span>
              </div>
              <div class="live-stat">
                <CheckCircleOutlined />
                <span>可用名额: {{ queueSnapshot.availableSlots }}</span>
              </div>
            </div>

            <a-button
              danger
              block
              class="cancel-btn"
              @click="handleCancel"
            >
              取消排队
            </a-button>
          </a-card>
        </div>

        <div v-else-if="isConfirmed" class="confirmed-section">
          <a-card class="confirmed-card" :class="{ urgent: secondsLeft < 60 }">
            <div class="confirmed-header">
              <CheckCircleOutlined class="queue-icon confirmed" />
              <div>
                <h2 class="queue-title">入场资格已确认</h2>
                <p class="queue-subtitle">请在倒计时结束前进入考场，否则资格将作废</p>
              </div>
            </div>

            <div class="countdown-container">
              <div class="countdown-label">入场倒计时</div>
              <div class="countdown-value" :class="{ urgent: secondsLeft < 60 }">
                {{ formatTime(secondsLeft) }}
              </div>
              <div class="countdown-bar">
                <div
                  class="countdown-progress"
                  :style="{ width: (secondsLeft / (queuePosition.admissionTimeout * 60) * 100) + '%' }"
                  :class="{ urgent: secondsLeft < 60 }"
                ></div>
              </div>
            </div>

            <div class="time-slot-info" v-if="queuePosition.timeSlotStart">
              <h4>考试时段</h4>
              <div class="slot-detail">
                <ClockCircleOutlined />
                <span>
                  {{ formatDateTime(queuePosition.timeSlotStart) }} - {{ formatDateTime(queuePosition.timeSlotEnd) }}
                </span>
              </div>
            </div>

            <div class="warn-notice" v-if="secondsLeft < 60">
              <a-alert
                type="warning"
                show-icon
                message="即将超时"
                description="请立即进入考场，否则资格将作废！"
              />
            </div>

            <a-space class="action-buttons" size="large">
              <a-button size="large" @click="handleCancel">
                放弃资格
              </a-button>
              <a-button
                type="primary"
                size="large"
                @click="handleEnterExam"
              >
                <PlayCircleOutlined /> 立即进入考场
              </a-button>
            </a-space>
          </a-card>
        </div>

        <div v-else-if="isAdmitted" class="admitted-section">
          <a-card class="admitted-card">
            <div class="admitted-header">
              <CheckCircleOutlined class="queue-icon admitted" />
              <div>
                <h2 class="queue-title">已在考场内</h2>
                <p class="queue-subtitle">您已成功进入考试，祝您考试顺利！</p>
              </div>
            </div>

            <a-button
              type="primary"
              size="large"
              block
              @click="handleEnterExam"
            >
              <PlayCircleOutlined /> 继续考试
            </a-button>
          </a-card>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.queue-page-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
}

.queue-wrapper {
  max-width: 600px;
  margin: 0 auto;
}

.back-btn {
  color: white;
  margin-bottom: 20px;
}

.loading-container,
.error-container {
  background: white;
  border-radius: 16px;
  padding: 60px 40px;
  text-align: center;
}

.exam-header {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  color: white;
}

.exam-title {
  color: white;
  font-size: 24px;
  margin: 0 0 12px 0;
}

.exam-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exam-duration {
  font-size: 14px;
  opacity: 0.9;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  opacity: 0.9;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.online {
  background: #52c41a;
  box-shadow: 0 0 8px #52c41a;
}

.status-dot.offline {
  background: #faad14;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.reserve-card,
.queue-card,
.confirmed-card,
.admitted-card {
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.expired-notice {
  margin-bottom: 24px;
}

.reserve-info {
  background: #f8fafc;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}

.info-row .label {
  color: #8c8c8c;
}

.info-row .value {
  font-weight: 600;
  color: #262626;
}

.time-slot-section {
  margin-bottom: 24px;
}

.time-slot-section h4 {
  margin-bottom: 12px;
  color: #262626;
}

.time-slot-group {
  width: 100%;
}

.slot-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.slot-item.full {
  opacity: 0.5;
}

.slot-time {
  flex: 1;
}

.slot-count {
  color: #8c8c8c;
  font-size: 13px;
}

.reserve-btn {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
}

.queue-header,
.confirmed-header,
.admitted-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f2f5;
}

.queue-icon {
  font-size: 48px;
}

.queue-icon.pending {
  color: #faad14;
}

.queue-icon.confirmed {
  color: #52c41a;
}

.queue-icon.admitted {
  color: #1890ff;
}

.queue-title {
  margin: 0 0 8px 0;
  font-size: 22px;
  color: #262626;
}

.queue-subtitle {
  margin: 0;
  color: #8c8c8c;
}

.queue-stats {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 24px 0;
  background: #f8fafc;
  border-radius: 12px;
  margin-bottom: 24px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #262626;
  line-height: 1;
  margin-bottom: 8px;
}

.stat-value.primary {
  color: #1890ff;
}

.stat-label {
  font-size: 13px;
  color: #8c8c8c;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: #e8e8e8;
}

.queue-progress {
  margin-bottom: 24px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  color: #595959;
}

.live-stats {
  display: flex;
  justify-content: space-around;
  padding: 16px;
  background: #e6f7ff;
  border-radius: 8px;
  margin-bottom: 24px;
}

.live-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #0050b3;
  font-size: 14px;
}

.cancel-btn {
  height: 44px;
  border-radius: 10px;
}

.countdown-container {
  text-align: center;
  padding: 32px;
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  border-radius: 12px;
  margin-bottom: 24px;
}

.confirmed-card.urgent .countdown-container {
  background: linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%);
}

.countdown-label {
  font-size: 14px;
  color: #595959;
  margin-bottom: 12px;
}

.countdown-value {
  font-size: 64px;
  font-weight: 700;
  color: #52c41a;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1;
  margin-bottom: 16px;
}

.countdown-value.urgent {
  color: #f5222d;
  animation: pulse 1s infinite;
}

.countdown-bar {
  height: 6px;
  background: #d9d9d9;
  border-radius: 3px;
  overflow: hidden;
}

.countdown-progress {
  height: 100%;
  background: #52c41a;
  transition: width 1s linear;
}

.countdown-progress.urgent {
  background: #f5222d;
}

.time-slot-info {
  margin-bottom: 24px;
}

.time-slot-info h4 {
  margin-bottom: 12px;
  color: #262626;
}

.slot-detail {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  color: #595959;
}

.warn-notice {
  margin-bottom: 24px;
}

.action-buttons {
  display: flex;
  justify-content: center;
}

.action-buttons .ant-btn {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
</style>
