<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message, Modal } from 'ant-design-vue';
import {
  LeftOutlined, HistoryOutlined, DiffOutlined,
  RollbackOutlined, PlusOutlined, MinusOutlined,
  SwapOutlined, ExclamationCircleOutlined
} from '@ant-design/icons-vue';
import {
  getExam, getExamVersions, diffExamVersions, rollbackExamVersion
} from '@/api';

const route = useRoute();
const router = useRouter();
const examId = route.params.id;

const exam = ref(null);
const versions = ref([]);
const loadingVersions = ref(false);
const loadingDiff = ref(false);
const leftVersion = ref(null);
const rightVersion = ref(null);
const diffResult = ref(null);
const rollbackLoading = ref(false);

const fetchVersions = async () => {
  loadingVersions.value = true;
  try {
    const res = await getExamVersions(examId);
    versions.value = res.data;
    if (res.data.length >= 2) {
      rightVersion.value = res.data[0].versionNumber;
      leftVersion.value = res.data[1].versionNumber;
    } else if (res.data.length === 1) {
      leftVersion.value = res.data[0].versionNumber;
    }
  } catch (e) {
    message.error('获取版本列表失败');
  } finally {
    loadingVersions.value = false;
  }
};

const fetchExam = async () => {
  try {
    const res = await getExam(examId);
    exam.value = res.data;
  } catch (e) {
    console.error('获取考试信息失败', e);
  }
};

const fetchDiff = async () => {
  if (!leftVersion.value || !rightVersion.value) {
    diffResult.value = null;
    return;
  }
  loadingDiff.value = true;
  try {
    const res = await diffExamVersions(examId, leftVersion.value, rightVersion.value);
    diffResult.value = res.data;
  } catch (e) {
    message.error('获取版本对比失败');
  } finally {
    loadingDiff.value = false;
  }
};

watch([leftVersion, rightVersion], () => {
  if (leftVersion.value && rightVersion.value) {
    fetchDiff();
  }
});

const getChangeTypeLabel = (type) => {
  const map = {
    'ADDED': '新增',
    'REMOVED': '删除',
    'SCORE_CHANGED': '分值变更',
    'SEQUENCE_CHANGED': '顺序调整'
  };
  return map[type] || type;
};

const getChangeTypeColor = (type) => {
  const map = {
    'ADDED': 'green',
    'REMOVED': 'red',
    'SCORE_CHANGED': 'orange',
    'SEQUENCE_CHANGED': 'blue'
  };
  return map[type] || 'default';
};

const getQuestionTypeLabel = (type) => {
  const map = {
    'SINGLE': '单选',
    'MULTI': '多选',
    'JUDGE': '判断',
    'SHORT': '简答'
  };
  return map[type] || type;
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const handleRollback = () => {
  if (!leftVersion.value) return;

  Modal.confirm({
    title: '确认回滚',
    icon: ExclamationCircleOutlined,
    content: `确定要回滚到版本 v${leftVersion.value} 吗？回滚后将生成新的版本记录。`,
    okText: '确认回滚',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      rollbackLoading.value = true;
      try {
        await rollbackExamVersion(examId, leftVersion.value);
        message.success('回滚成功');
        await fetchVersions();
      } catch (e) {
        message.error('回滚失败');
      } finally {
        rollbackLoading.value = false;
      }
    }
  });
};

const goToAssemble = () => {
  router.push(`/exam/${examId}/assemble`);
};

const handleVersionClick = (v) => {
  if (leftVersion.value === v.versionNumber) {
    leftVersion.value = rightVersion.value;
    rightVersion.value = null;
  } else if (rightVersion.value === v.versionNumber) {
    rightVersion.value = null;
  } else if (!leftVersion.value) {
    leftVersion.value = v.versionNumber;
  } else if (!rightVersion.value) {
    if (v.versionNumber > leftVersion.value) {
      rightVersion.value = v.versionNumber;
    } else {
      rightVersion.value = leftVersion.value;
      leftVersion.value = v.versionNumber;
    }
  } else {
    leftVersion.value = rightVersion.value;
    rightVersion.value = v.versionNumber;
  }
};

onMounted(() => {
  fetchExam();
  fetchVersions();
});
</script>

<template>
  <div class="versions-container">
    <div class="versions-header">
      <div class="header-left">
        <a-button type="link" @click="router.back()">
          <LeftOutlined /> 返回
        </a-button>
        <a-divider type="vertical" />
        <span class="page-title"><HistoryOutlined /> 试卷版本管理</span>
        <span class="exam-title" v-if="exam">{{ exam.title }}</span>
      </div>
      <div class="header-right">
        <a-button @click="goToAssemble">
          前往组卷
        </a-button>
      </div>
    </div>

    <div class="versions-body">
      <div class="version-list-pane">
        <div class="pane-title">版本列表</div>
        <div class="pane-subtitle">点击选择要对比的版本</div>

        <div v-if="loadingVersions" class="loading-wrap">
          <a-spin />
        </div>

        <div v-else-if="versions.length === 0" class="empty-wrap">
          <a-empty description="暂无版本记录" />
        </div>

        <div v-else class="version-list">
          <div
            v-for="v in versions"
            :key="v.id"
            class="version-item"
            :class="{
              'is-left': leftVersion === v.versionNumber,
              'is-right': rightVersion === v.versionNumber
            }"
            @click="handleVersionClick(v)"
          >
            <div class="version-badge">
              <span class="version-tag">v{{ v.versionNumber }}</span>
              <span v-if="leftVersion === v.versionNumber" class="badge-flag left-flag">A</span>
              <span v-if="rightVersion === v.versionNumber" class="badge-flag right-flag">B</span>
            </div>
            <div class="version-desc">
              {{ v.description || '保存组卷配置' }}
            </div>
            <div class="version-time">
              {{ formatDate(v.createdAt) }}
            </div>
          </div>
        </div>

        <div v-if="versions.length > 0" class="version-tip">
          <a-alert type="info" show-icon>
            <template #message>
              共 {{ versions.length }} 个版本，最多保留 20 个
            </template>
          </a-alert>
        </div>
      </div>

      <div class="diff-pane">
        <div class="diff-header">
          <div class="diff-title"><DiffOutlined /> 版本对比</div>
          <div class="diff-stats" v-if="diffResult">
            <a-tag color="green">新增 {{ diffResult.addedCount }}</a-tag>
            <a-tag color="red">删除 {{ diffResult.removedCount }}</a-tag>
            <a-tag color="orange">分值变更 {{ diffResult.scoreChangedCount }}</a-tag>
            <a-tag color="blue">顺序调整 {{ diffResult.sequenceChangedCount }}</a-tag>
          </div>
          <div class="diff-actions">
            <a-button
              type="primary"
              danger
              :disabled="!leftVersion"
              :loading="rollbackLoading"
              @click="handleRollback"
            >
              <RollbackOutlined /> 回滚到左侧版本
            </a-button>
          </div>
        </div>

        <div v-if="!leftVersion || !rightVersion" class="diff-empty">
          <a-empty description="请从左侧选择两个版本进行对比" />
        </div>

        <div v-else-if="loadingDiff" class="diff-loading">
          <a-spin size="large" />
        </div>

        <div v-else class="diff-content">
          <div class="diff-columns">
            <div class="diff-column left-col">
              <div class="col-header">
                <span class="col-badge left-badge">A</span>
                版本 v{{ leftVersion }}
              </div>
            </div>
            <div class="diff-column right-col">
              <div class="col-header">
                <span class="col-badge right-badge">B</span>
                版本 v{{ rightVersion }}
              </div>
            </div>
          </div>

          <div class="diff-list">
            <div
              v-for="(item, idx) in diffResult?.diffItems || []"
              :key="idx"
              class="diff-row"
              :class="`change-${item.changeType.toLowerCase()}`"
            >
              <div class="diff-cell left-cell">
                <div v-if="item.leftQuestion" class="question-card">
                  <div class="q-seq">#{{ item.leftQuestion.sequence }}</div>
                  <div class="q-body">
                    <div class="q-content" v-html="item.leftQuestion.content"></div>
                    <div class="q-meta">
                      <a-tag color="cyan">{{ item.leftQuestion.subject }}</a-tag>
                      <span class="q-type">{{ getQuestionTypeLabel(item.leftQuestion.type) }}</span>
                      <span class="q-score">{{ item.leftQuestion.score }} 分</span>
                    </div>
                  </div>
                </div>
                <div v-else class="empty-cell">—</div>
              </div>

              <div class="diff-arrow">
                <div class="change-indicator" :class="item.changeType.toLowerCase()">
                  <span class="change-icon">
                    <PlusOutlined v-if="item.changeType === 'ADDED'" />
                    <MinusOutlined v-else-if="item.changeType === 'REMOVED'" />
                    <SwapOutlined v-else-if="item.changeType === 'SCORE_CHANGED' || item.changeType === 'SEQUENCE_CHANGED'" />
                  </span>
                  <span class="change-label">{{ getChangeTypeLabel(item.changeType) }}</span>
                  <span v-if="item.changeType === 'SCORE_CHANGED'" class="change-detail">
                    {{ item.scoreChange > 0 ? '+' : '' }}{{ item.scoreChange }} 分
                  </span>
                  <span v-if="item.changeType === 'SEQUENCE_CHANGED'" class="change-detail">
                    {{ item.sequenceChange > 0 ? '↓' : '↑' }}{{ Math.abs(item.sequenceChange) }}
                  </span>
                </div>
              </div>

              <div class="diff-cell right-cell">
                <div v-if="item.rightQuestion" class="question-card">
                  <div class="q-seq">#{{ item.rightQuestion.sequence }}</div>
                  <div class="q-body">
                    <div class="q-content" v-html="item.rightQuestion.content"></div>
                    <div class="q-meta">
                      <a-tag color="cyan">{{ item.rightQuestion.subject }}</a-tag>
                      <span class="q-type">{{ getQuestionTypeLabel(item.rightQuestion.type) }}</span>
                      <span class="q-score" :class="{ 'score-changed': item.changeType === 'SCORE_CHANGED' }">
                        {{ item.rightQuestion.score }} 分
                      </span>
                    </div>
                  </div>
                </div>
                <div v-else class="empty-cell">—</div>
              </div>
            </div>
          </div>

          <div v-if="diffResult?.diffItems?.length === 0" class="no-changes">
            <a-result status="success" title="两个版本完全相同" sub-title="没有发现任何差异" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.versions-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

.versions-header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.exam-title {
  font-size: 14px;
  color: #666;
  background: #f0f5ff;
  padding: 4px 12px;
  border-radius: 4px;
}

.versions-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  padding: 16px;
  gap: 16px;
}

.version-list-pane {
  width: 280px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}

.pane-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.pane-subtitle {
  font-size: 12px;
  color: #999;
  margin-bottom: 16px;
}

.loading-wrap, .empty-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.version-list {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 16px;
}

.version-item {
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;
}

.version-item:hover {
  border-color: #1890ff;
  background: #f0f7ff;
}

.version-item.is-left {
  border-color: #1890ff;
  background: #e6f7ff;
}

.version-item.is-right {
  border-color: #52c41a;
  background: #f6ffed;
}

.version-item.is-left.is-right {
  border-color: #1890ff;
  background: linear-gradient(90deg, #e6f7ff 0%, #f6ffed 100%);
}

.version-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.version-tag {
  font-weight: 700;
  font-size: 14px;
  color: #333;
}

.badge-flag {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
}

.left-flag {
  background: #1890ff;
}

.right-flag {
  background: #52c41a;
}

.version-desc {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
  line-height: 1.4;
}

.version-time {
  font-size: 12px;
  color: #999;
}

.version-tip {
  margin-top: auto;
}

.diff-pane {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}

.diff-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.diff-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.diff-stats {
  display: flex;
  gap: 8px;
  flex: 1;
}

.diff-actions {
  margin-left: auto;
}

.diff-empty, .diff-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.diff-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.diff-columns {
  display: flex;
  margin-bottom: 12px;
  position: sticky;
  top: -16px;
  background: #fff;
  z-index: 5;
  padding-top: 4px;
  padding-bottom: 8px;
}

.diff-column {
  flex: 1;
  padding: 8px 12px;
}

.left-col {
  padding-right: 40px;
}

.right-col {
  padding-left: 40px;
}

.col-header {
  font-size: 14px;
  font-weight: 500;
  color: #666;
  display: flex;
  align-items: center;
  gap: 8px;
}

.col-badge {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
}

.left-badge {
  background: #1890ff;
}

.right-badge {
  background: #52c41a;
}

.diff-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.diff-row {
  display: flex;
  align-items: stretch;
  gap: 0;
  border-radius: 8px;
  overflow: hidden;
}

.diff-cell {
  flex: 1;
  padding: 12px;
  background: #fafafa;
}

.left-cell {
  border-right: none;
  border-radius: 8px 0 0 8px;
}

.right-cell {
  border-left: none;
  border-radius: 0 8px 8px 0;
}

.diff-row.change-added .left-cell {
  background: #fff7f7;
}

.diff-row.change-added .right-cell {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-left: none;
}

.diff-row.change-removed .left-cell {
  background: #fff1f0;
  border: 1px solid #ffccc7;
  border-right: none;
}

.diff-row.change-removed .right-cell {
  background: #f7f7f7;
}

.diff-row.change-score_changed .left-cell,
.diff-row.change-score_changed .right-cell {
  background: #fffbe6;
  border: 1px solid #ffe58f;
}

.diff-row.change-score_changed .left-cell {
  border-right: none;
}

.diff-row.change-score_changed .right-cell {
  border-left: none;
}

.diff-row.change-sequence_changed .left-cell,
.diff-row.change-sequence_changed .right-cell {
  background: #e6f7ff;
  border: 1px solid #91d5ff;
}

.diff-row.change-sequence_changed .left-cell {
  border-right: none;
}

.diff-row.change-sequence_changed .right-cell {
  border-left: none;
}

.diff-arrow {
  width: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.change-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.1);
}

.change-indicator.added {
  color: #52c41a;
}

.change-indicator.removed {
  color: #ff4d4f;
}

.change-indicator.score_changed {
  color: #fa8c16;
}

.change-indicator.sequence_changed {
  color: #1890ff;
}

.change-icon {
  font-size: 18px;
}

.change-label {
  font-size: 12px;
  font-weight: 500;
}

.change-detail {
  font-size: 11px;
  color: #999;
}

.question-card {
  display: flex;
  gap: 12px;
}

.q-seq {
  font-weight: 700;
  color: #1890ff;
  font-size: 14px;
  width: 36px;
  flex-shrink: 0;
}

.q-body {
  flex: 1;
  min-width: 0;
}

.q-content {
  font-size: 13px;
  color: #333;
  line-height: 1.5;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.q-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.q-type {
  font-size: 12px;
  color: #999;
}

.q-score {
  font-size: 12px;
  font-weight: 600;
  color: #f5222d;
  background: #fff1f0;
  padding: 2px 8px;
  border-radius: 4px;
}

.q-score.score-changed {
  background: #fff7e6;
  color: #fa8c16;
}

.empty-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  color: #ccc;
  font-size: 20px;
}

.no-changes {
  padding: 60px 0;
}
</style>
