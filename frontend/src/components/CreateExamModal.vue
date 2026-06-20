<script setup>
import { reactive, ref, onMounted } from 'vue';
import { createExam, getSystemConfig } from '@/api';
import { message } from 'ant-design-vue';

const props = defineProps(['open']);
const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const formRef = ref();

const formState = reactive({
  title: '',

  course: '',
  description: '',
  duration: 60,
  startTime: null,
  endTime: null,
});

onMounted(async () => {
  try {
    const res = await getSystemConfig();
    if (res.data.defaultDuration) {
      formState.duration = res.data.defaultDuration;
    }
  } catch (e) {}
});

const handleOk = async () => {
  try {
    const values = await formRef.value.validateFields();
    loading.value = true;
    
    // Format dates if needed, or send as is depending on backend
    const payload = {
      ...values,
      // Simple date handling for now
      startTime: values.startTime ? values.startTime.format('YYYY-MM-DD HH:mm:ss') : null,
      endTime: values.endTime ? values.endTime.format('YYYY-MM-DD HH:mm:ss') : null,
    };

    await createExam(payload);
    message.success('考试创建成功');
    emit('success');
    emit('update:open', false);
    formRef.value.resetFields();
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
    title="创建新考试"
    @ok="handleOk"
    @cancel="handleCancel"
    :confirmLoading="loading"
  >
    <a-form ref="formRef" :model="formState" layout="vertical">
      <a-form-item label="考试名称" name="title" :rules="[{ required: true }]">
        <a-input v-model:value="formState.title" />
      </a-form-item>
      <a-form-item label="课程" name="course" :rules="[{ required: true }]">
        <a-input v-model:value="formState.course" placeholder="例如：高等数学" />
      </a-form-item>
      <a-form-item label="描述" name="description">
        <a-textarea v-model:value="formState.description" />
      </a-form-item>
      <a-form-item label="时长 (分钟)" name="duration" :rules="[{ required: true }]">
        <a-input-number v-model:value="formState.duration" :min="1" />
      </a-form-item>
      <a-form-item label="开始时间" name="startTime">
        <a-date-picker show-time v-model:value="formState.startTime" />
      </a-form-item>
      <a-form-item label="结束时间" name="endTime">
        <a-date-picker show-time v-model:value="formState.endTime" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
