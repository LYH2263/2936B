<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { createPkMatch, createPkBotMatch, cancelPkMatch, getPkSession, getPkWeeklyRanking } from '@/api';
import { message } from 'ant-design-vue';
import {
  PlayCircleOutlined, RobotOutlined, TrophyOutlined,
  UserOutlined, ClockCircleOutlined, StarOutlined
} from '@ant-design/icons-vue';

const router = useRouter();
const matching = ref(false);
const matchingTimer = ref(null);
const matchTime = ref(0);
const weeklyRanking = ref([]);
const loadingRanking = ref(false);

const loadRanking = async () => {
  loadingRanking.value = true;
  try {
    const res = await getPkWeeklyRanking();
    weeklyRanking.value = res.data;
  } catch (e) {
    console.error('Failed to load ranking', e);
  } finally {
    loadingRanking.value = false;
  }
};

const startMatch = async () => {
  matching.value = true;
  matchTime.value = 0;
  try {
    const res = await createPkMatch();
    const session = res.data;

    if (session.state === 'IN_PROGRESS') {
      message.success('匹配成功！');
      router.push(`/pk/${session.id}`);
      return;
    }

    matchingTimer.value = setInterval(async () => {
      matchTime.value++;
      try {
        const stateRes = await getPkSession(session.id);
        if (stateRes.data.state === 'IN_PROGRESS') {
          clearInterval(matchingTimer.value);
          message.success('匹配成功！');
          router.push(`/pk/${session.id}`);
        }
      } catch (e) {
        console.error('Match polling error', e);
      }
    }, 2000);

    setTimeout(() => {
      if (matching.value) {
        clearInterval(matchingTimer.value);
        message.info('暂时没有在线玩家，是否尝试人机对战？');
      }
    }, 15000);
  } catch (e) {
    matching.value = false;
    message.error('匹配失败');
  }
};

const startBotMatch = async () => {
  try {
    const res = await createPkBotMatch();
    message.success('人机对战开始！');
    router.push(`/pk/${res.data.id}`);
  } catch (e) {
    message.error('创建人机对战失败');
  }
};

const cancelMatch = async () => {
  if (matchingTimer.value) {
    clearInterval(matchingTimer.value);
  }
  try {
    await cancelPkMatch();
    message.info('已取消匹配');
  } catch (e) {
    console.error('Cancel error', e);
  } finally {
    matching.value = false;
    matchTime.value = 0;
  }
};

const goToRanking = () => {
  router.push('/pk/ranking');
};

onMounted(() => {
  loadRanking();
});

onUnmounted(() => {
  if (matchingTimer.value) {
    clearInterval(matchingTimer.value);
  }
});
</script>

<template>
  <div class="pk-lobby">
    <div class="lobby-header">
      <h1>
        <TrophyOutlined class="trophy-icon" />
        趣味 PK 对战
      </h1>
      <p>挑战你的同学，看看谁才是真正的学霸！</p>
    </div>

    <div class="lobby-content">
      <div class="match-section">
        <div class="match-card" @click="!matching && startMatch()">
          <div class="card-icon">
            <UserOutlined />
          </div>
          <h3>真人对战</h3>
          <p>与在线同学随机匹配，10道题决胜负</p>
          <a-button type="primary" size="large" :loading="matching" v-if="!matching">
            <PlayCircleOutlined /> 开始匹配
          </a-button>
          <a-button danger v-else @click.stop="cancelMatch">
            取消匹配 ({{ matchTime }}s)
          </a-button>
        </div>

        <div class="match-card bot-card" @click="!matching && startBotMatch()">
          <div class="card-icon bot">
            <RobotOutlined />
          </div>
          <h3>人机对战</h3>
          <p>与 AI 小助手切磋，随时可以练习</p>
          <a-button size="large" :disabled="matching">
            <RobotOutlined /> 开始挑战
          </a-button>
        </div>
      </div>

      <div class="rules-section">
        <h3><StarOutlined /> 比赛规则</h3>
        <ul>
          <li>共 10 道客观题，每题 30 秒答题时间</li>
          <li>答对一题得 10 分，答错不扣分</li>
          <li>得分高者获胜，分数相同则平局</li>
          <li>中途断线超过 60 秒判负</li>
          <li>PK 成绩不计入正式考试，仅作娱乐</li>
        </ul>
      </div>

      <div class="ranking-preview">
        <div class="ranking-header">
          <h3><TrophyOutlined /> 本周排行榜</h3>
          <a-button type="link" @click="goToRanking">查看全部 &gt;</a-button>
        </div>
        <a-spin :spinning="loadingRanking">
          <div class="ranking-list" v-if="weeklyRanking.length > 0">
            <div class="ranking-item" v-for="item in weeklyRanking.slice(0, 5)" :key="item.userId">
              <div class="rank" :class="'rank-' + item.rank">
                {{ item.rank }}
              </div>
              <div class="player-info">
                <span class="name">{{ item.fullName || item.username }}</span>
                <span class="stats">{{ item.wins }}胜 / {{ item.totalGames }}场</span>
              </div>
              <div class="win-rate">
                {{ item.winRate.toFixed(1) }}%
              </div>
            </div>
          </div>
          <a-empty v-else description="暂无排行数据" />
        </a-spin>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pk-lobby {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 20px;
}

.lobby-header {
  text-align: center;
  margin-bottom: 40px;
}

.lobby-header h1 {
  font-size: 36px;
  margin-bottom: 10px;
  color: #1890ff;
}

.trophy-icon {
  margin-right: 10px;
}

.lobby-header p {
  color: #666;
  font-size: 16px;
}

.lobby-content {
  display: grid;
  gap: 30px;
}

.match-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.match-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 40px 30px;
  text-align: center;
  color: white;
  cursor: pointer;
  transition: transform 0.3s, box-shadow 0.3s;
}

.match-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.match-card.bot-card {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
}

.match-card.bot-card:hover {
  box-shadow: 0 8px 25px rgba(17, 153, 142, 0.4);
}

.card-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.match-card h3 {
  font-size: 24px;
  margin-bottom: 8px;
  color: white;
}

.match-card p {
  color: rgba(255, 255, 255, 0.85);
  margin-bottom: 24px;
}

.rules-section {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 24px;
}

.rules-section h3 {
  margin-bottom: 16px;
  color: #333;
}

.rules-section ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.rules-section li {
  padding: 8px 0;
  color: #555;
  padding-left: 20px;
  position: relative;
}

.rules-section li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #1890ff;
  font-weight: bold;
}

.ranking-preview {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.ranking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.ranking-header h3 {
  margin: 0;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 8px;
}

.rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #d9d9d9;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}

.rank-1 { background: #ffd700; }
.rank-2 { background: #c0c0c0; }
.rank-3 { background: #cd7f32; }

.player-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.player-info .name {
  font-weight: 500;
  color: #333;
}

.player-info .stats {
  font-size: 12px;
  color: #999;
}

.win-rate {
  font-weight: bold;
  color: #52c41a;
}
</style>
