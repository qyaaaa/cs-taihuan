import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { cancelQrLogin, deleteSession, getQrLoginStatus, getSessionStatus, importSession, startQrLogin, validateSessionApi } from '../api/session'
import { deleteAccount as deleteAccountApi } from '../api/accounts'

export const useSession = ({ restorePersistedInventory, accountId, onAccountUpdated, pendingNewAccount, createLocalAccount, selectAccount }) => {
  const loadingSession = ref(false)
  const sessionDialogVisible = ref(false)
  const sessionForm = reactive({
    cookie: '',
  })

  const sessionState = reactive({
    connected: false,
    valid: false,
    source: '',
    maskedCookie: '',
    updatedAt: '',
    lastValidatedAt: '',
    message: '尚未保存 BUFF 会话。',
  })

  const qrLogin = reactive({
    activeTab: 'qrcode',
    active: false,
    sessionId: null,
    qrcode: null,
    status: 'IDLE',
    message: '',
    pollTimer: null,
  })

  const resetQrLogin = () => {
    if (qrLogin.pollTimer) {
      clearInterval(qrLogin.pollTimer)
      qrLogin.pollTimer = null
    }
    qrLogin.activeTab = 'qrcode'
    qrLogin.active = false
    qrLogin.sessionId = null
    qrLogin.qrcode = null
    qrLogin.status = 'IDLE'
    qrLogin.message = ''
  }

  const beginQrLogin = async () => {
    resetQrLogin()
    loadingSession.value = true
    try {
      // pending 新账号先不传 account id，后端只在扫码真正成功后创建账号。
      const startAccountId = pendingNewAccount?.value ? null : resolveAccountId()
      const payload = await startQrLogin(startAccountId)
      qrLogin.active = true
      qrLogin.sessionId = payload.sessionId
      qrLogin.qrcode = payload.qrcode
      qrLogin.status = payload.status
      qrLogin.message = payload.message

      if (payload.status === 'SUCCESS') {
        // 已经处于登录态。
        await onQrLoginSuccess(payload)
        return
      }

      // 开始轮询扫码状态。
      qrLogin.pollTimer = setInterval(() => pollQrLoginStatus(), 2000)
    } catch (error) {
      ElMessage.error(error.message || '启动扫码登录失败')
      resetQrLogin()
    } finally {
      loadingSession.value = false
    }
  }

  const pollQrLoginStatus = async () => {
    if (!qrLogin.sessionId || qrLogin.status === 'SUCCESS'
      || qrLogin.status === 'EXPIRED' || qrLogin.status === 'FAILED') {
      return
    }
    try {
      const payload = await getQrLoginStatus(qrLogin.sessionId, resolveAccountId())
      qrLogin.status = payload.status
      qrLogin.message = payload.message

      if (payload.status === 'SUCCESS') {
        await onQrLoginSuccess(payload)
      } else if (payload.status === 'EXPIRED' || payload.status === 'FAILED') {
        qrLogin.active = false
        if (qrLogin.pollTimer) {
          clearInterval(qrLogin.pollTimer)
          qrLogin.pollTimer = null
        }
      }
    } catch (error) {
      // 轮询期间偶发错误可预期，静默重试。
    }
  }

  const onQrLoginSuccess = async (payload) => {
    if (qrLogin.pollTimer) {
      clearInterval(qrLogin.pollTimer)
      qrLogin.pollTimer = null
    }
    qrLogin.active = false
    qrLogin.qrcode = null
    sessionDialogVisible.value = false
    ElMessage.success('扫码登录成功，正在校验会话...')
    // 先刷新账号列表；后端可能刚刚创建了新账号。
    await onAccountUpdated?.()
    if (payload?.accountId) {
      if (pendingNewAccount) {
        pendingNewAccount.value = false
      }
      selectAccount?.(payload.accountId)
    }
    await restorePersistedInventory()
    await loadSessionStatus()
  }

  const cancelCurrentQrLogin = async () => {
    if (qrLogin.sessionId) {
      try {
        await cancelQrLogin(qrLogin.sessionId, resolveAccountId())
      } catch (error) {
        // 忽略取消失败。
      }
    }
    resetQrLogin()
  }

  const normalizeSession = (payload) => {
    sessionState.connected = Boolean(payload?.connected)
    sessionState.valid = Boolean(payload?.valid)
    sessionState.source = payload?.source || ''
    sessionState.maskedCookie = payload?.maskedCookie || ''
    sessionState.updatedAt = payload?.updatedAt || ''
    sessionState.lastValidatedAt = payload?.lastValidatedAt || ''
    sessionState.message = payload?.message || '尚未保存 BUFF 会话。'
  }

  const resolveAccountId = () => accountId?.value || null

  const validateSession = async ({ silent = false } = {}) => {
    loadingSession.value = true
    try {
      const requestedId = resolveAccountId()
      const payload = await validateSessionApi(requestedId)
      normalizeSession(payload)
      // 后端可能因 buff_user_id 命中已有账号而合并并删除当前重复槽位：刷新列表并切到规范账号。
      if (payload?.accountId && payload.accountId !== requestedId) {
        await onAccountUpdated?.()
        selectAccount?.(payload.accountId)
      } else if (payload?.valid) {
        await onAccountUpdated?.()
        await restorePersistedInventory()
      }
      if (!silent) {
        ElMessage.success(payload.message || '会话校验完成')
      }
      return payload
    } catch (error) {
      if (!silent) {
        ElMessage.error(error.message || '会话校验失败')
      } else {
        sessionState.valid = false
        sessionState.message = error.message || '自动校验失败，请重新导入 BUFF 会话。'
      }
      return null
    } finally {
      loadingSession.value = false
    }
  }

  const loadSessionStatus = async () => {
    loadingSession.value = true
    try {
      const payload = await getSessionStatus(resolveAccountId())
      normalizeSession(payload)
      if (payload?.connected) {
        loadingSession.value = false
        await validateSession({ silent: true })
      }
    } catch (error) {
      ElMessage.error(error.message || '读取登录状态失败')
    } finally {
      loadingSession.value = false
    }
  }

  const saveSession = async () => {
    if (!sessionForm.cookie.trim()) {
      ElMessage.warning('请先粘贴 BUFF Cookie')
      return
    }
    loadingSession.value = true
    // 记录本次调用创建的账号，导入失败时回滚，避免留下空账号。
    let createdAccount = null
    try {
      // pending 新账号只有在确实保存 Cookie 时才创建。
      if (pendingNewAccount?.value) {
        createdAccount = await createLocalAccount?.()
        if (!createdAccount) {
          loadingSession.value = false
          return
        }
        pendingNewAccount.value = false
      }
      const payload = await importSession(sessionForm.cookie, resolveAccountId())
      normalizeSession(payload)
      sessionDialogVisible.value = false
      sessionForm.cookie = ''
      loadingSession.value = false
      const validated = await validateSession({ silent: true })
      await onAccountUpdated?.()
      if (validated?.valid) {
        ElMessage.success('BUFF 会话已保存并校验通过')
      } else {
        ElMessage.warning(validated?.message || 'BUFF 会话已保存，自动校验未通过')
      }
    } catch (error) {
      ElMessage.error(error.message || '保存会话失败')
      // 回滚刚创建的账号，避免导入失败后留下空账号。
      if (createdAccount) {
        try {
          await deleteAccountApi(createdAccount.id)
          await onAccountUpdated?.()
        } catch (rollbackError) {
          // 尽力清理，忽略回滚失败。
        }
      }
    } finally {
      loadingSession.value = false
    }
  }

  const clearSession = async () => {
    loadingSession.value = true
    try {
      await deleteSession(resolveAccountId())
      await onAccountUpdated?.()
      normalizeSession({
        connected: false,
        valid: false,
        source: '',
        maskedCookie: '',
        updatedAt: '',
        lastValidatedAt: '',
        message: '已清除后端托管的 BUFF 会话。',
      })
      ElMessage.success('已清除 BUFF 会话')
    } catch (error) {
      ElMessage.error(error.message || '清除会话失败')
    } finally {
      loadingSession.value = false
    }
  }

  return {
    loadingSession,
    sessionDialogVisible,
    sessionForm,
    sessionState,
    qrLogin,
    loadSessionStatus,
    saveSession,
    validateSession,
    clearSession,
    beginQrLogin,
    cancelCurrentQrLogin,
    resetQrLogin,
  }
}
