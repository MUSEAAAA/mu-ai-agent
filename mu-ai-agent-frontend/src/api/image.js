import { http } from './http'

export async function explainImage({ file, question, signal, onUploadProgress }) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('question', question || '请解释这张图片')

  const response = await http.post('/ai/image/explain', formData, {
    signal,
    onUploadProgress,
  })

  return response.data
}
