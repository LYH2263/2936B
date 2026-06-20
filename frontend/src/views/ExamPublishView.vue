<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getExam, publishExam, getSystemConfig } from '@/api';
import { message } from 'ant-design-vue';
import { 
  LeftOutlined, InfoCircleOutlined, SettingOutlined, 
  SecurityScanOutlined, CheckCircleOutlined,
  UsergroupAddOutlined, AlertOutlined
} from '@ant-design/icons-vue';
import dayjs from 'dayjs';

const route = useRoute();
const router = useRouter();
const examId = route.params.id;

const currentStep = ref(0);
const loading = ref(false);
const exam = ref(null);
const formRef1 = ref();
const formRef2 = ref();
const formRef3 = ref();

const formState = reactive({
  title: '',
  description: '',
  course: '',
  startTime: null,
  endTime: null,
  duration: 60,
  // Security
  allowTabSwitch: true,
  tabSwitchLimit: 3,
  enableCamera: false,
  // Privacy
  publicScores: true,
  allowViewAnalysis: true,
  shuffleQuestions: false,
  shuffleOptions: false,
  // Targeting
  targetAudience: 'ALL',
  targetIds: ''
});

const fetchExamData = async () => {
  try {
    const [eRes, cRes] = await Promise.all([
      getExam(examId),
      getSystemConfig()
    ]);
    exam.value = eRes.data;
    
    // Merge existing data
    Object.assign(formState, {
      ...eRes.data,
      startTime: eRes.data.startTime ? dayjs(eRes.data.startTime) : null,
      endTime: eRes.data.endTime ? dayjs(eRes.data.endTime) : null,
    });

    // Handle system defaults if not set on exam
    if (!eRes.data.duration && cRes.data.defaultDuration) {
      formState.duration = cRes.data.defaultDuration;
    }
    if (eRes.data.tabSwitchLimit === null && cRes.data.tabSwitchLimit !== undefined) {
      formState.tabSwitchLimit = cRes.data.tabSwitchLimit;
    }
  } catch (e) {
    message.error('加载考试数据失败');
  }
};

const next = async () => {
  try {
    if (currentStep.value === 0) await formRef1.value.validateFields();
    if (currentStep.value === 1) await formRef2.value.validateFields();
    currentStep.value++;
  } catch (e) {
    // Validation fails locally
  }
};

const prev = () => {
  currentStep.value--;
};

const handlePublish = async () => {
  try {
    await formRef3.value.validateFields();
    loading.value = true;
    
    const payload = {
      ...formState,
      startTime: formState.startTime ? formState.startTime.format('YYYY-MM-DD HH:mm:ss') : null,
      endTime: formState.endTime ? formState.endTime.format('YYYY-MM-DD HH:mm:ss') : null,
    };

    await publishExam(examId, payload);
    message.success('考试发布成功');
    router.push('/dashboard');
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

onMounted(fetchExamData);

const steps = [
  { title: '基础设置', icon: InfoCircleOutlined },
  { title: '时间范围', icon: SettingOutlined },
  { title: '安全质量', icon: SecurityScanOutlined }
];
</script>

<template>
  <div class="publish-page-container">
    <div class="publish-header">
       <div class="header-left">
          <a-button type="link" @click="router.back()">
            <LeftOutlined /> 返回
          </a-button>
          <a-divider type="vertical" />
          <h2 class="page-title">考试发布向导</h2>
       </div>
       <div class="header-steps">
          <a-steps :current="currentStep" size="small" style="width: 500px">
             <a-step v-for="item in steps" :key="item.title" :title="item.title">
                <template #icon><component :is="item.icon" /></template>
             </a-step>
          </a-steps>
       </div>
    </div>

    <div class="publish-body">
       <div class="form-card">
          <!-- Step 1: Base Info -->
          <div v-show="currentStep === 0" class="step-content">
             <div class="section-title">📝 考试基本信息</div>
             <a-form ref="formRef1" :model="formState" layout="vertical">
                <a-form-item label="考试名称" name="title" :rules="[{ required: true, message: '请输入考试名称' }]">
                   <a-input v-model:value="formState.title" placeholder="例如：2024年春季学期数学期末考试" size="large" />
                </a-form-item>
                <a-form-item label="考试描述" name="description">
                   <a-textarea v-model:value="formState.description" rows="4" placeholder="告知学生考前注意事项、考察范围等..." />
                </a-form-item>
                <a-row :gutter="24">
                   <a-col :span="12">
                      <a-form-item label="所属课程" name="course" :rules="[{ required: true, message: '请指定所属课程' }]">
                         <a-input v-model:value="formState.course" placeholder="输入课程名称" />
                      </a-form-item>
                   </a-col>
                   <a-col :span="12">
                      <a-form-item label="考试时长 (分钟)" name="duration" :rules="[{ required: true, message: '请设置考试时长' }]">
                         <a-input-number v-model:value="formState.duration" :min="1" style="width: 100%" />
                      </a-form-item>
                   </a-col>
                </a-row>
             </a-form>
          </div>

          <!-- Step 2: Time and Audience -->
          <div v-show="currentStep === 1" class="step-content">
             <div class="section-title">⏰ 时间周期与考生范围</div>
             <a-form ref="formRef2" :model="formState" layout="vertical">
                <a-alert type="info" show-icon message="考生的作答时间必须在此范围内，且不得超过设定的总时长。" style="margin-bottom: 24px">
                  <template #icon><InfoCircleOutlined /></template>
                </a-alert>
                <a-row :gutter="24">
                   <a-col :span="12">
                      <a-form-item label="开始时间" name="startTime" :rules="[{ required: true, message: '请选择开始时间' }]">
                         <a-date-picker show-time v-model:value="formState.startTime" style="width: 100%" size="large" />
                      </a-form-item>
                   </a-col>
                   <a-col :span="12">
                      <a-form-item label="截止时间" name="endTime" :rules="[{ required: true, message: '请选择截止时间' }]">
                         <a-date-picker show-time v-model:value="formState.endTime" style="width: 100%" size="large" />
                      </a-form-item>
                   </a-col>
                </a-row>

                <a-divider />

                <a-form-item label="参与对象" name="targetAudience">
                   <a-radio-group v-model:value="formState.targetAudience" button-style="solid">
                      <a-radio-button value="ALL">全员可见</a-radio-button>
                      <a-radio-button value="CUSTOM">指定名单</a-radio-button>
                   </a-radio-group>
                </a-form-item>

                <div v-if="formState.targetAudience === 'CUSTOM'" class="audience-box">
                   <a-form-item label="名单列表 (用户名/ID)" name="targetIds" :rules="[{ required: true, message: '请提供名单' }]">
                      <a-textarea v-model:value="formState.targetIds" placeholder="输入用户名或ID，以英文逗号分隔" rows="4" />
                   </a-form-item>
                   <span class="tip"><UsergroupAddOutlined /> 提示：仅列表中匹配的用户在登录后可看到此考试。</span>
                </div>
             </a-form>
          </div>

          <!-- Step 3: Security and Analysis -->
          <div v-show="currentStep === 2" class="step-content">
             <div class="section-title">🔒 监考安全与结果公开</div>
             <a-form ref="formRef3" :model="formState" layout="vertical">
                <a-row :gutter="24">
                   <a-col :span="12">
                      <a-card title="防作弊与规则" size="small">
                         <a-form-item label="禁止切屏">
                            <a-switch v-model:checked="formState.allowTabSwitch" checked-children="开启" un-checked-children="关闭" :checkedValue="false" :unCheckedValue="true" />
                            <span class="switch-tip">限制考生切换窗口</span>
                         </a-form-item>
                         <a-form-item v-if="!formState.allowTabSwitch" label="最大切屏次数" name="tabSwitchLimit">
                            <a-input-number v-model:value="formState.tabSwitchLimit" :min="1" />
                            <span class="unit">次</span>
                         </a-form-item>
                          <a-form-item label="摄像头监控">
                             <a-switch v-model:checked="formState.enableCamera" />
                             <span class="switch-tip">考试过程中随机抓拍</span>
                          </a-form-item>
                          <a-form-item label="题目乱序">
                             <a-switch v-model:checked="formState.shuffleQuestions" />
                             <span class="switch-tip">每位学生题目顺序不同</span>
                          </a-form-item>
                          <a-form-item label="选项乱序">
                             <a-switch v-model:checked="formState.shuffleOptions" />
                             <span class="switch-tip">每道题的选项随机排列</span>
                          </a-form-item>
                      </a-card>
                   </a-col>
                   <a-col :span="12">
                      <a-card title="发布后设置" size="small">
                         <a-form-item label="自动公布成绩">
                            <a-switch v-model:checked="formState.publicScores" />
                            <span class="switch-tip">交卷后立即显示分数</span>
                         </a-form-item>
                         <a-form-item label="允许查看解析">
                            <a-switch v-model:checked="formState.allowViewAnalysis" />
                            <span class="switch-tip">学生可查看正确答案及评语</span>
                         </a-form-item>
                      </a-card>
                   </a-col>
                </a-row>

                <div class="final-warning">
                   <a-alert type="warning" show-icon message="请确认所有设置均已检查。发布后，部分核心参数（如起止时间）的微调可能会影响已在考场中的学生。">
                      <template #icon><AlertOutlined /></template>
                   </a-alert>
                </div>
             </a-form>
          </div>

          <!-- Footer Actions -->
          <div class="form-footer">
             <a-space>
                <a-button v-if="currentStep > 0" @click="prev" size="large">上一步</a-button>
                <a-button v-if="currentStep < steps.length - 1" type="primary" @click="next" size="large">下一步</a-button>
                <a-button v-if="currentStep === steps.length - 1" type="primary" :loading="loading" @click="handlePublish" size="large">
                   <CheckCircleOutlined /> 确认并发布
                </a-button>
             </a-space>
          </div>
       </div>
    </div>
  </div>
</template>

<style scoped>
.publish-page-container {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;
}

.publish-header {
  height: 64px;
  background: #fff;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e8e8e8;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.publish-body {
  flex: 1;
  padding: 40px;
  display: flex;
  justify-content: center;
}

.form-card {
  width: 100%;
  max-width: 800px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
}

.step-content {
  flex: 1;
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 32px;
  color: #1f1f1f;
}

.audience-box {
  background: #fafafa;
  padding: 20px;
  border-radius: 8px;
  border: 1px dashed #d9d9d9;
}
.audience-box .tip {
  display: block;
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 12px;
}

.switch-tip {
  margin-left: 12px;
  color: #8c8c8c;
  font-size: 13px;
}
.unit {
  margin-left: 8px;
  color: #595959;
}

.final-warning {
  margin-top: 32px;
}

.form-footer {
  margin-top: 48px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
  text-align: right;
}

:deep(.ant-input-lg), :deep(.ant-picker-large) {
  border-radius: 8px;
}
</style>
