<script setup>
import { ref, watch, computed } from 'vue';
import { getSubmission, gradeSubmission, getExamQuestions } from '@/api';
import { message } from 'ant-design-vue';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue';

const props = defineProps(['open', 'submissionId']);
const emit = defineEmits(['update:open', 'graded']);

const loading = ref(false);
const submission = ref(null);
const questions = ref([]);
const grades = ref({}); // { questionId: { score, teacherComment } }

const fetchData = async () => {
  if (!props.submissionId) return;
  loading.value = true;
  try {
    const res = await getSubmission(props.submissionId);
    submission.value = res.data;
    
    const qRes = await getExamQuestions(submission.value.exam.id);
    questions.value = qRes.data;
    
    // Initialize grades state
    const currentGrades = {};
    submission.value.answers.forEach(sa => {
      const qId = sa.question.id || sa.question;
      currentGrades[qId] = {
        score: sa.score,
        teacherComment: sa.teacherComment || ''
      };
    });
    grades.value = currentGrades;
  } catch (e) {
    message.error('加载详情失败');
  } finally {
    loading.value = false;
  }
};

watch(() => props.open, (newVal) => {
  if (newVal) {
    fetchData();
  }
});

const handleOk = async () => {
  try {
    await gradeSubmission(props.submissionId, grades.value);
    message.success('评分提交成功');
    emit('graded');
    emit('update:open', false);
  } catch (e) {
    message.error('评分失败');
  }
};

const handleCancel = () => {
  emit('update:open', false);
};

const getStudentAnswer = (qId) => {
  const sa = submission.value?.answers.find(a => (a.question.id || a.question) === qId);
  return sa ? sa.studentAnswer : '(未答)';
};

const isObjective = (type) => ['SINGLE', 'MULTI', 'JUDGE'].includes(type);

const getStatusColor = (sa) => {
  if (!sa) return '#666';
  // For objective, check against answer
  if (isObjective(sa.question.type)) {
     return sa.studentAnswer === sa.question.answer ? '#52c41a' : '#ff4d4f';
  }
  return '#1890ff';
};
</script>

<template>
  <a-modal
    :open="open"
    title="批改试卷"
    @ok="handleOk"
    @cancel="handleCancel"
    width="1000px"
    okText="提交评分"
    cancelText="取消"
    :confirmLoading="loading"
  >
    <div v-if="loading" style="text-align: center; padding: 50px;">
      <a-spin tip="加载中..." />
    </div>
    <div v-else-if="submission" class="grade-container">
      <div class="submission-info">
        <a-descriptions title="考生信息" bordered size="small">
          <a-descriptions-item label="考生">{{ submission.student?.fullName }}</a-descriptions-item>
          <a-descriptions-item label="考试名称">{{ submission.exam?.title }}</a-descriptions-item>
          <a-descriptions-item label="提交时间">{{ submission.endTime }}</a-descriptions-item>
          <a-descriptions-item label="当前总分">
             <span style="font-size: 18px; font-weight: bold; color: #1890ff">{{ submission.score }}</span>
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <a-divider />

      <div class="questions-list">
        <div v-for="(qItem, index) in questions" :key="qItem.id" class="q-grade-item">
          <div class="q-title">
            <a-tag color="blue">第 {{ index + 1 }} 题</a-tag>
            <span v-html="qItem.question.content"></span>
            <span class="q-score-limit">(分值: {{ qItem.score }})</span>
          </div>

          <div class="q-details">
            <div class="ans-block">
              <div class="label">学生答案:</div>
              <div class="content student-ans" :style="{ color: getStatusColor(submission.answers.find(a => (a.question.id || a.question) === qItem.question.id)) }">
                {{ getStudentAnswer(qItem.question.id) }}
              </div>
            </div>
            <div class="ans-block">
              <div class="label">标准答案:</div>
              <div class="content correct-ans">{{ qItem.question.answer }}</div>
            </div>
          </div>

          <div class="grading-panel">
            <a-row :gutter="16">
              <a-col :span="6">
                <div class="label">评分:</div>
                <a-input-number 
                  v-model:value="grades[qItem.question.id].score" 
                  :min="0" 
                  :max="qItem.score" 
                  style="width: 100%"
                />
              </a-col>
              <a-col :span="18">
                <div class="label">评语:</div>
                <a-input v-model:value="grades[qItem.question.id].teacherComment" placeholder="输入评语..." />
              </a-col>
            </a-row>
          </div>
          <a-divider dashed />
        </div>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.grade-container {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 8px;
}
.q-grade-item {
  margin-bottom: 24px;
}
.q-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 16px;
  background: #fafafa;
  padding: 8px 12px;
  border-radius: 4px;
}
.q-score-limit {
  color: #999;
  font-size: 13px;
  margin-left: 8px;
}
.q-details {
  display: flex;
  gap: 40px;
  margin-bottom: 16px;
  padding: 0 12px;
}
.ans-block .label {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 4px;
}
.ans-block .content {
  font-weight: 500;
  font-size: 15px;
}
.grading-panel {
  background: #e6f7ff;
  padding: 16px;
  border-radius: 6px;
  margin: 0 12px;
}
.grading-panel .label {
  font-size: 12px;
  color: #1890ff;
  margin-bottom: 4px;
}
</style>
