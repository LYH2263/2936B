<script setup>
import { ref, watch, computed } from 'vue';
import { getAllQuestions, addQuestionToExam } from '@/api';
import { message } from 'ant-design-vue';

const props = defineProps({
  open: Boolean,
  examId: Number
});

const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const questions = ref([]);
const selectedRowKeys = ref([]);

// Filters
const filterSubject = ref('');
const filterType = ref(null);
const filterDifficulty = ref(null);

const filteredQuestions = computed(() => {
    return questions.value.filter(q => {
        if (filterSubject.value && !q.subject.includes(filterSubject.value)) return false;
        if (filterType.value && q.type !== filterType.value) return false;
        if (filterDifficulty.value && q.difficulty !== filterDifficulty.value) return false;
        return true;
    });
});

const distinctSubjects = computed(() => {
    const s = new Set(questions.value.map(q => q.subject).filter(Boolean));
    return Array.from(s);
});

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '类型', dataIndex: 'type', width: 100, customRender: ({text}) => {
      const map = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', SHORT: '简答' };
      return map[text] || text;
  }},
  { title: '内容', dataIndex: 'content', ellipsis: true },
  { title: '难度/分值', dataIndex: 'defaultScore', width: 100 }
];

const fetchQuestions = async () => {
    loading.value = true;
    try {
        const res = await getAllQuestions();
        questions.value = res.data;
    } catch (e) {
        message.error('加载题库失败');
    } finally {
        loading.value = false;
    }
};

watch(() => props.open, (val) => {
    if (val) {
        fetchQuestions();
        selectedRowKeys.value = [];
    }
});

const handleOk = async () => {
    if (selectedRowKeys.value.length === 0) {
        message.warning('请选择题目');
        return;
    }
    
    loading.value = true;
    try {
        for (const qId of selectedRowKeys.value) {
            const q = questions.value.find(item => item.id === qId);
            await addQuestionToExam(props.examId, { 
                questionId: qId, 
                score: q ? q.defaultScore : 5, 
                sequence: 99 
            });
        }
        message.success(`成功添加 ${selectedRowKeys.value.length} 道题目`);
        emit('success');
        emit('update:open', false);
    } catch (e) {
        message.error('添加失败');
    } finally {
        loading.value = false;
    }
};

const handleAutoGenerate = () => {
    emit('autoGenerate');
    emit('update:open', false);
};

const handleCancel = () => {
    emit('update:open', false);
};

// Random Generate
const handleRandom = async () => {
    if (questions.value.length < 5) {
        message.warning('题库题目不足，无法随机生成');
        return;
    }
    
    // Pick 5 random
    const shuffled = [...questions.value].sort(() => 0.5 - Math.random());
    const selected = shuffled.slice(0, 5);
    selectedRowKeys.value = selected.map(q => q.id);
    message.success('已随机选择 5 道题目，请点击确定添加到试卷');
};
</script>

<template>
  <a-modal :open="open" title="题库选题" @ok="handleOk" @cancel="handleCancel" width="900px" :confirmLoading="loading">
    <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
        <a-select v-model:value="filterSubject" placeholder="按科目筛选" style="width: 120px" allowClear>
            <a-select-option v-for="s in distinctSubjects" :key="s" :value="s">{{ s }}</a-select-option>
        </a-select>
        <a-select v-model:value="filterType" placeholder="按类型" style="width: 100px" allowClear>
            <a-select-option value="SINGLE">单选</a-select-option>
            <a-select-option value="MULTI">多选</a-select-option>
            <a-select-option value="JUDGE">判断</a-select-option>
            <a-select-option value="SHORT">简答</a-select-option>
        </a-select>
        <a-select v-model:value="filterDifficulty" placeholder="难度" style="width: 80px" allowClear>
            <a-select-option :value="1">1</a-select-option>
            <a-select-option :value="2">2</a-select-option>
            <a-select-option :value="3">3</a-select-option>
            <a-select-option :value="4">4</a-select-option>
            <a-select-option :value="5">5</a-select-option>
        </a-select>
        
        <a-divider type="vertical" />
        
        <a-button @click="handleRandom">随机 5 题</a-button>
        <a-button type="primary" danger ghost @click="handleAutoGenerate">智能组卷</a-button>
        <span style="color: #999; font-size: 12px;">(提示: 选中后点击确定加入)</span>
    </div>
    
    <a-table 
        :dataSource="filteredQuestions" 
        :columns="columns" 
        rowKey="id"
        :row-selection="{ selectedRowKeys: selectedRowKeys, onChange: (keys) => selectedRowKeys = keys }"
        :pagination="{ pageSize: 5 }"
        size="small"
    />
  </a-modal>
</template>
