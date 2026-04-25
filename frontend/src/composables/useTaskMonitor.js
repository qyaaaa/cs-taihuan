import { computed, onBeforeUnmount, ref } from 'vue'
import { getTask } from '../api/tasks'

export const useTaskMonitor = () => {
  const inventoryTask = ref(null)
  const catalogTask = ref(null)
  const taskLogs = ref([])
  const taskLogKeys = new Set()
  const taskPollers = new Map()

  const trackedTasks = computed(() => [inventoryTask.value, catalogTask.value].filter(Boolean))
  const visibleTaskLogs = computed(() => taskLogs.value.slice(0, 24))

  const isRunningTask = (task) => task?.status === 'PENDING' || task?.status === 'RUNNING'

  const taskProgressStatus = (task) => {
    if (task?.status === 'SUCCEEDED') {
      return 'success'
    }
    if (task?.status === 'FAILED') {
      return 'exception'
    }
    return undefined
  }

  const taskCounterText = (task) => {
    if (!task) {
      return ''
    }
    const current = task.current ?? null
    const total = task.total ?? null
    if (current === null && total === null) {
      return '等待后端返回处理进度'
    }
    if (total === null) {
      return `当前进度：${current}`
    }
    return `当前进度：${current || 0} / ${total}`
  }

  const taskTypeLabel = (type) => {
    const labels = {
      INVENTORY_FETCH: 'BUFF 库存',
      INVENTORY_FORCE_FETCH: '强制库存',
      CATALOG_SYNC: '目录同步',
    }
    return labels[type] || type || '后台任务'
  }

  const taskStatusLabel = (status) => {
    const labels = {
      PENDING: '等待中',
      RUNNING: '执行中',
      SUCCEEDED: '已完成',
      FAILED: '失败',
    }
    return labels[status] || status || '未知'
  }

  const formatTaskTime = (timestamp) => {
    const value = Number(timestamp || Date.now())
    return new Date(value).toLocaleTimeString('zh-CN', {
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  }

  const recordTaskLog = (task, fallbackMessage = '') => {
    if (!task) {
      return
    }
    const message = task.errorMessage || task.message || fallbackMessage || '任务状态已更新'
    const key = [
      task.taskId || task.type || 'task',
      task.status || '',
      task.progress ?? '',
      task.current ?? '',
      task.total ?? '',
      message,
    ].join('|')
    if (taskLogKeys.has(key)) {
      return
    }
    taskLogKeys.add(key)
    taskLogs.value = [
      {
        id: `${Date.now()}-${taskLogs.value.length}`,
        taskId: task.taskId,
        type: task.type,
        status: task.status,
        progress: task.progress || 0,
        message,
        time: formatTaskTime(task.finishedAt || task.startedAt || task.createdAt || Date.now()),
      },
      ...taskLogs.value,
    ].slice(0, 40)
  }

  const updateInventoryTask = (task) => {
    inventoryTask.value = task
    recordTaskLog(task, '库存任务状态已更新')
  }

  const updateCatalogTask = (task) => {
    catalogTask.value = task
    recordTaskLog(task, '目录同步任务状态已更新')
  }

  const pollTask = (taskId, assignTask) => {
    if (taskPollers.has(taskId)) {
      clearTimeout(taskPollers.get(taskId))
    }

    return new Promise((resolve, reject) => {
      const poll = async () => {
        try {
          const task = await getTask(taskId)
          assignTask(task)
          if (task.status === 'SUCCEEDED') {
            taskPollers.delete(taskId)
            resolve(task)
            return
          }
          if (task.status === 'FAILED') {
            taskPollers.delete(taskId)
            reject(new Error(task.errorMessage || task.message || '任务执行失败'))
            return
          }
          const timer = setTimeout(poll, 1500)
          taskPollers.set(taskId, timer)
        } catch (error) {
          taskPollers.delete(taskId)
          reject(error)
        }
      }

      poll()
    })
  }

  onBeforeUnmount(() => {
    taskPollers.forEach((timer) => clearTimeout(timer))
    taskPollers.clear()
  })

  return {
    inventoryTask,
    catalogTask,
    trackedTasks,
    visibleTaskLogs,
    isRunningTask,
    taskProgressStatus,
    taskCounterText,
    taskTypeLabel,
    taskStatusLabel,
    updateInventoryTask,
    updateCatalogTask,
    pollTask,
  }
}
