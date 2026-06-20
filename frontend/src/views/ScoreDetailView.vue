<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getSubmission, getExamQuestions } from '@/api';
import { message } from 'ant-design-vue';
import { 
  LeftOutlined, CheckCircleFilled, CloseCircleFilled, 
  InfoCircleOutlined, TrophyOutlined, ClockCircleOutlined,
  CalendarOutlined, UserOutlined
} from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();
const submissionId = route.params.id;

const submission = ref(null);
const questions = ref([]);
const loading = ref(true);

const fetchData = async () => {
  try {
    const subRes = await getSubmission(submissionId);
    submission.value = subRes.data;
    
    const qRes = await getExamQuestions(submission.value.exam.id);
    questions.value = qRes.data;
  } catch (e) {
    message.error('获取成绩详情失败');
    router.push('/dashboard');
  } finally {
    loading.value = false;
  }
};

const getStudentAnswer = (qId) => {
  if (!submission.value || !submission.value.answers) return null;
  const sa = submission.value.answers.find(a => (a.question.id || a.question) === qId);
  return sa ? sa.studentAnswer : null;
};

const getQuestionScore = (qId) => {
  if (!submission.value || !submission.value.answers) return 0;
  const sa = submission.value.answers.find(a => (a.question.id || a.question) === qId);
  return sa ? sa.score : 0;
};

const getTeacherComment = (qId) => {
  if (!submission.value || !submission.value.answers) return null;
  const sa = submission.value.answers.find(a => (a.question.id || a.question) === qId);
  return sa ? sa.teacherComment : null;
};

const isCorrect = (qId, correctAns) => {
  const studentAns = getStudentAnswer(qId);
  return studentAns === correctAns;
};

const formatDate = (dateStr) => {
  if (!dateStr) return 'N/A';
  return new Date(dateStr).toLocaleString();
};

onMounted(fetchData);
</script>

<template>
  <div class="score-detail-wrapper">
    <!-- Sticky Header -->
    <div class="score-header">
      <div class="header-content">
        <a-button type="link" @click="router.back()" class="back-btn">
          <LeftOutlined /> 返回
        </a-button>
        <div class="exam-info-header" v-if="submission">
           <span class="exam-title">{{ submission.exam.title }}</span>
           <a-divider type="vertical" />
           <span class="exam-course">{{ submission.exam.course }}</span>
        </div>
        <div class="score-summary-pill" v-if="submission">
           <TrophyOutlined /> 最终得分: <span class="total-score">{{ submission.score }}</span> / {{ submission.examTotalScore }}
        </div>
      </div>
    </div>

    <!-- Paper Container -->
    <div class="paper-page" v-if="submission">
      <div class="paper-header">
         <h1 class="paper-title">考试成绩单 (试卷形式)</h1>
         <div class="metadata-row">
            <div class="meta-item"><UserOutlined /> 考生: {{ submission.student.fullName }}</div>
            <div class="meta-item"><CalendarOutlined /> 提交时间: {{ formatDate(submission.endTime) }}</div>
            <div class="meta-item"><ClockCircleOutlined /> 用时: {{ submission.duration || '--' }} 分钟</div>
         </div>
      </div>

      <div class="paper-body">
         <div v-for="(q, index) in questions" :key="q.id" class="question-block">
            <div class="q-header">
               <div class="q-index">第 {{ index + 1 }} 题</div>
               <div class="q-meta">
                  <a-tag color="blue">{{ { 'SINGLE': '单选题', 'MULTI': '多选题', 'JUDGE': '判断题', 'SHORT': '简答题' }[q.question.type] }}</a-tag>
                  <span class="q-score">分值: {{ q.score }} 分</span>
                  <div class="result-badge" :class="getQuestionScore(q.question.id) > 0 ? 'correct' : 'wrong'">
                     <template v-if="getQuestionScore(q.question.id) === q.score">
                        <CheckCircleFilled /> 正确
                     </template>
                     <template v-else-if="getQuestionScore(q.question.id) > 0">
                        <InfoCircleOutlined /> 部分正确
                     </template>
                     <template v-else>
                        <CloseCircleFilled /> 错误
                     </template>
                     <span class="earned-score">得分: {{ getQuestionScore(q.question.id) }}</span>
                  </div>
               </div>
            </div>

            <div class="q-content">
               <div class="q-text" v-html="q.question.content"></div>
               
               <!-- Options if applicable -->
               <div v-if="['SINGLE', 'MULTI'].includes(q.question.type)" class="options-list">
                  <div 
                    v-for="opt in JSON.parse(q.question.options)" 
                    :key="opt.label" 
                    class="option-row"
                    :class="{
                      'is-student': getStudentAnswer(q.question.id)?.split(',').includes(opt.label),
                      'is-correct': q.question.answer?.split(',').includes(opt.label)
                    }"
                  >
                     <span class="opt-key">{{ opt.label }}.</span>
                     <span class="opt-text">{{ opt.text }}</span>
                     <div class="opt-indicator">
                        <CheckCircleFilled v-if="q.question.answer?.split(',').includes(opt.label)" class="correct-icon" />
                        <CloseCircleFilled v-else-if="getStudentAnswer(q.question.id)?.split(',').includes(opt.label)" class="wrong-icon" />
                     </div>
                  </div>
               </div>

               <!-- Judge Result -->
               <div v-if="q.question.type === 'JUDGE'" class="judge-result">
                  <div class="judge-item" :class="{ 'is-student': getStudentAnswer(q.question.id) === 'TRUE', 'is-correct': q.question.answer === 'TRUE' }">正确 (TRUE)</div>
                  <div class="judge-item" :class="{ 'is-student': getStudentAnswer(q.question.id) === 'FALSE', 'is-correct': q.question.answer === 'FALSE' }">错误 (FALSE)</div>
               </div>

               <!-- Short Answer -->
               <div v-if="q.question.type === 'SHORT'" class="short-answer-display">
                  <div class="sub-label">我的回答:</div>
                  <div class="student-essay">{{ getStudentAnswer(q.question.id) || '(未作答)' }}</div>
               </div>
            </div>

            <!-- Analysis Section -->
            <div class="analysis-footer">
               <div class="row-flex">
                 <div class="ans-box">
                    <span class="label">我的答案:</span>
                    <span class="val" :class="getQuestionScore(q.question.id) > 0 ? 'success' : 'error'">{{ getStudentAnswer(q.question.id) || '空' }}</span>
                 </div>
                 <div class="ans-box">
                    <span class="label">正确答案:</span>
                    <span class="val primary">{{ q.question.answer }}</span>
                 </div>
               </div>
               
               <div class="teacher-comment" v-if="getTeacherComment(q.question.id)">
                  <div class="comment-label">教师评语:</div>
                  <div class="comment-content">{{ getTeacherComment(q.question.id) }}</div>
               </div>

               <div class="explanation">
                  <div class="exp-label">解析:</div>
                  <div class="exp-content">{{ q.question.analysis || '暂无解析' }}</div>
               </div>
            </div>
         </div>
      </div>
      
      <div class="paper-footer">
         <p>以上为本次考试的全部作答记录</p>
         <div class="footer-stamp">已阅</div>
      </div>
    </div>

    <div v-else-if="loading" class="loading-box">
       <a-spin size="large" tip="正在为您打印成绩单..." />
    </div>
  </div>
</template>

<style scoped>
.score-detail-wrapper {
  background-color: #f0f2f5;
  min-height: 100vh;
  padding-top: 80px;
  padding-bottom: 60px;
}

/* Header */
.score-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  background: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  z-index: 1000;
  display: flex;
  align-items: center;
}
.header-content {
  max-width: 1000px;
  margin: 0 auto;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}
.back-btn {
  font-size: 16px;
  color: #666;
}
.exam-info-header {
  font-weight: 600;
  font-size: 16px;
}
.score-summary-pill {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  padding: 6px 16px;
  border-radius: 20px;
  color: #52c41a;
  font-weight: 600;
}
.total-score {
  font-size: 20px;
  color: #f5222d;
}

/* Paper Styles */
.paper-page {
  max-width: 900px;
  margin: 0 auto;
  background: white;
  box-shadow: 0 10px 30px rgba(0,0,0,0.1);
  padding: 60px 80px;
  position: relative;
  border-radius: 4px;
}
.paper-header {
  text-align: center;
  margin-bottom: 60px;
  border-bottom: 2px solid #333;
  padding-bottom: 30px;
}
.paper-title {
  font-family: "Microsoft YaHei", serif;
  font-size: 32px;
  font-weight: bold;
  letter-spacing: 4px;
  color: #1a1a1a;
  margin-bottom: 24px;
}
.metadata-row {
  display: flex;
  justify-content: center;
  gap: 40px;
  color: #666;
  font-size: 14px;
}

.question-block {
  margin-bottom: 60px;
  border-bottom: 1px dashed #eee;
  padding-bottom: 40px;
}
.question-block:last-child {
  border-bottom: none;
}

.q-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.q-index {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}
.q-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}
.q-score {
  color: #999;
  font-size: 14px;
}

.result-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 13px;
}
.result-badge.correct { color: #52c41a; background: #f6ffed; }
.result-badge.wrong { color: #f5222d; background: #fff1f0; }
.earned-score {
  margin-left: 8px;
  border-left: 1px solid currentColor;
  padding-left: 8px;
}

.q-text {
  font-size: 17px;
  line-height: 1.8;
  color: #262626;
  margin-bottom: 24px;
}

/* Options */
.options-list {
  margin-left: 10px;
}
.option-row {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 8px;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.option-row.is-student {
  background: #f5f5f5;
}
.option-row.is-correct {
  background: #f6ffed;
  border-color: #b7eb8f;
}
.option-row.is-student.is-correct {
  background: #f6ffed;
}
.option-row:not(.is-correct).is-student {
  background: #fff1f0;
  border-color: #ffa39e;
}

.opt-key {
  font-weight: 700;
  width: 30px;
  color: #1890ff;
}
.opt-text {
  flex: 1;
}
.opt-indicator {
  width: 24px;
  display: flex;
  justify-content: center;
}
.correct-icon { color: #52c41a; font-size: 18px; }
.wrong-icon { color: #f5222d; font-size: 18px; }

/* Judge */
.judge-result {
  display: flex;
  gap: 20px;
}
.judge-item {
  padding: 10px 24px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  color: #8c8c8c;
}
.judge-item.is-student {
  background: #f5f5f5;
  color: #333;
}
.judge-item.is-correct {
  border-color: #b7eb8f;
  background: #f6ffed;
  color: #52c41a;
  font-weight: 600;
}

/* Short Answer */
.short-answer-display {
  background: #fafafa;
  padding: 20px;
  border-radius: 8px;
  border-left: 4px solid #d9d9d9;
}
.sub-label {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
}
.student-essay {
  font-size: 16px;
  color: #333;
  white-space: pre-wrap;
  line-height: 1.6;
}

/* Footer Analysis */
.analysis-footer {
  margin-top: 32px;
  padding: 24px;
  background: #fafafa;
  border-radius: 12px;
}
.row-flex {
  display: flex;
  gap: 40px;
  margin-bottom: 20px;
}
.ans-box .label {
  color: #999;
  margin-right: 8px;
  font-size: 14px;
}
.ans-box .val {
  font-weight: 700;
  font-size: 16px;
}
.ans-box .val.success { color: #52c41a; }
.ans-box .val.error { color: #f5222d; }
.ans-box .val.primary { color: #1890ff; }

.teacher-comment {
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #e6f7ff;
  border-radius: 8px;
  border: 1px solid #91d5ff;
}
.comment-label { font-weight: 600; color: #0050b3; margin-bottom: 4px; }
.comment-content { color: #002766; }

.explanation {
  border-top: 1px solid #eee;
  padding-top: 16px;
}
.exp-label { font-weight: 600; color: #555; margin-bottom: 6px; }
.exp-content { color: #666; line-height: 1.6; }

.paper-footer {
  margin-top: 80px;
  text-align: center;
  color: #ccc;
  font-size: 14px;
  position: relative;
}
.footer-stamp {
  position: absolute;
  right: 20px;
  bottom: 0;
  width: 100px;
  height: 100px;
  border: 4px solid #f5222d;
  color: #f5222d;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 900;
  transform: rotate(-25deg);
  opacity: 0.6;
}

.loading-box {
  height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .paper-page {
    padding: 30px 20px;
    margin: 10px;
  }
  .row-flex {
    flex-direction: column;
    gap: 10px;
  }
}
</style>
