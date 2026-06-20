<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getExam, getExamQuestions } from '@/api';
import { 
  ClockCircleOutlined, CalendarOutlined, BookOutlined, 
  LeftOutlined, SafetyCertificateOutlined, EyeOutlined,
  CloudOutlined, InfoCircleOutlined
} from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';

const route = useRoute();
const router = useRouter();
const examId = route.params.id;
const exam = ref(null);
const loading = ref(true);
const questions = ref([]);

const fetchData = async () => {
  try {
    const [eRes, qRes] = await Promise.all([
      getExam(examId),
      getExamQuestions(examId)
    ]);
    exam.value = eRes.data;
    questions.value = qRes.data;
  } catch (e) {
    message.error('加载考试信息失败');
    router.push('/dashboard');
  } finally {
    loading.value = false;
  }
};

const status = computed(() => {
  if (!exam.value) return {};
  const now = new Date();
  const start = exam.value.startTime ? new Date(exam.value.startTime) : null;
  const end = exam.value.endTime ? new Date(exam.value.endTime) : null;

  if (start && now < start) return { text: '未开始', color: 'blue', allow: false };
  if (end && now > end) return { text: '已结束', color: 'red', allow: false };
  return { text: '进行中', color: 'green', allow: true };
});

const canStart = computed(() => status.value.allow);

const totalScore = computed(() => questions.value.reduce((sum, q) => sum + (q.score || 0), 0));

onMounted(fetchData);

const startExam = () => {
  router.push(`/exam/${examId}`);
};

const goBack = () => {
  router.back();
};
</script>

<template>
  <div class="detail-page-wrapper">
    <div class="background-accent"></div>
    <div class="content-container" v-if="exam">
      <div class="back-link" @click="goBack">
        <LeftOutlined /> 返回列表
      </div>

      <div class="hero-section">
        <div class="hero-left">
           <img :src="exam.coverUrl || 'https://gw.alipayobjects.com/zos/rmsportal/JiqGstEfoWAOHiTxclqi.png'" alt="cover" class="exam-cover"/>
        </div>
        <div class="hero-right">
           <div class="title-row">
             <h1 class="exam-title">{{ exam.title }}</h1>
             <a-tag :color="status.color" class="status-tag">{{ status.text }}</a-tag>
           </div>
           <p class="course-name">{{ exam.course || '通识课程' }}</p>
           <div class="quick-stats">
              <div class="stat-item">
                 <div class="stat-val">{{ exam.duration }}</div>
                 <div class="stat-label">考试时长(分)</div>
              </div>
              <div class="stat-item">
                 <div class="stat-val">{{ questions.length }}</div>
                 <div class="stat-label">题目总数</div>
              </div>
              <div class="stat-item">
                 <div class="stat-val">{{ totalScore }}</div>
                 <div class="stat-label">总分</div>
              </div>
           </div>
        </div>
      </div>

      <a-row :gutter="24" class="main-body">
        <a-col :span="16">
           <a-card title="考试说明" class="info-card">
              <div class="description-box">
                {{ exam.description || '暂无详细考试说明' }}
              </div>
              <div class="rules-section">
                <h3><InfoCircleOutlined /> 考生规则</h3>
                <ul>
                  <li>请确保网络连接稳定，建议使用 Chrome 浏览器。</li>
                  <li>考试过程中系统将实时检测切屏行为，达到上限将自动交卷。</li>
                  <li>一旦进入考试，计时器将立即启动，请注意时间。</li>
                </ul>
              </div>
           </a-card>
        </a-col>
        <a-col :span="8">
           <a-card title="配置信息" class="info-card">
              <a-list size="small">
                <a-list-item>
                  <template #extra><EyeOutlined /></template>
                  <a-list-item-meta title="防作弊设置">
                    <template #description>
                      <a-tag v-if="!exam.allowTabSwitch" color="warning">切屏统计 (上限 {{ exam.tabSwitchLimit }} 次)</a-tag>
                      <a-tag v-else color="success">自由切屏</a-tag>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
                <a-list-item>
                  <template #extra><CloudOutlined /></template>
                  <a-list-item-meta title="实时监考">
                    <template #description>
                      <span v-if="exam.enableCamera">开启系统录像/抓拍监控</span>
                      <span v-else>未开启远程监控</span>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
                <a-list-item>
                  <template #extra><SafetyCertificateOutlined /></template>
                  <a-list-item-meta title="成绩发布">
                    <template #description>
                      <span v-if="exam.publicScores">交卷后立即显示分数</span>
                      <span v-else>老师阅卷后统一公布</span>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </a-list>
              
              <div class="start-action">
                <div v-if="status.text === '未开始'" class="countdown-hint">
                  <ClockCircleOutlined /> 距离开考还有 24:00:00
                </div>
                <a-button type="primary" block size="large" :disabled="!canStart" @click="startExam" class="primary-start-btn">
                   {{ canStart ? '正式进入考试' : '尚未开始或已结束' }}
                </a-button>
              </div>
           </a-card>
        </a-col>
      </a-row>
    </div>
    <div v-else class="loading-state">
       <a-spin size="large" />
    </div>
  </div>
</template>

<style scoped>
.detail-page-wrapper {
  min-height: 100vh;
  background-color: #f0f2f5;
  padding-bottom: 60px;
  position: relative;
}
.background-accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 280px;
  background: linear-gradient(135deg, #1890ff 0%, #3a76f0 100%);
  z-index: 0;
}
.content-container {
  position: relative;
  z-index: 1;
  max-width: 1100px;
  margin: 0 auto;
  padding-top: 24px;
}
.back-link {
  color: rgba(255,255,255,0.8);
  cursor: pointer;
  margin-bottom: 24px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s;
}
.back-link:hover {
  color: white;
  transform: translateX(-4px);
}
.hero-section {
  display: flex;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  margin-bottom: 24px;
}
.hero-left {
  width: 320px;
}
.exam-cover {
  width: 100%;
  height: 240px;
  object-fit: cover;
}
.hero-right {
  flex: 1;
  padding: 32px 40px;
  display: flex;
  flex-direction: column;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}
.exam-title {
  font-size: 32px;
  font-weight: 600;
  margin: 0;
  color: #1a1a1a;
}
.course-name {
  color: #666;
  font-size: 16px;
  margin-bottom: 24px;
}
.quick-stats {
  margin-top: auto;
  display: flex;
  gap: 48px;
}
.stat-val {
  font-size: 24px;
  font-weight: bold;
  color: #1890ff;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: #999;
}

.main-body {
  margin-top: 24px;
}
.info-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0,0,0,0.04);
}
.description-box {
  background: #fafafa;
  padding: 20px;
  border-radius: 8px;
  min-height: 120px;
  color: #444;
  line-height: 1.8;
  font-size: 15px;
}
.rules-section {
  margin-top: 32px;
}
.rules-section h3 {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.rules-section ul {
  padding-left: 20px;
  color: #666;
}
.rules-section li {
  margin-bottom: 10px;
}

.start-action {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}
.countdown-hint {
  text-align: center;
  color: #f5222d;
  margin-bottom: 16px;
  font-weight: 500;
}
.primary-start-btn {
  height: 52px;
  font-size: 18px;
  font-weight: 600;
  border-radius: 8px;
}

.loading-state {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
