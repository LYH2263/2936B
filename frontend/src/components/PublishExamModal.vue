<script setup>
import { reactive, ref, onMounted, watch } from 'vue';
import { publishExam, getSystemConfig } from '@/api';
import { message } from 'ant-design-vue';
import { InfoCircleOutlined, SettingOutlined, SecurityScanOutlined, UsergroupAddOutlined } from '@ant-design/icons-vue';

const props = defineProps(['open', 'exam']);
const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const formRef = ref();

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
  // Targeting
  targetAudience: 'ALL',
  targetIds: ''
});

onMounted(async () => {
  try {
    const res = await getSystemConfig();
    if (res.data.defaultDuration) formState.duration = res.data.defaultDuration;
    if (res.data.tabSwitchLimit !== undefined) {
      formState.tabSwitchLimit = res.data.tabSwitchLimit;
      if (res.data.defaultAntiCheat && res.data.defaultAntiCheat.includes('TAB_SWITCH')) {
        formState.allowTabSwitch = false;
      }
    }
  } catch (e) {}
});

watch(() => props.open, (val) => {
  if (val && props.exam) {
    Object.assign(formState, {
      ...props.exam,
      startTime: props.exam.startTime ? null : null, // Reset for picker logic
      endTime: props.exam.endTime ? null : null,
      tabSwitchLimit: formState.tabSwitchLimit // Retain global default if not set on exam
    });
  }
});

const handleOk = async () => {
  try {
    const values = await formRef.value.validateFields();
    loading.value = true;
    
    const payload = {
      ...values,
      startTime: values.startTime ? values.startTime.format('YYYY-MM-DD HH:mm:ss') : null,
      endTime: values.endTime ? values.endTime.format('YYYY-MM-DD HH:mm:ss') : null,
    };

    await publishExam(props.exam.id, payload);
    message.success('发布成功');
    emit('success');
    emit('update:open', false);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  emit('update:open', false);
};
</script>

<template>
  <a-modal
    :open="open"
    title="发布考试"
    @ok="handleOk"
    @cancel="handleCancel"
    :confirmLoading="loading"
    width="700px"
  >
    <a-form ref="formRef" :model="formState" layout="vertical">
      <a-tabs default-active-key="1">
        <a-tab-pane key="1" tab="基本信息">
          <template #tab>
            <span><InfoCircleOutlined />基本信息</span>
          </template>
          <a-form-item label="考试名称" name="title" :rules="[{ required: true }]">
            <a-input v-model:value="formState.title" />
          </a-form-item>
          <a-form-item label="考试描述" name="description">
            <a-textarea v-model:value="formState.description" rows="3" placeholder="填写考试说明或须知..." />
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="所属课程" name="course" :rules="[{ required: true }]">
                <a-input v-model:value="formState.course" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="考试时长 (分钟)" name="duration" :rules="[{ required: true }]">
                <a-input-number v-model:value="formState.duration" :min="1" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </a-tab-pane>

        <a-tab-pane key="2" tab="时间与范围">
          <template #tab>
            <span><SettingOutlined />时间与范围</span>
          </template>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="开始时间" name="startTime" :rules="[{ required: true }]">
                <a-date-picker show-time v-model:value="formState.startTime" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="截止时间" name="endTime" :rules="[{ required: true }]">
                <a-date-picker show-time v-model:value="formState.endTime" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
          
          <a-form-item label="参与对象" name="targetAudience">
            <a-radio-group v-model:value="formState.targetAudience">
              <a-radio value="ALL">全员 (所有人可见)</a-radio>
              <a-radio value="CUSTOM">指定名单 (按用户名/ID)</a-radio>
            </a-radio-group>
          </a-form-item>

          <a-form-item v-if="formState.targetAudience === 'CUSTOM'" label="名单列表" name="targetIds">
            <a-textarea v-model:value="formState.targetIds" placeholder="输入用户名或ID，以逗号分隔" rows="2" />
          </a-form-item>
        </a-tab-pane>

        <a-tab-pane key="3" tab="考试安全">
          <template #tab>
            <span><SecurityScanOutlined />考试安全</span>
          </template>
          <a-space direction="vertical" style="width: 100%">
            <a-card size="small" title="防作弊设置">
              <a-form-item label="禁止切屏">
                <a-switch v-model:checked="formState.allowTabSwitch" checked-children="开启" un-checked-children="关闭" :checkedValue="false" :unCheckedValue="true" />
                <span style="margin-left: 10px; color: #999;">开启后将限制切换窗口</span>
              </a-form-item>
              <a-form-item v-if="!formState.allowTabSwitch" label="最大切屏次数">
                <a-input-number v-model:value="formState.tabSwitchLimit" :min="1" />
              </a-form-item>
              <a-form-item label="摄像头监控">
                <a-switch v-model:checked="formState.enableCamera" />
                <span style="margin-left: 10px; color: #999;">开启后考试过程中随机抓拍</span>
              </a-form-item>
            </a-card>
            
            <a-card size="small" title="结果公开设置" style="margin-top: 10px">
              <a-form-item label="自动公布成绩">
                <a-switch v-model:checked="formState.publicScores" />
                <span style="margin-left: 10px; color: #999;">交卷后立即显示总分和排名</span>
              </a-form-item>
              <a-form-item label="允许查看解析">
                <a-switch v-model:checked="formState.allowViewAnalysis" />
                <span style="margin-left: 10px; color: #999;">学生可查看正确答案和教师评语</span>
              </a-form-item>
            </a-card>
          </a-space>
        </a-tab-pane>
      </a-tabs>
    </a-form>
  </a-modal>
</template>
