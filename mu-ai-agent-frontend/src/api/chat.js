import { http } from './http'

function readSseEvent(eventBlock) {
  const dataLines = eventBlock
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).replace(/^ /, ''))

  return dataLines.length ? dataLines.join('\n') : ''
}

export async function streamChat({ path, params, signal, onMessage }) {
  const textDecoder = new TextDecoder('utf-8')
  const response = await http.get(path, {
    params,
    signal,
    adapter: 'fetch',
    responseType: 'stream',
    headers: {
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache',
    },
  })

  const reader = response.data?.getReader?.()
  if (!reader) {
    throw new Error('当前浏览器不支持流式响应')
  }

  let buffer = ''

  const flushEvents = () => {
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''

    events.forEach((eventBlock) => {
      const data = readSseEvent(eventBlock)
      if (data && data !== '[DONE]') {
        onMessage(data)
      }
    })
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += textDecoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, '\n')
    flushEvents()
  }

  buffer += textDecoder.decode()
  if (buffer.trim()) {
    const data = readSseEvent(buffer)
    if (data && data !== '[DONE]') {
      onMessage(data)
    }
  }
}

export async function deleteChatMemory(chatId) {
  await http.delete(`/ai/chat/${encodeURIComponent(chatId)}`)
}
