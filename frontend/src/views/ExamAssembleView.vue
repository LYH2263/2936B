<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { 
  getExam, getExamQuestions, getAllQuestions, 
  addQuestionToExam, updateExamQuestion, removeQuestionFromExam 
} from '@/api';
import { message } from 'ant-design-vue';
import { 
  LeftOutlined, SearchOutlined, PlusCircleOutlined, 
  DeleteOutlined, HolderOutlined, SaveOutlined,
  CheckCircleOutlined, InfoCircleOutlined,
  ArrowUpOutlined, ArrowDownOutlined
} from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();
const examId = route.params.id;

const exam = ref(null);
const bankQuestions = ref([]);
const examQuestions = ref([]);
const loadingBank = ref(false);
const loadingExam = ref(false);
const saving = ref(false);

// Filters
const filterText = ref('');
const filterSubject = ref(null);
const filterType = ref(null);
const filterDifficulty = ref(null);

const fetchData = async () => {
  loadingExam.value = true;
  loadingBank.value = true;
  try {
    const [eRes, eqRes, bRes] = await Promise.all([
      getExam(examId),
      getExamQuestions(examId),
      getAllQuestions()
    ]);
    exam.value = eRes.data;
    examQuestions.value = eqRes.data.sort((a, b) => (a.sequence || 0) - (b.sequence || 0));
    bankQuestions.value = bRes.data;
  } catch (e) {
    message.error('获取数据失败');
  } finally {
    loadingExam.value = false;
    loadingBank.value = false;
  }
};

const filteredBank = computed(() => {
  return bankQuestions.value.filter(q => {
    // Exclude questions already in the exam
    const inExam = examQuestions.value.some(eq => eq.question.id === q.id);
    if (inExam) return false;

    if (filterText.value && !q.content.includes(filterText.value)) return false;
    if (filterSubject.value && q.subject !== filterSubject.value) return false;
    if (filterType.value && q.type !== filterType.value) return false;
    if (filterDifficulty.value && q.difficulty !== filterDifficulty.value) return false;
    return true;
  });
});

const distinctSubjects = computed(() => {
  return Array.from(new Set(bankQuestions.value.map(q => q.subject).filter(Boolean)));
});

const totalScore = computed(() => {
  return examQuestions.value.reduce((sum, q) => sum + (q.score || 0), 0);
});

// Actions
const addToExam = async (q) => {
  try {
    const res = await addQuestionToExam(examId, {
      questionId: q.id,
      score: q.defaultScore || 5,
      sequence: examQuestions.value.length + 1
    });
    // Optimistic or refresh
    const freshRes = await getExamQuestions(examId);
    examQuestions.value = freshRes.data.sort((a, b) => (a.sequence || 0) - (b.sequence || 0));
    message.success('添加成功');
  } catch (e) {
    message.error('添加失败');
  }
};

const removeFromExam = async (qId) => {
  try {
    await removeQuestionFromExam(examId, qId);
    examQuestions.value = examQuestions.value.filter(eq => eq.question.id !== qId);
    message.success('已移除');
  } catch (e) {
    message.error('移除失败');
  }
};

const saveAssembly = async () => {
  saving.value = true;
  try {
    // Update sequences and scores
    await Promise.all(examQuestions.value.map((eq, index) => 
      updateExamQuestion(examId, eq.question.id, {
        score: eq.score,
        sequence: index + 1
      })
    ));
    message.success('试卷配置已保存');
  } catch (e) {
    message.error('保存失败');
  } finally {
    saving.value = false;
  }
};

// Simple Sort Actions (D&D can be complex to implement bare-bones, 
// so I'll provide Arrow buttons + a drag placeholder UI)
const move = (index, direction) => {
  const newIndex = index + direction;
  if (newIndex < 0 || newIndex >= examQuestions.value.length) return;
  const temp = examQuestions.value[index];
  examQuestions.value[index] = examQuestions.value[newIndex];
  examQuestions.value[newIndex] = temp;
};

// Native Drag and Drop Implementation
const draggedItemIndex = ref(null);
const handleDragStart = (index) => {
  draggedItemIndex.value = index;
};
const handleDragOver = (e) => {
  e.preventDefault();
};
const handleDrop = (index) => {
  if (draggedItemIndex.value === null) return;
  const item = examQuestions.value.splice(draggedItemIndex.value, 1)[0];
  examQuestions.value.splice(index, 0, item);
  draggedItemIndex.value = null;
};

const getDifficultyColor = (d) => {
  if (d <= 2) return 'green';
  if (d <= 4) return 'orange';
  return 'red';
};

onMounted(fetchData);
</script>

<template>
  <div class="assemble-container">
    <!-- Top Nav -->
    <div class="assemble-header">
       <div class="header-left">
          <a-button type="link" @click="router.back()">
            <LeftOutlined /> 返回列表
          </a-button>
          <a-divider type="vertical" />
          <span class="exam-title-badge" v-if="exam">{{ exam.title }}</span>
       </div>
       <div class="header-right">
          <div class="total-score-pill">
             总分: <span>{{ totalScore }}</span>
          </div>
          <a-button type="primary" :loading="saving" @click="saveAssembly">
             <SaveOutlined /> 保存配置
          </a-button>
       </div>
    </div>

    <div class="main-split">
      <!-- Left: Question Bank -->
      <div class="left-pane">
        <div class="pane-header">
           <h3 class="pane-title"><SearchOutlined /> 题库检索</h3>
           <div class="filter-group">
              <a-input v-model:value="filterText" placeholder="搜索内容..." allowClear />
              <div class="filter-row">
                 <a-select v-model:value="filterSubject" placeholder="科目" allowClear style="flex: 1">
                    <a-select-option v-for="s in distinctSubjects" :key="s" :value="s">{{ s }}</a-select-option>
                 </a-select>
                 <a-select v-model:value="filterType" placeholder="类型" allowClear style="flex: 1">
                    <a-select-option value="SINGLE">单选</a-select-option>
                    <a-select-option value="MULTI">多选</a-select-option>
                    <a-select-option value="JUDGE">判断</a-select-option>
                    <a-select-option value="SHORT">简答</a-select-option>
                 </a-select>
              </div>
           </div>
        </div>
        
        <div class="bank-list-container">
           <div v-if="loadingBank" class="loading-state"><a-spin /></div>
           <div v-else-if="filteredBank.length === 0" class="empty-state">
              <a-empty description="暂无匹配题目" />
           </div>
           <div v-else class="bank-list">
              <div v-for="q in filteredBank" :key="q.id" class="bank-item">
                 <div class="item-tags">
                   <a-tag color="blue">{{ { 'SINGLE': '单选', 'MULTI': '多选', 'JUDGE': '判断', 'SHORT': '简答' }[q.type] }}</a-tag>
                   <a-tag :color="getDifficultyColor(q.difficulty)">难度 {{ q.difficulty }}</a-tag>
                 </div>
                 <div class="item-content" v-html="q.content"></div>
                 <div class="item-actions">
                    <span class="default-score">默认 {{ q.defaultScore || 5 }} 分</span>
                    <a-button type="primary" size="small" ghost @click="addToExam(q)">
                       <PlusCircleOutlined /> 加入
                    </a-button>
                 </div>
              </div>
           </div>
        </div>
      </div>

      <!-- Right: Exam Assembly -->
      <div class="right-pane">
        <div class="pane-header">
           <h3 class="pane-title"><InfoCircleOutlined /> 试卷组成 (可拖拽排序)</h3>
           <div class="pane-subtitle">共 {{ examQuestions.length }} 道题</div>
        </div>

        <div class="assembly-area">
           <div v-if="loadingExam" class="loading-state"><a-spin /></div>
           <div v-else-if="examQuestions.length === 0" class="empty-state">
              <a-empty description="右侧列表为空，请从左侧题库挑选题目" />
           </div>
           <div v-else class="assembly-list">
              <div 
                v-for="(eq, index) in examQuestions" 
                :key="eq.id" 
                class="assembly-item"
                draggable="true"
                @dragstart="handleDragStart(index)"
                @dragover="handleDragOver"
                @drop="handleDrop(index)"
                :class="{ 'is-dragging': draggedItemIndex === index }"
              >
                 <div class="drag-handle">
                    <HolderOutlined />
                 </div>
                 <div class="q-number">#{{ index + 1 }}</div>
                 <div class="q-main">
                    <div class="q-text" v-html="eq.question.content"></div>
                    <div class="q-meta">
                       <a-tag color="cyan">{{ eq.question.subject }}</a-tag>
                       <span class="q-type">{{ { 'SINGLE': '单选题', 'MULTI': '多选题', 'JUDGE': '判断题', 'SHORT': '简答题' }[eq.question.type] }}</span>
                    </div>
                 </div>
                 <div class="q-settings">
                    <div class="score-input">
                       <span class="label">分值</span>
                       <a-input-number v-model:value="eq.score" :min="1" size="small" />
                    </div>
                    <div class="order-controls">
                       <a-button size="small" type="text" @click="move(index, -1)" :disabled="index === 0"><ArrowUpOutlined /></a-button>
                       <a-button size="small" type="text" @click="move(index, 1)" :disabled="index === examQuestions.length - 1"><ArrowDownOutlined /></a-button>
                    </div>
                    <a-popconfirm title="确定从试卷中移除吗？" @confirm="removeFromExam(eq.question.id)">
                       <a-button type="text" danger size="small"><DeleteOutlined /></a-button>
                    </a-popconfirm>
                 </div>
              </div>
           </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.assemble-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8f9fa;
}

.assemble-header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
  z-index: 10;
}

.exam-title-badge {
  font-weight: 600;
  font-size: 16px;
  background: #f0f5ff;
  color: #1890ff;
  padding: 4px 12px;
  border-radius: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.total-score-pill {
  font-size: 15px;
  color: #666;
}
.total-score-pill span {
  font-size: 20px;
  font-weight: 700;
  color: #f5222d;
  margin-left: 4px;
}

.main-split {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* Common Pane Styles */
.pane-header {
  padding: 20px;
  border-bottom: 1px solid #eee;
  background: #fff;
}
.pane-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Left Pane */
.left-pane {
  width: 35%;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.filter-row {
  display: flex;
  gap: 8px;
}

.bank-list-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.bank-item {
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  transition: all 0.2s;
}
.bank-item:hover {
  border-color: #1890ff;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.08);
}

.item-tags {
  margin-bottom: 10px;
}
.item-content {
  font-size: 14px;
  color: #333;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.item-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.default-score {
  font-size: 12px;
  color: #999;
}

/* Right Pane */
.right-pane {
  flex: 1;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.assembly-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: #fafafa;
}

.assembly-list {
  max-width: 900px;
  margin: 0 auto;
}

.assembly-item {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  padding: 16px;
  transition: all 0.3s;
  cursor: default;
}

.assembly-item.is-dragging {
  opacity: 0.5;
  border-style: dashed;
  background: #e6f7ff;
}

.drag-handle {
  color: #ccc;
  cursor: grab;
  padding: 10px;
  font-size: 18px;
}
.drag-handle:active { cursor: grabbing; }

.q-number {
  font-weight: 700;
  color: #1890ff;
  font-size: 18px;
  width: 40px;
  margin-right: 12px;
}

.q-main {
  flex: 1;
}
.q-text {
  font-size: 15px;
  margin-bottom: 8px;
}
.q-type {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

.q-settings {
  display: flex;
  align-items: center;
  gap: 20px;
}

.score-input {
  display: flex;
  align-items: center;
  gap: 8px;
}
.score-input .label { font-size: 12px; color: #666; }

.order-controls {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.loading-state, .empty-state {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
