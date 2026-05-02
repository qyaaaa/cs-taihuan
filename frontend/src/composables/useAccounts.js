import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { createAccount, deleteAccount, listAccounts, updateAccount } from '../api/accounts'

const STORAGE_KEY = 'cs-taihuan-current-account-id'

export const useAccounts = () => {
  const accountState = reactive({
    accounts: [],
    currentAccountId: null,
    loading: false,
  })

  const currentAccount = computed(() => {
    return accountState.accounts.find((account) => account.id === accountState.currentAccountId) || null
  })

  const loadAccounts = async () => {
    accountState.loading = true
    try {
      const payload = await listAccounts()
      accountState.accounts = Array.isArray(payload) ? payload : []
      const savedId = Number(window.localStorage.getItem(STORAGE_KEY) || 0)
      const saved = accountState.accounts.find((account) => account.id === savedId)
      const next = saved || accountState.accounts[0] || null
      accountState.currentAccountId = next ? next.id : null
      persistCurrentAccount()
      return accountState.accounts
    } catch (error) {
      ElMessage.error(error.message || '读取账号列表失败')
      return []
    } finally {
      accountState.loading = false
    }
  }

  const changeAccount = (accountId) => {
    const normalizedId = Number(accountId)
    const account = accountState.accounts.find((item) => item.id === normalizedId)
    if (!account) {
      return null
    }
    accountState.currentAccountId = normalizedId
    persistCurrentAccount()
    return account
  }

  const createLocalAccount = async () => {
    accountState.loading = true
    try {
      const nickname = `账号 ${accountState.accounts.length + 1}`
      const account = await createAccount({ nickname })
      await loadAccounts()
      changeAccount(account.id)
      ElMessage.success('已创建新账号')
      return account
    } catch (error) {
      ElMessage.error(error.message || '创建账号失败')
      return null
    } finally {
      accountState.loading = false
    }
  }

  const updateLocalAccount = async (accountId, payload) => {
    accountState.loading = true
    try {
      const account = await updateAccount(accountId, payload)
      await loadAccounts()
      changeAccount(accountState.currentAccountId || account.id)
      ElMessage.success('账号信息已更新')
      return account
    } catch (error) {
      ElMessage.error(error.message || '更新账号失败')
      return null
    } finally {
      accountState.loading = false
    }
  }

  const deleteLocalAccount = async (accountId) => {
    accountState.loading = true
    try {
      await deleteAccount(accountId)
      await loadAccounts()
      ElMessage.success('账号已删除')
      return true
    } catch (error) {
      ElMessage.error(error.message || '删除账号失败')
      return false
    } finally {
      accountState.loading = false
    }
  }

  const persistCurrentAccount = () => {
    if (accountState.currentAccountId) {
      window.localStorage.setItem(STORAGE_KEY, String(accountState.currentAccountId))
    }
  }

  return {
    accountState,
    currentAccount,
    loadAccounts,
    changeAccount,
    createLocalAccount,
    updateLocalAccount,
    deleteLocalAccount,
  }
}
