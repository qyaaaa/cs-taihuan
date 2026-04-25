import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteSession, getSessionStatus, importSession, validateSessionApi } from '../api/session'

export const useSession = ({ restorePersistedInventory }) => {
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

  const normalizeSession = (payload) => {
    sessionState.connected = Boolean(payload?.connected)
    sessionState.valid = Boolean(payload?.valid)
    sessionState.source = payload?.source || ''
    sessionState.maskedCookie = payload?.maskedCookie || ''
    sessionState.updatedAt = payload?.updatedAt || ''
    sessionState.lastValidatedAt = payload?.lastValidatedAt || ''
    sessionState.message = payload?.message || '尚未保存 BUFF 会话。'
  }

  const loadSessionStatus = async () => {
    loadingSession.value = true
    try {
      const payload = await getSessionStatus()
      normalizeSession(payload)
      if (payload?.valid) {
        await restorePersistedInventory()
      }
    } catch (error) {
      ElMessage.error(error.message || '读取登录状态失败')
    } finally {
      loadingSession.value = false
    }
  }

  const saveSession = async () => {
    loadingSession.value = true
    try {
      const payload = await importSession(sessionForm.cookie)
      normalizeSession(payload)
      sessionDialogVisible.value = false
      sessionForm.cookie = ''
      ElMessage.success('BUFF 会话已保存到后端')
    } catch (error) {
      ElMessage.error(error.message || '保存会话失败')
    } finally {
      loadingSession.value = false
    }
  }

  const validateSession = async () => {
    loadingSession.value = true
    try {
      const payload = await validateSessionApi()
      normalizeSession(payload)
      if (payload?.valid) {
        await restorePersistedInventory()
      }
      ElMessage.success(payload.message || '会话校验完成')
    } catch (error) {
      ElMessage.error(error.message || '会话校验失败')
    } finally {
      loadingSession.value = false
    }
  }

  const clearSession = async () => {
    loadingSession.value = true
    try {
      await deleteSession()
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
    loadSessionStatus,
    saveSession,
    validateSession,
    clearSession,
  }
}
