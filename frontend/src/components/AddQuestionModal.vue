<script setup>
import { reactive, ref } from 'vue';
import { createQuestion, addQuestionToExam } from '@/api';
import { message } from 'ant-design-vue';

const props = defineProps(['open', 'examId']);
const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const formRef = ref();

const formState = reactive({
  content: '',
  type: 'SINGLE',
  options: [{ label: 'A', text: '' }, { label: 'B', text: '' }, { label: 'C', text: '' }, { label: 'D', text: '' }],
  answer: '',
  analysis: '',
  subject: '',
  knowledgePoint: '',
  difficulty: 3,
  defaultScore: 5,
});

const activeKey = ref('manual'); // manual or bank

const handleOk = async () => {
  try {
    const values = await formRef.value.validateFields();
    loading.value = true;
    
    // 1. Create Question
    const questionPayload = {
      content: values.content,
      type: values.type,
      options: JSON.stringify(values.options),
      answer: values.answer,
      analysis: values.analysis,
      subject: values.subject,
      knowledgePoint: values.knowledgePoint,
      difficulty: values.difficulty,
      defaultScore: values.defaultScore
    };
    
    const res = await createQuestion(questionPayload);
    const questionId = res.data.id;
    
    // 2. Link to Exam
    await addQuestionToExam(props.examId, {
      questionId: questionId,
      score: values.defaultScore,
      sequence: 0 // Default logic
    });

    message.success('题目添加成功');
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
    title="添加题目"
    @ok="handleOk"
    @cancel="handleCancel"
    :confirmLoading="loading"
    width="800px"
  >
    <a-tabs v-model:activeKey="activeKey">
      <a-tab-pane key="manual" tab="手动录入">
        <a-form ref="formRef" :model="formState" layout="vertical">
          <a-form-item label="题干" name="content" :rules="[{ required: true }]">
            <a-textarea v-model:value="formState.content" rows="3" />
          </a-form-item>
          
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="题型" name="type">
                <a-select v-model:value="formState.type">
                  <a-select-option value="SINGLE">单选题</a-select-option>
                  <a-select-option value="MULTI">多选题</a-select-option>
                  <a-select-option value="JUDGE">判断题</a-select-option>
                  <a-select-option value="SHORT">简答题</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="分值" name="defaultScore">
                <a-input-number v-model:value="formState.defaultScore" :min="1" />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item label="科目" name="subject" :rules="[{ required: true }]">
                <a-input v-model:value="formState.subject" placeholder="数学/英语" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="知识点" name="knowledgePoint">
                <a-input v-model:value="formState.knowledgePoint" placeholder="三角函数" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="难度" name="difficulty">
                <a-rate v-model:value="formState.difficulty" />
              </a-form-item>
            </a-col>
          </a-row>

          <template v-if="formState.type === 'SINGLE' || formState.type === 'MULTI'">
             <a-divider>选项</a-divider>
             <div v-for="(opt, idx) in formState.options" :key="idx" style="display: flex; margin-bottom: 8px;">
               <a-input v-model:value="opt.label" style="width: 50px; margin-right: 8px; text-align: center;" disabled />
               <a-input v-model:value="opt.text" placeholder="选项内容" />
             </div>
          </template>

          <a-form-item label="参考答案" name="answer" :rules="[{ required: true }]">
            <a-input v-model:value="formState.answer" placeholder="单选填选项(A), 多选填(ABC), 判断填(TRUE/FALSE)" />
          </a-form-item>
          
          <a-form-item label="解析" name="analysis">
            <a-textarea v-model:value="formState.analysis" />
          </a-form-item>
        </a-form>
      </a-tab-pane>
      <!-- Future: Add 'Select from Bank' tab -->
    </a-tabs>
  </a-modal>
</template>
