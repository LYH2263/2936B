<script setup>
import { onMounted } from 'vue';
import { RouterView } from 'vue-router'
import { useConfigStore } from '@/stores/config';

const configStore = useConfigStore();

onMounted(() => {
  configStore.fetchConfig();
});
</script>

<template>
  <a-config-provider :locale="zhCN">
    <router-view v-slot="{ Component }">
      <transition name="fade-slide" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </a-config-provider>
</template>

<script>
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
dayjs.locale('zh-cn');

export default {
  data() {
    return {
      zhCN,
    };
  },
};
</script>

<style scoped>
</style>
