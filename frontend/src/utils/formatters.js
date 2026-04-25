export const currency = (value) => {
  const number = Number(value || 0)
  return `¥${number.toFixed(2)}`
}

export const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
