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
    // Keep the original response text when it is not JSON.
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
  // Some endpoints (e.g. DELETE) return 200 with an empty body; avoid JSON parse errors.
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
