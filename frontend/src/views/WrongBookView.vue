<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useConfigStore } from '@/stores/config';
import { 
  getWrongQuestions, getWrongBookStats, getWrongBookSubjects,
  getWrongBookKnowledgePoints, removeFromWrongBook, markQuestionMastered
} from '@/api';
import { message, Modal } from 'ant-design-vue';
import { 
  BookOutlined, TrophyOutlined, PlusOutlined, DeleteOutlined,
  CheckCircleOutlined, FilterOutlined, ReloadOutlined,
  LeftOutlined, RightOutlined, AppstoreOutlined
} from '@ant-design/icons-vue';

const router = useRouter();
const authStore = useAuthStore();
const configStore = useConfigStore();

const stats = ref({
  totalWrong: 0,
  mastered: 0,
  weekNew: 0,
  weekMastered: 0
});

const loading = ref(false);
const questions = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);

const subjectFilter = ref(null);
const knowledgePointFilter = ref(null);
const difficultyFilter = ref(null);
const masteredFilter = ref(null);

const subjects = ref([]);
const knowledgePoints = ref([]);

const fetchStats = async () => {
  try {
    const res = await getWrongBookStats();
    stats.value = res.data;
  } catch (e) {
    console.error('Failed to fetch stats', e);
  }
};

const fetchFilters = async () => {
  try {
    const [subRes, kpRes] = await Promise.all([
      getWrongBookSubjects(),
      getWrongBookKnowledgePoints()
    ]);
    subjects.value = subRes.data;
    knowledgePoints.value = kpRes.data;
  } catch (e) {
    console.error('Failed to fetch filters', e);
  }
};

const fetchQuestions = async () => {
  loading.value = true;
  try {
    const params = {
      page: page.value - 1,
      size: pageSize.value
    };
    if (subjectFilter.value) params.subject = subjectFilter.value;
    if (knowledgePointFilter.value) params.knowledgePoint = knowledgePointFilter.value;
    if (difficultyFilter.value) params.difficulty = difficultyFilter.value;
    if (masteredFilter.value !== null) params.mastered = masteredFilter.value;
    
    const res = await getWrongQuestions(params);
    questions.value = res.data.content;
    total.value = res.data.totalElements;
  } catch (e) {
    console.error('Failed to fetch questions', e);
    message.error('加载错题列表失败');
  } finally {
    loading.value = false;
  }
};

const handlePageChange = (pageNum) => {
  page.value = pageNum;
  fetchQuestions();
};

const handleFilterChange = () => {
  page.value = 1;
  fetchQuestions();
};

const resetFilters = () => {
  subjectFilter.value = null;
  knowledgePointFilter.value = null;
  difficultyFilter.value = null;
  masteredFilter.value = null;
  page.value = 1;
  fetchQuestions();
};

const handleRemove = (question) => {
  Modal.confirm({
    title: '确认移除',
    content: `确定要将这道题移出错题本吗？`,
    okText: '确认移除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await removeFromWrongBook(question.questionId);
        message.success('已移出错题本');
        fetchQuestions();
        fetchStats();
        fetchFilters();
      } catch (e) {
        message.error('移除失败');
      }
    }
  });
};

const handleMarkMastered = (question) => {
  Modal.confirm({
    title: '标记为已掌握',
    content: `确定要将这道题标记为已掌握吗？`,
    okText: '确认标记',
    cancelText: '取消',
    onOk: async () => {
      try {
        await markQuestionMastered(question.questionId);
        message.success('已标记为掌握');
        fetchQuestions();
        fetchStats();
      } catch (e) {
        message.error('标记失败');
      }
    }
  });
};

const startPractice = () => {
  if (stats.value.totalWrong === 0) {
    message.warning('当前没有待练习的错题');
    return;
  }
  router.push('/wrong-book/practice');
};

const getTypeLabel = (type) => {
  const map = { 'SINGLE': '单选题', 'MULTI': '多选题', 'JUDGE': '判断题', 'SHORT': '简答题' };
  return map[type] || type;
};

const getDifficultyLabel = (d) => {
  const map = { 1: '简单', 2: '较易', 3: '中等', 4: '较难', 5: '困难' };
  return map[d] || '未知';
};

const getDifficultyColor = (d) => {
  const map = { 1: 'green', 2: 'cyan', 3: 'blue', 4: 'orange', 5: 'red' };
  return map[d] || 'default';
};

const totalPages = computed(() => Math.ceil(total.value / pageSize.value));

onMounted(() => {
  fetchStats();
  fetchFilters();
  fetchQuestions();
});
</script>

<template>
  <div class="wrong-book-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">
          <BookOutlined /> 错题本
        </h1>
        <p class="page-desc">整理你的错题，针对性复习提升</p>
      </div>
      <a-button type="primary" size="large" @click="startPractice" :disabled="stats.totalWrong === 0">
        <ReloadOutlined /> 一键重练
      </a-button>
    </div>

    <a-row :gutter="24" class="stats-row">
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-icon week-new">
            <PlusOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.weekNew }}</div>
            <div class="stat-label">本周新增</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-icon total">
            <BookOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalWrong }}</div>
            <div class="stat-label">待掌握错题</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-icon mastered">
            <TrophyOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.mastered }}</div>
            <div class="stat-label">已掌握</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card">
          <div class="stat-icon week-mastered">
            <CheckCircleOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.weekMastered }}</div>
            <div class="stat-label">本周掌握</div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card class="filter-card" :bordered="false">
      <div class="filter-header">
        <span class="filter-title"><FilterOutlined /> 筛选条件</span>
        <a-button type="link" @click="resetFilters">重置筛选</a-button>
      </div>
      <div class="filter-row">
        <div class="filter-item">
          <label>科目</label>
          <a-select v-model:value="subjectFilter" placeholder="全部科目" allowClear style="width: 160px" @change="handleFilterChange">
            <a-select-option v-for="s in subjects" :key="s" :value="s">{{ s }}</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <label>知识点</label>
          <a-select v-model:value="knowledgePointFilter" placeholder="全部知识点" allowClear style="width: 200px" @change="handleFilterChange">
            <a-select-option v-for="kp in knowledgePoints" :key="kp" :value="kp">{{ kp }}</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <label>难度</label>
          <a-select v-model:value="difficultyFilter" placeholder="全部难度" allowClear style="width: 140px" @change="handleFilterChange">
            <a-select-option :value="1">简单</a-select-option>
            <a-select-option :value="2">较易</a-select-option>
            <a-select-option :value="3">中等</a-select-option>
            <a-select-option :value="4">较难</a-select-option>
            <a-select-option :value="5">困难</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <label>掌握状态</label>
          <a-select v-model:value="masteredFilter" placeholder="全部" allowClear style="width: 140px" @change="handleFilterChange">
            <a-select-option :value="false">未掌握</a-select-option>
            <a-select-option :value="true">已掌握</a-select-option>
          </a-select>
        </div>
      </div>
    </a-card>

    <a-card class="question-list-card" :bordered="false" :loading="loading">
      <div class="list-header">
        <span class="list-title">错题列表 (共 {{ total }} 题)</span>
      </div>

      <div v-if="questions.length === 0 && !loading" class="empty-state">
        <a-empty description="暂无错题记录">
          <template #image>
            <div class="empty-icon"><BookOutlined /></div>
          </template>
          <p style="color: #8c8c8c;">完成考试后，错题会自动收录到这里</p>
        </a-empty>
      </div>

      <div v-else class="question-list">
        <div v-for="q in questions" :key="q.id" class="question-item">
          <div class="question-header">
            <div class="question-tags">
              <a-tag color="blue">{{ getTypeLabel(q.type) }}</a-tag>
              <a-tag v-if="q.subject" color="green">{{ q.subject }}</a-tag>
              <a-tag v-if="q.knowledgePoint">{{ q.knowledgePoint }}</a-tag>
              <a-tag :color="getDifficultyColor(q.difficulty)">难度: {{ getDifficultyLabel(q.difficulty) }}</a-tag>
              <a-tag v-if="q.mastered" color="gold">已掌握</a-tag>
            </div>
            <div class="question-meta">
              <span class="score-info">得分: {{ q.scoreGot || 0 }} / {{ q.fullScore || q.defaultScore }}</span>
              <span class="wrong-count">错 {{ q.wrongCount }} 次</span>
            </div>
          </div>
          <div class="question-content" v-html="q.content"></div>
          <div class="question-footer">
            <div class="my-answer">
              <span class="label">我的作答：</span>
              <span class="answer-text wrong">{{ q.studentAnswer || '(未作答)' }}</span>
            </div>
            <div class="correct-answer">
              <span class="label">正确答案：</span>
              <span class="answer-text correct">{{ q.answer }}</span>
            </div>
          </div>
          <div class="question-actions">
            <a-button size="small" @click="handleMarkMastered(q)" v-if="!q.mastered">
              <CheckCircleOutlined /> 标记掌握
            </a-button>
            <a-button size="small" danger @click="handleRemove(q)">
              <DeleteOutlined /> 移出错题本
            </a-button>
          </div>
        </div>
      </div>

      <div v-if="totalPages > 1" class="pagination-wrapper">
        <a-pagination 
          :current="page" 
          :total="total" 
          :page-size="pageSize"
          :show-size-changer="false"
          :show-quick-jumper="false"
          @change="handlePageChange"
        />
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.wrong-book-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-desc {
  color: #8c8c8c;
  margin: 8px 0 0 0;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.stat-card :deep(.ant-card-body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
}

.stat-icon.week-new {
  background: linear-gradient(135deg, #ff7a45, #fa541c);
}

.stat-icon.total {
  background: linear-gradient(135deg, #1890ff, #096dd9);
}

.stat-icon.mastered {
  background: linear-gradient(135deg, #52c41a, #389e0d);
}

.stat-icon.week-mastered {
  background: linear-gradient(135deg, #722ed1, #531dab);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-top: 4px;
}

.filter-card {
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filter-title {
  font-weight: 600;
  font-size: 15px;
  color: #262626;
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-item label {
  font-size: 13px;
  color: #595959;
}

.question-list-card {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.list-header {
  margin-bottom: 20px;
  font-weight: 600;
  font-size: 16px;
  color: #262626;
}

.empty-state {
  padding: 60px 0;
}

.empty-icon {
  font-size: 64px;
  color: #d9d9d9;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-item {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 20px;
  transition: all 0.2s;
}

.question-item:hover {
  border-color: #bae0ff;
  background: #f0f7ff;
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.question-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.question-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  font-size: 13px;
}

.score-info {
  color: #8c8c8c;
}

.wrong-count {
  color: #ff4d4f;
  font-weight: 500;
}

.question-content {
  font-size: 16px;
  line-height: 1.8;
  color: #1a1a1a;
  margin-bottom: 16px;
}

.question-footer {
  display: flex;
  gap: 32px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: white;
  border-radius: 8px;
  border: 1px dashed #e8e8e8;
}

.my-answer, .correct-answer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label {
  font-size: 13px;
  color: #8c8c8c;
  flex-shrink: 0;
}

.answer-text {
  font-weight: 500;
  font-size: 14px;
}

.answer-text.wrong {
  color: #ff4d4f;
}

.answer-text.correct {
  color: #52c41a;
}

.question-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
