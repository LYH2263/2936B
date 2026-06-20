<script setup>
import { ref, computed, onMounted, watch, h, shallowRef } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useConfigStore } from '@/stores/config';
import { getExams, getMySubmissions, publishExam, deleteExam, getExamSubmissions, updateProfile, getSystemConfig, getStudentStats, getTeacherStats } from '@/api';
import { message } from 'ant-design-vue';
import { 
  UserOutlined, LogoutOutlined, PlusOutlined, UnorderedListOutlined, 
  BankOutlined, ProfileOutlined, BookOutlined, TeamOutlined, FileTextOutlined,
  SettingOutlined, DashboardOutlined, TrophyOutlined, HourglassOutlined, ReadOutlined,
  SearchOutlined, BarsOutlined
} from '@ant-design/icons-vue';
import CreateExamModal from '@/components/CreateExamModal.vue';
import AddQuestionModal from '@/components/AddQuestionModal.vue';
import QuestionBankModal from '@/components/QuestionBankModal.vue';
import GradeSubmissionModal from '@/components/GradeSubmissionModal.vue';
import AutoGenerateModal from '@/components/AutoGenerateModal.vue';
import ExamEditorModal from '@/components/ExamEditorModal.vue';
import UserManagementView from '@/views/UserManagementView.vue';
import { getNotifications, markNotificationRead } from '@/api';
import { notification, Button as AButton } from 'ant-design-vue';
import SystemConfigView from '@/views/SystemConfigView.vue';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const configStore = useConfigStore();
const activeKey = ref([route.query.tab || 'overview']);
const activeTab = ref(route.query.tab || 'overview'); 

const collapsed = ref(false);
const stats = ref({
  totalExams: 0,
  avgScore: 0,
  passRate: 0,
  pendingExamsCount: 0,
  recentSubmissions: []
});

const teacherStats = ref({
  totalExams: 0,
  totalSubmissions: 0,
  activeExams: 0,
  totalStudents: 0,
  recentSubmissions: []
});

watch(() => route.query.tab, (newTab) => {
  if (newTab) {
    activeTab.value = newTab;
    activeKey.value = [newTab];
  }
});

const loading = ref(false); // Added loading ref

const checkNotifications = async () => {
  try {
    const res = await getNotifications();
    const list = res.data;
    list.forEach(item => {
      notification.info({
        message: item.title,
        description: item.content,
        duration: 0, // No auto-close
        btn: () => h(
          AButton,
          {
            type: 'primary',
            size: 'small',
            onClick: () => {
              markNotificationRead(item.id);
              notification.close(item.id);
              notification.close(item.id);
              if (item.type === 'EXAM_PUBLISHED') {
                activeTab.value = 'hall';
                router.push({ path: '/dashboard', query: { tab: 'hall' } });
              }
            },
          },
          '查看详细'
        ),
        key: item.id,
        onClose: () => markNotificationRead(item.id)
      });
    });
  } catch (e) {
    console.error('Failed to fetch notifications', e);
  }
};

const hallViewMode = ref('card'); // 'card' or 'table'
const exams = shallowRef([]);
const submissions = shallowRef([]);
const createExamVisible = ref(false);
const addQuestionVisible = ref(false);
const questionBankVisible = ref(false);
const analysisVisible = ref(false);
const detailVisible = ref(false);
const submissionListVisible = ref(false);
const autoGenerateVisible = ref(false);
const editorVisible = ref(false);
const publishVisible = ref(false);
const gradeVisible = ref(false);
const currentExamId = ref(null);
const currentExam = ref(null);
const currentSubmissionId = ref(null);
const examSubmissions = shallowRef([]);
// Removed local sysConfig as it's now in configStore

// Filters
const searchText = ref('');
const courseFilter = ref(null);
const statusFilter = ref(null);

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getExams();
    exams.value = res.data;
    
    const subRes = await getMySubmissions();
    submissions.value = subRes.data;

    if (authStore.user?.role === 'STUDENT') {
      const statsRes = await getStudentStats();
      stats.value = statsRes.data;
      checkNotifications();
    } else if (authStore.isTeacher) {
      const tStatsRes = await getTeacherStats();
      teacherStats.value = tStatsRes.data;
    }
  } catch (e) {
    console.error('Failed to fetch data', e);
  } finally {
    loading.value = false;
  }
};

const getExamStatus = (exam) => {
  const now = new Date();
  const start = exam.startTime ? new Date(exam.startTime) : null;
  const end = exam.endTime ? new Date(exam.endTime) : null;

  if (start && now < start) return { text: '未开始', color: 'blue' };
  if (end && now > end) return { text: '已结束', color: 'red' };
  return { text: '进行中', color: 'green' };
};

const handleMenuClick = ({ key }) => {
  activeTab.value = key;
  router.push({ path: '/dashboard', query: { tab: key } });
};

const filteredExams = computed(() => {
  return exams.value.filter(exam => {
    // Search by title
    if (searchText.value && !exam.title.includes(searchText.value)) return false;
    // Filter by course
    if (courseFilter.value && exam.course !== courseFilter.value) return false;
    
    // Filter by status (Derived)
    if (statusFilter.value) {
       const now = new Date();
       const start = exam.startTime ? new Date(exam.startTime) : null;
       const end = exam.endTime ? new Date(exam.endTime) : null;
       
       if (statusFilter.value === 'NOT_STARTED') {
         if (!start || now >= start) return false;
       } else if (statusFilter.value === 'ENDED') {
         if (!end || now <= end) return false;
       } else if (statusFilter.value === 'IN_PROGRESS') {
         if ((start && now < start) || (end && now > end)) return false;
       }
    }
    return true;
  });
});

const distinctCourses = computed(() => {
   const courses = new Set(exams.value.map(e => e.course).filter(c => c));
   return Array.from(courses);
});

const showDetail = (exam) => {
  router.push(`/exam/${exam.id}/detail`);
};

const showCreateExam = () => {
  createExamVisible.value = true;
};

const showAddQuestion = (examId) => {
  router.push(`/exam/${examId}/assemble`);
};

const showQuestionBank = (examId) => {
  currentExamId.value = examId;
  questionBankVisible.value = true;
};

const showAnalysis = (examId) => {
  router.push(`/exam/${examId}/analysis`);
};

const showEditor = (examId) => {
  router.push(`/exam/${examId}/assemble`);
};

const showSubmissions = async (examId) => {
  currentExamId.value = examId;
  submissionListVisible.value = true;
  try {
    const res = await getExamSubmissions(examId);
    examSubmissions.value = res.data;
  } catch (e) {
    message.error('加载记录失败');
  }
};

const showGradeModal = (submissionId) => {
  currentSubmissionId.value = submissionId;
  gradeVisible.value = true;
};

const onGraded = () => {
  if (currentExamId.value) {
    showSubmissions(currentExamId.value);
  }
  fetchData(); // Update global scores if student view is affected (though unlikely for teacher)
};

const handleAutoGenerateRequest = () => {
  autoGenerateVisible.value = true;
};

const handleAutoGenerateSuccess = () => {
  fetchData();
  autoGenerateVisible.value = false;
  // Automatically show assemble page after generation for "preview and adjust"
  router.push(`/exam/${currentExamId.value}/assemble`);
};

const enterExam = (examId) => {
  router.push(`/exam/${examId}`);
};

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};

const handlePublish = (exam) => {
  router.push(`/exam/${exam.id}/publish`);
};

const handleDelete = async (examId) => {
  try {
    await deleteExam(examId);
    message.success('删除成功');
    fetchData();
  } catch (e) {
    message.error('删除失败');
  }
};

// Profile editing
const isEditingProfile = ref(false);
const updatingProfile = ref(false);
const profileForm = ref({
  fullName: '',
  password: ''
});

const startEditProfile = () => {
  profileForm.value = {
    fullName: authStore.user?.fullName || '',
    password: ''
  };
  isEditingProfile.value = true;
};

const cancelEditProfile = () => {
  isEditingProfile.value = false;
};

const handleUpdateProfile = async () => {
  updatingProfile.value = true;
  try {
    const res = await updateProfile(profileForm.value);
    // Update store
    authStore.user = res.data;
    localStorage.setItem('user', JSON.stringify(res.data));
    message.success('资料已更新');
    isEditingProfile.value = false;
  } catch (e) {
    message.error('资料更新失败');
  } finally {
    updatingProfile.value = false;
  }
};

onMounted(() => {
  fetchData();
});


// Re-added userInitial computed property as it was in the original code and not explicitly removed by the instruction.
const userInitial = computed(() => {
  return authStore.user?.fullName?.[0] || authStore.user?.username?.[0] || 'U';
});
</script>

<template>
  <a-layout class="layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible class="sider">
      <div class="logo-container">
        <img v-if="configStore.logoUrl" :src="configStore.logoUrl" alt="logo" />
        <span v-if="!collapsed">{{ configStore.sysName }}</span>
      </div>
      <a-menu v-model:selectedKeys="activeKey" theme="dark" mode="inline" @click="handleMenuClick">
        <a-menu-item key="overview" v-if="authStore.isTeacher || authStore.user?.role === 'STUDENT'">
          <DashboardOutlined />
          <span>概览</span>
        </a-menu-item>
        <a-menu-item key="manage" v-if="authStore.isTeacher">
          <FileTextOutlined />
          <span>试卷管理</span>
        </a-menu-item>
        <a-menu-item key="hall">
          <BookOutlined />
          <span>考试大厅</span>
        </a-menu-item>
        <a-menu-item key="scores" v-if="authStore.user?.role === 'STUDENT'">
          <TrophyOutlined />
          <span>我的成绩</span>
        </a-menu-item>
        <a-menu-item key="users" v-if="authStore.isTeacher || authStore.isAdmin">
          <TeamOutlined />
          <span>用户管理</span>
        </a-menu-item>
        <a-menu-item key="config" v-if="authStore.isAdmin">
          <SettingOutlined />
          <span>系统设置</span>
        </a-menu-item>
        <a-menu-item key="profile">
          <UserOutlined />
          <span>个人资料</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="header">
        <div class="header-left">
          <span style="font-size: 18px; font-weight: 500;">
            {{ { 
              'overview': '数据概览', 
              'manage': '试卷管理', 
              'hall': '考试大厅', 
              'scores': '我的成绩', 
              'users': '用户管理', 
              'config': '系统设置', 
              'profile': '个人资料' 
            }[activeTab] }}
          </span>
        </div>
        <div class="user-info">
          <span style="margin-right: 16px;">欢迎, {{ authStore.user?.fullName }} ({{ authStore.user?.role }})</span>
          <a-button type="link" @click="handleLogout">
            <LogoutOutlined /> 退出
          </a-button>
        </div>
      </a-layout-header>

      <a-layout-content class="content">
        <div class="main-container">
          <!-- Overview Tab (STUDENT) -->
          <div v-if="activeTab === 'overview' && authStore.user?.role === 'STUDENT'" class="overview-container">
            <a-skeleton :loading="loading" active :paragraph="{ rows: 4 }">
              <a-row :gutter="24">
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="已参加考试" :value="stats.totalExams">
                      <template #prefix><ReadOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="平均分" :value="stats.avgScore" :precision="1" suffix="%">
                      <template #prefix><TrophyOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="及格率" :value="stats.passRate" :precision="1" suffix="%">
                      <template #prefix><SecurityScanOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="待参加考试" :value="stats.pendingExamsCount" value-style="color: #cf1322">
                      <template #prefix><HourglassOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
              </a-row>
            </a-skeleton>

            <a-row :gutter="16" style="margin-top: 24px;" type="flex">
              <a-col :span="14">
                <a-card title="待参加考试快照" :bordered="false" class="premium-card">
                  <template #extra>
                    <a @click="handleMenuClick({key: 'hall'})">进入大厅</a>
                  </template>
                  <a-list :dataSource="exams.filter(e => e.state === 'PUBLISHED').slice(0, 3)" size="small">
                    <template #renderItem="{ item }">
                      <a-list-item>
                        <a-list-item-meta :title="item.title" :description="item.course">
                          <template #avatar>
                            <a-avatar style="background-color: #fde3cf; color: #f56a00">{{ item.course?.[0] || 'E' }}</a-avatar>
                          </template>
                        </a-list-item-meta>
                        <a-button type="link" @click="showDetail(item)">立即参加</a-button>
                      </a-list-item>
                    </template>
                  </a-list>
                  <a-empty v-if="exams.filter(e => e.state === 'PUBLISHED').length === 0" description="暂无待参加考试" />
                </a-card>
              </a-col>
              <a-col :span="10">
                <a-card title="最近成绩" :bordered="false" class="premium-card">
                  <template #extra>
                    <a @click="handleMenuClick({key: 'scores'})">更多</a>
                  </template>
                  <a-timeline>
                    <a-timeline-item v-for="sub in stats.recentSubmissions.slice(0, 3)" :key="sub.id" :color="sub.score >= (sub.examTotalScore || 100) * 0.6 ? 'green' : 'red'">
                      <p>{{ sub.exam.title }} - <b>{{ sub.score }}分</b></p>
                      <p style="color: #999; font-size: 12px;">{{ sub.endTime }}</p>
                    </a-timeline-item>
                  </a-timeline>
                </a-card>
              </a-col>
            </a-row>
          </div>

          <!-- TEACHER: Overview -->
          <div v-if="activeTab === 'overview' && authStore.isTeacher" class="overview-container">
            <a-skeleton :loading="loading" active :paragraph="{ rows: 4 }">
              <a-row :gutter="24">
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="试卷总数" :value="teacherStats.totalExams">
                      <template #prefix><FileTextOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="累计答题" :value="teacherStats.totalSubmissions">
                      <template #prefix><TeamOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="进行中考试" :value="teacherStats.activeExams" value-style="color: #3f51b5">
                      <template #prefix><HourglassOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
                <a-col :span="6">
                  <a-card class="premium-card">
                    <a-statistic title="参与学生数" :value="teacherStats.totalStudents">
                      <template #prefix><TeamOutlined /></template>
                    </a-statistic>
                  </a-card>
                </a-col>
              </a-row>
            </a-skeleton>

            <a-row :gutter="16" style="margin-top: 24px;">
              <a-col :span="16">
                <a-card title="最近全服动态" :bordered="false" class="dashboard-card">
                  <a-table :dataSource="teacherStats.recentSubmissions" :pagination="false" size="small" :columns="[
                    { title: '学生', dataIndex: ['student', 'fullName'], key: 'student' },
                    { title: '考试', dataIndex: ['exam', 'title'], key: 'exam' },
                    { title: '得分', dataIndex: 'score', key: 'score' },
                    { title: '提交时间', dataIndex: 'endTime', key: 'time', customRender: ({text}) => text ? new Date(text).toLocaleString() : 'N/A' }
                  ]" />
                </a-card>
              </a-col>
              <a-col :span="8">
                <a-card title="快捷操作" :bordered="false" class="dashboard-card">
                  <a-space direction="vertical" style="width: 100%">
                    <a-button type="primary" block @click="createExamVisible = true">
                      <PlusOutlined /> 新建考试
                    </a-button>
                    <a-button block @click="handleMenuClick({key: 'manage'})">
                      <UnorderedListOutlined /> 试卷列表
                    </a-button>
                    <a-button block @click="handleMenuClick({key: 'users'})">
                      <TeamOutlined /> 用户管理
                    </a-button>
                  </a-space>
                </a-card>
              </a-col>
            </a-row>
          </div>

          <!-- TEACHER: Exam Management -->
          <div v-if="activeTab === 'manage' && authStore.isTeacher">
             <div style="margin-bottom: 16px;">
               <a-button type="primary" @click="createExamVisible = true">
                 <PlusOutlined /> 创建试卷
               </a-button>
             </div>
             
             <a-table :loading="loading" :dataSource="exams" :columns="[
               { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
               { title: '名称', dataIndex: 'title', key: 'title' },
               { title: '时长', dataIndex: 'duration', key: 'duration', customRender: ({text}) => `${text} 分钟` },
               { title: '状态', dataIndex: 'state', key: 'state', customRender: ({text}) => ({'DRAFT':'草稿','PUBLISHED':'已发布','ENDED':'已结束'}[text] || text) },
               { title: '操作', key: 'action' }
             ]">
               <template #bodyCell="{ column, record }">
                 <template v-if="column.key === 'action'">
                   <a-button type="link" size="small" @click="showAddQuestion(record.id)">添加题目</a-button>
                   <a-divider type="vertical" />
                   <a-button type="link" size="small" :disabled="record.state === 'PUBLISHED'" @click="handlePublish(record)">发布</a-button>
                   <a-divider type="vertical" />
                   <a-popconfirm title="确定删除吗？将删除所有相关答题记录。" @confirm="handleDelete(record.id)">
                     <a-button type="link" size="small" danger>删除</a-button>
                   </a-popconfirm>
                   <a-divider type="vertical" />
                   <a-button type="link" size="small" @click="showAnalysis(record.id)">数据分析</a-button>
                   <a-divider type="vertical" />
                    <a-button type="link" size="small" @click="showSubmissions(record.id)">阅卷中心</a-button>
                    <a-divider type="vertical" />
                    <a-button type="link" size="small" @click="showEditor(record.id)">维护试卷</a-button>
                  </template>
               </template>
             </a-table>
          </div>

          <!-- Exam Hall -->
          <div v-if="activeTab === 'hall'">
             <div style="margin-bottom: 24px; display: flex; justify-content: space-between; align-items: center; background: white; padding: 16px; border-radius: 8px; box-shadow: 0 1px 2px rgba(0,0,0,0.05);">
                <div style="display: flex; gap: 16px; align-items: center;">
                   <a-input v-model:value="searchText" placeholder="搜索考试名称" style="width: 240px" allowClear>
                      <template #prefix><SearchOutlined /></template>
                   </a-input>
                   <a-select v-model:value="courseFilter" placeholder="选择课程" style="width: 160px" allowClear>
                      <a-select-option v-for="c in distinctCourses" :key="c" :value="c">{{ c }}</a-select-option>
                   </a-select>
                   <a-select v-model:value="statusFilter" placeholder="考试状态" style="width: 140px" allowClear>
                      <a-select-option value="NOT_STARTED">未开始</a-select-option>
                      <a-select-option value="IN_PROGRESS">进行中</a-select-option>
                      <a-select-option value="ENDED">已结束</a-select-option>
                   </a-select>
                </div>
                <div v-if="!authStore.isTeacher">
                   <a-radio-group v-model:value="hallViewMode" button-style="solid">
                      <a-radio-button value="card"><AppstoreOutlined /></a-radio-button>
                      <a-radio-button value="table"><BarsOutlined /></a-radio-button>
                   </a-radio-group>
                </div>
             </div>
          
             <!-- Card Layout -->
             <a-list 
                v-if="hallViewMode === 'card' || authStore.isTeacher"
                :loading="loading" 
                :grid="{ gutter: 24, xs: 1, sm: 2, md: 3, lg: 3, xl: 4, xxl: 4 }" 
                :dataSource="authStore.isTeacher ? exams : filteredExams"
             >
                <template #renderItem="{ item }">
                  <a-list-item>
                    <a-card hoverable @click="!authStore.isTeacher ? showDetail(item) : null">
                      <template #cover>
                        <img :src="item.coverUrl || 'https://gw.alipayobjects.com/zos/rmsportal/JiqGstEfoWAOHiTxclqi.png'" alt="cover" style="height: 150px; object-fit: cover;" />
                      </template>
                      <a-card-meta :title="item.title">
                        <template #description>
                          <div><BookOutlined /> {{ item.course || '未指定' }}</div>
                          <div style="height: 40px; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin-top: 4px;">
                            {{ item.description || '暂无描述' }}
                          </div>
                          <div style="margin-top: 8px;">
                            时长: {{ item.duration }} 分钟
                          </div>
                        </template>
                      </a-card-meta>
                      <template #actions>
                         <div v-if="authStore.isTeacher">
                             <a-button type="link" size="small" @click.stop="showAddQuestion(item.id)">
                               <PlusOutlined />加题
                             </a-button>
                             <a-button type="link" size="small" @click.stop="showQuestionBank(item.id)">
                               <BankOutlined />题库
                             </a-button>
                         </div>
                         <div v-else>
                            <a-button type="primary" block>查看详情</a-button>
                         </div>
                      </template>
                    </a-card>
                  </a-list-item>
                </template>
             </a-list>

             <!-- Table Layout -->
             <a-table 
                v-if="hallViewMode === 'table' && !authStore.isTeacher"
                :loading="loading"
                :dataSource="filteredExams"
                :columns="[
                   { title: '考试名称', dataIndex: 'title', key: 'title' },
                   { title: '所属课程', dataIndex: 'course', key: 'course' },
                   { title: '开始时间', dataIndex: 'startTime', key: 'startTime', customRender: ({text}) => text ? new Date(text).toLocaleString() : '无' },
                   { title: '截止时间', dataIndex: 'endTime', key: 'endTime', customRender: ({text}) => text ? new Date(text).toLocaleString() : '无' },
                   { title: '时长', dataIndex: 'duration', key: 'duration', customRender: ({text}) => `${text} 分钟` },
                   { title: '状态', key: 'status' },
                   { title: '操作', key: 'action', width: 120 }
                ]"
             >
                <template #bodyCell="{ column, record }">
                   <template v-if="column.key === 'status'">
                      <a-tag :color="getExamStatus(record).color">{{ getExamStatus(record).text }}</a-tag>
                   </template>
                   <template v-if="column.key === 'action'">
                      <a-button type="link" @click="showDetail(record)">查看详情</a-button>
                   </template>
                </template>
             </a-table>
          </div>
          
          <!-- STUDENT: My Scores -->
          <div v-if="activeTab === 'scores' && authStore.user?.role === 'STUDENT'">
             <a-table :loading="loading" :dataSource="submissions" :columns="[
               { title: '考试名称', dataIndex: ['exam', 'title'], key: 'title' },
               { title: '考试时间', dataIndex: 'endTime', key: 'endTime' },
               { title: '得分', dataIndex: 'score', key: 'score', customRender: ({text}) => text + ' 分' },
               { title: '总分', dataIndex: 'examTotalScore', key: 'examTotalScore', customRender: ({text}) => text + ' 分' },
               { title: '排名', dataIndex: 'ranking', key: 'ranking', customRender: ({text}) => text ? `第 ${text} 名` : '未排名' },
               { title: '状态', dataIndex: 'state', key: 'state', customRender: ({text}) => text === 'SUBMITTED' ? '已提交' : (text === 'IN_PROGRESS' ? '进行中' : text) },
               { title: '操作', key: 'action' }
             ]">
               <template #bodyCell="{ column, record }">
                 <template v-if="column.key === 'action'">
                    <a-button type="link" size="small" @click="router.push(`/score/${record.id}`)">
                     查看解析
                   </a-button>
                 </template>
               </template>
             </a-table>
          </div>

          <!-- USER MANAGEMENT -->
          <div v-if="activeTab === 'users'">
             <UserManagementView />
          </div>

          <!-- PROFILE -->
          <div v-if="activeTab === 'profile'">
             <a-card title="个人基本信息" style="max-width: 800px;">
                <template #extra>
                   <a-button type="link" v-if="!isEditingProfile" @click="startEditProfile">编辑资料</a-button>
                   <a-button type="link" v-else @click="cancelEditProfile">取消</a-button>
                </template>
                
                <a-form v-if="isEditingProfile" :model="profileForm" layout="vertical" @finish="handleUpdateProfile">
                   <a-form-item label="姓名" required>
                      <a-input v-model:value="profileForm.fullName" placeholder="请输入姓名" />
                   </a-form-item>
                   <a-form-item label="新密码">
                      <a-input-password v-model:value="profileForm.password" placeholder="不修改请留空" />
                   </a-form-item>
                   <a-form-item>
                      <a-button type="primary" html-type="submit" :loading="updatingProfile" block>保存修改</a-button>
                   </a-form-item>
                </a-form>
                
                <a-descriptions v-else bordered column="2">
                   <a-descriptions-item label="用户名">{{ authStore.user?.username }}</a-descriptions-item>
                   <a-descriptions-item label="姓名">{{ authStore.user?.fullName }}</a-descriptions-item>
                   <a-descriptions-item label="学号/工号">{{ authStore.user?.username || authStore.user?.id }}</a-descriptions-item>
                   <a-descriptions-item label="角色">
                      <a-tag :color="authStore.user?.role === 'ADMIN' ? 'red' : (authStore.user?.role === 'TEACHER' ? 'blue' : 'green')">
                         {{ { 'ADMIN': '管理员', 'TEACHER': '教师', 'STUDENT': '学生' }[authStore.user?.role] || authStore.user?.role }}
                      </a-tag>
                   </a-descriptions-item>
                   <a-descriptions-item label="所在班级" v-if="authStore.user?.role === 'STUDENT'">{{ authStore.user?.clazz || '未分配' }}</a-descriptions-item>
                   <a-descriptions-item label="注册时间">
                      {{ authStore.user?.createdAt ? new Date(authStore.user.createdAt).toLocaleDateString() : 'N/A' }}
                   </a-descriptions-item>
                 </a-descriptions>
              </a-card>
          </div>

          <!-- SYSTEM CONFIG -->
          <div v-if="activeTab === 'config'">
             <SystemConfigView />
          </div>
        </div>
      </a-layout-content>

      <a-layout-footer style="text-align: center; color: rgba(0, 0, 0, 0.45); padding: 16px 0;">
        {{ configStore.footerText }}
      </a-layout-footer>
    <!-- Modals -->
    <CreateExamModal v-model:open="createExamVisible" @success="fetchData" />
    <AddQuestionModal v-if="currentExamId" v-model:open="addQuestionVisible" :examId="currentExamId" @success="fetchData" />
    <QuestionBankModal 
      v-if="currentExamId" 
      v-model:open="questionBankVisible" 
      :examId="currentExamId" 
      @success="fetchData"
      @autoGenerate="handleAutoGenerateRequest"
    />
    <AutoGenerateModal 
      v-if="currentExamId" 
      v-model:open="autoGenerateVisible" 
      :examId="currentExamId" 
      :course="exams.find(e => e.id === currentExamId)?.course"
      @success="handleAutoGenerateSuccess"
    />
    <ExamEditorModal v-if="currentExamId" v-model:open="editorVisible" :examId="currentExamId" @success="fetchData" />

    <!-- Teacher: Submissions Modal -->
    <a-modal v-model:open="submissionListVisible" title="提交记录" :footer="null" width="800px">
       <a-table :dataSource="examSubmissions" :columns="[
         { title: '学生', dataIndex: ['student', 'fullName'], key: 'student' },
         { title: '总分', dataIndex: 'score', key: 'score' },
         { title: '提交时间', dataIndex: 'endTime', key: 'endTime' },
         { title: '操作', key: 'action' }
       ]" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button type="link" @click="showGradeModal(record.id)">评分/修改</a-button>
            </template>
          </template>
       </a-table>
    </a-modal>

    <GradeSubmissionModal 
      v-model:open="gradeVisible" 
      :submissionId="currentSubmissionId"
      @graded="onGraded"
    />

    </a-layout>
  </a-layout>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--bg-main);
}
.sider {
  box-shadow: 4px 0 10px rgba(0, 0, 0, 0.05);
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  z-index: 100;
}
.logo-container {
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  overflow: hidden;
}
.logo-container img {
  height: 32px;
  filter: drop-shadow(0 0 8px rgba(255, 255, 255, 0.2));
}
.logo-container span {
  color: white;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
  background: linear-gradient(90deg, #fff, #9ca3af);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.header {
  background: rgba(255, 255, 255, 0.8) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  padding: 0 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  height: 72px;
  line-height: 72px;
}
.content {
  margin: 32px;
  padding: 0;
}
.main-container {
  max-width: 1400px;
  margin: 0 auto;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-info span {
  font-weight: 500;
  color: var(--text-main);
}
.premium-card {
  height: 100%;
}
:deep(.ant-menu-item) {
  margin: 4px 12px !important;
  border-radius: var(--radius-md) !important;
  width: calc(100% - 24px) !important;
}
:deep(.ant-menu-item-selected) {
  background: var(--primary-color) !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.4);
}
:deep(.ant-statistic-title) {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}
:deep(.ant-statistic-content) {
  font-weight: 700;
  color: var(--text-main);
}
</style>
