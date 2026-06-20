<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useAuthStore } from '@/stores/auth';
import {
  getGradingExams,
  getGradingQuestions,
  getGradingQueue,
  getGradingQuestionDetail,
  updateRubric,
  batchGrade,
  getGradingStats
} from '@/api';
import { message } from 'ant-design-vue';
import {
  LeftOutlined,
  RightOutlined,
  CheckOutlined,
  EditOutlined,
  SaveOutlined,
  ReloadOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  UserOutlined
} from '@ant-design/icons-vue';

const authStore = useAuthStore();

const exams = ref([]);
const selectedExamId = ref(null);
const questions = ref([]);
const selectedQuestionId = ref(null);
const queue = ref([]);
const currentIndex = ref(0);
const stats = ref({
  todayGradedCount: 0,
  avgGradingSeconds: 0,
  totalPendingCount: 0
});

const loading = ref(false);
const queueLoading = ref(false);
const submitting = ref(false);

const questionDetail = ref(null);
const rubricEditMode = ref(false);
const rubricDraft = ref('');

const scores = ref({});
const comments = ref({});

const fetchExams = async () => {
  try {
    const res = await getGradingExams();
    exams.value = res.data;
    if (exams.value.length > 0 && !selectedExamId.value) {
      selectedExamId.value = exams.value[0].id;
    }
  } catch (e) {
    console.error('Failed to fetch exams', e);
  }
};

const fetchQuestions = async () => {
  if (!selectedExamId.value) return;
  try {
    const res = await getGradingQuestions(selectedExamId.value);
    questions.value = res.data;
    if (questions.value.length > 0 && !selectedQuestionId.value) {
      selectedQuestionId.value = questions.value[0].id;
    }
  } catch (e) {
    console.error('Failed to fetch questions', e);
  }
};

const fetchQuestionDetail = async () => {
  if (!selectedQuestionId.value) return;
  try {
    const res = await getGradingQuestionDetail(selectedQuestionId.value);
    questionDetail.value = res.data;
    rubricDraft.value = res.data.analysis || '';
  } catch (e) {
    console.error('Failed to fetch question detail', e);
  }
};

const fetchQueue = async () => {
  if (!selectedExamId.value) return;
  queueLoading.value = true;
  try {
    const res = await getGradingQueue({
      examId: selectedExamId.value,
      questionId: selectedQuestionId.value,
      limit: 20
    });
    queue.value = res.data;
    currentIndex.value = 0;
    scores.value = {};
    comments.value = {};
    queue.value.forEach(item => {
      scores.value[item.submissionAnswerId] = item.currentScore ?? null;
      comments.value[item.submissionAnswerId] = '';
    });
  } catch (e) {
    message.error('加载待批改队列失败');
  } finally {
    queueLoading.value = false;
  }
};

const fetchStats = async () => {
  try {
    const res = await getGradingStats();
    stats.value = res.data;
  } catch (e) {
    console.error('Failed to fetch stats', e);
  }
};

watch(selectedExamId, () => {
  fetchQuestions();
});

watch(selectedQuestionId, () => {
  fetchQuestionDetail();
  fetchQueue();
});

const currentItem = computed(() => {
  return queue.value[currentIndex.value] || null;
});

const currentQuestion = computed(() => {
  return questions.value.find(q => q.id === selectedQuestionId.value) || null;
});

const maxScore = computed(() => {
  return currentQuestion.value?.score || questionDetail.value?.defaultScore || 5;
});

const formatTime = (seconds) => {
  if (!seconds) return '0秒';
  const s = Math.round(seconds);
  if (s < 60) return s + '秒';
  const m = Math.floor(s / 60);
  const sec = s % 60;
  return m + '分' + sec + '秒';
};

const scoreWithKey = (score) => {
  if (!currentItem.value) return;
  const actualScore = Math.min(score, maxScore.value);
  scores.value[currentItem.value.submissionAnswerId] = actualScore;
};

const goNext = () => {
  if (currentIndex.value < queue.value.length - 1) {
    currentIndex.value++;
  }
};

const goPrev = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--;
  }
};

const submitCurrentAndNext = async () => {
  if (!currentItem.value) return;
  if (scores.value[currentItem.value.submissionAnswerId] == null) {
    message.warning('请先给当前答案评分');
    return;
  }

  const hasMore = currentIndex.value < queue.value.length - 1;
  
  const item = currentItem.value;
  const gradeItem = {
    submissionAnswerId: item.submissionAnswerId,
    version: item.version,
    score: scores.value[item.submissionAnswerId],
    teacherComment: comments.value[item.submissionAnswerId] || ''
  };

  submitting.value = true;
  try {
    const res = await batchGrade([gradeItem]);
    if (res.data.failedCount > 0) {
      message.error(res.data.failedMessages[0] || '提交失败');
    } else {
      message.success('已提交评分');
      if (hasMore) {
        goNext();
      } else {
        message.success('当前批次已全部批改完成');
        fetchQueue();
      }
      fetchStats();
    }
  } catch (e) {
    message.error('提交失败');
  } finally {
    submitting.value = false;
  }
};

const submitBatch = async () => {
  const gradedItems = queue.value.filter(
    item => scores.value[item.submissionAnswerId] != null
  );
  
  if (gradedItems.length === 0) {
    message.warning('请至少给一份答案评分');
    return;
  }

  const batch = gradedItems.map(item => ({
    submissionAnswerId: item.submissionAnswerId,
    version: item.version,
    score: scores.value[item.submissionAnswerId],
    teacherComment: comments.value[item.submissionAnswerId] || ''
  }));

  submitting.value = true;
  try {
    const res = await batchGrade(batch);
    if (res.data.successCount > 0) {
      message.success(`成功批改 ${res.data.successCount} 份`);
    }
    if (res.data.failedCount > 0) {
      message.error(`失败 ${res.data.failedCount} 份：${res.data.failedMessages.slice(0, 2).join('; ')}`);
    }
    fetchStats();
    fetchQueue();
  } catch (e) {
    message.error('批量提交失败');
  } finally {
    submitting.value = false;
  }
};

const saveRubric = async () => {
  if (!selectedQuestionId.value) return;
  try {
    await updateRubric(selectedQuestionId.value, rubricDraft.value);
    questionDetail.value.analysis = rubricDraft.value;
    rubricEditMode.value = false;
    message.success('评分标准已保存');
  } catch (e) {
    message.error('保存失败');
  }
};

const handleKeyDown = (e) => {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
    if (e.key === 'Escape') {
      e.target.blur();
    }
    return;
  }

  const num = parseInt(e.key);
  if (num >= 1 && num <= 9) {
    e.preventDefault();
    scoreWithKey(num);
  } else if (e.key === '0') {
    e.preventDefault();
    scoreWithKey(0);
  } else if (e.key === 'ArrowRight' || e.key === 'j') {
    e.preventDefault();
    goNext();
  } else if (e.key === 'ArrowLeft' || e.key === 'k') {
    e.preventDefault();
    goPrev();
  } else if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    submitCurrentAndNext();
  }
};

onMounted(() => {
  fetchExams();
  fetchStats();
  window.addEventListener('keydown', handleKeyDown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown);
});
</script>

<template>
  <div class="grading-workbench">
    <div class="workbench-header">
      <div class="header-left">
        <h2><FileTextOutlined /> 批量批改工作台</h2>
      </div>
      <div class="header-right">
        <div class="stat-card">
          <span class="stat-label">今日已批</span>
          <span class="stat-value">{{ stats.todayGradedCount }}</span>
          <span class="stat-unit">份</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">平均用时</span>
          <span class="stat-value">{{ formatTime(stats.avgGradingSeconds) }}</span>
        </div>
        <div class="stat-card pending">
          <span class="stat-label">待批改</span>
          <span class="stat-value">{{ stats.totalPendingCount }}</span>
          <span class="stat-unit">份</span>
        </div>
      </div>
    </div>

    <div class="workbench-body">
      <div class="panel left-panel">
        <div class="panel-section">
          <div class="section-title">选择考试</div>
          <a-select
            v-model:value="selectedExamId"
            style="width: 100%"
            placeholder="请选择考试"
            :loading="loading"
            @change="fetchQueue"
          >
            <a-select-option
              v-for="exam in exams"
              :key="exam.id"
              :value="exam.id"
            >
              {{ exam.title }}
            </a-select-option>
          </a-select>
        </div>

        <div class="panel-section">
          <div class="section-title">题目列表</div>
          <div class="question-list">
            <div
              v-for="q in questions"
              :key="q.id"
              :class="['question-item', { active: q.id === selectedQuestionId }]"
              @click="selectedQuestionId = q.id"
            >
              <div class="q-score-badge">{{ q.score }}分</div>
              <div class="q-content">{{ q.content }}</div>
            </div>
            <a-empty v-if="questions.length === 0" description="暂无主观题" />
          </div>
        </div>

        <div class="panel-section">
          <div class="section-title">
            <span>待批改队列</span>
            <a-button size="small" type="text" @click="fetchQueue">
              <ReloadOutlined /> 刷新
            </a-button>
          </div>
          <div class="queue-list" v-loading="queueLoading">
            <div
              v-for="(item, idx) in queue"
              :key="item.submissionAnswerId"
              :class="['queue-item', { active: idx === currentIndex, graded: scores[item.submissionAnswerId] != null }]"
              @click="currentIndex = idx"
            >
              <div class="queue-index">{{ idx + 1 }}</div>
              <div class="queue-info">
                <div class="queue-student">
                  <UserOutlined /> {{ item.studentName }}
                </div>
                <div class="queue-submit">
                  <ClockCircleOutlined /> {{ item.submitTime }}
                </div>
              </div>
              <div v-if="scores[item.submissionAnswerId] != null" class="queue-score">
                {{ scores[item.submissionAnswerId] }}分
              </div>
            </div>
            <a-empty v-if="queue.length === 0" description="暂无待批改答案" />
          </div>
        </div>
      </div>

      <div class="panel middle-panel">
        <div class="panel-section">
          <div class="section-title">
            <span>题目与评分标准</span>
            <a-button
              v-if="!rubricEditMode"
              size="small"
              type="text"
              @click="rubricEditMode = true"
            >
              <EditOutlined /> 编辑
            </a-button>
            <template v-else>
              <a-button size="small" type="primary" @click="saveRubric">
                <SaveOutlined /> 保存
              </a-button>
              <a-button size="small" @click="rubricEditMode = false; rubricDraft = questionDetail?.analysis || ''">
                取消
              </a-button>
            </template>
          </div>

          <div class="question-detail" v-if="questionDetail">
            <div class="q-stem">
              <div class="q-type-badge">简答题</div>
              <div class="q-full-score">满分：{{ maxScore }} 分</div>
              <div class="q-content-text" v-html="questionDetail.content"></div>
            </div>

            <div class="rubric-section">
              <div class="rubric-label">评分标准 (Rubric)</div>
              <div v-if="!rubricEditMode" class="rubric-content">
                {{ questionDetail.analysis || '暂无评分标准，请点击编辑添加。' }}
              </div>
              <a-textarea
                v-else
                v-model:value="rubricDraft"
                :rows="10"
                placeholder="请输入评分标准..."
              />
            </div>

            <div v-if="currentQuestion" class="question-stats">
              <a-divider />
              <div class="stats-row">
                <span>本题待批改：</span>
                <b>{{ queue.length }} 份</b>
              </div>
            </div>
          </div>
          <a-skeleton v-else active :paragraph="{ rows: 8 }" />
        </div>
      </div>

      <div class="panel right-panel">
        <div class="panel-header">
          <div class="nav-buttons">
            <a-button
              :disabled="currentIndex === 0"
              @click="goPrev"
              size="small"
            >
              <LeftOutlined /> 上一份
            </a-button>
            <span class="progress-text">
              {{ currentIndex + 1 }} / {{ queue.length }}
            </span>
            <a-button
              :disabled="currentIndex >= queue.length - 1"
              @click="goNext"
              size="small"
            >
              下一份 <RightOutlined />
            </a-button>
          </div>
          <a-button type="primary" :loading="submitting" @click="submitBatch">
            批量提交已批改
          </a-button>
        </div>

        <div class="answer-cards-container">
          <div class="answer-card" v-if="currentItem">
            <div class="card-header">
              <div class="student-info">
                <a-avatar style="background-color: #1890ff">
                  {{ currentItem.studentName?.[0] || '?' }}
                </a-avatar>
                <div class="student-meta">
                  <div class="student-name">{{ currentItem.studentName }}</div>
                  <div class="submit-time">
                    <ClockCircleOutlined /> 提交于 {{ currentItem.submitTime }}
                  </div>
                </div>
              </div>
              <div class="score-display">
                <span class="score-label">得分</span>
                <span class="score-value">
                  {{ scores[currentItem.submissionAnswerId] ?? '-' }}
                </span>
                <span class="score-max">/ {{ maxScore }}</span>
              </div>
            </div>

            <div class="card-body">
              <div class="answer-label">学生答案</div>
              <div class="answer-content">
                {{ currentItem.studentAnswer || '(未作答)' }}
              </div>
            </div>

            <div class="card-footer">
              <div class="scoring-section">
                <div class="scoring-label">快捷评分（数字键 0-9）</div>
                <div class="score-buttons">
                  <a-button
                    v-for="s in Math.min(maxScore, 10)"
                    :key="s - 1"
                    :type="scores[currentItem.submissionAnswerId] === (s - 1) ? 'primary' : 'default'"
                    size="large"
                    @click="scoreWithKey(s - 1)"
                  >
                    {{ s - 1 }}
                  </a-button>
                </div>
                <div class="score-input-row">
                  <span class="score-input-label">精确给分：</span>
                  <a-input-number
                    v-model:value="scores[currentItem.submissionAnswerId]"
                    :min="0"
                    :max="maxScore"
                    :precision="0"
                    size="large"
                    style="width: 120px"
                  />
                  <span class="score-input-suffix">/ {{ maxScore }} 分</span>
                </div>
              </div>

              <div class="comment-section">
                <div class="comment-label">教师评语</div>
                <a-textarea
                  v-model:value="comments[currentItem.submissionAnswerId]"
                  :rows="3"
                  placeholder="输入评语（可选）"
                />
              </div>

              <div class="action-section">
                <a-button type="primary" :loading="submitting" @click="submitCurrentAndNext" block size="large">
                  <CheckOutlined /> 评分并下一份（Enter）
                </a-button>
              </div>

              <div class="shortcuts-hint">
                <span>快捷键：← → 切换答案</span>
                <span>0-9 快速给分</span>
                <span>Enter 提交并下一份</span>
              </div>
            </div>
          </div>

          <a-empty v-else description="请选择考试和题目开始批改" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grading-workbench {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 32px - 72px);
  background: var(--bg-main);
}

.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: white;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-right {
  display: flex;
  gap: 24px;
}

.stat-card {
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 8px 16px;
  background: #f0f5ff;
  border-radius: 8px;
}

.stat-card.pending {
  background: #fff7e6;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--primary-color);
}

.stat-card.pending .stat-value {
  color: #fa8c16;
}

.stat-unit {
  font-size: 13px;
  color: var(--text-secondary);
}

.workbench-body {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.panel {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.left-panel {
  width: 280px;
  flex-shrink: 0;
}

.middle-panel {
  flex: 1;
  min-width: 0;
}

.right-panel {
  width: 420px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.panel-section {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.panel-section:last-child {
  flex: 1;
  overflow-y: auto;
  border-bottom: none;
}

.left-panel .panel-section:last-child {
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.question-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.question-item:hover {
  background: #f5f5f5;
}

.question-item.active {
  background: #e6f7ff;
  border-color: #91d5ff;
}

.q-score-badge {
  flex-shrink: 0;
  background: var(--primary-color);
  color: white;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.question-item.active .q-score-badge {
  background: #1890ff;
}

.q-content {
  flex: 1;
  font-size: 13px;
  color: var(--text-main);
  line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  overflow-y: auto;
}

.queue-item {
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  gap: 10px;
  align-items: center;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.queue-item:hover {
  background: #f5f5f5;
}

.queue-item.active {
  background: #e6f7ff;
  border-color: #91d5ff;
}

.queue-item.graded {
  opacity: 0.7;
}

.queue-item.graded.active {
  opacity: 1;
}

.queue-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.queue-item.active .queue-index {
  background: var(--primary-color);
  color: white;
}

.queue-info {
  flex: 1;
  min-width: 0;
}

.queue-student {
  font-size: 13px;
  color: var(--text-main);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.queue-submit {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.queue-score {
  flex-shrink: 0;
  background: #52c41a;
  color: white;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.question-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.q-stem {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  position: relative;
}

.q-type-badge {
  display: inline-block;
  background: var(--primary-color);
  color: white;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 4px;
  margin-bottom: 12px;
}

.q-full-score {
  position: absolute;
  top: 16px;
  right: 16px;
  font-size: 14px;
  color: var(--text-secondary);
}

.q-content-text {
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-main);
}

.rubric-section {
  border: 1px solid #d9f7be;
  background: #fcffe6;
  border-radius: 8px;
  padding: 16px;
}

.rubric-label {
  font-size: 13px;
  font-weight: 600;
  color: #389e0d;
  margin-bottom: 8px;
}

.rubric-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-main);
  white-space: pre-wrap;
}

.question-stats {
  margin-top: 8px;
}

.stats-row {
  font-size: 14px;
  color: var(--text-secondary);
}

.stats-row b {
  color: var(--primary-color);
  font-size: 16px;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.nav-buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-text {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.answer-cards-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.answer-card {
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.card-header {
  padding: 16px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.student-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.student-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.student-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.submit-time {
  font-size: 12px;
  color: var(--text-secondary);
}

.score-display {
  text-align: right;
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.score-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-right: 4px;
}

.score-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--primary-color);
  min-width: 40px;
  text-align: center;
}

.score-max {
  font-size: 14px;
  color: var(--text-secondary);
}

.card-body {
  padding: 20px 16px;
  flex: 1;
  min-height: 150px;
}

.answer-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  font-weight: 500;
}

.answer-content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-main);
  white-space: pre-wrap;
  background: #fafafa;
  padding: 12px;
  border-radius: 6px;
  min-height: 100px;
}

.card-footer {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scoring-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.scoring-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.score-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.score-buttons .ant-btn {
  width: 40px;
  height: 40px;
  font-size: 16px;
  font-weight: 600;
}

.score-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.score-input-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.score-input-suffix {
  font-size: 13px;
  color: var(--text-secondary);
}

.comment-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.comment-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.shortcuts-hint {
  display: flex;
  justify-content: center;
  gap: 16px;
  font-size: 11px;
  color: var(--text-secondary);
}

:deep(.ant-empty) {
  padding: 40px 0;
}
</style>
