<script setup>
import { ref, computed, onMounted, watch, nextTick, h } from 'vue';
import { message, Modal } from 'ant-design-vue';
import {
  WarningOutlined, SearchOutlined, DownloadOutlined,
  ExclamationCircleOutlined, CheckCircleOutlined,
  SyncOutlined, ReloadOutlined, BellOutlined,
  TeamOutlined, ReadOutlined, FileTextOutlined, FilterOutlined
} from '@ant-design/icons-vue';
import * as echarts from 'echarts';
import {
  scanLearningAlerts, getLearningAlerts, getAlertStats,
  resolveAlert, resolveAlertsBatch, exportLearningAlerts
} from '@/api';
import { useAuthStore } from '@/stores/auth';

const authStore = useAuthStore();

const loading = ref(false);
const scanning = ref(false);
const exporting = ref(false);
const dataList = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(15);
const selectedRowKeys = ref([]);
const detailVisible = ref(false);
const currentDetail = ref(null);

const stats = ref({
  totalUnresolved: 0,
  bySeverity: { HIGH: 0, MEDIUM: 0, LOW: 0 },
  byType: { CONSECUTIVE_LOW_SCORE: 0, KNOWLEDGE_POINT_LOW: 0, LONG_TIME_NO_EXAM: 0 }
});

const filters = ref({
  alertType: null,
  severity: null,
  isResolved: null,
  keyword: null
});

const resolvedKeyword = computed(() => filters.value.keyword?.trim() || null);

const typeOptions = [
  { label: '连续低分', value: 'CONSECUTIVE_LOW_SCORE' },
  { label: '知识点薄弱', value: 'KNOWLEDGE_POINT_LOW' },
  { label: '长期缺考', value: 'LONG_TIME_NO_EXAM' }
];

const severityOptions = [
  { label: '严重 (红色)', value: 'HIGH' },
  { label: '中等 (橙色)', value: 'MEDIUM' },
  { label: '轻微 (黄色)', value: 'LOW' }
];

const resolvedOptions = [
  { label: '全部', value: null },
  { label: '未处理', value: false },
  { label: '已处理', value: true }
];

const typeTextMap = {
  CONSECUTIVE_LOW_SCORE: '连续低分',
  KNOWLEDGE_POINT_LOW: '知识点薄弱',
  LONG_TIME_NO_EXAM: '长期缺考'
};

const severityTextMap = { HIGH: '严重', MEDIUM: '中等', LOW: '轻微' };
const severityColorMap = { HIGH: 'red', MEDIUM: 'orange', LOW: 'gold' };
const severityTagColorMap = { HIGH: 'red', MEDIUM: 'orange', LOW: 'gold' };

const fetchStats = async () => {
  try {
    const res = await getAlertStats();
    stats.value = res.data;
  } catch (e) {
    console.error(e);
  }
};

const fetchList = async () => {
  loading.value = true;
  try {
    const params = {
      page: page.value,
      size: pageSize.value,
      alertType: filters.value.alertType,
      severity: filters.value.severity,
      isResolved: filters.value.isResolved
    };
    const res = await getLearningAlerts(params);
    let list = res.data.list;
    if (resolvedKeyword.value) {
      const kw = resolvedKeyword.value.toLowerCase();
      list = list.filter(a =>
        (a.student?.fullName || '').toLowerCase().includes(kw) ||
        (a.student?.username || '').toLowerCase().includes(kw) ||
        (a.student?.clazz || '').toLowerCase().includes(kw) ||
        (a.title || '').toLowerCase().includes(kw) ||
        (a.detail || '').toLowerCase().includes(kw)
      );
    }
    dataList.value = list;
    total.value = resolvedKeyword.value ? list.length : res.data.total;
    nextTick(() => {
      document.querySelectorAll('.sparkline-container').forEach(renderSparkline);
    });
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const renderSparkline = (el) => {
  const id = el.dataset.id;
  const trend = JSON.parse(el.dataset.trend || '[]');
  if (!id || !trend || trend.length === 0) {
    el.innerHTML = '<span style="color:#999;font-size:12px">暂无数据</span>';
    return;
  }
  const chart = echarts.init(el);
  chart.setOption({
    grid: { top: 5, right: 5, bottom: 5, left: 5 },
    xAxis: { type: 'category', show: false, data: trend.map((_, i) => i) },
    yAxis: { type: 'value', show: false, min: 0, max: 100 },
    series: [{
      type: 'line',
      data: trend,
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2, color: trend[trend.length - 1] < 60 ? '#ff4d4f' : '#52c41a' },
      itemStyle: { color: trend[trend.length - 1] < 60 ? '#ff4d4f' : '#52c41a' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: trend[trend.length - 1] < 60 ? 'rgba(255,77,79,0.3)' : 'rgba(82,196,26,0.3)' },
          { offset: 1, color: 'rgba(0,0,0,0)' }
        ])
      },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { color: '#faad14', type: 'dashed', width: 1 },
        data: [{ yAxis: 60, label: { show: false } }]
      }
    }]
  });
};

const handleScan = async () => {
  Modal.confirm({
    title: '确认执行扫描',
    icon: () => h(ExclamationCircleOutlined, { style: 'color:#faad14' }),
    content: '将对所有学生执行预警扫描，可能需要较长时间。是否继续？',
    okText: '开始扫描',
    cancelText: '取消',
    okType: 'primary',
    onOk: async () => {
      scanning.value = true;
      try {
        const res = await scanLearningAlerts();
        const s = res.data.stats;
        message.success(
          `扫描完成！新增连续低分 ${s.CONSECUTIVE_LOW_SCORE || 0} 条，` +
          `知识点薄弱 ${s.KNOWLEDGE_POINT_LOW || 0} 条，` +
          `长期缺考 ${s.LONG_TIME_NO_EXAM || 0} 条`
        );
        fetchStats();
        fetchList();
      } catch (e) {
        message.error('扫描失败');
      } finally {
        scanning.value = false;
      }
    }
  });
};

const handleResolve = async (record) => {
  try {
    await resolveAlert(record.id);
    message.success('已标记为已处理');
    fetchStats();
    fetchList();
  } catch (e) {
    message.error('操作失败');
  }
};

const handleBatchResolve = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要处理的预警');
    return;
  }
  Modal.confirm({
    title: '批量标记已处理',
    content: `确定将选中的 ${selectedRowKeys.value.length} 条预警标记为已处理吗？`,
    okType: 'primary',
    onOk: async () => {
      try {
        await resolveAlertsBatch(selectedRowKeys.value);
        message.success(`已批量处理 ${selectedRowKeys.value.length} 条预警`);
        selectedRowKeys.value = [];
        fetchStats();
        fetchList();
      } catch (e) {
        message.error('操作失败');
      }
    }
  });
};

const handleExport = async () => {
  exporting.value = true;
  try {
    const params = {
      alertType: filters.value.alertType,
      severity: filters.value.severity,
      isResolved: filters.value.isResolved
    };
    const res = await exportLearningAlerts(params);
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    const disposition = res.headers['content-disposition'] || '';
    let filename = 'learning-alerts.xlsx';
    const match = disposition.match(/filename\*?=UTF-8''([^;]+)/i) ||
                  disposition.match(/filename="?([^"]+)"?/i);
    if (match) filename = decodeURIComponent(match[1]);
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    message.success('导出成功');
  } catch (e) {
    message.error('导出失败');
  } finally {
    exporting.value = false;
  }
};

const showDetail = async (record) => {
  try {
    const res = await getAlertDetail(record.id);
    currentDetail.value = res.data;
    detailVisible.value = true;
    nextTick(() => {
      const el = document.getElementById('detail-sparkline');
      if (el && currentDetail.value?.scoreTrend?.length) {
        const chart = echarts.init(el);
        const trend = currentDetail.value.scoreTrend;
        chart.setOption({
          title: { text: '成绩趋势（%）', left: 'center', textStyle: { fontSize: 14 } },
          tooltip: { trigger: 'axis', formatter: p => `第${p[0].dataIndex + 1}次考试: ${p[0].value}%` },
          grid: { top: 50, right: 20, bottom: 30, left: 40 },
          xAxis: { type: 'category', data: trend.map((_, i) => `第${i + 1}次`) },
          yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
          series: [{
            type: 'line', data: trend, smooth: true, symbolSize: 8,
            lineStyle: { width: 3, color: '#722ed1' },
            itemStyle: { color: '#722ed1' },
            areaStyle: { color: 'rgba(114,46,209,0.15)' },
            markLine: {
              symbol: 'none', lineStyle: { color: '#ff4d4f', type: 'dashed' },
              data: [{ yAxis: 60, label: { formatter: '及格线 60%' } }]
            }
          }]
        });
      }
    });
  } catch (e) {
    message.error('加载详情失败');
  }
};

const resetFilters = () => {
  filters.value = { alertType: null, severity: null, isResolved: null, keyword: null };
  page.value = 1;
  fetchList();
};

const onPageChange = (p) => {
  page.value = p;
  fetchList();
};

const columns = [
  {
    title: '严重程度',
    dataIndex: 'severity',
    key: 'severity',
    width: 100,
    fixed: 'left',
    customRender: ({ record }) => {
      const color = severityTagColorMap[record.severity] || 'default';
      const icon = record.severity === 'HIGH' ? h(ExclamationCircleOutlined) :
                    record.severity === 'MEDIUM' ? h(WarningOutlined) : h(BellOutlined);
      return h('span', null, [
        h('a-tag', { color, style: { fontWeight: 600 } }, () => [
          icon,
          ' ' + (severityTextMap[record.severity] || record.severity)
        ])
      ]);
    }
  },
  {
    title: '预警类型',
    dataIndex: 'alertType',
    key: 'alertType',
    width: 120,
    customRender: ({ record }) => {
      const map = {
        CONSECUTIVE_LOW_SCORE: { icon: h(FileTextOutlined), color: 'red' },
        KNOWLEDGE_POINT_LOW: { icon: h(ReadOutlined), color: 'orange' },
        LONG_TIME_NO_EXAM: { icon: h(TeamOutlined), color: 'purple' }
      };
      const cfg = map[record.alertType] || { color: 'default' };
      return h('a-tag', { color: cfg.color }, () => [
        cfg.icon, ' ' + (typeTextMap[record.alertType] || record.alertType)
      ]);
    }
  },
  {
    title: '学生信息',
    key: 'student',
    width: 180,
    customRender: ({ record }) => {
      const s = record.student || {};
      return h('div', null, [
        h('div', { style: { fontWeight: 500 } }, s.fullName || '未知'),
        h('div', { style: { color: '#888', fontSize: 12 } },
          `${s.username || ''}${s.clazz ? ' · ' + s.clazz : ''}`)
      ]);
    }
  },
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true, width: 180 },
  {
    title: '详情',
    dataIndex: 'detail',
    key: 'detail',
    ellipsis: true,
    width: 260,
    customRender: ({ text }) => h('span', { style: { color: '#555' } }, text)
  },
  {
    title: '成绩趋势',
    key: 'trend',
    width: 160,
    customRender: ({ record }) => {
      const trend = record.scoreTrend || [];
      return h('div', {
        class: 'sparkline-container',
        'data-id': 'spark-' + record.id,
        'data-trend': JSON.stringify(trend),
        style: { width: '150px', height: '48px' }
      });
    }
  },
  {
    title: '状态',
    dataIndex: 'isResolved',
    key: 'isResolved',
    width: 100,
    customRender: ({ record }) => {
      if (record.isResolved) {
        return h('a-tag', { color: 'green', icon: () => h(CheckCircleOutlined) }, () => '已处理');
      }
      return h('a-tag', { color: severityTagColorMap[record.severity] || 'red', icon: () => h(WarningOutlined) }, () => '待处理');
    }
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 170,
    sorter: (a, b) => new Date(a.createdAt) - new Date(b.createdAt),
    customRender: ({ text }) => text ? new Date(text).toLocaleString('zh-CN') : '-'
  },
  {
    title: '操作',
    key: 'action',
    width: 180,
    fixed: 'right',
    customRender: ({ record }) => {
      return h('div', null, [
        h('a-button', {
          type: 'link', size: 'small',
          onClick: () => showDetail(record)
        }, () => '查看详情'),
        !record.isResolved ? [
          h('a-divider', { type: 'vertical' }),
          h('a-popconfirm', {
            title: '标记为已处理？',
            okText: '确认',
            cancelText: '取消',
            onConfirm: () => handleResolve(record)
          }, {
            default: () => h('a-button', { type: 'link', size: 'small', danger: true }, () => '标记处理')
          })
        ] : null
      ]);
    }
  }
];

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys) => { selectedRowKeys.value = keys; },
  getCheckboxProps: (record) => ({ disabled: record.isResolved })
}));

watch([() => filters.value.alertType, () => filters.value.severity, () => filters.value.isResolved], () => {
  page.value = 1;
  fetchList();
});

onMounted(() => {
  fetchStats();
  fetchList();
});
</script>

<template>
  <div class="alert-center">
    <a-card :bordered="false" class="stats-card">
      <a-row :gutter="24">
        <a-col :span="6">
          <a-card class="stat-item" :bordered="false">
            <a-statistic title="未处理预警总数" :value="stats.totalUnresolved" value-style="color:#722ed1;font-weight:700">
              <template #prefix><WarningOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-item stat-high" :bordered="false">
            <a-statistic title="严重" :value="stats.bySeverity.HIGH || 0" value-style="color:#ff4d4f;font-weight:700">
              <template #prefix><ExclamationCircleOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-item stat-medium" :bordered="false">
            <a-statistic title="中等" :value="stats.bySeverity.MEDIUM || 0" value-style="color:#fa8c16;font-weight:700">
              <template #prefix><WarningOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-item stat-low" :bordered="false">
            <a-statistic title="轻微" :value="stats.bySeverity.LOW || 0" value-style="color:#faad14;font-weight:700">
              <template #prefix><BellOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="16" style="margin-top:16px">
        <a-col :span="8">
          <div class="type-stat">
            <div class="type-label"><FileTextOutlined /> 连续低分</div>
            <div class="type-count">{{ stats.byType.CONSECUTIVE_LOW_SCORE || 0 }}</div>
          </div>
        </a-col>
        <a-col :span="8">
          <div class="type-stat">
            <div class="type-label"><ReadOutlined /> 知识点薄弱</div>
            <div class="type-count">{{ stats.byType.KNOWLEDGE_POINT_LOW || 0 }}</div>
          </div>
        </a-col>
        <a-col :span="8">
          <div class="type-stat">
            <div class="type-label"><TeamOutlined /> 长期缺考</div>
            <div class="type-count">{{ stats.byType.LONG_TIME_NO_EXAM || 0 }}</div>
          </div>
        </a-col>
      </a-row>
    </a-card>

    <a-card :bordered="false" style="margin-top:16px">
      <div class="toolbar">
        <div class="toolbar-left">
          <a-space wrap>
            <a-input
              v-model:value="filters.keyword"
              placeholder="搜索学生姓名/账号/班级/标题"
              style="width:260px"
              allowClear
              @pressEnter="fetchList"
            >
              <template #prefix><SearchOutlined /></template>
            </a-input>
            <a-select v-model:value="filters.alertType" placeholder="预警类型" style="width:140px" allowClear>
              <a-select-option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
            </a-select>
            <a-select v-model:value="filters.severity" placeholder="严重程度" style="width:140px" allowClear>
              <a-select-option v-for="s in severityOptions" :key="s.value" :value="s.value">{{ s.label }}</a-select-option>
            </a-select>
            <a-select v-model:value="filters.isResolved" placeholder="处理状态" style="width:120px" allowClear>
              <a-select-option v-for="r in resolvedOptions" :key="r.value ?? 'all'" :value="r.value">{{ r.label }}</a-select-option>
            </a-select>
            <a-button @click="fetchList"><SearchOutlined /> 搜索</a-button>
            <a-button @click="resetFilters">重置</a-button>
          </a-space>
        </div>
        <div class="toolbar-right">
          <a-space>
            <a-button :loading="scanning" type="primary" @click="handleScan">
              <SyncOutlined :spin="scanning" /> 执行扫描
            </a-button>
            <a-button
              type="primary"
              :disabled="selectedRowKeys.length === 0"
              @click="handleBatchResolve"
              ghost
            >
              <CheckCircleOutlined /> 批量处理 ({{ selectedRowKeys.length }})
            </a-button>
            <a-button :loading="exporting" @click="handleExport">
              <DownloadOutlined /> 导出 Excel
            </a-button>
            <a-button @click="() => { fetchStats(); fetchList(); }">
              <ReloadOutlined /> 刷新
            </a-button>
          </a-space>
        </div>
      </div>

      <a-table
        :loading="loading"
        :data-source="dataList"
        :columns="columns"
        :pagination="{
          current: page,
          pageSize: pageSize,
          total: total,
          showSizeChanger: false,
          showQuickJumper: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: onPageChange
        }"
        :row-selection="rowSelection"
        :row-key="(r) => r.id"
        :scroll="{ x: 1400 }"
        size="middle"
        :custom-row="(record) => ({
          style: record.isResolved ? { opacity: 0.55, background: '#fafafa' } : {}
        })"
      />
    </a-card>

    <a-modal
      v-model:open="detailVisible"
      title="预警详情"
      :footer="null"
      width="720px"
      :destroy-on-close="true"
    >
      <div v-if="currentDetail" class="detail-content">
        <div class="detail-header">
          <a-tag :color="severityTagColorMap[currentDetail.severity]" style="font-size:14px;padding:4px 12px">
            {{ severityTextMap[currentDetail.severity] || currentDetail.severity }}
          </a-tag>
          <a-tag color="purple" style="font-size:14px;padding:4px 12px">
            {{ typeTextMap[currentDetail.alertType] || currentDetail.alertType }}
          </a-tag>
          <a-tag :color="currentDetail.isResolved ? 'green' : 'red'" style="font-size:14px;padding:4px 12px">
            {{ currentDetail.isResolved ? '已处理' : '待处理' }}
          </a-tag>
        </div>

        <a-descriptions bordered size="small" :column="2" style="margin-top:16px">
          <a-descriptions-item label="学生姓名">
            <b>{{ currentDetail.student?.fullName }}</b>
          </a-descriptions-item>
          <a-descriptions-item label="学生账号">
            {{ currentDetail.student?.username }}
          </a-descriptions-item>
          <a-descriptions-item label="班级">
            {{ currentDetail.student?.clazz || '未分配' }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ currentDetail.createdAt ? new Date(currentDetail.createdAt).toLocaleString('zh-CN') : '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="预警标题" :span="2">
            <b style="color:#1890ff">{{ currentDetail.title }}</b>
          </a-descriptions-item>
          <a-descriptions-item label="详情描述" :span="2">
            <div style="line-height:1.7">{{ currentDetail.detail }}</div>
          </a-descriptions-item>
          <a-descriptions-item v-if="currentDetail.resolvedAt" label="处理时间" :span="2">
            {{ new Date(currentDetail.resolvedAt).toLocaleString('zh-CN') }}
            <span v-if="currentDetail.resolvedBy">
              （处理人：{{ currentDetail.resolvedBy.fullName }}）
            </span>
          </a-descriptions-item>
        </a-descriptions>

        <div v-if="currentDetail.scoreTrend?.length" style="margin-top:20px">
          <div id="detail-sparkline" style="width:100%;height:240px"></div>
        </div>

        <div v-if="currentDetail.relatedData" style="margin-top:20px">
          <a-divider orientation="left">关联数据</a-divider>
          <div v-if="currentDetail.relatedData.knowledgePoint">
            <h4 style="margin:0 0 8px">📚 知识点信息</h4>
            <a-descriptions size="small" bordered :column="2">
              <a-descriptions-item label="知识点">
                <b>{{ currentDetail.relatedData.knowledgePoint }}</b>
              </a-descriptions-item>
              <a-descriptions-item label="正确率">
                <a-tag :color="(currentDetail.relatedData.correctRate || 0) < 0.4 ? 'red' : 'orange'">
                  {{ ((currentDetail.relatedData.correctRate || 0) * 100).toFixed(1) }}%
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="相关题目数">{{ currentDetail.relatedData.totalQuestions }} 道</a-descriptions-item>
              <a-descriptions-item label="预警阈值">
                {{ ((currentDetail.relatedData.threshold || 0.4) * 100).toFixed(0) }}%
              </a-descriptions-item>
            </a-descriptions>
            <div v-if="currentDetail.relatedData.questionDetails?.length" style="margin-top:12px">
              <h5 style="margin:12px 0 8px;color:#555">📝 最近相关错题：</h5>
              <a-list size="small" bordered :dataSource="currentDetail.relatedData.questionDetails.slice(0, 5)">
                <template #renderItem="{ item, index }">
                  <a-list-item>
                    <div style="flex:1">
                      <div style="font-size:13px">
                        <b>#{{ index + 1 }}</b> {{ item.questionContent }}
                      </div>
                      <div style="font-size:12px;color:#999;margin-top:4px">
                        得分: <b :style="{color: item.score == 0 ? '#ff4d4f' : '#52c41a'}">{{ item.score }}</b>
                        / {{ item.maxScore }} 分
                        <span v-if="item.endTime" style="margin-left:12px">
                          考试时间：{{ new Date(item.endTime).toLocaleDateString('zh-CN') }}
                        </span>
                      </div>
                    </div>
                  </a-list-item>
                </template>
              </a-list>
            </div>
          </div>

          <div v-if="currentDetail.relatedData.consecutiveCount !== undefined">
            <h4 style="margin:0 0 8px">📊 连续低分信息</h4>
            <a-descriptions size="small" bordered :column="2">
              <a-descriptions-item label="连续不及格次数">
                <a-tag color="red"><b>{{ currentDetail.relatedData.consecutiveCount }} 次</b></a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="及格线阈值">
                {{ ((currentDetail.relatedData.threshold || 0.6) * 100).toFixed(0) }}%
              </a-descriptions-item>
            </a-descriptions>
            <div v-if="currentDetail.relatedData.scoreTrend?.length" style="margin-top:12px">
              <h5 style="margin:12px 0 8px;color:#555">📈 最近成绩记录：</h5>
              <a-timeline>
                <a-timeline-item
                  v-for="(t, i) in currentDetail.relatedData.scoreTrend.slice(0, 5)"
                  :key="i"
                  :color="(t.score / (t.totalScore || 100)) >= 0.6 ? 'green' : 'red'"
                >
                  <p style="margin:0">
                    <b>{{ t.examTitle }}</b> —
                    <span :style="{ color: (t.score / (t.totalScore || 100)) >= 0.6 ? '#52c41a' : '#ff4d4f', fontWeight: 600 }">
                      {{ t.score }} / {{ t.totalScore }} 分
                      （{{ ((t.score / (t.totalScore || 100)) * 100).toFixed(1) }}%）
                    </span>
                  </p>
                  <p style="margin:4px 0 0;font-size:12px;color:#999" v-if="t.endTime">
                    提交时间：{{ new Date(t.endTime).toLocaleString('zh-CN') }}
                  </p>
                </a-timeline-item>
              </a-timeline>
            </div>
          </div>

          <div v-if="currentDetail.relatedData.daysSinceLastExam !== undefined">
            <h4 style="margin:0 0 8px">📅 缺考信息</h4>
            <a-descriptions size="small" bordered :column="2">
              <a-descriptions-item label="未参加考试天数">
                <a-tag :color="currentDetail.relatedData.daysSinceLastExam >= 60 ? 'red' : 'orange'">
                  <b>{{ currentDetail.relatedData.daysSinceLastExam }} 天</b>
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="预警阈值">
                {{ currentDetail.relatedData.threshold }} 天
              </a-descriptions-item>
              <a-descriptions-item label="上次考试时间" v-if="currentDetail.relatedData.lastExamTime">
                {{ new Date(currentDetail.relatedData.lastExamTime).toLocaleString('zh-CN') }}
              </a-descriptions-item>
              <a-descriptions-item label="注册时间" v-if="currentDetail.relatedData.registeredAt">
                {{ new Date(currentDetail.relatedData.registeredAt).toLocaleString('zh-CN') }}
              </a-descriptions-item>
            </a-descriptions>
          </div>
        </div>

        <div style="margin-top:24px;text-align:right">
          <a-space>
            <a-button @click="detailVisible = false">关闭</a-button>
            <a-button
              v-if="!currentDetail.isResolved"
              type="primary"
              danger
              @click="() => { handleResolve(currentDetail); detailVisible = false; }"
            >
              <CheckCircleOutlined /> 标记已处理
            </a-button>
          </a-space>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.alert-center {
  max-width: 1500px;
  margin: 0 auto;
}
.stats-card {
  background: linear-gradient(135deg, #f6f9fc 0%, #ffffff 100%);
}
.stat-item {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: transform 0.2s;
}
.stat-item:hover { transform: translateY(-2px); }
.stat-high { border-left: 4px solid #ff4d4f; }
.stat-medium { border-left: 4px solid #fa8c16; }
.stat-low { border-left: 4px solid #faad14; }
.type-stat {
  background: #fafafa;
  border-radius: 8px;
  padding: 14px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.type-label { font-size: 14px; color: #555; font-weight: 500; }
.type-count { font-size: 22px; font-weight: 700; color: #722ed1; }
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-left, .toolbar-right { display: flex; flex-wrap: wrap; }
.detail-header { display: flex; gap: 8px; align-items: center; }
</style>
