<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getPracticeQuestions, submitPracticeResult } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { useConfigStore } from '@/stores/config';
import { message, Modal } from 'ant-design-vue';
import { 
  ClockCircleOutlined, CheckCircleOutlined, CloseCircleOutlined, 
  LeftOutlined, RightOutlined, FlagOutlined, FlagFilled,
  SendOutlined, AppstoreOutlined, UserOutlined, EyeOutlined,
  InfoCircleOutlined, CheckCircleFilled, CloseCircleFilled,
  ArrowLeftOutlined, ReloadOutlined
} from '@ant-design/icons-vue';

const router = useRouter();
const authStore = useAuthStore();
const configStore = useConfigStore();

const questions = ref([]);
const answers = ref({});
const flagged = ref(new Set());
const currentIndex = ref(0);
const loading = ref(true);
const submitted = ref(false);

const currentQuestion = computed(() => questions.value[currentIndex.value]);

const fetchPracticeQuestions = async () => {
  loading.value = true;
  try {
    const res = await getPracticeQuestions(10);
    questions.value = res.data;
    if (questions.value.length === 0) {
      message.warning('当前没有待练习的错题');
      router.back();
    }
  } catch (e) {
    console.error(e);
    message.error('加载练习题目失败');
    router.back();
  } finally {
    loading.value = false;
  }
};

const toggleFlag = () => {
  const qId = currentQuestion.value.id;
  if (flagged.value.has(qId)) {
    flagged.value.delete(qId);
  } else {
    flagged.value.add(qId);
  }
};

const jumpTo = (index) => {
  currentIndex.value = index;
};

const nextQuestion = () => {
  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value++;
  }
};

const prevQuestion = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--;
  }
};

const handleSubmit = () => {
  Modal.confirm({
    title: '确认提交练习',
    content: '确定要提交练习吗？提交后将立即查看解析。',
    okText: '确认提交',
    cancelText: '继续检查',
    onOk: async () => {
      await doSubmit();
    }
  });
};

const doSubmit = async () => {
  try {
    const finalAnswers = {};
    const correctMap = {};
    
    questions.value.forEach(q => {
      const rawAnswer = answers.value[q.id];
      let userAnswer = rawAnswer;
      
      if (q.type === 'MULTI' && Array.isArray(rawAnswer)) {
        userAnswer = rawAnswer.sort().join(',');
        finalAnswers[q.id] = userAnswer;
      } else {
        finalAnswers[q.id] = rawAnswer;
      }
      
      let isCorrect = false;
      
      if (q.type === 'SINGLE' || q.type === 'JUDGE') {
        isCorrect = userAnswer != null && userAnswer === q.answer;
      } else if (q.type === 'MULTI') {
        if (userAnswer != null && q.answer != null) {
          const userOpts = userAnswer.split(/[,\s]+/).map(s => s.trim()).sort().join(',');
          const correctOpts = q.answer.split(/[,\s]+/).map(s => s.trim()).sort().join(',');
          isCorrect = userOpts === correctOpts;
        }
      } else if (q.type === 'SHORT') {
        isCorrect = false;
      }
      
      correctMap[q.id] = isCorrect;
    });
    
    await submitPracticeResult({ answers: finalAnswers, correctMap });
    submitted.value = true;
    message.success('练习已提交');
  } catch (e) {
    message.error('提交失败');
  }
};

const isCorrect = (qId) => {
  const q = questions.value.find(item => item.id === qId);
  if (!q) return false;
  
  let userAnswer = answers.value[qId];
  
  if (q.type === 'MULTI' && Array.isArray(userAnswer)) {
    userAnswer = userAnswer.sort().join(',');
  }
  
  if (q.type === 'SINGLE' || q.type === 'JUDGE') {
    return userAnswer != null && userAnswer === q.answer;
  } else if (q.type === 'MULTI') {
    if (userAnswer != null && q.answer != null) {
      const userOpts = userAnswer.split(/[,\s]+/).map(s => s.trim()).sort().join(',');
      const correctOpts = q.answer.split(/[,\s]+/).map(s => s.trim()).sort().join(',');
      return userOpts === correctOpts;
    }
    return false;
  }
  
  return false;
};

const isAnswered = (qId) => {
  const answer = answers.value[qId];
  if (answer === undefined || answer === null) return false;
  if (Array.isArray(answer)) return answer.length > 0;
  return answer !== '';
};

const answeredCount = computed(() => {
  if (questions.value.length === 0) return 0;
  let count = 0;
  questions.value.forEach(q => {
    if (isAnswered(q.id)) count++;
  });
  return Math.round((count / questions.value.length) * 100);
});

const correctCount = computed(() => {
  if (!submitted.value) return 0;
  let count = 0;
  questions.value.forEach(q => {
    if (isCorrect(q.id)) count++;
  });
  return count;
});

const retryPractice = async () => {
  answers.value = {};
  flagged.value = new Set();
  currentIndex.value = 0;
  submitted.value = false;
  await fetchPracticeQuestions();
};

const backToList = () => {
  router.push('/wrong-book');
};

onMounted(() => {
  fetchPracticeQuestions();
});
</script>

<template>
  <div class="practice-page-container" v-if="!loading">
    <a-layout class="full-height-layout">
      <a-layout-header class="practice-header">
         <div class="header-left">
           <a-button type="text" @click="backToList" class="back-btn">
             <ArrowLeftOutlined /> 返回错题本
           </a-button>
           <div class="practice-title-wrap">
             <h2 class="practice-title">错题重练</h2>
             <span class="practice-subtitle">
               {{ submitted ? `答对 ${correctCount} / ${questions.length} 题` : `共 ${questions.length} 道错题练习` }}
             </span>
           </div>
         </div>
         
         <div class="header-right">
            <div class="user-id-badge">
               <UserOutlined /> {{ authStore.user?.fullName }} ({{ authStore.user?.username }})
            </div>
            <a-button 
              v-if="!submitted" 
              type="primary" 
              shape="round" 
              @click="handleSubmit" 
              class="submit-btn"
            >
               提交练习
            </a-button>
            <a-button 
              v-else 
              type="primary" 
              shape="round" 
              @click="retryPractice"
              class="submit-btn"
            >
               <ReloadOutlined /> 再来一组
            </a-button>
         </div>
      </a-layout-header>
      
      <a-layout class="practice-main-layout">
         <a-layout-sider width="320" theme="light" class="practice-sider" :trigger="null">
            <div class="sider-inner">
               <div class="navigator-card">
                  <div class="nav-header">
                     <AppstoreOutlined /> 答题卡
                  </div>
                  <div class="nav-indicators">
                     <div class="ind-item"><span class="dot current"></span> 当前</div>
                     <div class="ind-item"><span class="dot done"></span> 已答</div>
                     <div class="ind-item"><span class="dot flag"></span> 标记</div>
                     <div class="ind-item" v-if="submitted"><span class="dot correct"></span> 答对</div>
                     <div class="ind-item" v-if="submitted"><span class="dot wrong"></span> 答错</div>
                  </div>
                  <div class="nav-grid">
                     <div 
                        v-for="(q, idx) in questions" 
                        :key="q.id" 
                        class="nav-cell"
                        :class="{ 
                           'is-active': currentIndex === idx,
                           'is-done': isAnswered(q.id),
                           'is-flagged': flagged.has(q.id),
                           'is-correct': submitted && isCorrect(q.id),
                           'is-wrong': submitted && !isCorrect(q.id)
                        }"
                        @click="jumpTo(idx)"
                     >
                        {{ idx + 1 }}
                     </div>
                  </div>
               </div>
               
               <div class="practice-tips">
                  <p><InfoCircleOutlined /> 本次练习不计入正式成绩</p>
                  <p><InfoCircleOutlined /> 答对的题目将自动标记为已掌握</p>
               </div>
            </div>
         </a-layout-sider>

         <a-layout-content class="practice-content">
            <div class="question-canvas" v-if="currentQuestion">
               <div class="q-scope-header">
                  <div class="q-type-label">
                     <a-tag color="blue">
                        {{ { 'SINGLE': '单选题', 'MULTI': '多选题', 'JUDGE': '判断题', 'SHORT': '简答题' }[currentQuestion.type] }}
                     </a-tag>
                     <span class="q-index-info">第 {{ currentIndex + 1 }} 题 / 共 {{ questions.length }} 题</span>
                  </div>
                  <div class="q-actions-right">
                    <span v-if="currentQuestion.subject" class="q-subject-tag">{{ currentQuestion.subject }}</span>
                    <a-button 
                      v-if="!submitted"
                      type="text" 
                      class="flag-btn" 
                      @click="toggleFlag"
                      :class="{ 'flagged-active': flagged.has(currentQuestion.id) }"
                    >
                      <template #icon>
                        <FlagFilled v-if="flagged.has(currentQuestion.id)" />
                        <FlagOutlined v-else />
                      </template>
                      标记此题
                    </a-button>
                  </div>
               </div>

               <div class="q-content-body">
                  <div class="question-text" v-html="currentQuestion.content"></div>
                  
                  <div class="answer-interaction-area">
                     <a-radio-group 
                       v-if="currentQuestion.type === 'SINGLE'" 
                       v-model:value="answers[currentQuestion.id]" 
                       :disabled="submitted" 
                       class="choice-group"
                     >
                        <a-radio class="choice-item" v-for="opt in JSON.parse(currentQuestion.options)" :key="opt.label" :value="opt.label">
                           <span class="choice-key">{{ opt.label }}</span>
                           <span class="choice-text">{{ opt.text }}</span>
                        </a-radio>
                     </a-radio-group>
                     
                     <a-checkbox-group 
                       v-if="currentQuestion.type === 'MULTI'" 
                       v-model:value="answers[currentQuestion.id]" 
                       :disabled="submitted" 
                       class="choice-group"
                     >
                        <div v-for="opt in JSON.parse(currentQuestion.options)" :key="opt.label" class="choice-item multi-wrap">
                           <a-checkbox :value="opt.label">
                              <span class="choice-key">{{ opt.label }}</span>
                              <span class="choice-text">{{ opt.text }}</span>
                           </a-checkbox>
                        </div>
                     </a-checkbox-group>

                     <a-radio-group 
                       v-if="currentQuestion.type === 'JUDGE'" 
                       v-model:value="answers[currentQuestion.id]" 
                       :disabled="submitted" 
                       class="judge-group"
                     >
                        <a-radio-button value="TRUE" class="judge-btn">正确 (TRUE)</a-radio-button>
                        <a-radio-button value="FALSE" class="judge-btn">错误 (FALSE)</a-radio-button>
                     </a-radio-group>
                     
                     <a-textarea 
                        v-if="currentQuestion.type === 'SHORT'" 
                        v-model:value="answers[currentQuestion.id]" 
                        placeholder="在此输入您的回答..."
                        :rows="8" 
                        :disabled="submitted" 
                        class="essay-editor"
                     />
                  </div>
               </div>

               <div v-if="submitted" class="analysis-section">
                  <div class="analysis-header-row">
                     <div class="judge-banner" :class="isCorrect(currentQuestion.id) ? 'correct' : 'wrong'">
                        <CheckCircleFilled v-if="isCorrect(currentQuestion.id)" />
                        <CloseCircleFilled v-else />
                        <span>{{ isCorrect(currentQuestion.id) ? '回答正确' : '回答错误' }}</span>
                     </div>
                  </div>
                  
                  <div class="analysis-details-grid">
                     <div class="detail-col">
                        <div class="detail-label">我的作答</div>
                        <div class="detail-val" :class="{ 'error-text': !isCorrect(currentQuestion.id) }">
                           {{ answers[currentQuestion.id] || '(未答)' }}
                        </div>
                     </div>
                     <div class="detail-col">
                        <div class="detail-label">正确答案</div>
                        <div class="detail-val correct-text">{{ currentQuestion.answer }}</div>
                     </div>
                  </div>

                  <div class="explanation-box">
                     <div class="exp-label">解析说明</div>
                     <div class="exp-content">{{ currentQuestion.analysis || '暂无解析数据' }}</div>
                  </div>
               </div>
            </div>

            <div class="practice-pager">
               <div class="pager-left">
                  <a-button @click="prevQuestion" :disabled="currentIndex === 0" size="large" ghost type="primary" class="nav-btn">
                     <LeftOutlined /> 上一题
                  </a-button>
               </div>
               <div class="pager-center">
                  进度: <a-progress :percent="answeredCount" size="small" style="width: 200px" />
               </div>
               <div class="pager-right">
                  <a-button type="primary" @click="nextQuestion" :disabled="currentIndex === questions.length - 1" size="large" class="nav-btn next">
                     下一题 <RightOutlined />
                  </a-button>
               </div>
            </div>
         </a-layout-content>
      </a-layout>
    </a-layout>
  </div>
  <div v-else class="practice-loading">
    <a-spin size="large" tip="正在加载练习题目..." />
  </div>
</template>

<style scoped>
.practice-page-container {
  height: 100vh;
  background-color: #f7f9fc;
}
.full-height-layout {
  height: 100%;
}

.practice-header {
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

.back-btn {
  color: #595959;
  font-size: 14px;
}

.practice-title-wrap {
  display: flex;
  flex-direction: column;
}

.practice-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: #1a1a1a;
}

.practice-subtitle {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.user-id-badge {
  color: #595959;
  font-size: 14px;
}

.submit-btn {
  font-weight: 600;
  padding: 0 28px;
  height: 40px;
}

.practice-sider {
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
.dot.flag { background: #faad14; border-color: #faad14; }
.dot.correct { background: #52c41a; border-color: #52c41a; }
.dot.wrong { background: #ff4d4f; border-color: #ff4d4f; }

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

.nav-cell.is-flagged {
  background: #fffbe6;
  border-color: #faad14;
}

.nav-cell.is-active {
  background: #1890ff !important;
  border-color: #1890ff !important;
  color: white !important;
  box-shadow: 0 4px 8px rgba(24, 144, 255, 0.35);
}

.nav-cell.is-correct {
  background: #f6ffed;
  border-color: #b7eb8f;
  color: #52c41a;
}

.nav-cell.is-wrong {
  background: #fff1f0;
  border-color: #ffa39e;
  color: #ff4d4f;
}

.practice-tips {
  margin-top: 24px;
  padding: 16px;
  background: #e6f7ff;
  border-radius: 8px;
  font-size: 12px;
  color: #0050b3;
}

.practice-tips p {
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.practice-content {
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

.q-actions-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.q-subject-tag {
  color: #595959;
  font-size: 14px;
  background: #f0f5ff;
  padding: 4px 12px;
  border-radius: 4px;
  color: #2f54eb;
}

.flag-btn {
  color: #8c8c8c;
}

.flagged-active {
  color: #faad14 !important;
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
}

.choice-item:hover {
  border-color: #1890ff;
  background: #f0f7ff;
}

.choice-item.ant-radio-wrapper-checked, 
.choice-item.ant-checkbox-wrapper-checked {
  border-color: #1890ff;
  background: #f0f7ff;
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

.essay-editor {
  border-radius: 12px;
  padding: 24px;
  font-size: 17px;
  line-height: 1.8;
  background: #f8fafc;
}

.practice-pager {
  position: sticky;
  bottom: 0px;
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

.analysis-section {
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

.practice-loading {
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
