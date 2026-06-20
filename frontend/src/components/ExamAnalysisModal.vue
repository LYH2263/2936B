<script setup>
import { ref, onMounted, watch, nextTick, h } from 'vue';
import { getStatistics, exportExamStatistics } from '@/api';
import * as echarts from 'echarts';
import { message } from 'ant-design-vue';
import { DownloadOutlined, UserOutlined, FileTextOutlined, BarChartOutlined, PrinterOutlined } from '@ant-design/icons-vue';

const props = defineProps(['open', 'examId']);
const emit = defineEmits(['update:open', 'viewSubmission']);

const loading = ref(false);
const stats = ref(null);
const activeTab = ref('overall');
const barChartRef = ref(null);
const pieChartRef = ref(null);
let barChartInstance = null;
let pieChartInstance = null;

const fetchStats = async () => {
  if (!props.examId) return;
  loading.value = true;
  try {
    const res = await getStatistics(props.examId);
    stats.value = res.data;
    
    if (activeTab.value === 'overall') {
       await nextTick();
       initCharts();
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const initCharts = () => {
  if (!stats.value) return;
  
  // Bar Chart
  if (barChartRef.value) {
    if (barChartInstance) barChartInstance.dispose();
    barChartInstance = echarts.init(barChartRef.value);
    const dist = stats.value.scoreDistribution;
    const xAxisData = ['0-59', '60-69', '70-79', '80-89', '90-100'];
    const seriesData = xAxisData.map(key => dist[key] || 0);
    barChartInstance.setOption({
      title: { text: '成绩分段分布', left: 'center' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: xAxisData, name: '分数段' },
      yAxis: { type: 'value', name: '人数' },
      series: [{
        data: seriesData,
        type: 'bar',
        barWidth: '60%',
        itemStyle: { color: '#1890ff' }
      }]
    });
  }

  // Pie Chart
  if (pieChartRef.value) {
    if (pieChartInstance) pieChartInstance.dispose();
    pieChartInstance = echarts.init(pieChartRef.value);
    const passCount = Math.round(stats.value.totalSubmissions * stats.value.passRate / 100);
    const failCount = stats.value.totalSubmissions - passCount;
    pieChartInstance.setOption({
      title: { text: '及格情况统计', left: 'center' },
      tooltip: { trigger: 'item' },
      legend: { bottom: '0', left: 'center' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: '20', fontWeight: 'bold' } },
        labelLine: { show: false },
        data: [
          { value: passCount, name: '及格', itemStyle: { color: '#52c41a' } },
          { value: failCount, name: '不及格', itemStyle: { color: '#ff4d4f' } }
        ]
      }]
    });
  }
};

const handleExport = async () => {
  try {
    const res = await exportExamStatistics(props.examId);
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `考试统计_${props.examId}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (e) {
    message.error('导出失败');
  }
};

const handlePrint = () => {
  window.print();
};

watch(() => props.open, (newVal) => {
  if (newVal) {
    fetchStats();
  }
});

watch(activeTab, (newTab) => {
  if (newTab === 'overall') {
    nextTick(() => initCharts());
  }
});

const handleCancel = () => {
  emit('update:open', false);
};

const rankingColumns = [
  { title: '排名', dataIndex: 'rank', key: 'rank', width: 80, align: 'center' },
  { title: '学号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'fullName', key: 'fullName' },
  { title: '得分', dataIndex: 'score', key: 'score', sorter: (a, b) => a.score - b.score },
  { title: '操作', key: 'action', width: 120 }
];

const questionColumns = [
  { title: '题号', dataIndex: 'sequence', key: 'sequence', width: 80, align: 'center' },
  { title: '内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '题型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '平均得分', dataIndex: 'averageScore', key: 'averageScore', customRender: ({text}) => text.toFixed(1) },
  { title: '正确率', dataIndex: 'correctRate', key: 'correctRate', customRender: ({text}) => text.toFixed(1) + '%' }
];

const handleViewSubmission = (submissionId) => {
  emit('viewSubmission', submissionId);
  emit('update:open', false);
};
</script>

<template>
  <a-modal
    :open="open"
    title="考试数据分析"
    @cancel="handleCancel"
    :footer="null"
    width="1000px"
    class="analysis-modal"
  >
    <div v-if="loading" style="text-align: center; padding: 100px;">
      <a-spin size="large" />
    </div>
    <div v-else-if="stats" id="printable-area">
      <div class="header-actions">
        <div class="exam-info">
          <h2 style="margin-bottom: 4px;">{{ stats.title }}</h2>
          <span style="color: #8c8c8c;">统计日期: {{ new Date().toLocaleDateString() }}</span>
        </div>
        <div class="btn-group no-print">
          <a-button :icon="h(PrinterOutlined)" @click="handlePrint" style="margin-right: 8px;">
            打印 / 导出 PDF
          </a-button>
          <a-button type="primary" :icon="h(DownloadOutlined)" @click="handleExport">
            导出 Excel (CSV)
          </a-button>
        </div>
      </div>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="overall" tab="全体概况">
          <template #tab>
            <span><BarChartOutlined />全体概况</span>
          </template>
          <a-row :gutter="16" style="margin-bottom: 32px;">
            <a-col :span="6">
              <a-card size="small">
                <a-statistic title="实考/应考" :value="stats.totalSubmissions" :suffix="' / ' + stats.totalParticipants" />
              </a-card>
            </a-col>
            <a-col :span="6">
              <a-card size="small">
                <a-statistic title="平均分" :value="stats.averageScore" :precision="1" />
              </a-card>
            </a-col>
            <a-col :span="6">
              <a-card size="small">
                <a-statistic title="及格率" :value="stats.passRate" :precision="1" suffix="%" :value-style="{ color: stats.passRate >= 60 ? '#3f8600' : '#cf1322' }" />
              </a-card>
            </a-col>
            <a-col :span="6">
              <a-card size="small">
                <a-statistic title="最高分" :value="stats.maxScore" :value-style="{ color: '#1890ff' }" />
              </a-card>
            </a-col>
          </a-row>
          
          <a-row :gutter="16">
            <a-col :span="14">
              <div ref="barChartRef" style="width: 100%; height: 400px;"></div>
            </a-col>
            <a-col :span="10">
              <div ref="pieChartRef" style="width: 100%; height: 400px;"></div>
            </a-col>
          </a-row>
        </a-tab-pane>

        <a-tab-pane key="rankings" tab="成绩排行">
          <template #tab>
            <span><UserOutlined />成绩排行</span>
          </template>
          <a-table 
            :dataSource="stats.rankings" 
            :columns="rankingColumns" 
            size="middle"
            :pagination="{ pageSize: 12 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'rank'">
                 <a-tag :color="record.rank <= 3 ? 'gold' : ''">{{ record.rank }}</a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-button type="link" @click="handleViewSubmission(record.submissionId)" class="no-print">查看详情</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="questions" tab="题目分析">
          <template #tab>
            <span><FileTextOutlined />题目分析</span>
          </template>
          <a-table 
            :dataSource="stats.questionAnalysis" 
            :columns="questionColumns" 
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'correctRate'">
                <a-progress 
                  :percent="Math.round(record.correctRate)" 
                  size="small" 
                  :status="record.correctRate < 50 ? 'exception' : 'normal'"
                />
              </template>
              <template v-if="column.key === 'type'">
                <a-tag color="blue">{{ record.type }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </div>
    <div v-else style="padding: 100px; text-align: center;">
      <a-empty description="暂无数据" />
    </div>
  </a-modal>
</template>

<style scoped>
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

:deep(.ant-card) {
  background: #fafafa;
}

@media print {
  .no-print {
    display: none !important;
  }
  .analysis-modal {
    width: 100% !important;
  }
  :deep(.ant-modal-close) {
    display: none !important;
  }
  :deep(.ant-tabs-nav) {
    display: none !important;
  }
  :deep(.ant-tabs-content-holder) {
    display: block !important;
  }
  :deep(.ant-tab-pane) {
    display: block !important;
    opacity: 1 !important;
  }
  #printable-area {
    padding: 20px;
  }
}
</style>
