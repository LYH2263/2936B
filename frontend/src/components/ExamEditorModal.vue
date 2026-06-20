<script setup>
import { ref, watch } from 'vue';
import { getExamQuestions, updateExamQuestion, removeQuestionFromExam } from '@/api';
import { message } from 'ant-design-vue';
import { DragOutlined, DeleteOutlined, SaveOutlined } from '@ant-design/icons-vue';

const props = defineProps(['open', 'examId']);
const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const questions = ref([]);

const fetchQuestions = async () => {
    loading.value = true;
    try {
        const res = await getExamQuestions(props.examId);
        questions.value = res.data;
    } catch (e) {
        message.error('加载试题失败');
    } finally {
        loading.value = false;
    }
};

watch(() => props.open, (val) => {
    if (val) fetchQuestions();
});

const handleUpdate = async (record) => {
    try {
        await updateExamQuestion(props.examId, record.question.id, {
            score: record.score,
            sequence: record.sequence
        });
        message.success('保存成功');
    } catch (e) {
        message.error('保存失败');
    }
};

const handleDelete = async (questionId) => {
    try {
        await removeQuestionFromExam(props.examId, questionId);
        message.success('删除成功');
        fetchQuestions();
        emit('success');
    } catch (e) {
        message.error('删除失败');
    }
};
</script>

<template>
  <a-modal :open="open" title="试卷内容维护" @cancel="emit('update:open', false)" width="800px" :footer="null">
    <a-table :dataSource="questions" :pagination="false" size="small">
       <a-table-column title="内容" dataIndex="['question', 'content']" ellipsis />
       <a-table-column title="分值" key="score" width="100">
         <template #default="{ record }">
           <a-input-number v-model:value="record.score" :min="1" size="small" style="width: 60px" />
         </template>
       </a-table-column>
       <a-table-column title="排序" key="sequence" width="100">
         <template #default="{ record }">
           <a-input-number v-model:value="record.sequence" size="small" style="width: 60px" />
         </template>
       </a-table-column>
       <a-table-column title="操作" key="action" width="120">
         <template #default="{ record }">
           <a-button type="link" size="small" @click="handleUpdate(record)"><SaveOutlined /></a-button>
           <a-popconfirm title="确定移除吗？" @confirm="handleDelete(record.question.id)">
             <a-button type="link" size="small" danger><DeleteOutlined /></a-button>
           </a-popconfirm>
         </template>
       </a-table-column>
    </a-table>
  </a-modal>
</template>
