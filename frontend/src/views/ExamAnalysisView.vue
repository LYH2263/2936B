<script setup>
import { ref, shallowRef, onMounted, computed, nextTick, h } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getStatistics, exportExamStatistics } from '@/api';
import * as echarts from 'echarts';
import { message } from 'ant-design-vue';
import { 
  DownloadOutlined, UserOutlined, FileTextOutlined, 
  BarChartOutlined, PrinterOutlined, LeftOutlined,
  DashboardOutlined, TeamOutlined, RiseOutlined
} from '@ant-design/icons-vue';
import dayjs from 'dayjs';

const route = useRoute();
const router = useRouter();
const examId = route.params.id;

const loading = ref(false);
const stats = shallowRef(null);
const activeTab = ref('overall');
const barChartRef = ref(null);
const pieChartRef = ref(null);
const difficultyChartRef = ref(null);

let barChartInstance = null;
let pieChartInstance = null;
let difficultyChartInstance = null;

const fetchStats = async () => {
  loading.value = true;
  try {
    const res = await getStatistics(examId);
    stats.value = res.data;
    
    await nextTick();
    if (activeTab.value === 'overall') {
       initOverviewCharts();
    } else if (activeTab.value === 'questions') {
       initQuestionCharts();
    }
  } catch (e) {
    message.error('加载统计数据失败');
  } finally {
    loading.value = false;
  }
};

const initOverviewCharts = () => {
  if (!stats.value) return;
  
  // Bar Chart: Score Distribution
  if (barChartRef.value) {
    if (barChartInstance) barChartInstance.dispose();
    barChartInstance = echarts.init(barChartRef.value);
    const dist = stats.value.scoreDistribution || {};
    const xAxisData = ['0-59', '60-69', '70-79', '80-89', '90-100'];
    const seriesData = xAxisData.map(key => dist[key] || 0);
    barChartInstance.setOption({
      title: { text: '成绩分段分布图', left: 'center', textStyle: { fontWeight: 'normal', fontSize: 16 } },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
      xAxis: { type: 'category', data: xAxisData, axisTick: { alignWithLabel: true } },
      yAxis: { type: 'value', name: '人数', splitLine: { lineStyle: { type: 'dashed' } } },
      series: [{
        name: '人数',
        type: 'bar',
        data: seriesData,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#1890ff' },
            { offset: 1, color: '#36cfc9' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        barWidth: '40%'
      }]
    });
  }

  // Ring Chart: Pass Rate
  if (pieChartRef.value) {
    if (pieChartInstance) pieChartInstance.dispose();
    pieChartInstance = echarts.init(pieChartRef.value);
    const passCount = Math.round(stats.value.totalSubmissions * (stats.value.passRate / 100));
    const failCount = stats.value.totalSubmissions - passCount;
    pieChartInstance.setOption({
      title: { text: '考试及格占比', left: 'center', top: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
      legend: { bottom: '0', left: 'center', icon: 'circle' },
      series: [{
        type: 'pie',
        radius: ['50%', '75%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: false } },
        data: [
          { value: passCount, name: '及格', itemStyle: { color: '#52c41a' } },
          { value: failCount, name: '不及格', itemStyle: { color: '#ff4d4f' } }
        ]
      }]
    });
  }
};

const initQuestionCharts = () => {
    if (!difficultyChartRef.value || !stats.value) return;
    if (difficultyChartInstance) difficultyChartInstance.dispose();
    difficultyChartInstance = echarts.init(difficultyChartRef.value);
    
    const data = stats.value.questionAnalysis || [];
    difficultyChartInstance.setOption({
      title: { text: '题目正确率趋势', left: 'center' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.map(q => `Q${q.sequence}`), name: '题号' },
      yAxis: { type: 'value', name: '正确率 %', min: 0, max: 100 },
      series: [{
        data: data.map(q => q.correctRate),
        type: 'line',
        smooth: true,
        symbolSize: 8,
        lineStyle: { width: 3, color: '#722ed1' },
        areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(114, 46, 209, 0.3)' },
                { offset: 1, color: 'rgba(114, 46, 209, 0)' }
            ])
        },
        itemStyle: { color: '#722ed1' }
      }]
    });
};

const handleExport = async () => {
  try {
    const res = await exportExamStatistics(examId);
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `考试统计_${stats.value?.title || examId}_${dayjs().format('YYYYMMDD')}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    message.success('导出成功');
  } catch (e) {
    message.error('导出概况失败');
  }
};

const handleViewSubmission = (submissionId) => {
  router.push(`/score/${submissionId}`);
};

onMounted(fetchStats);

const rankingColumns = [
  { title: '排名', dataIndex: 'rank', key: 'rank', width: 80, align: 'center', fixed: 'left' },
  { title: '工号/学号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'fullName', key: 'fullName' },
  { title: '得分', dataIndex: 'score', key: 'score', sorter: (a, b) => a.score - b.score },
  { title: '耗时', dataIndex: 'usedTime', key: 'usedTime', customRender: ({text}) => text ? `${text}分钟` : '-' },
  { title: '操作', key: 'action', width: 120, fixed: 'right' }
];

const questionColumns = [
  { title: '顺序', dataIndex: 'sequence', key: 'sequence', width: 80, align: 'center' },
  { title: '题目预览', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '分值', dataIndex: 'score', key: 'score', width: 80 },
  { title: '平均分', dataIndex: 'averageScore', key: 'averageScore', customRender: ({text}) => text.toFixed(1) },
  { title: '正确率', dataIndex: 'correctRate', key: 'correctRate', width: 200 }
];

const handleTabChange = (key) => {
    if (key === 'overall') nextTick(() => initOverviewCharts());
    if (key === 'questions') nextTick(() => initQuestionCharts());
}
</script>

<template>
  <div class="analysis-page">
    <div class="analysis-header">
      <div class="header-left">
        <a-button type="link" @click="router.back()">
          <LeftOutlined /> 返回仪表盘
        </a-button>
        <a-divider type="vertical" />
        <h2 v-if="stats">{{ stats.title }} - 数据深度分析</h2>
        <a-skeleton-input v-else active size="small" style="width: 200px" />
      </div>
      <div class="header-right no-print">
         <a-space>
             <a-button @click="window.print()">
               <PrinterOutlined /> 打印报告
             </a-button>
             <a-button type="primary" @click="handleExport">
               <DownloadOutlined /> 导出数据
             </a-button>
         </a-space>
      </div>
    </div>

    <div class="analysis-content">
      <div v-if="loading" class="loading-state">
        <a-spin size="large" tip="正在生成深度分析报告..." />
      </div>
      
      <div v-else-if="stats" class="stats-dashboard">
        <!-- Summary Cards -->
        <a-row :gutter="20" class="mb-24">
          <a-col :span="6">
            <div class="stat-card">
              <div class="icon-box blue"><DashboardOutlined /></div>
              <div class="card-info">
                <div class="label">实考人数 / 应考人数</div>
                <div class="value">{{ stats.totalSubmissions }} <small>/ {{ stats.totalParticipants }}</small></div>
              </div>
            </div>
          </a-col>
          <a-col :span="6">
            <div class="stat-card">
              <div class="icon-box purple"><RiseOutlined /></div>
              <div class="card-info">
                <div class="label">全卷平均分</div>
                <div class="value">{{ stats.averageScore.toFixed(1) }}</div>
              </div>
            </div>
          </a-col>
          <a-col :span="6">
            <div class="stat-card">
              <div class="icon-box green"><TeamOutlined /></div>
              <div class="card-info">
                <div class="label">整体及格率</div>
                <div class="value" :style="{ color: stats.passRate >= 60 ? '#52c41a' : '#f5222d' }">
                  {{ stats.passRate.toFixed(1) }}%
                </div>
              </div>
            </div>
          </a-col>
          <a-col :span="6">
            <div class="stat-card">
              <div class="icon-box orange"><RiseOutlined /></div>
              <div class="card-info">
                <div class="label">最高分 / 总分</div>
                <div class="value">{{ stats.maxScore }} <small>/ {{ stats.examTotalScore }}</small></div>
              </div>
            </div>
          </a-col>
        </a-row>

        <div class="main-tabs-wrapper">
          <a-tabs v-model:activeKey="activeTab" type="card" @change="handleTabChange">
            <!-- Overall View -->
            <a-tab-pane key="overall" tab="多维概况">
              <div class="tab-pane-inner">
                <a-row :gutter="24">
                  <a-col :span="14">
                    <div class="chart-container shadow">
                      <div ref="barChartRef" style="height: 450px;"></div>
                    </div>
                  </a-col>
                  <a-col :span="10">
                    <div class="chart-container shadow">
                      <div ref="pieChartRef" style="height: 450px;"></div>
                    </div>
                  </a-col>
                </a-row>
              </div>
            </a-tab-pane>

            <!-- Question Analysis -->
            <a-tab-pane key="questions" tab="题目效度分析">
              <div class="tab-pane-inner">
                 <div class="chart-container shadow mb-24">
                    <div ref="difficultyChartRef" style="height: 300px;"></div>
                 </div>
                 <a-table 
                    :dataSource="stats.questionAnalysis" 
                    :columns="questionColumns" 
                    :pagination="false"
                    class="analysis-table shadow"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'correctRate'">
                         <div class="progress-box">
                            <a-progress 
                              :percent="Math.round(record.correctRate)" 
                              size="small" 
                              :status="record.correctRate < 50 ? 'exception' : 'normal'"
                              :stroke-color="record.correctRate >= 80 ? '#52c41a' : '' "
                            />
                            <span class="rate-text">{{ record.correctRate.toFixed(1) }}%</span>
                         </div>
                      </template>
                      <template v-if="column.key === 'type'">
                        <a-tag :color="record.type === 'SINGLE' ? 'blue' : (record.type === 'MULTI' ? 'orange' : 'green')">
                          {{ record.type }}
                        </a-tag>
                      </template>
                      <template v-if="column.key === 'content'">
                         <div v-html="record.content"></div>
                      </template>
                    </template>
                  </a-table>
              </div>
            </a-tab-pane>

            <!-- Rankings -->
            <a-tab-pane key="rankings" tab="考生表现明细">
              <div class="tab-pane-inner">
                <a-table 
                  :dataSource="stats.rankings" 
                  :columns="rankingColumns" 
                  class="analysis-table shadow"
                  :pagination="{ pageSize: 20 }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'rank'">
                       <div class="rank-badge" :class="{ 'top-3': record.rank <= 3 }">
                          {{ record.rank }}
                       </div>
                    </template>
                    <template v-if="column.key === 'action'">
                      <a-button type="link" @click="handleViewSubmission(record.submissionId)">
                        查看作答详情
                      </a-button>
                    </template>
                  </template>
                </a-table>
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>
      </div>

      <div v-else class="empty-state">
         <a-empty description="暂无考试数据，待学生提交后即可生成分析报告" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-page {
  min-height: 100vh;
  background: #f7f9fb;
}

.analysis-header {
  height: 64px;
  background: #fff;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header-left {
  display: flex;
  align-items: center;
}
.header-left h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.analysis-content {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.mb-24 { margin-bottom: 24px; }

.loading-state, .empty-state {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 12px;
}

/* Stat Cards */
.stat-card {
  background: #fff;
  padding: 24px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
  transition: transform 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
}
.icon-box {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.icon-box.blue { background: #e6f7ff; color: #1890ff; }
.icon-box.purple { background: #f9f0ff; color: #722ed1; }
.icon-box.green { background: #f6ffed; color: #52c41a; }
.icon-box.orange { background: #fff7e6; color: #fa8c16; }

.card-info .label {
  color: #8c8c8c;
  font-size: 13px;
  margin-bottom: 4px;
}
.card-info .value {
  font-size: 24px;
  font-weight: bold;
  color: #262626;
  line-height: 1;
}
.card-info .value small {
  font-size: 14px;
  color: #bfbfbf;
  font-weight: normal;
}

/* Charts & Tables */
.main-tabs-wrapper {
  background: transparent;
}
:deep(.ant-tabs-card > .ant-tabs-nav .ant-tabs-tab) {
  border-radius: 8px 8px 0 0;
  background: #fff;
  border: none;
  margin-right: 4px;
}
:deep(.ant-tabs-card > .ant-tabs-nav .ant-tabs-tab-active) {
  background: #1890ff;
  color: #fff !important;
}

.tab-pane-inner {
  padding-top: 8px;
}

.chart-container {
  background: #fff;
  padding: 24px;
  border-radius: 12px;
  margin-bottom: 24px;
}

.shadow {
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
}

.analysis-table {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.progress-box {
    display: flex;
    align-items: center;
    gap: 12px;
}
.rate-text {
    width: 50px;
    font-size: 12px;
    color: #595959;
}

.rank-badge {
    width: 28px;
    height: 28px;
    background: #f0f0f0;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
}
.rank-badge.top-3 {
    background: #fff7e6;
    color: #faad14;
    border: 1px solid #ffe58f;
}

@media print {
  .no-print { display: none !important; }
  .analysis-page { background: #fff; }
  .analysis-content { padding: 0; }
  .stat-card { border: 1px solid #eee; box-shadow: none; }
  .chart-container, .analysis-table { box-shadow: none; border: 1px solid #eee; }
}
</style>
