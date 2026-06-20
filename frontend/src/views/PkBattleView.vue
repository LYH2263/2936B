<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getPkSession, submitPkAnswer, forfeitPk } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { message, Modal } from 'ant-design-vue';
import {
  UserOutlined, ClockCircleOutlined, TrophyOutlined,
  CheckOutlined, CloseOutlined, ArrowLeftOutlined,
  FlagOutlined
} from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const sessionId = route.params.id;

const state = ref(null);
const loading = ref(true);
const selectedAnswer = ref(null);
const answered = ref(false);
const timeLeft = ref(30);
const timer = ref(null);
const pollTimer = ref(null);
const showResult = ref(false);

const authUser = computed(() => authStore.user);

const totalScore = computed(() => state.value ? state.value.totalQuestions * 10 : 100);

const myScorePercent = computed(() => {
  if (!state.value) return 0;
  return (state.value.myScore / totalScore.value) * 100;
});

const opponentScorePercent = computed(() => {
  if (!state.value) return 0;
  return (state.value.opponentScore / totalScore.value) * 100;
});

const questionNumber = computed(() => {
  if (!state.value) return 1;
  return state.value.currentQuestionIndex + 1;
});

const parsedOptions = computed(() => {
  if (!state.value?.currentQuestion?.options) return [];
  try {
    return JSON.parse(state.value.currentQuestion.options);
  } catch (e) {
    return [];
  }
});

const questionTypeText = computed(() => {
  const type = state.value?.currentQuestion?.type;
  const typeMap = {
    SINGLE: '单选题',
    MULTI: '多选题',
    JUDGE: '判断题'
  };
  return typeMap[type] || type;
});

const fetchState = async () => {
  try {
    const res = await getPkSession(sessionId);
    const prevState = state.value;
    state.value = res.data;

    if (prevState && prevState.currentQuestionIndex !== res.data.currentQuestionIndex) {
      selectedAnswer.value = null;
      answered.value = false;
    }

    if (res.data.timeLeft !== undefined && res.data.timeLeft !== null) {
      timeLeft.value = Math.max(0, res.data.timeLeft);
    }

    if (res.data.state === 'FINISHED' && !showResult.value) {
      showResult.value = true;
      clearTimers();
    }

    loading.value = false;
  } catch (e) {
    console.error('Failed to fetch PK state', e);
    if (e.response?.status === 401) {
      router.push('/login');
    }
  }
};

const clearTimers = () => {
  if (timer.value) {
    clearInterval(timer.value);
    timer.value = null;
  }
  if (pollTimer.value) {
    clearInterval(pollTimer.value);
    pollTimer.value = null;
  }
};

const startTimer = () => {
  timer.value = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--;
      if (timeLeft.value === 0 && !answered.value) {
        fetchState();
      }
    }
  }, 1000);
};

const startPolling = () => {
  pollTimer.value = setInterval(fetchState, 2000);
};

const submitAnswer = async (answer) => {
  if (answered.value || !state.value || state.value.state !== 'IN_PROGRESS') return;

  selectedAnswer.value = answer;
  answered.value = true;

  const timeUsed = 30 - timeLeft.value;

  try {
    await submitPkAnswer(sessionId, {
      answer: answer,
      timeUsed: timeUsed
    });
  } catch (e) {
    message.error('提交答案失败');
    answered.value = false;
  }
};

const confirmForfeit = () => {
  Modal.confirm({
    title: '确认认输',
    content: '确定要放弃本局比赛吗？认输将判定为负。',
    okText: '确认认输',
    okType: 'danger',
    cancelText: '继续比赛',
    onOk: async () => {
      try {
        await forfeitPk(sessionId);
        message.info('已认输');
        await fetchState();
      } catch (e) {
        message.error('操作失败');
      }
    }
  });
};

const goBack = () => {
  router.push('/pk');
};

const getResultText = () => {
  if (!state.value) return '';
  const resultMap = {
    WIN: '🎉 恭喜你赢了！',
    LOSE: '😢 很遗憾，你输了',
    DRAW: '🤝 平局！势均力敌'
  };
  return resultMap[state.value.result] || '';
};

const getMyAnswerForQuestion = (index) => {
  if (!state.value?.myAnswers) return null;
  return state.value.myAnswers.find(a => a.questionIndex === index);
};

const getOpponentAnswerForQuestion = (index) => {
  if (!state.value?.opponentAnswers) return null;
  return state.value.opponentAnswers.find(a => a.questionIndex === index);
};

onMounted(async () => {
  await fetchState();
  if (state.value?.state === 'IN_PROGRESS') {
    startTimer();
    startPolling();
  }
});

onUnmounted(() => {
  clearTimers();
});
</script>

<template>
  <div class="pk-battle" :class="{ 'finished': showResult }">
    <div v-if="loading" class="loading-container">
      <a-spin size="large" tip="加载中..." />
    </div>

    <template v-else>
      <div v-if="!showResult" class="battle-header">
        <div class="player-info my-side">
          <div class="avatar">
            <UserOutlined />
          </div>
          <div class="info">
            <div class="name">{{ authUser?.fullName || authUser?.username }}</div>
            <div class="score-text">{{ state.myScore }} 分</div>
          </div>
          <div class="score-bar">
            <div class="score-fill my" :style="{ width: myScorePercent + '%' }"></div>
          </div>
        </div>

        <div class="vs-section">
          <div class="question-no">第 {{ questionNumber }} / {{ state.totalQuestions }} 题</div>
          <div class="timer" :class="{ 'warning': timeLeft <= 10, 'danger': timeLeft <= 5 }">
            <ClockCircleOutlined /> {{ timeLeft }}s
          </div>
          <div class="vs-text">VS</div>
        </div>

        <div class="player-info opponent-side">
          <div class="avatar opponent">
            <UserOutlined />
          </div>
          <div class="info right">
            <div class="name">{{ state.opponentName }}</div>
            <div class="score-text">{{ state.opponentScore }} 分</div>
          </div>
          <div class="score-bar">
            <div class="score-fill opponent" :style="{ width: opponentScorePercent + '%' }"></div>
          </div>
          <div v-if="state.opponentAnswered" class="opponent-status">
            <CheckOutlined /> 已作答
          </div>
        </div>
      </div>

      <div v-if="!showResult && state.currentQuestion" class="question-area">
        <div class="question-type-tag">{{ questionTypeText }}</div>
        <div class="question-content">
          {{ questionNumber }}. {{ state.currentQuestion.content }}
        </div>

        <div class="options-list" v-if="parsedOptions.length > 0">
          <div
            v-for="opt in parsedOptions"
            :key="opt.label"
            class="option-item"
            :class="{
              'selected': selectedAnswer === opt.label,
              'answered': answered,
              'correct': answered && opt.label === state.currentQuestion.answer,
              'wrong': answered && selectedAnswer === opt.label && opt.label !== state.currentQuestion.answer
            }"
            @click="submitAnswer(opt.label)"
          >
            <span class="option-label">{{ opt.label }}</span>
            <span class="option-text">{{ opt.text }}</span>
          </div>
        </div>

        <div class="options-list" v-else-if="state.currentQuestion.type === 'JUDGE'">
          <div
            class="option-item"
            :class="{
              'selected': selectedAnswer === '正确',
              'answered': answered,
              'correct': answered && '正确' === state.currentQuestion.answer,
              'wrong': answered && selectedAnswer === '正确' && '正确' !== state.currentQuestion.answer
            }"
            @click="submitAnswer('正确')"
          >
            <span class="option-label">✓</span>
            <span class="option-text">正确</span>
          </div>
          <div
            class="option-item"
            :class="{
              'selected': selectedAnswer === '错误',
              'answered': answered,
              'correct': answered && '错误' === state.currentQuestion.answer,
              'wrong': answered && selectedAnswer === '错误' && '错误' !== state.currentQuestion.answer
            }"
            @click="submitAnswer('错误')"
          >
            <span class="option-label">✗</span>
            <span class="option-text">错误</span>
          </div>
        </div>

        <div v-if="answered" class="waiting-text">
          <a-spin size="small" /> 等待对手作答...
        </div>
      </div>

      <div v-if="!showResult" class="battle-footer">
        <a-button danger ghost @click="confirmForfeit">
          <FlagOutlined /> 认输
        </a-button>
      </div>

      <div v-if="showResult" class="result-container">
        <div class="result-header">
          <TrophyOutlined class="trophy-icon" />
          <h2>{{ getResultText() }}</h2>
          <p class="winner-name" v-if="state.winnerName">获胜者：{{ state.winnerName }}</p>
        </div>

        <div class="result-scores">
          <div class="score-card my">
            <div class="card-avatar"><UserOutlined /></div>
            <div class="card-name">{{ authUser?.fullName || authUser?.username }}</div>
            <div class="card-score">{{ state.myScore }}</div>
            <div class="card-label">我的得分</div>
          </div>
          <div class="vs-divider">VS</div>
          <div class="score-card opponent">
            <div class="card-avatar"><UserOutlined /></div>
            <div class="card-name">{{ state.opponentName }}</div>
            <div class="card-score">{{ state.opponentScore }}</div>
            <div class="card-label">对手得分</div>
          </div>
        </div>

        <div class="result-detail">
          <h3>逐题对比</h3>
          <div class="detail-table">
            <div class="detail-row header">
              <div class="col-no">题号</div>
              <div class="col-my">我的答案</div>
              <div class="col-correct">正确答案</div>
              <div class="col-opponent">对手答案</div>
            </div>
            <div
              v-for="(q, idx) in state.questions"
              :key="q.id"
              class="detail-row"
            >
              <div class="col-no">{{ idx + 1 }}</div>
              <div class="col-my">
                <span v-if="getMyAnswerForQuestion(idx)"
                  :class="getMyAnswerForQuestion(idx).isCorrect ? 'correct' : 'wrong'">
                  {{ getMyAnswerForQuestion(idx).answer || '未作答' }}
                  <CheckOutlined v-if="getMyAnswerForQuestion(idx).isCorrect" />
                  <CloseOutlined v-else />
                </span>
                <span v-else class="empty">-</span>
              </div>
              <div class="col-correct correct">
                {{ q.answer }}
              </div>
              <div class="col-opponent">
                <span v-if="getOpponentAnswerForQuestion(idx)"
                  :class="getOpponentAnswerForQuestion(idx).isCorrect ? 'correct' : 'wrong'">
                  {{ getOpponentAnswerForQuestion(idx).answer || '未作答' }}
                  <CheckOutlined v-if="getOpponentAnswerForQuestion(idx).isCorrect" />
                  <CloseOutlined v-else />
                </span>
                <span v-else class="empty">-</span>
              </div>
            </div>
          </div>
        </div>

        <div class="result-actions">
          <a-button size="large" @click="goBack">
            <ArrowLeftOutlined /> 返回大厅
          </a-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pk-battle {
  min-height: 100vh;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  color: white;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.pk-battle.finished {
  background: linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%);
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  color: white;
}

.battle-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 40px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 16px;
  margin-bottom: 30px;
}

.player-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.player-info.opponent-side {
  align-items: flex-end;
}

.avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.avatar.opponent {
  background: linear-gradient(135deg, #f093fb, #f5576c);
}

.info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info.right {
  align-items: flex-end;
}

.name {
  font-size: 18px;
  font-weight: 600;
}

.score-text {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
}

.score-bar {
  height: 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
  width: 200px;
}

.score-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.score-fill.my {
  background: linear-gradient(90deg, #667eea, #764ba2);
}

.score-fill.opponent {
  background: linear-gradient(90deg, #f093fb, #f5576c);
}

.opponent-status {
  font-size: 12px;
  color: #52c41a;
  margin-top: 4px;
}

.vs-section {
  text-align: center;
  padding: 0 30px;
}

.question-no {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 8px;
}

.timer {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 8px;
}

.timer.warning {
  color: #faad14;
}

.timer.danger {
  color: #ff4d4f;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.vs-text {
  font-size: 24px;
  font-weight: bold;
  background: linear-gradient(90deg, #667eea, #f5576c);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.question-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
}

.question-type-tag {
  display: inline-block;
  padding: 4px 16px;
  background: rgba(102, 126, 234, 0.3);
  border-radius: 20px;
  font-size: 14px;
  margin-bottom: 20px;
}

.question-content {
  font-size: 22px;
  text-align: center;
  line-height: 1.6;
  margin-bottom: 40px;
  max-width: 800px;
}

.options-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
  max-width: 700px;
}

.option-item {
  background: rgba(255, 255, 255, 0.08);
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 20px 24px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  gap: 16px;
}

.option-item:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(102, 126, 234, 0.5);
}

.option-item.selected {
  background: rgba(102, 126, 234, 0.2);
  border-color: #667eea;
}

.option-item.answered {
  cursor: default;
}

.option-item.correct {
  background: rgba(82, 196, 26, 0.2);
  border-color: #52c41a;
}

.option-item.wrong {
  background: rgba(255, 77, 79, 0.2);
  border-color: #ff4d4f;
}

.option-label {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  flex-shrink: 0;
}

.option-text {
  flex: 1;
  font-size: 16px;
}

.waiting-text {
  margin-top: 30px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
}

.battle-footer {
  display: flex;
  justify-content: center;
  padding: 20px;
}

.result-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 20px;
  width: 100%;
}

.result-header {
  text-align: center;
  margin-bottom: 40px;
}

.trophy-icon {
  font-size: 64px;
  color: #ffd700;
  margin-bottom: 16px;
}

.result-header h2 {
  font-size: 36px;
  margin-bottom: 10px;
}

.winner-name {
  color: rgba(255, 255, 255, 0.7);
  font-size: 16px;
}

.result-scores {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 40px;
  margin-bottom: 40px;
}

.score-card {
  background: rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 30px 40px;
  text-align: center;
  min-width: 160px;
}

.card-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.score-card.my .card-avatar {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.score-card.opponent .card-avatar {
  background: linear-gradient(135deg, #f093fb, #f5576c);
}

.card-name {
  font-size: 16px;
  margin-bottom: 8px;
}

.card-score {
  font-size: 42px;
  font-weight: bold;
  margin-bottom: 4px;
}

.card-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.vs-divider {
  font-size: 20px;
  font-weight: bold;
  color: rgba(255, 255, 255, 0.4);
}

.result-detail {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 30px;
}

.result-detail h3 {
  margin: 0 0 16px 0;
  font-size: 18px;
}

.detail-table {
  border-radius: 8px;
  overflow: hidden;
}

.detail-row {
  display: grid;
  grid-template-columns: 60px 1fr 1fr 1fr;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  align-items: center;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-row.header {
  background: rgba(255, 255, 255, 0.08);
  font-weight: 600;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
}

.col-no {
  text-align: center;
}

.col-my, .col-opponent, .col-correct {
  text-align: center;
}

.correct {
  color: #52c41a;
}

.wrong {
  color: #ff4d4f;
}

.empty {
  color: rgba(255, 255, 255, 0.3);
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}
</style>
