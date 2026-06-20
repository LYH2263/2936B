<script setup>
import { reactive, ref } from 'vue';
import { autoGenerateExam } from '@/api';
import { message } from 'ant-design-vue';

const props = defineProps(['open', 'examId', 'course']);
const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const formRef = ref();

const formState = reactive({
  subject: props.course || '',
  totalScore: 100,
  countsByType: {
    SINGLE: 10,
    MULTI: 5,
    JUDGE: 5,
    SHORT: 2
  },
  scoresByType: {
    SINGLE: 2,
    MULTI: 4,
    JUDGE: 2,
    SHORT: 10
  },
  difficultyLevels: [2, 3, 4],
  knowledgePoints: []
});

const handleOk = async () => {
  try {
    await formRef.value.validateFields();
    loading.value = true;
    const payload = {
      subject: formState.subject,
      totalScore: formState.totalScore,
      countsByType: formState.countsByType,
      scoresByType: formState.scoresByType,
      difficultyLevels: formState.difficultyLevels,
      knowledgePoints: formState.knowledgePoints
    };
    
    await autoGenerateExam(props.examId, payload);
    message.success('自动组卷成功');
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
    title="自动组卷策略设置"
    @ok="handleOk"
    @cancel="handleCancel"
    :confirmLoading="loading"
    width="600px"
  >
    <a-form ref="formRef" :model="formState" layout="vertical">
      <a-form-item label="科目" name="subject" :rules="[{ required: true }]">
        <a-input v-model:value="formState.subject" />
      </a-form-item>
      
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="期望总分" name="totalScore">
            <a-input-number v-model:value="formState.totalScore" :min="1" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="难度范围" name="difficultyLevels">
            <a-select v-model:value="formState.difficultyLevels" mode="multiple" placeholder="选择难度">
              <a-select-option :value="1">入门</a-select-option>
              <a-select-option :value="2">简单</a-select-option>
              <a-select-option :value="3">中等</a-select-option>
              <a-select-option :value="4">困难</a-select-option>
              <a-select-option :value="5">专家</a-select-option>
            </a-select>
          </a-form-item>
        </a-col>
      </a-row>

      <a-divider>题型设置 (数量 & 分值)</a-divider>
      <div v-for="(count, type) in formState.countsByType" :key="type" style="margin-bottom: 12px;">
         <a-row :gutter="16" align="middle">
            <a-col :span="4">
               <strong>{{ type === 'SINGLE' ? '单选' : (type === 'MULTI' ? '多选' : (type === 'JUDGE' ? '判断' : '简答')) }}</strong>
            </a-col>
            <a-col :span="10">
               <a-input-number v-model:value="formState.countsByType[type]" :min="0" style="width: 100%" addon-before="数量" />
            </a-col>
            <a-col :span="10">
               <a-input-number v-model:value="formState.scoresByType[type]" :min="1" style="width: 100%" addon-before="每题分值" />
            </a-col>
         </a-row>
      </div>

    </a-form>
  </a-modal>
</template>
