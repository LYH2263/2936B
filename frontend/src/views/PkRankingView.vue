<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getPkWeeklyRanking } from '@/api';
import {
  TrophyOutlined, UserOutlined, ArrowLeftOutlined,
  StarOutlined, FireOutlined
} from '@ant-design/icons-vue';

const router = useRouter();
const weeklyRanking = ref([]);
const loading = ref(true);

const loadRanking = async () => {
  loading.value = true;
  try {
    const res = await getPkWeeklyRanking();
    weeklyRanking.value = res.data;
  } catch (e) {
    console.error('Failed to load ranking', e);
  } finally {
    loading.value = false;
  }
};

const goBack = () => {
  router.push('/pk');
};

const getRankIcon = (rank) => {
  if (rank === 1) return '🥇';
  if (rank === 2) return '🥈';
  if (rank === 3) return '🥉';
  return rank;
};

onMounted(() => {
  loadRanking();
});
</script>

<template>
  <div class="pk-ranking">
    <div class="ranking-header">
      <a-button ghost @click="goBack">
        <ArrowLeftOutlined /> 返回大厅
      </a-button>
      <h1>
        <TrophyOutlined /> 本周 PK 排行榜
      </h1>
      <p>每周一零点重置，与真人对战计入排行</p>
    </div>

    <div class="ranking-content">
      <a-spin :spinning="loading" tip="加载中...">
        <div v-if="weeklyRanking.length > 0" class="ranking-list">
          <div class="top-three">
            <div
              v-for="item in weeklyRanking.slice(0, 3)"
              :key="item.userId"
              class="top-card"
              :class="'rank-' + item.rank"
            >
              <div class="rank-badge">{{ getRankIcon(item.rank) }}</div>
              <div class="player-avatar">
                <UserOutlined />
              </div>
              <div class="player-name">{{ item.fullName || item.username }}</div>
              <div class="player-stats">
                <div class="wins">
                  <StarOutlined /> {{ item.wins }} 胜
                </div>
                <div class="total">
                  共 {{ item.totalGames }} 场
                </div>
              </div>
              <div class="win-rate">
                <div class="rate-value">{{ item.winRate.toFixed(1) }}%</div>
                <div class="rate-label">胜率</div>
              </div>
            </div>
          </div>

          <div class="rest-list" v-if="weeklyRanking.length > 3">
            <div
              v-for="item in weeklyRanking.slice(3)"
              :key="item.userId"
              class="ranking-item"
            >
              <div class="rank-num">{{ item.rank }}</div>
              <div class="rank-avatar">
                <UserOutlined />
              </div>
              <div class="rank-info">
                <div class="rank-name">{{ item.fullName || item.username }}</div>
                <div class="rank-stats">{{ item.wins }}胜 / {{ item.totalGames }}场</div>
              </div>
              <div class="rank-winrate">
                <FireOutlined /> {{ item.winRate.toFixed(1) }}%
              </div>
            </div>
          </div>
        </div>

        <a-empty v-else description="暂无排行数据，快去挑战吧！" />
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.pk-ranking {
  max-width: 900px;
  margin: 0 auto;
  padding: 30px 20px;
  min-height: 100vh;
}

.ranking-header {
  text-align: center;
  margin-bottom: 40px;
}

.ranking-header h1 {
  font-size: 32px;
  color: #1890ff;
  margin: 20px 0 10px;
}

.ranking-header p {
  color: #999;
  margin: 0;
}

.ranking-content {
  background: #f5f7fa;
  border-radius: 16px;
  padding: 30px;
}

.top-three {
  display: flex;
  justify-content: center;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 30px;
  flex-wrap: wrap;
}

.top-card {
  background: white;
  border-radius: 16px;
  padding: 30px 24px;
  text-align: center;
  width: 200px;
  position: relative;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s;
}

.top-card:hover {
  transform: translateY(-4px);
}

.top-card.rank-1 {
  order: 2;
  width: 220px;
  background: linear-gradient(180deg, #fff9e6 0%, white 100%);
  border: 2px solid #ffd700;
}

.top-card.rank-2 {
  order: 1;
  background: linear-gradient(180deg, #f5f5f5 0%, white 100%);
}

.top-card.rank-3 {
  order: 3;
  background: linear-gradient(180deg, #fff2e8 0%, white 100%);
}

.rank-badge {
  font-size: 36px;
  margin-bottom: 10px;
}

.player-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
  font-size: 28px;
  color: white;
}

.player-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333;
}

.player-stats {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #666;
}

.wins {
  color: #52c41a;
  font-weight: 500;
}

.win-rate {
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.rate-value {
  font-size: 24px;
  font-weight: bold;
  color: #1890ff;
}

.rate-label {
  font-size: 12px;
  color: #999;
}

.rest-list {
  background: white;
  border-radius: 12px;
  overflow: hidden;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}

.ranking-item:last-child {
  border-bottom: none;
}

.ranking-item:hover {
  background: #fafafa;
}

.rank-num {
  width: 30px;
  text-align: center;
  font-weight: bold;
  color: #999;
  font-size: 16px;
}

.rank-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #e6f7ff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1890ff;
}

.rank-info {
  flex: 1;
}

.rank-name {
  font-weight: 500;
  color: #333;
}

.rank-stats {
  font-size: 12px;
  color: #999;
}

.rank-winrate {
  color: #52c41a;
  font-weight: 500;
  font-size: 14px;
}
</style>
