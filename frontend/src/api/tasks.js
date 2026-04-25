import { request } from './http'

export const getTask = (taskId) => request(`/api/tasks/${taskId}`)
