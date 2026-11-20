<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createRole,
  deleteRole,
  listPermissions,
  listRoles,
  updateRole,
  type Permission,
  type Role
} from '../api/admin'

const loading = ref(false)
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])

const dialogVisible = ref(false)
const saving = ref(false)
const editingRole = ref<Role | null>(null)
const form = reactive({
  code: '',
  name: '',
  description: '',
  permissionCodes: [] as string[]
})

const permissionGroups = computed(() => {
  const groups = new Map<string, Permission[]>()
  for (const permission of permissions.value) {
    const prefix = permission.code.split('_')[0] || 'OTHER'
    if (!groups.has(prefix)) groups.set(prefix, [])
    groups.get(prefix)!.push(permission)
  }
  return Array.from(groups.entries())
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [roleResponse, permissionResponse] = await Promise.all([listRoles(), listPermissions()])
    roles.value = roleResponse.items
    permissions.value = permissionResponse.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载角色失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingRole.value = null
  form.code = ''
  form.name = ''
  form.description = ''
  form.permissionCodes = []
  dialogVisible.value = true
}

function openEdit(role: Role) {
  editingRole.value = role
  form.code = role.code
  form.name = role.name
  form.description = role.description ?? ''
  form.permissionCodes = role.permissions.map((permission) => permission.code)
  dialogVisible.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写角色码和名称')
    return
  }
  if (form.permissionCodes.length === 0) {
    ElMessage.warning('请至少选择一个权限')
    return
  }
  saving.value = true
  try {
    if (editingRole.value) {
      await updateRole(editingRole.value.id, {
        name: form.name,
        description: form.description || undefined,
        permissionCodes: form.permissionCodes
      })
      ElMessage.success('角色已更新')
    } else {
      await createRole({
        code: form.code,
        name: form.name,
        description: form.description || undefined,
        permissionCodes: form.permissionCodes
      })
      ElMessage.success('角色已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(role: Role) {
  try {
    await ElMessageBox.confirm(`确定删除角色 ${role.name}（${role.code}）吗？`, '删除角色', {
      type: 'warning'
    })
    await deleteRole(role.id)
    ElMessage.success('角色已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }
}

</script>

<template>
  <div>
    <div class="head">
      <div>
        <h2>角色权限</h2>
        <p>基于 RBAC 管理角色和权限码，系统角色不可删除。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roles" border stripe>
      <el-table-column prop="code" label="角色码" width="170" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column label="系统角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isSystem ? 'warning' : 'info'">{{ row.isSystem ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="权限数量" width="100">
        <template #default="{ row }">{{ row.permissions.length }}</template>
      </el-table-column>
      <el-table-column label="权限" min-width="320">
        <template #default="{ row }">
          <div class="permission-list">
            <el-tag v-for="permission in row.permissions" :key="permission.code" size="small" type="info">
              {{ permission.code }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" :disabled="row.isSystem" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingRole ? '编辑角色' : '新建角色'"
      width="720px"
    >
      <el-form label-position="top">
        <el-form-item label="角色码">
          <el-input v-model="form.code" :disabled="!!editingRole" placeholder="CONTENT_REVIEWER" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="内容审核员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="权限">
          <div class="permission-groups">
            <el-card v-for="[group, items] in permissionGroups" :key="group" shadow="never" class="permission-group">
              <template #header>{{ group }}</template>
              <el-checkbox-group v-model="form.permissionCodes">
                <el-checkbox v-for="permission in items" :key="permission.code" :label="permission.code">
                  {{ permission.name }}（{{ permission.code }}）
                </el-checkbox>
              </el-checkbox-group>
            </el-card>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.head h2 { margin: 0 0 4px; }
.head p { margin: 0 0 16px; color: #64748b; }
.permission-list { display: flex; flex-wrap: wrap; gap: 4px; }
.permission-groups { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; width: 100%; }
.permission-group :deep(.el-card__header) { padding: 8px 12px; font-weight: 600; }
.permission-group :deep(.el-card__body) { padding: 10px 12px; }
.permission-group :deep(.el-checkbox) { display: flex; margin-bottom: 6px; }
</style>
