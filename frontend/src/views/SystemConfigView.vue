<script setup>
import { ref, onMounted, h } from 'vue';
import { getSystemConfig, updateSystemConfig } from '@/api';
import { message } from 'ant-design-vue';
import { 
  SaveOutlined, ReloadOutlined, SettingOutlined, 
  SafetyCertificateOutlined, BookOutlined, MailOutlined 
} from '@ant-design/icons-vue';

const loading = ref(false);
const saving = ref(false);
const activeKey = ref('basic');
const configForm = ref({
  // Basic
  sysName: '',
  logoUrl: '',
  footerText: '',
  // Security
  passwordMinLength: 6,
  loginMaxFailures: 5,
  sessionTimeout: 30,
  // Exam Rules
  defaultDuration: 60,
  tabSwitchLimit: 3,
  defaultAntiCheat: '',
  // Notification
  smtpHost: '',
  smtpPort: 465,
  smtpUser: '',
  smtpPass: '',
  smsApiUrl: '',
  smsApiKey: ''
});

const fetchConfig = async () => {
  loading.value = true;
  try {
    const res = await getSystemConfig();
    configForm.value = { ...configForm.value, ...res.data };
  } catch (e) {
    message.error('获取系统设置失败');
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  saving.value = true;
  try {
    await updateSystemConfig(configForm.value);
    message.success('系统设置已更新');
    // Refresh only if basic info changed to apply title/logo globally
    if (activeKey.value === 'basic') {
      window.location.reload();
    }
  } catch (e) {
    message.error('更新系统设置失败');
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  fetchConfig();
});
</script>

<template>
  <div class="system-config-container">
    <a-card title="系统设置" :loading="loading">
      <template #extra>
        <a-space>
          <a-button @click="fetchConfig" :icon="h(ReloadOutlined)">重置显示</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave" :icon="h(SaveOutlined)">保存设置</a-button>
        </a-space>
      </template>

      <a-tabs v-model:activeKey="activeKey">
        <!-- 基础信息 -->
        <a-tab-pane key="basic">
          <template #tab>
            <span><setting-outlined />基础信息</span>
          </template>
          <a-form :model="configForm" layout="vertical" class="config-form">
            <a-form-item label="系统名称" required tooltip="显示在浏览器标题栏和侧边栏顶部">
              <a-input v-model:value="configForm.sysName" placeholder="例如：在线考试系统" />
            </a-form-item>
            <a-form-item label="LOGO URL" tooltip="系统左上角显示的图标地址">
              <a-input v-model:value="configForm.logoUrl" placeholder="请输入图片 URL" />
              <div v-if="configForm.logoUrl" style="margin-top: 10px;">
                <p>预览：</p>
                <img :src="configForm.logoUrl" alt="logo" style="max-height: 50px; border: 1px solid #eee; padding: 5px;" />
              </div>
            </a-form-item>
            <a-form-item label="版权页脚" tooltip="页面底部显示的版权信息">
              <a-textarea v-model:value="configForm.footerText" :rows="3" placeholder="例如：Copyright © 2026 在线考试系统" />
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <!-- 安全设置 -->
        <a-tab-pane key="security">
          <template #tab>
            <span><safety-certificate-outlined />安全设置</span>
          </template>
          <a-form :model="configForm" layout="vertical" class="config-form">
            <a-row :gutter="24">
              <a-col :span="12">
                <a-form-item label="密码最短长度" required>
                  <a-input-number v-model:value="configForm.passwordMinLength" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="登录失败次数限制" required tooltip="超过此次数账户将被锁定（需手动解锁）">
                  <a-input-number v-model:value="configForm.loginMaxFailures" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="会话超时时间 (分钟)" required>
                  <a-input-number v-model:value="configForm.sessionTimeout" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-tab-pane>

        <!-- 考试规则 -->
        <a-tab-pane key="exam">
          <template #tab>
            <span><book-outlined />考试规则</span>
          </template>
          <a-form :model="configForm" layout="vertical" class="config-form">
            <a-row :gutter="24">
              <a-col :span="12">
                <a-form-item label="默认考试时长 (分钟)" required>
                  <a-input-number v-model:value="configForm.defaultDuration" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="默认切屏次数限制" required>
                  <a-input-number v-model:value="configForm.tabSwitchLimit" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="默认防作弊策略" tooltip="多个策略用逗号分隔（如：TAB_SWITCH,CAMERA）">
                  <a-input v-model:value="configForm.defaultAntiCheat" placeholder="例如：TAB_SWITCH" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-tab-pane>

        <!-- 邮件/短信 -->
        <a-tab-pane key="notification">
          <template #tab>
            <span><mail-outlined />通知配置</span>
          </template>
          <a-form :model="configForm" layout="vertical" class="config-form">
            <a-divider orientation="left">SMTP 邮件服务</a-divider>
            <a-row :gutter="24">
              <a-col :span="16">
                <a-form-item label="SMTP 主机">
                  <a-input v-model:value="configForm.smtpHost" placeholder="例如：smtp.exmail.qq.com" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="端口">
                  <a-input-number v-model:value="configForm.smtpPort" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="SMTP 账号">
                  <a-input v-model:value="configForm.smtpUser" placeholder="请输入发送邮箱" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="SMTP 密码">
                  <a-input-password v-model:value="configForm.smtpPass" placeholder="请输入授权码" />
                </a-form-item>
              </a-col>
            </a-row>

            <a-divider orientation="left">短信服务接口 (SMS)</a-divider>
            <a-form-item label="API URL">
              <a-input v-model:value="configForm.smsApiUrl" placeholder="请输入短信接口地址" />
            </a-form-item>
            <a-form-item label="API Key">
              <a-input-password v-model:value="configForm.smsApiKey" placeholder="请输入秘钥" />
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
      
      <div style="margin-top: 40px;">
        <a-alert message="注意" description="高级配置修改后部分后端策略（如密码强度校验）将立即生效。通知类服务需确保参数配置正确。" type="info" show-icon />
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.system-config-container {
  padding: 24px;
}
.config-form {
  max-width: 800px;
  padding: 20px 0;
}
</style>
