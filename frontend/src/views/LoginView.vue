<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { message } from 'ant-design-vue';
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';
import { useConfigStore } from '@/stores/config';

const router = useRouter();
const authStore = useAuthStore();
const configStore = useConfigStore();
const loading = ref(false);

const formState = reactive({
  username: localStorage.getItem('remembered_username') || '',
  password: '',
  remember: !!localStorage.getItem('remembered_username'),
});

const onFinish = async (values) => {
  loading.value = true;
  const success = await authStore.login(values);
  loading.value = false;
  
  if (success) {
    if (formState.remember) {
      localStorage.setItem('remembered_username', formState.username);
    } else {
      localStorage.removeItem('remembered_username');
    }
    message.success('登录成功');
    router.push('/dashboard');
  }
};
</script>

<template>
  <div class="login-container">
    <a-card class="login-card" :bordered="false">
      <div class="login-header">
        <img v-if="configStore.logoUrl" :src="configStore.logoUrl" alt="logo" class="logo" />
        <span class="title">{{ configStore.sysName }}</span>
      </div>
      
      <a-form
        :model="formState"
        name="normal_login"
        class="login-form"
        @finish="onFinish"
      >
        <a-form-item
          name="username"
          :rules="[{ required: true, message: '请输入用户名或学号!' }]"
        >
          <a-input v-model:value="formState.username" placeholder="用户名 / 学号" size="large">
            <template #prefix>
              <UserOutlined class="site-form-item-icon" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
          name="password"
          :rules="[{ required: true, message: '请输入密码!' }]"
        >
          <a-input-password v-model:value="formState.password" placeholder="密码" size="large">
            <template #prefix>
              <LockOutlined class="site-form-item-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <div class="login-options">
          <a-form-item name="remember" no-style>
            <a-checkbox v-model:checked="formState.remember">记住我</a-checkbox>
          </a-form-item>
          <a class="login-form-forgot" @click="message.info('请联系系统管理员重置密码')">
            忘记密码
          </a>
        </div>

        <a-form-item>
          <a-button :loading="loading" type="primary" html-type="submit" class="login-form-button" size="large">
            登录
          </a-button>
        </a-form-item>
        
       
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: radial-gradient(circle at top left, #4f46e5 0%, #111827 100%);
  overflow: hidden;
  position: relative;
}

.login-container::before {
  content: "";
  position: absolute;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.4) 0%, transparent 70%);
  top: -100px;
  right: -100px;
  z-index: 0;
}

.login-card {
  width: 440px;
  background: rgba(255, 255, 255, 0.05) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  border-radius: var(--radius-lg);
  padding: 40px;
  z-index: 10;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 48px;
}

.logo {
  height: 64px;
  margin-bottom: 16px;
  filter: drop-shadow(0 0 12px rgba(255, 255, 255, 0.2));
}

.title {
  font-size: 28px;
  color: white;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.login-form :deep(.ant-input-affix-wrapper),
.login-form :deep(.ant-input-password) {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: white;
}

.login-form :deep(.ant-input) {
  background: transparent;
  color: white;
}

.login-form :deep(.ant-input-prefix) {
  color: rgba(255, 255, 255, 0.5);
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.login-options :deep(.ant-checkbox-wrapper) {
  color: rgba(255, 255, 255, 0.7);
}

.login-form-forgot {
  color: var(--primary-color);
  font-weight: 500;
  cursor: pointer;
}

.login-form-button {
  width: 100%;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
  background: var(--primary-color);
  border: none;
  box-shadow: 0 10px 15px -3px rgba(79, 70, 229, 0.4);
}

.login-form-button:hover {
  background: var(--primary-hover);
  transform: translateY(-1px);
}
</style>
