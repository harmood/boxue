<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { disableTotp, enableTotp, getTotpStatus, setupTotp, type TotpSetup, type TotpStatus } from '../api/totp'
import { useAdminAuthStore } from '../stores/auth'

const auth = useAdminAuthStore()
const loading = ref(false)
const busy = ref(false)
const status = ref<TotpStatus>({ enabled: false, confirmedAt: null })
const setupData = ref<TotpSetup | null>(null)
const qrDataUrl = ref('')
const setupForm = reactive({ password: '' })
const enableForm = reactive({ code: '' })
const disableForm = reactive({ password: '', code: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    status.value = await getTotpStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 2FA 状态失败')
  } finally {
    loading.value = false
  }
}

async function startSetup() {
  if (!setupForm.password) {
    ElMessage.warning('请输入当前密码以确认身份')
    return
  }
  busy.value = true
  try {
    setupData.value = await setupTotp(setupForm.password)
    setupForm.password = ''
    qrDataUrl.value = await QRCode.toDataURL(setupData.value.otpauthUri, {
      width: 220,
      margin: 1
    })
    enableForm.code = ''
    ElMessage.success('已生成 TOTP 密钥，请用验证器扫码')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成 TOTP 密钥失败')
  } finally {
    busy.value = false
  }
}

async function enable() {
  if (!/^\d{6}$/.test(enableForm.code)) {
    ElMessage.warning('请输入 6 位动态验证码')
    return
  }
  busy.value = true
  try {
    status.value = await enableTotp(enableForm.code)
    setupData.value = null
    qrDataUrl.value = ''
    await auth.refreshProfile()
    ElMessage.success('2FA 已启用，下次登录需要动态验证码')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启用 2FA 失败')
  } finally {
    busy.value = false
  }
}

async function disable() {
  if (!disableForm.password) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!/^\d{6}$/.test(disableForm.code)) {
    ElMessage.warning('请输入 6 位动态验证码')
    return
  }
  busy.value = true
  try {
    await disableTotp(disableForm.password, disableForm.code)
    disableForm.password = ''
    disableForm.code = ''
    await load()
    await auth.refreshProfile()
    ElMessage.success('2FA 已关闭')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关闭 2FA 失败')
  } finally {
    busy.value = false
  }
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-'
}
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <h2>安全设置</h2>
        <p class="muted">为管理员账号启用基于时间的一次性密码（TOTP）双因素认证。</p>
      </div>
      <el-tag :type="status.enabled ? 'success' : 'warning'" size="large">
        {{ status.enabled ? '2FA 已启用' : '2FA 未启用' }}
      </el-tag>
    </div>

    <el-card v-if="status.enabled" shadow="never" class="section">
      <template #header><span>双因素认证状态</span></template>
      <el-alert
        type="success"
        :closable="false"
        title="管理员登录已受 TOTP 保护"
        :description="`启用时间：${formatDate(status.confirmedAt)}`"
        show-icon
      />
      <el-divider />
      <h4>关闭 2FA</h4>
      <p class="hint">关闭后下次登录只需要密码。需要验证当前密码和 TOTP，防止会话被盗后直接关闭。</p>
      <el-form label-width="100px" class="form">
        <el-form-item label="当前密码">
          <el-input v-model="disableForm.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="disableForm.code" maxlength="6" placeholder="6 位动态验证码" />
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="busy" @click="disable">关闭 2FA</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-else shadow="never" class="section">
      <template #header><span>启用双因素认证</span></template>
      <el-alert
        type="warning"
        :closable="false"
        title="管理员账号尚未启用 2FA"
        description="建议立即启用。启用后登录需要密码 + 验证器动态验证码。"
        show-icon
      />

      <div v-if="!setupData" class="setup-start">
        <el-form label-width="100px" class="form">
          <el-form-item label="当前密码" required>
            <el-input
              v-model="setupForm.password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="确认身份后再生成密钥"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy" @click="startSetup">生成 TOTP 密钥</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-else class="setup-body">
        <div class="qr">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="TOTP 二维码" />
          <p class="hint">使用 Google Authenticator、Microsoft Authenticator、1Password 等验证器扫码。</p>
        </div>
        <div class="secret-panel">
          <div class="label">无法扫码时手动输入密钥</div>
          <el-input :model-value="setupData.secret" readonly />
          <div class="label">otpauth URI</div>
          <el-input :model-value="setupData.otpauthUri" readonly type="textarea" :rows="3" />
          <el-form label-width="100px" class="form">
            <el-form-item label="验证码" required>
              <el-input v-model="enableForm.code" maxlength="6" placeholder="输入验证器当前 6 位验证码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="busy" @click="enable">验证并启用</el-button>
              <el-button @click="setupData = null">取消</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
h2 { margin: 0 0 4px; }
h4 { margin: 0 0 6px; }
.muted { margin: 0 0 16px; color: #64748b; }
.section { margin-bottom: 16px; }
.hint { color: #64748b; font-size: 13px; line-height: 1.6; }
.setup-start { margin-top: 20px; }
.setup-body { display: flex; gap: 24px; margin-top: 20px; flex-wrap: wrap; }
.qr { width: 240px; text-align: center; }
.qr img { width: 220px; height: 220px; border: 1px solid #e5e7eb; border-radius: 12px; }
.secret-panel { flex: 1; min-width: 320px; }
.label { margin: 12px 0 6px; color: #475569; font-size: 13px; font-weight: 600; }
.form { margin-top: 16px; }
</style>
