<script setup>
</script>

<template>
  <main>
    <a-result status="success" title="系统初始化成功!" sub-title="前端环境准备就绪，Docker构建正常。">
      <template #extra>
        <a-button key="console" type="primary">进入控制台</a-button>
        <a-button key="buy">文档</a-button>
      </template>
    </a-result>
  </main>
</template>
