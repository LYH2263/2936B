<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import {
  getTemplates, searchTemplates, getTemplateQuestions, deleteTemplate,
  createExamFromTemplate, getPendingTemplates, reviewTemplate, getMyTemplates
} from '@/api';
import { message, Modal } from 'ant-design-vue';
import {
  SearchOutlined, AppstoreOutlined, EyeOutlined, PlusOutlined,
  DeleteOutlined, CopyOutlined, TagOutlined, ClockCircleOutlined,
  CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined,
  GlobalOutlined, LockOutlined, UserOutlined
} from '@ant-design/icons-vue';

const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const templates = ref([]);
const searchText = ref('');
const courseFilter = ref(null);
const tabKey = ref('all');

const previewVisible = ref(false);
const previewTemplate = ref(null);
const previewQuestions = ref([]);
const previewLoading = ref(false);

const createWizardVisible = ref(false);
const createWizardTemplate = ref(null);
const createWizardTitle = ref('');
const createWizardLoading = ref(false);

const pendingTemplates = ref([]);
const adminTabKey = ref('all');

const fetchTemplates = async () => {
  loading.value = true;
  try {
    if (searchText.value || courseFilter.value) {
      const res = await searchTemplates({
        course: courseFilter.value || undefined,
        keyword: searchText.value || undefined
      });
      templates.value = res.data;
    } else {
      const res = await getTemplates();
      templates.value = res.data;
    }
  } catch (e) {
    message.error('加载模板失败');
  } finally {
    loading.value = false;
  }
};

const fetchPendingTemplates = async () => {
  if (!authStore.isAdmin) return;
  try {
    const res = await getPendingTemplates();
    pendingTemplates.value = res.data;
  } catch (e) {
    console.error('Failed to fetch pending templates');
  }
};

const distinctCourses = computed(() => {
  const courses = new Set(templates.value.map(t => t.course).filter(c => c));
  return Array.from(courses);
});

const filteredTemplates = computed(() => {
  let list = templates.value;
  if (tabKey.value === 'public') {
    list = list.filter(t => t.visibility === 'PUBLIC');
  } else if (tabKey.value === 'private') {
    list = list.filter(t => t.visibility === 'PRIVATE');
  } else if (tabKey.value === 'my') {
    list = list.filter(t => t.creator?.id === authStore.user?.id);
  }
  return list;
});

const showPreview = async (template) => {
  previewTemplate.value = template;
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const res = await getTemplateQuestions(template.id);
    previewQuestions.value = res.data;
  } catch (e) {
    message.error('加载模板题目失败');
  } finally {
    previewLoading.value = false;
  }
};

const startCreateWizard = (template) => {
  createWizardTemplate.value = template;
  createWizardTitle.value = template.name + '（副本）';
  createWizardVisible.value = true;
};

const handleCreateFromTemplate = async () => {
  if (!createWizardTitle.value.trim()) {
    message.warning('请输入试卷名称');
    return;
  }
  createWizardLoading.value = true;
  try {
    const res = await createExamFromTemplate(createWizardTemplate.value.id, {
      title: createWizardTitle.value
    });
    message.success('从模板创建试卷成功！');
    createWizardVisible.value = false;
    router.push(`/exam/${res.data.id}/assemble`);
  } catch (e) {
    message.error('创建失败');
  } finally {
    createWizardLoading.value = false;
  }
};

const handleDelete = async (templateId) => {
  Modal.confirm({
    title: '确认删除',
    content: '删除模板不会影响已生成的考试，确认删除？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteTemplate(templateId);
        message.success('模板已删除');
        fetchTemplates();
      } catch (e) {
        message.error('删除失败');
      }
    }
  });
};

const handleReview = async (templateId, status) => {
  try {
    await reviewTemplate(templateId, { reviewStatus: status });
    message.success(status === 'APPROVED' ? '已审核通过' : '已拒绝');
    fetchPendingTemplates();
    fetchTemplates();
  } catch (e) {
    message.error('审核操作失败');
  }
};

const getTotalScore = (questions) => {
  return questions.reduce((sum, q) => sum + (q.score || 0), 0);
};

const getVisibilityTag = (template) => {
  if (template.visibility === 'PUBLIC') {
    return { color: 'blue', text: '公开', icon: GlobalOutlined };
  }
  return { color: 'default', text: '私有', icon: LockOutlined };
};

const getReviewStatusTag = (status) => {
  switch (status) {
    case 'APPROVED': return { color: 'green', text: '已通过' };
    case 'PENDING': return { color: 'orange', text: '待审核' };
    case 'REJECTED': return { color: 'red', text: '已拒绝' };
    default: return { color: 'default', text: status };
  }
};

onMounted(() => {
  fetchTemplates();
  fetchPendingTemplates();
});
</script>

<template>
  <div class="templates-page">
    <a-page-header title="试卷模板库" sub-title="快速复用试卷结构，一键生成新试卷" class="page-header">
      <template #extra>
        <a-button v-if="authStore.isTeacher || authStore.isAdmin" @click="router.push('/dashboard')">
          返回首页
        </a-button>
      </template>
    </a-page-header>

    <div class="templates-content">
      <div class="search-bar">
        <a-input
          v-model:value="searchText"
          placeholder="搜索模板名称或标签"
          style="width: 280px"
          allowClear
          @pressEnter="fetchTemplates"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-select
          v-model:value="courseFilter"
          placeholder="按科目筛选"
          style="width: 160px"
          allowClear
          @change="fetchTemplates"
        >
          <a-select-option v-for="c in distinctCourses" :key="c" :value="c">{{ c }}</a-select-option>
        </a-select>
        <a-button type="primary" @click="fetchTemplates">
          <SearchOutlined /> 搜索
        </a-button>
      </div>

      <a-tabs v-model:activeKey="tabKey">
        <a-tab-pane key="all" tab="全部模板" />
        <a-tab-pane key="public" tab="公共模板" />
        <a-tab-pane key="private" tab="私有模板" />
        <a-tab-pane key="my" tab="我创建的" />
        <a-tab-pane v-if="authStore.isAdmin && pendingTemplates.length > 0" key="pending" :tab="`待审核 (${pendingTemplates.length})`" />
      </a-tabs>

      <div v-if="tabKey === 'pending' && authStore.isAdmin">
        <a-list
          :loading="loading"
          :grid="{ gutter: 24, xs: 1, sm: 2, md: 3, lg: 3, xl: 4, xxl: 4 }"
          :dataSource="pendingTemplates"
        >
          <template #renderItem="{ item }">
            <a-list-item>
              <a-card hoverable>
                <a-card-meta :title="item.name">
                  <template #description>
                    <div style="margin-bottom: 8px;">{{ item.description || '暂无描述' }}</div>
                    <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px;">
                      <a-tag v-if="item.course" color="blue">{{ item.course }}</a-tag>
                      <a-tag color="orange"><ExclamationCircleOutlined /> 待审核</a-tag>
                    </div>
                    <div style="font-size: 12px; color: #999;">
                      <UserOutlined /> {{ item.creator?.fullName || item.creator?.username }}
                    </div>
                  </template>
                </a-card-meta>
                <template #actions>
                  <a-button type="link" size="small" @click="handleReview(item.id, 'APPROVED')">
                    <CheckCircleOutlined /> 通过
                  </a-button>
                  <a-button type="link" size="small" danger @click="handleReview(item.id, 'REJECTED')">
                    <CloseCircleOutlined /> 拒绝
                  </a-button>
                </template>
              </a-card>
            </a-list-item>
          </template>
        </a-list>
        <a-empty v-if="pendingTemplates.length === 0" description="暂无待审核模板" />
      </div>

      <div v-else>
        <a-list
          :loading="loading"
          :grid="{ gutter: 24, xs: 1, sm: 2, md: 3, lg: 3, xl: 4, xxl: 4 }"
          :dataSource="filteredTemplates"
        >
          <template #renderItem="{ item }">
            <a-list-item>
              <a-card hoverable class="template-card">
                <template #cover>
                  <div class="card-cover" :style="{ background: getCoverGradient(item.course) }">
                    <AppstoreOutlined style="font-size: 48px; color: rgba(255,255,255,0.6);" />
                    <div class="cover-title">{{ item.name }}</div>
                  </div>
                </template>
                <a-card-meta>
                  <template #description>
                    <div class="template-meta">
                      <div class="template-desc">{{ item.description || '暂无描述' }}</div>
                      <div style="display: flex; gap: 6px; flex-wrap: wrap; margin: 8px 0;">
                        <a-tag v-if="item.course" color="blue" style="margin: 0;">{{ item.course }}</a-tag>
                        <a-tag :color="getVisibilityTag(item).color" style="margin: 0;">
                          <component :is="getVisibilityTag(item).icon" /> {{ getVisibilityTag(item).text }}
                        </a-tag>
                        <a-tag v-if="item.reviewStatus !== 'APPROVED'" :color="getReviewStatusTag(item.reviewStatus).color" style="margin: 0;">
                          {{ getReviewStatusTag(item.reviewStatus).text }}
                        </a-tag>
                      </div>
                      <div v-if="item.tags" style="display: flex; gap: 4px; flex-wrap: wrap; margin-bottom: 8px;">
                        <a-tag v-for="tag in item.tags.split(',').filter(t => t.trim())" :key="tag" style="margin: 0; font-size: 11px;">
                          <TagOutlined /> {{ tag.trim() }}
                        </a-tag>
                      </div>
                      <div style="display: flex; justify-content: space-between; color: #999; font-size: 12px;">
                        <span><ClockCircleOutlined /> {{ item.duration }} 分钟</span>
                        <span><UserOutlined /> {{ item.creator?.fullName || item.creator?.username }}</span>
                      </div>
                    </div>
                  </template>
                </a-card-meta>
                <template #actions>
                  <a-tooltip title="预览"><EyeOutlined @click="showPreview(item)" /></a-tooltip>
                  <a-tooltip title="从模板创建"><CopyOutlined @click="startCreateWizard(item)" /></a-tooltip>
                  <a-tooltip v-if="item.creator?.id === authStore.user?.id || authStore.isAdmin" title="删除">
                    <DeleteOutlined @click="handleDelete(item.id)" />
                  </a-tooltip>
                </template>
              </a-card>
            </a-list-item>
          </template>
        </a-list>
        <a-empty v-if="filteredTemplates.length === 0 && !loading" description="暂无模板" />
      </div>
    </div>

    <a-drawer
      v-model:open="previewVisible"
      :title="previewTemplate?.name || '模板预览'"
      width="640"
      placement="right"
    >
      <a-skeleton :loading="previewLoading" active :paragraph="{ rows: 6 }">
        <template v-if="previewTemplate">
          <a-descriptions bordered :column="2" size="small" style="margin-bottom: 24px;">
            <a-descriptions-item label="模板名称" :span="2">{{ previewTemplate.name }}</a-descriptions-item>
            <a-descriptions-item label="科目">{{ previewTemplate.course || '未指定' }}</a-descriptions-item>
            <a-descriptions-item label="时长">{{ previewTemplate.duration }} 分钟</a-descriptions-item>
            <a-descriptions-item label="可见性">
              <a-tag :color="getVisibilityTag(previewTemplate).color">
                {{ getVisibilityTag(previewTemplate).text }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="总分">{{ getTotalScore(previewQuestions) }} 分</a-descriptions-item>
            <a-descriptions-item label="标签" :span="2">
              <a-tag v-for="tag in (previewTemplate.tags || '').split(',').filter(t => t.trim())" :key="tag">
                {{ tag.trim() }}
              </a-tag>
              <span v-if="!previewTemplate.tags">无</span>
            </a-descriptions-item>
            <a-descriptions-item label="描述" :span="2">
              {{ previewTemplate.description || '暂无描述' }}
            </a-descriptions-item>
          </a-descriptions>

          <a-divider>题目列表 ({{ previewQuestions.length }} 题)</a-divider>
          <a-table
            :dataSource="previewQuestions"
            :pagination="false"
            size="small"
            :columns="[
              { title: '序号', dataIndex: 'sequence', width: 60, key: 'seq' },
              { title: '题型', dataIndex: ['question', 'type'], width: 80, key: 'type',
                customRender: ({text}) => ({'SINGLE':'单选','MULTI':'多选','JUDGE':'判断','SHORT':'简答'}[text] || text) },
              { title: '题干', dataIndex: ['question', 'content'], key: 'content',
                customRender: ({text}) => text?.length > 50 ? text.substring(0, 50) + '...' : text },
              { title: '分值', dataIndex: 'score', width: 60, key: 'score' }
            ]"
            rowKey="id"
          />

          <div style="margin-top: 24px; text-align: center;">
            <a-button type="primary" size="large" @click="startCreateWizard(previewTemplate); previewVisible = false">
              <CopyOutlined /> 使用此模板创建试卷
            </a-button>
          </div>
        </template>
      </a-skeleton>
    </a-drawer>

    <a-modal
      v-model:open="createWizardVisible"
      title="从模板创建试卷"
      :confirmLoading="createWizardLoading"
      @ok="handleCreateFromTemplate"
      okText="创建"
      cancelText="取消"
    >
      <div style="margin-bottom: 16px;">
        <div style="margin-bottom: 8px;">
          <strong>来源模板：</strong>{{ createWizardTemplate?.name }}
        </div>
        <div style="margin-bottom: 8px;">
          <strong>科目：</strong>{{ createWizardTemplate?.course || '未指定' }}
        </div>
        <div>
          <strong>时长：</strong>{{ createWizardTemplate?.duration }} 分钟
        </div>
      </div>
      <a-form layout="vertical">
        <a-form-item label="新试卷名称" required>
          <a-input v-model:value="createWizardTitle" placeholder="请输入新试卷的名称" />
        </a-form-item>
      </a-form>
      <a-alert
        message="将从模板复制题目引用与分值结构，生成一张草稿试卷"
        type="info"
        showIcon
        style="margin-top: 8px;"
      />
    </a-modal>
  </div>
</template>

<script>
const coverGradients = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
];

export default {
  methods: {
    getCoverGradient(course) {
      let idx = 0;
      if (course) {
        for (let i = 0; i < course.length; i++) idx += course.charCodeAt(i);
        idx = idx % coverGradients.length;
      }
      return coverGradients[idx];
    }
  }
};
</script>

<style scoped>
.templates-page {
  min-height: 100vh;
  background: #f0f2f5;
}
.page-header {
  background: white;
  border-bottom: 1px solid #f0f0f0;
  padding: 16px 32px;
}
.templates-content {
  padding: 24px 32px;
  max-width: 1400px;
  margin: 0 auto;
}
.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
.template-card {
  height: 100%;
  transition: transform 0.2s, box-shadow 0.2s;
}
.template-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}
.card-cover {
  height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}
.cover-title {
  color: white;
  font-size: 14px;
  font-weight: 600;
  text-shadow: 0 1px 3px rgba(0,0,0,0.3);
  margin-top: 8px;
  padding: 0 16px;
  text-align: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.template-meta {
  min-height: 80px;
}
.template-desc {
  height: 36px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
