<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { ClockCircleOutlined, CalendarOutlined, BookOutlined } from '@ant-design/icons-vue';

const props = defineProps(['open', 'exam']);
const emit = defineEmits(['update:open']);
const router = useRouter();

const handleCancel = () => {
  emit('update:open', false);
};

const handleStart = () => {
  if (canStart.value) {
    router.push(`/exam/${props.exam.id}`);
  }
};

const status = computed(() => {
  if (!props.exam) return {};
  const now = new Date();
  const start = props.exam.startTime ? new Date(props.exam.startTime) : null;
  const end = props.exam.endTime ? new Date(props.exam.endTime) : null;

  if (start && now < start) return { text: '未开始', color: 'blue', allow: false };
  if (end && now > end) return { text: '已结束', color: 'red', allow: false };
  return { text: '进行中', color: 'green', allow: true };
});

const canStart = computed(() => status.value.allow);

const durationText = computed(() => props.exam?.duration ? `${props.exam.duration} 分钟` : '无限制');
const timeRangeText = computed(() => {
  const format = (d) => d ? new Date(d).toLocaleString() : '无';
  return `${format(props.exam?.startTime)} ~ ${format(props.exam?.endTime)}`;
});
</script>

<template>
  <a-modal
    :open="open"
    title="考试详情"
    @cancel="handleCancel"
    :footer="null"
    width="600px"
  >
    <div v-if="exam" class="detail-container">
       <div class="header">
          <h2 class="title">{{ exam.title }}</h2>
          <a-tag :color="status.color">{{ status.text }}</a-tag>
       </div>
       
       <a-descriptions bordered :column="1" style="margin-top: 20px;">
          <a-descriptions-item label="所属课程">
             <BookOutlined /> {{ exam.course || '未指定' }}
          </a-descriptions-item>
          <a-descriptions-item label="考试时间">
             <CalendarOutlined /> {{ timeRangeText }}
          </a-descriptions-item>
          <a-descriptions-item label="考试时长">
             <ClockCircleOutlined /> {{ durationText }}
          </a-descriptions-item>
          <a-descriptions-item label="描述">
             {{ exam.description || '暂无描述' }}
          </a-descriptions-item>
       </a-descriptions>
       
       <div class="actions" style="margin-top: 24px; text-align: right;">
          <a-button @click="handleCancel" style="margin-right: 12px;">关闭</a-button>
          <a-button type="primary" :disabled="!canStart" @click="handleStart">
             {{ canStart ? '开始考试' : '当前无法参加' }}
          </a-button>
       </div>
    </div>
  </a-modal>
</template>

<style scoped>
.title {
  display: inline-block;
  margin-right: 12px;
  margin-bottom: 0;
}
</style>
