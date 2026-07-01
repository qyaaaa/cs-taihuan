const parseErrorText = (text, status) => {
  const normalized = String(text || '').trim()
  if (!normalized) {
    return `Request failed: ${status}`
  }
  try {
    const payload = JSON.parse(normalized)
    if (payload?.message) {
      return payload.message
    }
  } catch (error) {
    // 非 JSON 响应保留原始文本。
  }
  return normalized
}

export const request = async (url, options = {}) => {
  const response = await fetch(url, options)
  if (!response.ok) {
    const text = await response.text()
    throw new Error(parseErrorText(text, response.status))
  }
  if (response.status === 204) {
    return null
  }
  // 某些接口（如 DELETE）会返回 200 且 body 为空，避免 JSON 解析报错。
  const text = await response.text()
  if (!text) {
    return null
  }
  return JSON.parse(text)
}

export const postJson = async (url, payload) => {
  return request(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export const getJson = async (url) => {
  return request(url)
}
