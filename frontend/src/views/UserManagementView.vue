<script setup>
import { ref, shallowRef, onMounted, h } from 'vue';
import { getUsers, createUser, updateUser, deleteUser, importUsers, exportUsers, resetUserPassword } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { message, Modal } from 'ant-design-vue';
import { 
  UserAddOutlined, 
  ImportOutlined, 
  ExportOutlined, 
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  KeyOutlined
} from '@ant-design/icons-vue';

const authStore = useAuthStore();
const loading = ref(false);
const users = shallowRef([]);
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
});

const searchForm = ref({
  keyword: '',
  role: undefined,
  clazz: ''
});

const modalVisible = ref(false);
const modalTitle = ref('新增用户');
const editingUser = ref(null);
const userForm = ref({
  username: '',
  fullName: '',
  password: '',
  role: 'STUDENT',
  clazz: ''
});

const formRef = ref();
const userRules = {
  username: [{ required: true, message: '请输入用户名' }],
  fullName: [{ required: true, message: '请输入姓名' }],
  password: [{ required: true, message: '请输入初始密码' }],
  role: [{ required: true, message: '请选择角色' }]
};

const fetchUsers = async (page = 1) => {
  loading.value = true;
  try {
    const res = await getUsers({
      keyword: searchForm.value.keyword,
      role: searchForm.value.role,
      clazz: searchForm.value.clazz,
      page: page - 1,
      size: pagination.value.pageSize
    });
    users.value = res.data.content;
    pagination.value.total = res.data.totalElements;
    pagination.value.current = page;
  } catch (e) {
    message.error('获取用户列表失败');
  } finally {
    loading.value = false;
  }
};

const handleTableChange = (pag) => {
  fetchUsers(pag.current);
};

const handleSearch = () => {
  fetchUsers(1);
};

const showAddModal = () => {
  editingUser.value = null;
  modalTitle.value = '新增用户';
  userForm.value = {
    username: '',
    fullName: '',
    password: '',
    role: 'STUDENT',
    clazz: ''
  };
  modalVisible.value = true;
};

const showEditModal = (record) => {
  editingUser.value = record;
  modalTitle.value = '编辑用户';
  userForm.value = {
    username: record.username,
    fullName: record.fullName,
    role: record.role,
    clazz: record.clazz || ''
  };
  modalVisible.value = true;
};

const handleModalOk = async () => {
  try {
    if (formRef.value) {
      await formRef.value.validateFields();
    }
    if (editingUser.value) {
      await updateUser(editingUser.value.id, userForm.value);
      message.success('更新成功');
    } else {
      await createUser(userForm.value);
      message.success('创建成功');
    }
    modalVisible.value = false;
    fetchUsers(pagination.value.current);
  } catch (e) {
    // Error handled by interceptor
  }
};

const handleDelete = (id) => {
  Modal.confirm({
    title: '确定删除该用户吗？',
    content: '删除后无法恢复',
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteUser(id);
        message.success('删除成功');
        fetchUsers(pagination.value.current);
      } catch (e) {}
    }
  });
};

const handleResetPassword = (record) => {
  let newPassword = '';
  Modal.confirm({
    title: '重置密码',
    content: h('div', [
      h('div', { style: 'margin-bottom: 8px' }, `为用户 ${record.username} 设置新密码:`),
      h('input', { 
        type: 'password', 
        class: 'ant-input',
        onInput: (e) => { newPassword = e.target.value; }
      })
    ]),
    onOk: async () => {
      if (!newPassword) {
        message.warning('密码不能为空');
        return Promise.reject();
      }
      try {
        await resetUserPassword(record.id, newPassword);
        message.success('密码重置成功');
      } catch (e) {}
    }
  });
};

const handleExport = async () => {
  try {
    const res = await exportUsers();
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', '用户数据导出.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (e) {
    message.error('导出失败');
  }
};

const handleImport = async (options) => {
  const { file } = options;
  const formData = new FormData();
  formData.append('file', file);
  try {
    await importUsers(formData);
    message.success('批量导入完成');
    fetchUsers(1);
  } catch (e) {
    message.error('导入出错，请检查CSV格式');
  }
};

onMounted(() => {
  fetchUsers();
});

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'fullName', key: 'fullName' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '班级', dataIndex: 'clazz', key: 'clazz' },
  { title: '操作', key: 'action', width: 250 }
];
</script>

<template>
  <div class="user-management-container">
    <a-card title="用户管理">
      <template #extra>
        <a-space>
          <a-upload :customRequest="handleImport" :showUploadList="false">
            <a-button :icon="h(ImportOutlined)">批量导入</a-button>
          </a-upload>
          <a-button @click="handleExport" :icon="h(ExportOutlined)">导出</a-button>
          <a-button type="primary" @click="showAddModal" :icon="h(UserAddOutlined)">新增用户</a-button>
        </a-space>
      </template>

      <div class="search-bar" style="margin-bottom: 24px;">
        <a-form layout="inline" :model="searchForm">
          <a-form-item label="关键字">
            <a-input v-model:value="searchForm.keyword" placeholder="用户名/姓名" @pressEnter="handleSearch" />
          </a-form-item>
          <a-form-item label="角色">
            <a-select v-model:value="searchForm.role" style="width: 120px" placeholder="全部" allowClear>
              <a-select-option value="STUDENT">学生</a-select-option>
              <a-select-option value="TEACHER">老师</a-select-option>
              <a-select-option value="ADMIN" v-if="authStore.isAdmin">管理员</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="班级">
            <a-input v-model:value="searchForm.clazz" placeholder="班级名称" @pressEnter="handleSearch" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :icon="h(SearchOutlined)" @click="handleSearch">搜索</a-button>
          </a-form-item>
        </a-form>
      </div>

      <a-table 
        :dataSource="users" 
        :columns="columns" 
        :pagination="pagination"
        :loading="loading"
        @change="handleTableChange"
        rowKey="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <a-tag :color="record.role === 'ADMIN' ? 'volcano' : (record.role === 'TEACHER' ? 'blue' : 'green')">
              {{ record.role === 'ADMIN' ? '管理员' : (record.role === 'TEACHER' ? '老师' : '学生') }}
            </a-tag>
          </template>
          <template v-if="column.key === 'clazz'">
            {{ record.clazz || '-' }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button size="small" :icon="h(EditOutlined)" @click="showEditModal(record)" :disabled="record.role === 'ADMIN' && !authStore.isAdmin">编辑</a-button>
              <a-button size="small" :icon="h(KeyOutlined)" @click="handleResetPassword(record)" :disabled="record.role === 'ADMIN' && !authStore.isAdmin">重置密码</a-button>
              <a-button size="small" danger :icon="h(DeleteOutlined)" @click="handleDelete(record.id)" :disabled="record.role === 'ADMIN' && !authStore.isAdmin">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      @ok="handleModalOk"
      :destroyOnClose="true"
    >
      <a-form ref="formRef" :model="userForm" :rules="userRules" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }" style="margin-top: 24px;">
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="userForm.username" :disabled="!!editingUser" />
        </a-form-item>
        <a-form-item label="姓名" name="fullName">
          <a-input v-model:value="userForm.fullName" />
        </a-form-item>
        <a-form-item label="初始密码" name="password" v-if="!editingUser">
          <a-input-password v-model:value="userForm.password" placeholder="默认123456" />
        </a-form-item>
        <a-form-item label="角色" name="role">
          <a-select v-model:value="userForm.role">
            <a-select-option value="STUDENT">学生</a-select-option>
            <a-select-option value="TEACHER">老师</a-select-option>
            <a-select-option value="ADMIN" v-if="authStore.isAdmin">管理员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="班级" name="clazz" v-if="userForm.role === 'STUDENT'">
          <a-input v-model:value="userForm.clazz" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.user-management-container {
  padding: 24px;
}
</style>
