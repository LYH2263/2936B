<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getReplayData } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { useConfigStore } from '@/stores/config';
import { message, Modal } from 'ant-design-vue';
import {
  PlayCircleOutlined, PauseCircleOutlined,
  CaretLeftOutlined, CaretRightOutlined,
  ClockCircleOutlined, UserOutlined, EyeOutlined,
  InfoCircleOutlined, AppstoreOutlined,
  BackwardOutlined, ForwardOutlined,
  CheckCircleFilled, CloseCircleFilled
} from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const configStore = useConfigStore();
const submissionId = route.params.submissionId;

const replayData = ref(null);
const loading = ref(true);
const currentSnapshotIndex = ref(0);
const isPlaying = ref(false);
const playSpeed = ref(1);
const playTimer = ref(null);
const currentElapsedSeconds = ref(0);
const replayQuestions = ref([]);
const replayAnswers = ref({});
const replayCurrentIndex = ref(0);
const replayTimeLeft = ref(0);

const speedOptions = [0.5, 1, 1.5, 2, 3];

const totalDuration = computed(() => {
  if (!replayData.value || !replayData.value.timeline.length) return 0;
  const lastSnapshot = replayData.value.timeline[replayData.value.timeline.length - 1];
  return lastSnapshot.elapsedSeconds;
});

const currentSnapshot = computed(() => {
  if (!replayData.value || !replayData.value.timeline.length) return null;
  return replayData.value.timeline[currentSnapshotIndex.value];
});

const currentQuestion = computed(() => {
  if (!replayQuestions.value.length) return null;
  return replayQuestions.value[replayCurrentIndex.value];
});

const formatTime = (seconds) => {
  if (!seconds || seconds < 0) seconds = 0;
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s < 10 ? '0' + s : s}`;
};

const progressPercent = computed(() => {
  if (!totalDuration.value) return 0;
  return (currentElapsedSeconds.value / totalDuration.value) * 100;
});

const fetchReplayData = async () => {
  try {
    const res = await getReplayData(submissionId);
    replayData.value = res.data;
    replayQuestions.value = res.data.questions.map((eq, idx) => ({
      ...eq,
      question: {
        id: eq.questionId,
        type: eq.type,
        content: eq.content,
        options: eq.options
      },
      score: eq.score
    }));

    if (res.data.timeline.length > 0) {
      applySnapshot(0);
    }
  } catch (e) {
    console.error(e);
    if (e.response?.status === 403) {
      Modal.error({
        title: '无权访问',
        content: e.response.data?.message || '您没有权限查看此回放',
        onOk: () => router.push('/dashboard')
      });
    } else {
      message.error('加载回放数据失败');
      router.push('/dashboard');
    }
  } finally {
    loading.value = false;
  }
};

const applySnapshot = (index) => {
  if (!replayData.value || !replayData.value.timeline.length) return;
  if (index < 0 || index >= replayData.value.timeline.length) return;

  currentSnapshotIndex.value = index;
  const snapshot = replayData.value.timeline[index];

  replayAnswers.value = { ...snapshot.answers };
  replayCurrentIndex.value = snapshot.currentQuestionIndex;
  replayTimeLeft.value = snapshot.timeLeft;
  currentElapsedSeconds.value = snapshot.elapsedSeconds;
};

const findNearestSnapshotIndex = (targetSeconds) => {
  if (!replayData.value || !replayData.value.timeline.length) return 0;

  let nearestIdx = 0;
  let minDiff = Math.abs(replayData.value.timeline[0].elapsedSeconds - targetSeconds);

  for (let i = 1; i < replayData.value.timeline.length; i++) {
    const diff = Math.abs(replayData.value.timeline[i].elapsedSeconds - targetSeconds);
    if (diff < minDiff) {
      minDiff = diff;
      nearestIdx = i;
    } else if (replayData.value.timeline[i].elapsedSeconds > targetSeconds) {
      break;
    }
  }

  return nearestIdx;
};

const togglePlay = () => {
  if (isPlaying.value) {
    pausePlayback();
  } else {
    startPlayback();
  }
};

const startPlayback = () => {
  if (currentSnapshotIndex.value >= replayData.value.timeline.length - 1) {
    currentSnapshotIndex.value = 0;
    applySnapshot(0);
  }
  isPlaying.value = true;
  tickPlayback();
};

const pausePlayback = () => {
  isPlaying.value = false;
  if (playTimer.value) {
    clearTimeout(playTimer.value);
    playTimer.value = null;
  }
};

const tickPlayback = () => {
  if (!isPlaying.value) return;

  const nextIdx = currentSnapshotIndex.value + 1;
  if (nextIdx >= replayData.value.timeline.length) {
    pausePlayback();
    return;
  }

  const currentSnap = replayData.value.timeline[currentSnapshotIndex.value];
  const nextSnap = replayData.value.timeline[nextIdx];
  const timeDiff = Math.max(100, (nextSnap.elapsedSeconds - currentSnap.elapsedSeconds) * 1000 / playSpeed.value);

  applySnapshot(nextIdx);

  playTimer.value = setTimeout(tickPlayback, timeDiff);
};

const jumpTo = (index) => {
  pausePlayback();
  replayCurrentIndex.value = index;
};

const prevQuestion = () => {
  if (replayCurrentIndex.value > 0) {
    replayCurrentIndex.value--;
  }
};

const nextQuestion = () => {
  if (replayCurrentIndex.value < replayQuestions.value.length - 1) {
    replayCurrentIndex.value++;
  }
};

const handleProgressClick = (e) => {
  pausePlayback();
  const rect = e.currentTarget.getBoundingClientRect();
  const percent = (e.clientX - rect.left) / rect.width;
  const targetSeconds = percent * totalDuration.value;
  const nearestIdx = findNearestSnapshotIndex(targetSeconds);
  applySnapshot(nearestIdx);
};

const changeSpeed = (speed) => {
  playSpeed.value = speed;
};

const skipBackward = () => {
  pausePlayback();
  const targetSeconds = Math.max(0, currentElapsedSeconds.value - 30);
  const nearestIdx = findNearestSnapshotIndex(targetSeconds);
  applySnapshot(nearestIdx);
};

const skipForward = () => {
  pausePlayback();
  const targetSeconds = Math.min(totalDuration.value, currentElapsedSeconds.value + 30);
  const nearestIdx = findNearestSnapshotIndex(targetSeconds);
  applySnapshot(nearestIdx);
};

const isCorrect = (qId) => {
  if (!replayData.value?.canViewAnalysis) return false;
  const q = replayQuestions.value.find(i => i.question.id === qId);
  if (!q) return false;
  const studentAns = replayAnswers.value[qId];
  let correctAns;
  try {
    const qData = JSON.parse(q.question);
    correctAns = qData.answer;
  } catch {
    correctAns = q.question.answer;
  }
  return studentAns === correctAns;
};

const getCorrectAnswer = (qId) => {
  if (!replayData.value?.canViewAnalysis) return '***';
  const q = replayQuestions.value.find(i => i.question.id === qId);
  if (!q) return '';
  try {
    const qData = JSON.parse(q.question);
    return qData.answer;
  } catch {
    return q.question.answer;
  }
};

const getAnalysis = (qId) => {
  if (!replayData.value?.canViewAnalysis) return '教师设置了隐藏解析';
  const q = replayQuestions.value.find(i => i.question.id === qId);
  if (!q) return '';
  try {
    const qData = JSON.parse(q.question);
    return qData.analysis || '暂无解析数据';
  } catch {
    return q.question.analysis || '暂无解析数据';
  }
};

watch(playSpeed, () => {
  if (isPlaying.value) {
    pausePlayback();
    startPlayback();
  }
});

onMounted(() => {
  fetchReplayData();
});

onUnmounted(() => {
  pausePlayback();
});
</script>

<template>
  <div class="replay-page-container" v-if="replayData">
    <a-layout class="full-height-layout">
      <a-layout-header class="prof-exam-header">
         <div class="header-left">
           <div class="brand-logo" v-if="configStore.logoUrl">
             <img :src="configStore.logoUrl" alt="logo" />
           </div>
           <div class="exam-meta-info">
             <h2 class="exam-title-text">{{ replayData.examTitle }}</h2>
             <span class="course-tag">
               <EyeOutlined /> 答题回放 {{ replayData.isTeacherView ? '- 教师视图' : '' }}
             </span>
           </div>
         </div>

         <div class="header-center">
            <div class="replay-badge">
              <UserOutlined /> {{ replayData.studentName }} 的答题记录
            </div>
         </div>

         <div class="header-right">
            <a-button shape="round" @click="router.push('/dashboard')">
              退出回放
            </a-button>
         </div>
      </a-layout-header>

      <a-layout class="exam-main-layout">
         <a-layout-sider width="320" theme="light" class="prof-exam-sider" :trigger="null">
            <div class="sider-inner">
               <div class="navigator-card">
                  <div class="nav-header">
                     <AppstoreOutlined /> 答题卡导航
                  </div>
                  <div class="nav-indicators">
                     <div class="ind-item"><span class="dot current"></span> 当前</div>
                     <div class="ind-item"><span class="dot done"></span> 已答</div>
                     <div class="ind-item"><span class="dot"></span> 未答</div>
                  </div>
                  <div class="nav-grid">
                     <div
                        v-for="(q, idx) in replayQuestions"
                        :key="q.id"
                        class="nav-cell"
                        :class="{
                           'is-active': replayCurrentIndex === idx,
                           'is-done': replayAnswers[q.question.id] !== undefined && replayAnswers[q.question.id] !== ''
                        }"
                        @click="jumpTo(idx)"
                     >
                        {{ idx + 1 }}
                     </div>
                  </div>
               </div>

               <div class="replay-info-card">
                  <div class="info-header">
                    <InfoCircleOutlined /> 回放信息
                  </div>
                  <div class="info-item">
                    <span class="info-label">当前时间</span>
                    <span class="info-value">{{ formatTime(currentElapsedSeconds) }} / {{ formatTime(totalDuration) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">剩余时间</span>
                    <span class="info-value">{{ formatTime(replayTimeLeft) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">快照数量</span>
                    <span class="info-value">{{ replayData.timeline.length }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">当前快照</span>
                    <span class="info-value">#{{ currentSnapshotIndex + 1 }}</span>
                  </div>
               </div>
            </div>
         </a-layout-sider>

         <a-layout-content class="prof-exam-content">
            <div class="question-canvas" v-if="currentQuestion">
               <div class="q-scope-header">
                  <div class="q-type-label">
                     <a-tag color="blue">
                        {{ { 'SINGLE': '单选题', 'MULTI': '多选题', 'JUDGE': '判断题', 'SHORT': '简答题' }[currentQuestion.question.type] }}
                     </a-tag>
                     <span class="q-index-info">第 {{ replayCurrentIndex + 1 }} 题 / 共 {{ replayQuestions.length }} 题</span>
                  </div>
                  <div class="q-actions-right">
                    <span class="q-score-tag">分值: {{ currentQuestion.score }}分</span>
                  </div>
               </div>

               <div class="q-content-body">
                  <div class="question-text" v-html="currentQuestion.question.content"></div>

                  <div class="answer-interaction-area">
                     <!-- SINGLE -->
                     <a-radio-group v-if="currentQuestion.question.type === 'SINGLE'" :value="replayAnswers[currentQuestion.question.id]" disabled class="choice-group">
                        <a-radio class="choice-item" v-for="opt in JSON.parse(currentQuestion.question.options)" :key="opt.label" :value="opt.label">
                           <span class="choice-key">{{ opt.label }}</span>
                           <span class="choice-text">{{ opt.text }}</span>
                        </a-radio>
                     </a-radio-group>

                     <!-- MULTI -->
                     <a-checkbox-group v-if="currentQuestion.question.type === 'MULTI'" :value="replayAnswers[currentQuestion.question.id]" disabled class="choice-group">
                        <div v-for="opt in JSON.parse(currentQuestion.question.options)" :key="opt.label" class="choice-item multi-wrap">
                           <a-checkbox :value="opt.label" :disabled="true">
                              <span class="choice-key">{{ opt.label }}</span>
                              <span class="choice-text">{{ opt.text }}</span>
                           </a-checkbox>
                        </div>
                     </a-checkbox-group>

                     <!-- JUDGE -->
                     <a-radio-group v-if="currentQuestion.question.type === 'JUDGE'" :value="replayAnswers[currentQuestion.question.id]" disabled class="judge-group">
                        <a-radio-button value="TRUE" class="judge-btn">正确 (TRUE)</a-radio-button>
                        <a-radio-button value="FALSE" class="judge-btn">错误 (FALSE)</a-radio-button>
                     </a-radio-group>

                     <!-- SHORT -->
                     <div v-if="currentQuestion.question.type === 'SHORT'" class="short-answer-display">
                        <div class="short-answer-label">学生作答:</div>
                        <div class="short-answer-content">{{ replayAnswers[currentQuestion.question.id] || '(未作答)' }}</div>
                     </div>
                  </div>
               </div>

               <!-- Analysis View -->
               <div v-if="replayData.canViewAnalysis" class="prof-analysis-section">
                  <div class="analysis-header-row">
                     <div class="judge-banner" :class="isCorrect(currentQuestion.question.id) ? 'correct' : 'wrong'">
                        <CheckCircleFilled v-if="isCorrect(currentQuestion.question.id)" />
                        <CloseCircleFilled v-else />
                        <span>{{ isCorrect(currentQuestion.question.id) ? '回答正确' : '回答错误' }}</span>
                     </div>
                  </div>

                  <div class="analysis-details-grid">
                     <div class="detail-col">
                        <div class="detail-label">学生作答</div>
                        <div class="detail-val" :class="{ 'error-text': !isCorrect(currentQuestion.question.id) }">
                           {{ replayAnswers[currentQuestion.question.id] || '(未答)' }}
                        </div>
                     </div>
                     <div class="detail-col">
                        <div class="detail-label">正确答案</div>
                        <div class="detail-val correct-text">{{ getCorrectAnswer(currentQuestion.question.id) }}</div>
                     </div>
                  </div>

                  <div class="explanation-box">
                     <div class="exp-label">解析说明</div>
                     <div class="exp-content">{{ getAnalysis(currentQuestion.question.id) }}</div>
                  </div>
               </div>
            </div>

            <div class="prof-exam-pager">
               <div class="pager-left">
                  <a-button @click="prevQuestion" :disabled="replayCurrentIndex === 0" size="large" ghost type="primary" class="nav-btn">
                    <CaretLeftOutlined /> 上一题
                  </a-button>
               </div>
               <div class="pager-center">
                  进度: <a-progress :percent="Math.round(((Object.keys(replayAnswers).filter(k => replayAnswers[k] !== '').length) / replayQuestions.length) * 100)" size="small" style="width: 200px" />
               </div>
               <div class="pager-right">
                  <a-button type="primary" @click="nextQuestion" :disabled="replayCurrentIndex === replayQuestions.length - 1" size="large" class="nav-btn next">
                     下一题 <CaretRightOutlined />
                  </a-button>
               </div>
            </div>
         </a-layout-content>
      </a-layout>
    </a-layout>

    <!-- Replay Player Controls -->
    <div class="replay-player-bar">
      <div class="player-progress" @click="handleProgressClick">
        <div class="progress-bg"></div>
        <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        <div class="progress-handle" :style="{ left: progressPercent + '%' }"></div>
      </div>

      <div class="player-controls">
        <div class="controls-left">
          <a-button type="text" class="player-btn" @click="skipBackward">
            <BackwardOutlined />
          </a-button>
          <a-button type="primary" shape="circle" size="large" class="play-btn" @click="togglePlay">
            <PauseCircleOutlined v-if="isPlaying" />
            <PlayCircleOutlined v-else />
          </a-button>
          <a-button type="text" class="player-btn" @click="skipForward">
            <ForwardOutlined />
          </a-button>
          <span class="time-display">{{ formatTime(currentElapsedSeconds) }} / {{ formatTime(totalDuration) }}</span>
        </div>

        <div class="controls-right">
          <span class="speed-label">倍速:</span>
          <a-radio-group :value="playSpeed" @change="(e) => changeSpeed(e.target.value)" size="small" button-style="solid">
            <a-radio-button v-for="speed in speedOptions" :key="speed" :value="speed">
              {{ speed }}x
            </a-radio-button>
          </a-radio-group>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="prof-exam-loading">
    <a-spin size="large" tip="系统正在加载回放数据，请稍候..." />
  </div>
</template>

<style scoped>
.replay-page-container {
  height: 100vh;
  background-color: #f7f9fc;
  display: flex;
  flex-direction: column;
}
.full-height-layout {
  flex: 1;
  min-height: 0;
}

/* Header Styles */
.prof-exam-header {
  height: 72px;
  background: white;
  border-bottom: 2px solid #e1e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32px;
  line-height: normal;
  z-index: 1000;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.brand-logo img {
  height: 36px;
}
.exam-title-text {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: #1a1a1a;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-tag {
  font-size: 12px;
  color: #8c8c8c;
  background: #f0f2f5;
  padding: 2px 8px;
  border-radius: 4px;
}
.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}
.replay-badge {
  background: #e6f7ff;
  color: #1890ff;
  padding: 8px 20px;
  border-radius: 20px;
  font-weight: 600;
  font-size: 14px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

/* Sider Styles */
.prof-exam-sider {
  border-right: 1px solid #e1e8f0;
  overflow-y: auto;
}
.sider-inner {
  padding: 24px;
}
.navigator-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.nav-header {
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #262626;
}
.nav-indicators {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 24px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}
.ind-item {
  font-size: 12px;
  color: #8c8c8c;
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1px solid #d9d9d9;
}
.dot.current { background: #1890ff; border-color: #1890ff; }
.dot.done { background: #e6f7ff; border-color: #91d5ff; }

.nav-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}
.nav-cell {
  width: 100%;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e1e8f0;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
  font-size: 14px;
}
.nav-cell:hover {
  border-color: #1890ff;
  color: #1890ff;
}
.nav-cell.is-done {
  background: #e6f7ff;
  border-color: #91d5ff;
}
.nav-cell.is-active {
  background: #1890ff !important;
  border-color: #1890ff !important;
  color: white !important;
  box-shadow: 0 4px 8px rgba(24, 144, 255, 0.35);
}

.replay-info-card {
  margin-top: 24px;
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.info-header {
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 16px;
  color: #262626;
  display: flex;
  align-items: center;
  gap: 8px;
}
.info-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
  font-size: 14px;
}
.info-item:last-child {
  border-bottom: none;
}
.info-label {
  color: #8c8c8c;
}
.info-value {
  font-weight: 600;
  color: #262626;
  font-family: 'JetBrains Mono', monospace;
}

/* Content Area Styles */
.prof-exam-content {
  padding: 40px 60px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.question-canvas {
  background: white;
  min-height: 500px;
  border-radius: 16px;
  padding: 48px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.04);
  margin-bottom: 40px;
  flex: 1;
}
.q-scope-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f2f5;
}
.q-index-info {
  margin-left: 12px;
  font-size: 14px;
  color: #8c8c8c;
}
.q-score-tag {
  color: #595959;
  font-size: 14px;
  margin-right: 20px;
}
.question-text {
  font-size: 20px;
  line-height: 1.8;
  color: #1a1a1a;
  margin-bottom: 40px;
}
.answer-interaction-area {
  margin-top: 24px;
}
.choice-group {
  width: 100%;
}
.choice-item {
  width: 100%;
  margin-bottom: 16px !important;
  display: flex !important;
  align-items: center;
  padding: 16px 24px;
  border: 1px solid #e1e8f0;
  border-radius: 12px;
  transition: all 0.2s;
  font-size: 16px;
  background: #fafafa;
}
.choice-key {
  font-weight: 700;
  margin-right: 12px;
  color: #1890ff;
}
.choice-text {
  color: #262626;
}
.judge-group {
  display: flex;
  gap: 16px;
}
.judge-btn {
  height: 50px;
  min-width: 160px;
  text-align: center;
  line-height: 48px;
}
.short-answer-display {
  background: #f8fafc;
  border-radius: 12px;
  padding: 24px;
}
.short-answer-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 8px;
}
.short-answer-content {
  font-size: 17px;
  line-height: 1.8;
  color: #262626;
  background: white;
  padding: 16px;
  border-radius: 8px;
  border: 1px dashed #d9d9d9;
}

/* Sticky Pager */
.prof-exam-pager {
  position: sticky;
  bottom: 90px;
  background: white;
  padding: 20px 40px;
  border-radius: 12px;
  box-shadow: 0 -4px 12px rgba(0,0,0,0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 99;
}
.nav-btn {
  min-width: 140px;
  height: 46px;
  font-weight: 600;
  border-radius: 23px;
}

/* Analysis Styles */
.prof-analysis-section {
  margin-top: 48px;
  background: #f8fafc;
  padding: 32px;
  border-radius: 16px;
  border-left: 6px solid #1890ff;
}
.analysis-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.judge-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
}
.judge-banner.correct { color: #52c41a; }
.judge-banner.wrong { color: #f5222d; }

.analysis-details-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 32px;
}
.detail-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 8px;
}
.detail-val {
  font-size: 18px;
  font-weight: 600;
  padding: 12px 16px;
  background: white;
  border-radius: 8px;
  border: 1px dashed #d9d9d9;
}
.correct-text { color: #52c41a; border-color: #b7eb8f; background: #f6ffed; }
.error-text { color: #f5222d; border-color: #ffa39e; background: #fff1f0; }

.explanation-box {
  margin-bottom: 24px;
}
.exp-label {
  font-weight: 600;
  color: #262626;
  margin-bottom: 10px;
}
.exp-content {
  line-height: 1.8;
  color: #595959;
}

/* Replay Player Bar */
.replay-player-bar {
  background: linear-gradient(180deg, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.95) 100%);
  padding: 12px 32px 16px;
  z-index: 1001;
}
.player-progress {
  height: 8px;
  background: rgba(255,255,255,0.2);
  border-radius: 4px;
  position: relative;
  cursor: pointer;
  margin-bottom: 12px;
}
.progress-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}
.progress-fill {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  background: #1890ff;
  border-radius: 4px;
  transition: width 0.1s;
}
.progress-handle {
  position: absolute;
  top: 50%;
  width: 16px;
  height: 16px;
  background: white;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  transition: left 0.1s;
}
.player-progress:hover .progress-handle {
  transform: translate(-50%, -50%) scale(1.2);
}

.player-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.controls-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.player-btn {
  color: white;
  font-size: 20px;
}
.player-btn:hover {
  color: #1890ff;
  background: rgba(255,255,255,0.1);
}
.play-btn {
  width: 48px;
  height: 48px;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.time-display {
  color: white;
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
  margin-left: 12px;
  font-weight: 500;
}
.controls-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.speed-label {
  color: rgba(255,255,255,0.7);
  font-size: 13px;
}

.prof-exam-loading {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
}

:deep(.ant-radio-wrapper::after), :deep(.ant-checkbox-wrapper::after) {
  display: none !important;
}
:deep(.ant-radio-inner), :deep(.ant-checkbox-inner) {
  width: 20px;
  height: 20px;
}
:deep(.choice-text) {
  white-space: normal;
}
</style>
