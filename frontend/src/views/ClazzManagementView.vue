<script setup>
import { ref, shallowRef, onMounted, h, computed } from 'vue';
import { 
  getClazzes, getClazzStudents, createClazz, updateClazz, 
  deleteClazz, addStudentsToClazz, removeStudentFromClazz, 
  importClazzStudents, getUsers 
} from '@/api';
import { useAuthStore } from '@/stores/auth';
import { message, Modal } from 'ant-design-vue';
import { 
  TeamOutlined, PlusOutlined, ImportOutlined, 
  UserAddOutlined, EditOutlined, DeleteOutlined, 
  SearchOutlined, UserOutlined 
} from '@ant-design/icons-vue';

const authStore = useAuthStore();
const loading = ref(false);
const clazzes = shallowRef([]);
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
});

const searchKeyword = ref('');

const modalVisible = ref(false);
const modalTitle = ref('新增班级');
const editingClazz = ref(null);
const clazzForm = ref({
  name: '',
  grade: '',
  teacherId: null
});
const formRef = ref();
const clazzRules = {
  name: [{ required: true, message: '请输入班级名称' }],
  grade: [{ required: true, message: '请输入年级' }]
};

const studentModalVisible = ref(false);
const currentClazz = ref(null);
const clazzStudents = shallowRef([]);
const availableStudents = shallowRef([]);
const selectedStudentIds = ref([]);
const studentModalLoading = ref(false);

const fetchClazzes = async (page = 1) => {
  loading.value = true;
  try {
    const res = await getClazzes({
      keyword: searchKeyword.value,
      page: page - 1,
      size: pagination.value.pageSize
    });
    clazzes.value = res.data.content;
    pagination.value.total = res.data.totalElements;
    pagination.value.current = page;
  } catch (e) {
    message.error('获取班级列表失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  fetchClazzes(1);
};

const showAddModal = () => {
  editingClazz.value = null;
  modalTitle.value = '新增班级';
  clazzForm.value = {
    name: '',
    grade: '',
    teacherId: authStore.user?.id || null
  };
  modalVisible.value = true;
};

const showEditModal = (record) => {
  editingClazz.value = record;
  modalTitle.value = '编辑班级';
  clazzForm.value = {
    name: record.name,
    grade: record.grade,
    teacherId: record.teacher?.id
  };
  modalVisible.value = true;
};

const handleModalOk = async () => {
  try {
    if (formRef.value) {
      await formRef.value.validateFields();
    }
    if (editingClazz.value) {
      await updateClazz(editingClazz.value.id, clazzForm.value);
      message.success('更新成功');
    } else {
      await createClazz(clazzForm.value);
      message.success('创建成功');
    }
    modalVisible.value = false;
    fetchClazzes(pagination.value.current);
  } catch (e) {}
};

const handleDelete = (id, name) => {
  Modal.confirm({
    title: `确定删除班级「${name}」吗？`,
    content: '删除后班级关联的学生关系将被解除，但用户账号不会被删除。',
    okText: '确定',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteClazz(id);
        message.success('删除成功');
        fetchClazzes(pagination.value.current);
      } catch (e) {}
    }
  });
};

const showStudentModal = async (record) => {
  currentClazz.value = record;
  studentModalVisible.value = true;
  await fetchClazzStudents(record.id);
  await fetchAvailableStudents();
};

const fetchClazzStudents = async (clazzId) => {
  try {
    const res = await getClazzStudents(clazzId);
    clazzStudents.value = res.data;
  } catch (e) {
    message.error('获取班级学生失败');
  }
};

const fetchAvailableStudents = async () => {
  studentModalLoading.value = true;
  try {
    const res = await getUsers({
      role: 'STUDENT',
      page: 0,
      size: 1000
    });
    const currentStudentIds = new Set(clazzStudents.value.map(s => s.id));
    availableStudents.value = res.data.content.filter(s => !currentStudentIds.has(s.id));
  } catch (e) {
    message.error('获取学生列表失败');
  } finally {
    studentModalLoading.value = false;
  }
};

const handleAddStudents = async () => {
  if (selectedStudentIds.value.length === 0) {
    message.warning('请先选择要添加的学生');
    return;
  }
  try {
    await addStudentsToClazz(currentClazz.value.id, selectedStudentIds.value);
    message.success(`成功添加 ${selectedStudentIds.value.length} 名学生`);
    selectedStudentIds.value = [];
    await fetchClazzStudents(currentClazz.value.id);
    await fetchAvailableStudents();
  } catch (e) {}
};

const handleRemoveStudent = (studentId, studentName) => {
  Modal.confirm({
    title: `确定将「${studentName}」移出班级吗？`,
    content: '学生账号不会被删除，只是移出该班级。',
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await removeStudentFromClazz(currentClazz.value.id, studentId);
        message.success('已移出班级');
        await fetchClazzStudents(currentClazz.value.id);
        await fetchAvailableStudents();
      } catch (e) {}
    }
  });
};

const handleImport = async (options) => {
  if (!currentClazz.value) {
    message.warning('请先选择班级');
    return;
  }
  const { file } = options;
  const formData = new FormData();
  formData.append('file', file);
  try {
    const res = await importClazzStudents(currentClazz.value.id, formData);
    const { successCount, skipCount, errors } = res.data;
    let msg = `导入完成：新增 ${successCount} 人，跳过 ${skipCount} 人`;
    if (errors && errors.length > 0) {
      msg += `，错误 ${errors.length} 条`;
      message.warning(msg);
    } else {
      message.success(msg);
    }
    await fetchClazzStudents(currentClazz.value.id);
    await fetchAvailableStudents();
  } catch (e) {
    message.error('导入出错，请检查CSV格式');
  }
};

const downloadTemplate = () => {
  const content = '学号,姓名,密码\n2022001,张三,123456\n2022002,李四,123456';
  const blob = new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', '班级学生导入模板.csv');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

onMounted(() => {
  fetchClazzes();
});

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '班级名称', dataIndex: 'name', key: 'name' },
  { title: '年级', dataIndex: 'grade', key: 'grade' },
  { title: '班主任', key: 'teacher', customRender: ({ record }) => record.teacher?.fullName || '-' },
  { title: '学生人数', key: 'studentCount', customRender: ({ record }) => {
    const clazz = clazzes.value.find(c => c.id === record.id);
    return clazz?._studentCount || '加载中';
  }},
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', customRender: ({text}) => text ? new Date(text).toLocaleDateString() : '-' },
  { title: '操作', key: 'action', width: 280 }
];

const studentColumns = [
  { title: '学号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'fullName', key: 'fullName' },
  { title: '操作', key: 'action', width: 120 }
];
</script>

<template>
  <div class="clazz-management-container">
    <a-card title="班级管理">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="showAddModal" :icon="h(PlusOutlined)">新建班级</a-button>
        </a-space>
      </template>

      <div class="search-bar" style="margin-bottom: 24px;">
        <a-input 
          v-model:value="searchKeyword" 
          placeholder="搜索班级名称/年级" 
          style="width: 300px"
          :prefix="h(SearchOutlined)"
          @pressEnter="handleSearch"
          allowClear
        />
        <a-button type="primary" style="margin-left: 12px" @click="handleSearch" :icon="h(SearchOutlined)">
          搜索
        </a-button>
      </div>

      <a-table 
        :dataSource="clazzes" 
        :columns="columns" 
        :pagination="pagination"
        :loading="loading"
        @change="(pag) => fetchClazzes(pag.current)"
        rowKey="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button size="small" :icon="h(UserOutlined)" @click="showStudentModal(record)">
                学生管理
              </a-button>
              <a-button size="small" :icon="h(EditOutlined)" @click="showEditModal(record)">
                编辑
              </a-button>
              <a-button 
                size="small" 
                danger 
                :icon="h(DeleteOutlined)" 
                @click="handleDelete(record.id, record.name)"
                :disabled="record.teacher?.id !== authStore.user?.id && !authStore.isAdmin"
              >
                删除
              </a-button>
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
      width="500px"
    >
      <a-form ref="formRef" :model="clazzForm" :rules="clazzRules" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }" style="margin-top: 24px;">
        <a-form-item label="班级名称" name="name">
          <a-input v-model:value="clazzForm.name" placeholder="如：软件2201班" />
        </a-form-item>
        <a-form-item label="年级" name="grade">
          <a-input v-model:value="clazzForm.grade" placeholder="如：2022级" />
        </a-form-item>
        <a-form-item label="班主任" v-if="authStore.isAdmin" name="teacherId">
          <a-select v-model:value="clazzForm.teacherId" placeholder="选择班主任">
            <a-select-option :value="authStore.user?.id">{{ authStore.user?.fullName }}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="studentModalVisible"
      :title="`${currentClazz?.name || ''} - 学生管理`"
      :footer="null"
      width="900px"
    >
      <a-row :gutter="16">
        <a-col :span="11">
          <a-card size="small" title="已加入学生" :extra="`${clazzStudents.length} 人`">
            <template #extra>
              <a-space>
                <span>{{ clazzStudents.length }} 人</span>
                <a-upload :customRequest="handleImport" :showUploadList="false">
                  <a-button size="small" :icon="h(ImportOutlined)">批量导入</a-button>
                </a-upload>
                <a-button size="small" @click="downloadTemplate">下载模板</a-button>
              </a-space>
            </template>
            <a-table 
              :dataSource="clazzStudents" 
              :columns="studentColumns" 
              :pagination="{ pageSize: 8, showSizeChanger: false }"
              size="small"
              rowKey="id"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'action'">
                  <a-button 
                    type="link" 
                    size="small" 
                    danger 
                    @click="handleRemoveStudent(record.id, record.fullName)"
                  >
                    移出班级
                  </a-button>
                </template>
              </template>
            </a-table>
          </a-card>
        </a-col>
        
        <a-col :span="2" style="display: flex; align-items: center; justify-content: center;">
          <a-button 
            type="primary" 
            :icon="h(UserAddOutlined)" 
            @click="handleAddStudents"
            :disabled="selectedStudentIds.length === 0"
          >
            添加
          </a-button>
        </a-col>
        
        <a-col :span="11">
          <a-card size="small" title="可选学生" :loading="studentModalLoading">
            <a-table 
              :dataSource="availableStudents" 
              :columns="[
                { title: '选择', key: 'select', width: 50 },
                { title: '学号', dataIndex: 'username', key: 'username' },
                { title: '姓名', dataIndex: 'fullName', key: 'fullName' },
                { title: '当前班级', dataIndex: 'clazz', key: 'clazz', customRender: ({text}) => text || '-' }
              ]" 
              :pagination="{ pageSize: 8, showSizeChanger: false }"
              size="small"
              rowKey="id"
              :rowSelection="{ selectedRowKeys: selectedStudentIds, onChange: (keys) => selectedStudentIds = keys }"
            />
          </a-card>
        </a-col>
      </a-row>
    </a-modal>
  </div>
</template>

<style scoped>
.clazz-management-container {
  padding: 24px;
}
</style>
