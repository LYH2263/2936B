<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  getQnaUnansweredThreads,
  getQnaThreadDetail,
  addQnaMessage,
  markQnaAsFaq,
  toggleQnaPin,
  getQnaUnansweredCount
} from '@/api';
import { message } from 'ant-design-vue';
import {
  MessageOutlined,
  PushpinOutlined,
  PushpinFilled,
  StarOutlined,
  StarFilled,
  SendOutlined,
  UserOutlined,
  EyeOutlined
} from '@ant-design/icons-vue';

const router = useRouter();
const loading = ref(false);
const threads = ref([]);
const selectedThread = ref(null);
const threadDetail = ref(null);
const replyContent = ref('');
const replying = ref(false);
const unansweredCount = ref(0);

const fetchUnanswered = async () => {
  loading.value = true;
  try {
    const [tRes, cRes] = await Promise.all([
      getQnaUnansweredThreads(),
      getQnaUnansweredCount()
    ]);
    threads.value = tRes.data;
    unansweredCount.value = cRes.data.count || 0;
  } catch (e) {
    console.error('Failed to fetch unanswered threads', e);
    message.error('加载待回复提问失败');
  } finally {
    loading.value = false;
  }
};

const selectThread = async (thread) => {
  selectedThread.value = thread;
  try {
    const res = await getQnaThreadDetail(thread.id);
    threadDetail.value = res.data;
  } catch (e) {
    console.error('Failed to fetch thread detail', e);
    message.error('加载详情失败');
  }
};

const sendReply = async () => {
  if (!replyContent.value.trim()) {
    message.warning('请输入回复内容');
    return;
  }
  replying.value = true;
  try {
    await addQnaMessage(selectedThread.value.id, { content: replyContent.value });
    message.success('回复成功');
    replyContent.value = '';
    await selectThread(selectedThread.value);
    await fetchUnanswered();
  } catch (e) {
    console.error('Failed to send reply', e);
    message.error('回复失败');
  } finally {
    replying.value = false;
  }
};

const toggleFaq = async (thread) => {
  try {
    await markQnaAsFaq(thread.id, { isFaq: !thread.isFaq });
    message.success(thread.isFaq ? '已取消 FAQ 标记' : '已标记为 FAQ');
    thread.isFaq = !thread.isFaq;
    if (selectedThread.value && selectedThread.value.id === thread.id) {
      await selectThread(thread);
    }
  } catch (e) {
    message.error('操作失败');
  }
};

const togglePin = async (thread) => {
  try {
    await toggleQnaPin(thread.id);
    message.success(thread.isPinned ? '已取消置顶' : '已置顶');
    thread.isPinned = !thread.isPinned;
    if (selectedThread.value && selectedThread.value.id === thread.id) {
      await selectThread(thread);
    }
  } catch (e) {
    message.error('操作失败');
  }
};

const goToExam = (examId) => {
  router.push(`/exam/${examId}/detail`);
};

const formatTime = (t) => {
  if (!t) return '';
  return new Date(t).toLocaleString();
};

onMounted(fetchUnanswered);
</script>

<template>
  <div class="qna-center">
    <div class="qna-header">
      <h2><MessageOutlined /> 考试答疑中心</h2>
      <a-tag color="red" v-if="unansweredCount > 0">
        {{ unansweredCount }} 条待回复
      </a-tag>
      <a-button type="primary" size="small" @click="fetchUnanswered">刷新</a-button>
    </div>

    <a-row :gutter="24">
      <a-col :span="10">
        <a-card title="待回复提问列表" :bordered="false" class="list-card">
          <a-spin :spinning="loading">
            <a-list
              :dataSource="threads"
              item-layout="vertical"
              size="small"
            >
              <template #renderItem="{ item }">
                <a-list-item
                  :class="{ 'thread-selected': selectedThread?.id === item.id }"
                  @click="selectThread(item)"
                  class="thread-item"
                >
                  <div class="thread-title-row">
                    <span class="thread-title">
                      <PushpinFilled v-if="item.isPinned" class="pin-icon" />
                      <StarFilled v-if="item.isFaq" class="faq-icon" />
                      {{ item.title }}
                    </span>
                  </div>
                  <div class="thread-meta">
                    <span><UserOutlined /> {{ item.student?.fullName || item.student?.username }}</span>
                    <span class="divider">·</span>
                    <span>考试: {{ item.exam?.title }}</span>
                    <span class="divider">·</span>
                    <span>{{ formatTime(item.createdAt) }}</span>
                  </div>
                  <div class="thread-actions">
                    <a-button
                      size="small"
                      :type="item.isPinned ? 'primary' : 'default'"
                      @click.stop="togglePin(item)"
                    >
                      <PushpinOutlined v-if="!item.isPinned" />
                      <PushpinFilled v-else />
                      {{ item.isPinned ? '取消置顶' : '置顶' }}
                    </a-button>
                    <a-button
                      size="small"
                      :type="item.isFaq ? 'primary' : 'default'"
                      @click.stop="toggleFaq(item)"
                    >
                      <StarOutlined v-if="!item.isFaq" />
                      <StarFilled v-else />
                      {{ item.isFaq ? '取消FAQ' : '设为FAQ' }}
                    </a-button>
                    <a-button size="small" @click.stop="goToExam(item.exam?.id)">
                      <EyeOutlined /> 查看考试
                    </a-button>
                  </div>
                </a-list-item>
              </template>
              <a-empty v-if="threads.length === 0" description="暂无待回复提问" />
            </a-list>
          </a-spin>
        </a-card>
      </a-col>

      <a-col :span="14">
        <a-card title="提问详情" :bordered="false" class="detail-card">
          <div v-if="threadDetail" class="detail-content">
            <div class="detail-header">
              <h3>
                <PushpinFilled v-if="threadDetail.isPinned" class="pin-icon" />
                <StarFilled v-if="threadDetail.isFaq" class="faq-icon" />
                {{ threadDetail.title }}
              </h3>
              <div class="detail-meta">
                <a-tag color="blue">学生: {{ threadDetail.student?.fullName || threadDetail.student?.username }}</a-tag>
                <a-tag color="green">考试: {{ threadDetail.exam?.title }}</a-tag>
                <a-tag v-if="threadDetail.isAnswered" color="success">已回复</a-tag>
                <a-tag v-else color="warning">待回复</a-tag>
                <span class="time">{{ formatTime(threadDetail.createdAt) }}</span>
              </div>
            </div>

            <div class="messages-container">
              <div
                v-for="msg in threadDetail.messages"
                :key="msg.id"
                :class="['message-item', msg.senderRole === 'STUDENT' ? 'msg-student' : 'msg-teacher']"
              >
                <div class="msg-avatar">
                  <a-avatar :style="{ backgroundColor: msg.senderRole === 'STUDENT' ? '#1890ff' : '#52c41a' }">
                    {{ msg.sender?.fullName?.[0] || msg.sender?.username?.[0] || 'U' }}
                  </a-avatar>
                </div>
                <div class="msg-body">
                  <div class="msg-header">
                    <span class="msg-sender">
                      {{ msg.sender?.fullName || msg.sender?.username }}
                      <a-tag size="small" :color="msg.senderRole === 'STUDENT' ? 'blue' : 'green'">
                        {{ msg.senderRole === 'STUDENT' ? '学生' : '教师' }}
                      </a-tag>
                    </span>
                    <span class="msg-time">{{ formatTime(msg.createdAt) }}</span>
                  </div>
                  <div class="msg-content" v-html="msg.content"></div>
                </div>
              </div>
            </div>

            <div class="reply-box">
              <a-textarea
                v-model:value="replyContent"
                :rows="4"
                placeholder="输入回复内容，支持简单富文本格式..."
                show-count
                :maxlength="2000"
              />
              <div class="reply-actions">
                <a-button
                  type="primary"
                  :loading="replying"
                  @click="sendReply"
                >
                  <SendOutlined /> 发送回复
                </a-button>
              </div>
            </div>
          </div>
          <a-empty v-else description="请选择左侧待回复提问查看详情" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.qna-center {
  padding: 0;
}
.qna-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.qna-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.list-card,
.detail-card {
  height: calc(100vh - 260px);
}
.thread-item {
  cursor: pointer;
  transition: all 0.2s;
  border-radius: 8px;
  padding: 12px !important;
}
.thread-item:hover {
  background: #f5f5f5;
}
.thread-selected {
  background: #e6f7ff !important;
  border-left: 3px solid #1890ff;
}
.thread-title-row {
  margin-bottom: 6px;
}
.thread-title {
  font-weight: 500;
  color: #1a1a1a;
}
.pin-icon {
  color: #f5222d;
  margin-right: 4px;
}
.faq-icon {
  color: #faad14;
  margin-right: 4px;
}
.thread-meta {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}
.thread-meta .divider {
  margin: 0 6px;
}
.thread-actions {
  display: flex;
  gap: 8px;
}
.detail-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.detail-header h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.detail-meta .time {
  font-size: 12px;
  color: #999;
  margin-left: auto;
}
.messages-container {
  max-height: 400px;
  overflow-y: auto;
  padding: 8px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 16px;
}
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.msg-avatar {
  flex-shrink: 0;
}
.msg-body {
  flex: 1;
  background: white;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}
.msg-teacher .msg-body {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}
.msg-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.msg-sender {
  font-weight: 500;
}
.msg-time {
  font-size: 12px;
  color: #999;
}
.msg-content {
  line-height: 1.6;
  color: #333;
}
.reply-box {
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
}
.reply-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
