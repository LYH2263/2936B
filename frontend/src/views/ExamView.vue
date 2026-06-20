<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getExam, getExamQuestions, submitExam, getSubmission, recordCheating } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { useConfigStore } from '@/stores/config';
import { message, Modal, notification } from 'ant-design-vue';
import { 
  ClockCircleOutlined, CheckCircleOutlined, CloseCircleOutlined, 
  LeftOutlined, RightOutlined, FlagOutlined, FlagFilled,
  SendOutlined, AppstoreOutlined, UserOutlined, EyeOutlined,
  InfoCircleOutlined, SafetyOutlined, CheckCircleFilled, CloseCircleFilled
} from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const configStore = useConfigStore();
const examId = route.params.id;
const submissionId = route.query.submissionId;

const exam = ref(null);
const questions = ref([]);
const answers = ref({}); // { questionId: answer }
const flagged = ref(new Set());
const currentIndex = ref(0);
const loading = ref(true);
const timeLeft = ref(0);
const timer = ref(null);
const reminderSent = ref(false);

const isAnalysis = computed(() => !!submissionId);
const submissionData = ref(null);

// Anti-cheating
const tabSwitchCount = ref(0);

const currentQuestion = computed(() => questions.value[currentIndex.value]);
const totalScore = computed(() => questions.value.reduce((sum, q) => sum + (q.score || 0), 0));

const fetchData = async () => {
  try {
    if (isAnalysis.value) {
      // Analysis Mode
      const subRes = await getSubmission(submissionId);
      submissionData.value = subRes.data;
      exam.value = subRes.data.exam;
      
      const qRes = await getExamQuestions(subRes.data.exam.id);
      questions.value = qRes.data;
      
      if (submissionData.value.answers) {
        submissionData.value.answers.forEach(sa => {
          const qId = sa.question.id || sa.question; 
          answers.value[qId] = sa.studentAnswer;
        });
      }
    } else {
      // Exam Mode
      const [examRes, qRes] = await Promise.all([
        getExam(examId),
        getExamQuestions(examId)
      ]);
      exam.value = examRes.data;
      questions.value = qRes.data;
      
      // Check for saved progress
      const saved = localStorage.getItem(`exam_progress_${examId}_${authStore.user?.id}`);
      if (saved) {
        try {
          const { savedAnswers, savedFlagged } = JSON.parse(saved);
          answers.value = savedAnswers || {};
          flagged.value = new Set(savedFlagged || []);
        } catch (e) {
          console.error('Failed to restore progress', e);
        }
      }

      timeLeft.value = exam.value.duration * 60; // seconds
      startTimer();
      
      document.addEventListener('visibilitychange', handleVisibilityChange);
      if (exam.value.enableCamera) {
          initCamera();
      }
    }
  } catch (e) {
    console.error(e);
    message.error('加载失败');
    router.push('/dashboard');
  } finally {
    loading.value = false;
  }
};

const startTimer = () => {
  timer.value = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--;
      
      // 5-minute reminder
      if (timeLeft.value <= 300 && !reminderSent.value) {
        notification.warning({
          message: '考试提醒',
          description: '距离考试结束还有不到 5 分钟，请妥善安排答题进度并及时交卷。',
          duration: 10,
        });
        reminderSent.value = true;
      }
    } else {
      clearInterval(timer.value);
      handleSubmit(true);
    }
  }, 1000);
};

const formatTime = (seconds) => {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s < 10 ? '0' + s : s}`;
};

// Auto-save progress
watch(answers, (newVal) => {
  if (!isAnalysis.value && exam.value) {
    localStorage.setItem(`exam_progress_${examId}_${authStore.user?.id}`, JSON.stringify({
      savedAnswers: newVal,
      savedFlagged: Array.from(flagged.value)
    }));
  }
}, { deep: true });

const toggleFlag = () => {
  const qId = currentQuestion.value.question.id;
  if (flagged.value.has(qId)) {
    flagged.value.delete(qId);
  } else {
    flagged.value.add(qId);
  }
  // Also save flagged state
  localStorage.setItem(`exam_progress_${examId}_${authStore.user?.id}`, JSON.stringify({
    savedAnswers: answers.value,
    savedFlagged: Array.from(flagged.value)
  }));
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

const handleSubmit = async (auto = false) => {
  if (!auto) {
    Modal.confirm({
      title: '确认交卷',
      content: '确定要提交试卷吗？提交前请检查是否还有遗漏。',
      okText: '确认交卷',
      cancelText: '继续检查',
      onOk: async () => {
        await doSubmit();
      }
    });
  } else {
    message.info('考试时间到，自动交卷');
    await doSubmit();
  }
};

const doSubmit = async () => {
  try {
    clearInterval(timer.value);
    document.removeEventListener('visibilitychange', handleVisibilityChange);
    
    // Clean up local storage
    localStorage.removeItem(`exam_progress_${examId}_${authStore.user?.id}`);

    const finalAnswers = {};
    Object.keys(answers.value).forEach(qId => {
      const val = answers.value[qId];
      finalAnswers[qId] = Array.isArray(val) ? val.sort().join(',') : val;
    });

    const res = await submitExam(examId, finalAnswers);
    
    Modal.success({
      title: '交卷成功',
      content: `您的考试得分：${res.data.score} 分。点击确定后将返回成绩单。`,
      onOk: () => {
        router.push({ path: '/dashboard', query: { tab: 'scores' } });
      }
    });
    
  } catch (e) {
    message.error('提交失败');
  }
};

const getCorrectAnswer = (qId) => {
   if (!isAnalysis.value) return null;
   const q = questions.value.find(i => i.question.id === qId);
   if (exam.value && !exam.value.allowViewAnalysis) return '***';
   return q ? q.question.answer : '';
};

const getAnalysis = (qId) => {
   if (!isAnalysis.value) return null;
   const q = questions.value.find(i => i.question.id === qId);
   if (exam.value && !exam.value.allowViewAnalysis) return '教师设置了隐藏解析';
   return q ? q.question.analysis : '';
};

const getTeacherComment = (qId) => {
   if (!isAnalysis.value || !submissionData.value.answers) return null;
   const sa = submissionData.value.answers.find(a => (a.question.id || a.question) === qId);
   if (exam.value && !exam.value.allowViewAnalysis) return null;
   return sa ? sa.teacherComment : null;
};

const getQuestionScore = (qId) => {
   if (!isAnalysis.value || !submissionData.value.answers) return 0;
   const sa = submissionData.value.answers.find(a => (a.question.id || a.question) === qId);
   return sa ? sa.score : 0;
};

const isCorrect = (qId) => {
   if (!isAnalysis.value) return false;
   const studentAns = answers.value[qId];
   const correctAns = getCorrectAnswer(qId);
   return studentAns === correctAns;
};

const videoRef = ref(null);
const stream = ref(null);

const initCamera = async () => {
  try {
    stream.value = await navigator.mediaDevices.getUserMedia({ video: true });
    if (videoRef.value) {
      videoRef.value.srcObject = stream.value;
    }
  } catch (e) {
    message.warning('无法访问摄像头，无法进行智能监考');
  }
};

const handleVisibilityChange = () => {
  if (document.hidden && !isAnalysis.value && timeLeft.value > 0 && exam.value) {
    recordCheatingEvent('TAB_SWITCH', 'Student switched tab/minimized window');
    
    if (exam.value.allowTabSwitch === false) {
        tabSwitchCount.value++;
        const limit = exam.value.tabSwitchLimit || 3;
        
        if (tabSwitchCount.value >= limit) {
            Modal.error({
                title: '考试由于严重违规已强制结束',
                content: `由于您切屏次数达到上限 (${limit} 次)，系统已自动交卷。`,
                onOk: () => handleSubmit(true)
            });
        } else {
            Modal.warning({
                title: '警告：检测到切屏行为',
                content: `您已切出考试界面 ${tabSwitchCount.value} 次！限制次数为 ${limit} 次，超出后将自动交卷。`,
                centered: true,
            });
        }
    }
  }
};

const recordCheatingEvent = async (type, detail) => {
    try {
        await recordCheating(examId, { type, detail });
    } catch (e) {
        console.error('Failed to record cheating event', e);
    }
};

const handleCopyPaste = (e) => {
    e.preventDefault();
    recordCheatingEvent('LOCKDOWN_VIOLATION', `Attempted ${e.type} operation`);
    message.warning('为了考试公平，系统已禁用复制、粘贴和剪切功能');
};

const initLockdown = () => {
    const canvas = document.querySelector('.question-canvas');
    if (canvas) {
        canvas.addEventListener('copy', handleCopyPaste);
        canvas.addEventListener('paste', handleCopyPaste);
        canvas.addEventListener('cut', handleCopyPaste);
        canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    }
};

const snapshotInterval = ref(null);
const takeSnapshot = () => {
    if (!videoRef.value || !stream.value) return;
    
    const canvas = document.createElement('canvas');
    canvas.width = 320;
    canvas.height = 240;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(videoRef.value, 0, 0, 320, 240);
    
    // In a real app, we'd upload this blob. For now, we log the event.
    recordCheatingEvent('SNAPSHOT', 'Periodic camera snapshot captured');
};

onMounted(() => {
  fetchData().then(() => {
      // Initialize lockdown after data is loaded and DOM is ready
      nextTick(() => {
          initLockdown();
          if (!isAnalysis.value && exam.value?.enableCamera) {
              snapshotInterval.value = setInterval(takeSnapshot, 5 * 60 * 1000); // Every 5 mins
          }
      });
  });
});

onUnmounted(() => {
  clearInterval(timer.value);
  clearInterval(snapshotInterval.value);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  
  const canvas = document.querySelector('.question-canvas');
  if (canvas) {
      canvas.removeEventListener('copy', handleCopyPaste);
      canvas.removeEventListener('paste', handleCopyPaste);
      canvas.removeEventListener('cut', handleCopyPaste);
  }

  if (stream.value) {
    stream.value.getTracks().forEach(track => track.stop());
  }
});
</script>

<template>
  <div class="exam-page-container" v-if="exam">
    <a-layout class="full-height-layout">
      <!-- Fixed Sticky Header -->
      <a-layout-header class="prof-exam-header">
         <div class="header-left">
           <div class="brand-logo" v-if="configStore.logoUrl">
             <img :src="configStore.logoUrl" alt="logo" />
           </div>
           <div class="exam-meta-info">
             <h2 class="exam-title-text">{{ exam.title }}</h2>
             <span class="course-tag">{{ exam.course }}</span>
           </div>
         </div>
         
         <div class="header-center">
            <div class="timer-display" :class="{ 'urgent': timeLeft < (exam.duration * 60 * 0.1) }" v-if="!isAnalysis">
               <span class="time-label">剩余时间</span>
               <span class="time-val">{{ formatTime(timeLeft) }}</span>
               <div class="progress-under">
                 <div class="progress-bar" :style="{ width: (timeLeft / (exam.duration * 60) * 100) + '%' }"></div>
               </div>
            </div>
            <div v-else class="analysis-badge">
               <EyeOutlined /> 试卷解析预览模式
            </div>
         </div>
         
         <div class="header-right">
            <div class="user-id-badge">
               <UserOutlined /> {{ authStore.user?.fullName }} ({{ authStore.user?.username }})
            </div>
            <a-button type="primary" danger shape="round" @click="handleSubmit(false)" v-if="!isAnalysis" class="finish-exam-btn">
               提交试卷
            </a-button>
            <a-button shape="round" @click="router.push('/dashboard')" v-else>
               退出解析
            </a-button>
         </div>
      </a-layout-header>
      
      <a-layout class="exam-main-layout">
         <!-- Left Sider: Answer Sheet & Monitor -->
         <a-layout-sider width="320" theme="light" class="prof-exam-sider" :trigger="null">
            <div class="sider-inner">
               <div class="navigator-card">
                  <div class="nav-header">
                     <AppstoreOutlined /> 答题卡导航
                  </div>
                  <div class="nav-indicators">
                     <div class="ind-item"><span class="dot current"></span> 当前</div>
                     <div class="ind-item"><span class="dot done"></span> 已答</div>
                     <div class="ind-item"><span class="dot flag"></span> 标记</div>
                     <div class="ind-item"><span class="dot"></span> 未答</div>
                  </div>
                  <div class="nav-grid">
                     <div 
                        v-for="(q, idx) in questions" 
                        :key="q.id" 
                        class="nav-cell"
                        :class="{ 
                           'is-active': currentIndex === idx,
                           'is-done': answers[q.question.id] !== undefined && answers[q.question.id] !== '',
                           'is-flagged': flagged.has(q.question.id)
                        }"
                        @click="jumpTo(idx)"
                     >
                        {{ idx + 1 }}
                     </div>
                  </div>
               </div>
               
               <div v-if="!isAnalysis && exam.enableCamera" class="monitor-card">
                  <div class="monitor-head">智能监考系统</div>
                  <div class="video-container">
                    <video ref="videoRef" autoplay playsinline muted></video>
                    <div class="live-status">
                       <span class="status-pulse"></span> REC 实时监控中
                    </div>
                  </div>
               </div>

               <div class="exam-rules-mini">
                  <p><InfoCircleOutlined /> 系统检测到切屏将立即计入次数</p>
                  <p v-if="!exam.allowTabSwitch"><SafetyOutlined /> 切屏限制: {{ tabSwitchCount }} / {{ exam.tabSwitchLimit }}</p>
               </div>
            </div>
         </a-layout-sider>

         <!-- Right Content: Question Content -->
         <a-layout-content class="prof-exam-content">
            <div class="question-canvas" v-if="currentQuestion">
               <div class="q-scope-header">
                  <div class="q-type-label">
                     <a-tag color="blue">
                        {{ { 'SINGLE': '单选题', 'MULTI': '多选题', 'JUDGE': '判断题', 'SHORT': '简答题' }[currentQuestion.question.type] }}
                     </a-tag>
                     <span class="q-index-info">第 {{ currentIndex + 1 }} 题 / 共 {{ questions.length }} 题</span>
                  </div>
                  <div class="q-actions-right">
                    <span class="q-score-tag">分值: {{ currentQuestion.score }}分</span>
                    <a-button 
                      v-if="!isAnalysis"
                      type="text" 
                      class="flag-btn" 
                      @click="toggleFlag"
                      :class="{ 'flagged-active': flagged.has(currentQuestion.question.id) }"
                    >
                      <template #icon>
                        <FlagFilled v-if="flagged.has(currentQuestion.question.id)" />
                        <FlagOutlined v-else />
                      </template>
                      标记此题
                    </a-button>
                  </div>
               </div>

               <div class="q-content-body">
                  <div class="question-text" v-html="currentQuestion.question.content"></div>
                  
                  <div class="answer-interaction-area">
                     <!-- SINGLE -->
                     <a-radio-group v-if="currentQuestion.question.type === 'SINGLE'" v-model:value="answers[currentQuestion.question.id]" :disabled="isAnalysis" class="choice-group">
                        <a-radio class="choice-item" v-for="opt in JSON.parse(currentQuestion.question.options)" :key="opt.label" :value="opt.label">
                           <span class="choice-key">{{ opt.label }}</span>
                           <span class="choice-text">{{ opt.text }}</span>
                        </a-radio>
                     </a-radio-group>
                     
                     <!-- MULTI -->
                     <a-checkbox-group v-if="currentQuestion.question.type === 'MULTI'" v-model:value="answers[currentQuestion.question.id]" :disabled="isAnalysis" class="choice-group">
                        <div v-for="opt in JSON.parse(currentQuestion.question.options)" :key="opt.label" class="choice-item multi-wrap">
                           <a-checkbox :value="opt.label">
                              <span class="choice-key">{{ opt.label }}</span>
                              <span class="choice-text">{{ opt.text }}</span>
                           </a-checkbox>
                        </div>
                     </a-checkbox-group>

                     <!-- JUDGE -->
                     <a-radio-group v-if="currentQuestion.question.type === 'JUDGE'" v-model:value="answers[currentQuestion.question.id]" :disabled="isAnalysis" class="judge-group">
                        <a-radio-button value="TRUE" class="judge-btn">正确 (TRUE)</a-radio-button>
                        <a-radio-button value="FALSE" class="judge-btn">错误 (FALSE)</a-radio-button>
                     </a-radio-group>
                     
                     <!-- SHORT -->
                     <a-textarea 
                        v-if="currentQuestion.question.type === 'SHORT'" 
                        v-model:value="answers[currentQuestion.question.id]" 
                        placeholder="在此输入您的回答..."
                        :rows="12" 
                        :disabled="isAnalysis" 
                        class="essay-editor"
                     />
                  </div>
               </div>

               <!-- Analysis View (Visible only during review) -->
               <div v-if="isAnalysis" class="prof-analysis-section">
                  <div class="analysis-header-row">
                     <div class="judge-banner" :class="isCorrect(currentQuestion.question.id) ? 'correct' : 'wrong'">
                        <CheckCircleFilled v-if="isCorrect(currentQuestion.question.id)" />
                        <CloseCircleFilled v-else />
                        <span>{{ isCorrect(currentQuestion.question.id) ? '回答正确' : '回答错误' }}</span>
                     </div>
                     <div class="analysis-stat-info">
                        本题得分: <span class="score-num">{{ getQuestionScore(currentQuestion.question.id) }}</span> / {{ currentQuestion.score }}
                     </div>
                  </div>
                  
                  <div class="analysis-details-grid">
                     <div class="detail-col">
                        <div class="detail-label">我的作答</div>
                        <div class="detail-val" :class="{ 'error-text': !isCorrect(currentQuestion.question.id) }">
                           {{ answers[currentQuestion.question.id] || '(未答)' }}
                        </div>
                     </div>
                     <div class="detail-col">
                        <div class="detail-label">正确答案</div>
                        <div class="detail-val correct-text">{{ getCorrectAnswer(currentQuestion.question.id) }}</div>
                     </div>
                  </div>

                  <div class="teacher-comment-box" v-if="getTeacherComment(currentQuestion.question.id)">
                     <div class="comment-label">教师评语</div>
                     <div class="comment-content">{{ getTeacherComment(currentQuestion.question.id) }}</div>
                  </div>

                  <div class="explanation-box">
                     <div class="exp-label">解析说明</div>
                     <div class="exp-content">{{ getAnalysis(currentQuestion.question.id) || '暂无解析数据' }}</div>
                  </div>
               </div>
            </div>

            <!-- Enhanced Sticky Pager -->
            <div class="prof-exam-pager">
               <div class="pager-left">
                  <a-button @click="prevQuestion" :disabled="currentIndex === 0" size="large" ghost type="primary" class="nav-btn">
                     <LeftOutlined /> 上一题
                  </a-button>
               </div>
               <div class="pager-center">
                  进度: <a-progress :percent="Math.round(((Object.keys(answers).filter(k => answers[k] !== '').length) / questions.length) * 100)" size="small" style="width: 200px" />
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
  <div v-else class="prof-exam-loading">
    <a-spin size="large" tip="系统正在加载试卷，请稍候..." />
  </div>
</template>

<style scoped>
.exam-page-container {
  height: 100vh;
  background-color: #f7f9fc;
}
.full-height-layout {
  height: 100%;
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
.timer-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 240px;
}
.time-label {
  font-size: 11px;
  color: #999;
  text-transform: uppercase;
  margin-bottom: 2px;
}
.time-val {
  font-size: 24px;
  font-weight: 700;
  color: #1890ff;
  font-family: 'JetBrains Mono', 'Monaco', monospace;
  line-height: 1;
}
.timer-display.urgent .time-val {
  color: #f5222d;
  animation: timer-pulse 1s infinite;
}
.progress-under {
  width: 100%;
  height: 4px;
  background: #e1e8f0;
  border-radius: 2px;
  margin-top: 6px;
  overflow: hidden;
}
.progress-bar {
  height: 100%;
  background: #1890ff;
  transition: width 0.3s;
}
.timer-display.urgent .progress-bar {
  background: #f5222d;
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
.finish-exam-btn {
  font-weight: 600;
  padding: 0 28px;
  height: 40px;
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
.dot.flag { background: #faad14; border-color: #faad14; }

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
  position: relative;
}
.nav-cell.is-active {
  background: #1890ff !important;
  border-color: #1890ff !important;
  color: white !important;
  box-shadow: 0 4px 8px rgba(24, 144, 255, 0.35);
}

.monitor-card {
  margin-top: 24px;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
}
.monitor-head {
  padding: 8px 12px;
  font-size: 10px;
  color: rgba(255,255,255,0.7);
  background: rgba(40,40,40,0.8);
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1;
}
.video-container {
  width: 100%;
  aspect-ratio: 16/10;
}
.video-container video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.live-status {
  position: absolute;
  bottom: 8px;
  right: 8px;
  font-size: 9px;
  color: #52c41a;
  background: rgba(0,0,0,0.6);
  padding: 2px 8px;
  border-radius: 10px;
}
.status-pulse {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: #52c41a;
  border-radius: 50%;
  margin-right: 4px;
  animation: status-pulse 1.5s infinite;
}

.exam-rules-mini {
  margin-top: 24px;
  padding: 16px;
  background: #fff1f0;
  border-radius: 8px;
  font-size: 12px;
  color: #cf1322;
}
.exam-rules-mini p {
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
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
  min-height: 600px;
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

/* Sticky Pager */
.prof-exam-pager {
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

.score-num {
  font-size: 24px;
  color: #1890ff;
  font-weight: 800;
}
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

.teacher-comment-box, .explanation-box {
  margin-bottom: 24px;
}
.comment-label, .exp-label {
  font-weight: 600;
  color: #262626;
  margin-bottom: 10px;
}
.comment-content {
  background: #e6f7ff;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #91d5ff;
  color: #0050b3;
}
.exp-content {
  line-height: 1.8;
  color: #595959;
}

.prof-exam-loading {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
}

@keyframes timer-pulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.05); }
  100% { transform: scale(1); }
}
@keyframes status-pulse {
  0% { opacity: 1; }
  50% { opacity: 0.4; }
  100% { opacity: 1; }
}

/* Custom Checkbox/Radio overrides for premium feel */
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
