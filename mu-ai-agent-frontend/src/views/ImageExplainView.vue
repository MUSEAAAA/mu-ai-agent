<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLogo from '../components/AppLogo.vue'
import { explainImage } from '../api/image'

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const ACCEPTED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp']
const MAX_FILE_SIZE = 10 * 1024 * 1024

const fileInput = ref(null)
const selectedFile = ref(null)
const previewUrl = ref('')
const question = ref('请详细解释这张图片，并指出其中值得关注的细节')
const result = ref('')
const errorMessage = ref('')
const isDragging = ref(false)
const isAnalyzing = ref(false)
const uploadProgress = ref(0)
const copied = ref(false)
let abortController = null

const fileSize = computed(() => {
  if (!selectedFile.value) return ''
  const megabytes = selectedFile.value.size / 1024 / 1024
  return megabytes >= 1
    ? `${megabytes.toFixed(2)} MB`
    : `${Math.max(1, Math.round(selectedFile.value.size / 1024))} KB`
})

function openFilePicker() {
  if (!isAnalyzing.value) fileInput.value?.click()
}

function validateFile(file) {
  if (!file) return '请选择一张图片'

  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!ACCEPTED_TYPES.includes(file.type) || !ACCEPTED_EXTENSIONS.includes(extension)) {
    return '仅支持 JPG、JPEG、PNG 或 WebP 图片'
  }

  if (file.size > MAX_FILE_SIZE) {
    return '图片大小不能超过 10MB'
  }

  return ''
}

function setSelectedFile(file) {
  if (isAnalyzing.value) return

  const validationError = validateFile(file)
  errorMessage.value = validationError
  if (validationError) return

  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  result.value = ''
  uploadProgress.value = 0
  copied.value = false
}

function handleFileChange(event) {
  setSelectedFile(event.target.files?.[0])
  event.target.value = ''
}

function handleDrop(event) {
  isDragging.value = false
  setSelectedFile(event.dataTransfer?.files?.[0])
}

function clearImage() {
  if (isAnalyzing.value) return

  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  selectedFile.value = null
  previewUrl.value = ''
  result.value = ''
  errorMessage.value = ''
  uploadProgress.value = 0
}

async function analyzeImage() {
  if (!selectedFile.value || isAnalyzing.value) return

  errorMessage.value = ''
  result.value = ''
  copied.value = false
  uploadProgress.value = 0
  isAnalyzing.value = true
  abortController = new AbortController()

  try {
    const response = await explainImage({
      file: selectedFile.value,
      question: question.value.trim() || '请解释这张图片',
      signal: abortController.signal,
      onUploadProgress: (event) => {
        if (event.total) {
          uploadProgress.value = Math.round((event.loaded / event.total) * 100)
        }
      },
    })
    result.value = typeof response === 'string' ? response : JSON.stringify(response, null, 2)
    uploadProgress.value = 100
  } catch (error) {
    if (error.name !== 'CanceledError' && error.name !== 'AbortError') {
      errorMessage.value = `分析失败：${error.message}`
    }
  } finally {
    abortController = null
    isAnalyzing.value = false
  }
}

function stopAnalysis() {
  abortController?.abort()
}

async function copyResult() {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(result.value)
    copied.value = true
    window.setTimeout(() => {
      copied.value = false
    }, 1600)
  } catch {
    errorMessage.value = '复制失败，请手动选择分析结果'
  }
}

onBeforeUnmount(() => {
  abortController?.abort()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <main class="vision-page">
    <nav class="vision-nav">
      <RouterLink to="/" aria-label="返回应用中心">
        <AppLogo />
      </RouterLink>
      <RouterLink to="/" class="vision-nav__back">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="m14 6-6 6 6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        返回应用中心
      </RouterLink>
    </nav>

    <header class="vision-header">
      <div class="vision-header__eyebrow"><span></span> MULTIMODAL VISION</div>
      <h1>让 AI 看懂你的图片</h1>
      <p>上传图片并告诉 AI 你想了解什么，它会结合画面内容给出分析。</p>
    </header>

    <section class="vision-workspace">
      <div class="vision-upload-panel">
        <div class="vision-section-heading">
          <span>01</span>
          <div>
            <h2>上传图片</h2>
            <p>支持 JPG、JPEG、PNG、WebP，最大 10MB</p>
          </div>
        </div>

        <input
          ref="fileInput"
          class="vision-file-input"
          type="file"
          accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
          @change="handleFileChange"
        />

        <div
          v-if="!previewUrl"
          class="vision-dropzone"
          :class="{ 'vision-dropzone--active': isDragging }"
          role="button"
          tabindex="0"
          @click="openFilePicker"
          @keydown.enter="openFilePicker"
          @keydown.space.prevent="openFilePicker"
          @dragenter.prevent="isDragging = true"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
        >
          <span class="vision-dropzone__icon">
            <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
              <path d="M6 8h20v16H6V8Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" />
              <circle cx="21" cy="13" r="2.5" fill="currentColor" opacity=".55" />
              <path d="m9 21 5-5 4 4 2.5-2.5L24 21" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
          <strong>点击选择或拖拽图片到这里</strong>
          <span>请选择清晰、内容完整的图片</span>
          <button type="button" tabindex="-1">选择图片</button>
        </div>

        <div v-else class="vision-preview">
          <img :src="previewUrl" :alt="selectedFile?.name || '待分析图片'" />
          <div class="vision-preview__overlay">
            <button type="button" :disabled="isAnalyzing" @click="openFilePicker">更换图片</button>
            <button type="button" :disabled="isAnalyzing" @click="clearImage">移除</button>
          </div>
          <div class="vision-preview__meta">
            <span>
              <strong>{{ selectedFile?.name }}</strong>
              <small>{{ fileSize }}</small>
            </span>
            <span class="vision-preview__ready">
              <i></i>
              已就绪
            </span>
          </div>
        </div>

        <p v-if="errorMessage" class="vision-error">{{ errorMessage }}</p>
      </div>

      <div class="vision-analysis-panel">
        <div class="vision-section-heading">
          <span>02</span>
          <div>
            <h2>提出问题</h2>
            <p>问题越具体，分析结果越贴近你的需求</p>
          </div>
        </div>

        <label class="vision-question">
          <span>你想了解图片的什么内容？</span>
          <textarea
            v-model="question"
            rows="5"
            maxlength="500"
            :disabled="isAnalyzing"
            placeholder="例如：请描述图片中的主要内容，并分析画面的构图和氛围"
          ></textarea>
          <small>{{ question.length }} / 500</small>
        </label>

        <button
          v-if="!isAnalyzing"
          class="vision-submit"
          type="button"
          :disabled="!selectedFile"
          @click="analyzeImage"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 3v3m0 12v3M3 12h3m12 0h3m-3.64-6.36-2.12 2.12M8.76 15.24l-2.12 2.12m10.72 0-2.12-2.12M8.76 8.76 6.64 6.64" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            <circle cx="12" cy="12" r="3.5" stroke="currentColor" stroke-width="1.7" />
          </svg>
          开始分析
        </button>
        <button v-else class="vision-submit vision-submit--stop" type="button" @click="stopAnalysis">
          <span></span>
          停止分析
        </button>

        <div v-if="isAnalyzing" class="vision-progress">
          <div><span :style="{ width: `${Math.max(uploadProgress, 8)}%` }"></span></div>
          <p>
            <span class="typing-indicator"><i></i><i></i><i></i></span>
            AI 正在观察并理解图片...
          </p>
        </div>

        <div class="vision-result" :class="{ 'vision-result--filled': result }">
          <div class="vision-result__header">
            <div>
              <span class="vision-result__mark">
                <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
                  <path d="M8 21V11l8 7 8-7v10" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
                  <circle cx="16" cy="7" r="2" fill="currentColor" />
                </svg>
              </span>
              <span>
                <strong>AI 分析结果</strong>
                <small>VISION ANALYST</small>
              </span>
            </div>
            <button v-if="result" type="button" @click="copyResult">
              {{ copied ? '已复制' : '复制结果' }}
            </button>
          </div>
          <p v-if="result">{{ result }}</p>
          <div v-else class="vision-result__empty">
            <svg viewBox="0 0 48 48" fill="none" aria-hidden="true">
              <rect x="8" y="10" width="32" height="28" rx="6" stroke="currentColor" stroke-width="1.6" />
              <path d="m13 32 8-8 6 6 4-4 4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
              <circle cx="31" cy="19" r="3" fill="currentColor" opacity=".45" />
            </svg>
            <span>上传图片并开始分析后，结果将显示在这里</span>
          </div>
        </div>
      </div>
    </section>

    <footer class="vision-footer">AI 生成内容仅供参考，请勿上传包含敏感隐私的图片</footer>
  </main>
</template>
