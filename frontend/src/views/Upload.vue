<template>
  <div class="upload-container">
    <h2>上传学术文献</h2>
    <p class="subtitle">支持PDF格式文件，系统将自动提取元数据并建立索引</p>

    <el-form :model="form" label-width="100px" class="upload-form">
      <el-form-item label="文献标题">
        <el-input v-model="form.title" placeholder="可选，系统将自动提取" />
      </el-form-item>

      <el-form-item label="作者">
        <el-input v-model="form.authors" placeholder="多个作者用逗号分隔" />
      </el-form-item>

      <el-form-item label="期刊名称">
        <el-input v-model="form.journal" placeholder="可选" />
      </el-form-item>

      <el-form-item label="发表年份">
        <el-input v-model.number="form.year" type="number" placeholder="可选" />
      </el-form-item>

      <el-form-item label="DOI">
        <el-input v-model="form.doi" placeholder="可选" />
      </el-form-item>

      <el-form-item label="PDF文件">
        <div class="upload-area" :class="{ 'drag-over': isDragOver }" 
             @drop.prevent="handleDrop" @dragover.prevent="isDragOver = true" 
             @dragleave="isDragOver = false">
          <input type="file" ref="fileInput" accept=".pdf" class="file-input" 
                 @change="handleFileSelect" />
          <div class="upload-icon">📁</div>
          <p>点击或拖拽PDF文件到此处上传</p>
          <p class="hint">支持.pdf格式，最大50MB</p>
          <div v-if="selectedFile" class="selected-file">
            {{ selectedFile.name }}
            <span class="remove-file" @click="removeFile">✕</span>
          </div>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="upload" :loading="uploading" :disabled="!selectedFile">
          {{ uploading ? '上传中...' : '上传文献' }}
        </el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>

    <el-dialog title="上传成功" :visible.sync="showSuccessDialog" width="400px">
      <div class="success-content">
        <div class="success-icon">✅</div>
        <p>文献上传成功！</p>
        <p class="success-title">{{ uploadedTitle }}</p>
      </div>
      <template #footer>
        <el-button @click="showSuccessDialog = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import axios from 'axios'

const form = reactive({
  title: '',
  authors: '',
  journal: '',
  year: null,
  doi: ''
})

const fileInput = ref(null)
const selectedFile = ref(null)
const isDragOver = ref(false)
const uploading = ref(false)
const showSuccessDialog = ref(false)
const uploadedTitle = ref('')

const handleFileSelect = (event) => {
  const file = event.target.files[0]
  if (file && file.type === 'application/pdf') {
    selectedFile.value = file
  } else {
    alert('请选择PDF格式的文件')
  }
}

const handleDrop = (event) => {
  isDragOver.value = false
  const file = event.dataTransfer.files[0]
  if (file && file.type === 'application/pdf') {
    selectedFile.value = file
  } else {
    alert('请选择PDF格式的文件')
  }
}

const removeFile = () => {
  selectedFile.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const resetForm = () => {
  form.title = ''
  form.authors = ''
  form.journal = ''
  form.year = null
  form.doi = ''
  removeFile()
}

const upload = async () => {
  if (!selectedFile.value) return

  uploading.value = true
  const formData = new FormData()
  formData.append('file', selectedFile.value)
  if (form.title) formData.append('title', form.title)
  if (form.authors) formData.append('authors', form.authors)
  if (form.journal) formData.append('journal', form.journal)
  if (form.year) formData.append('year', form.year)
  if (form.doi) formData.append('doi', form.doi)

  try {
    const response = await axios.post('/api/literature/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    
    if (response.data.code === 200) {
      uploadedTitle.value = response.data.data.title
      showSuccessDialog.value = true
      resetForm()
    }
  } catch (error) {
    console.error('Upload failed:', error)
    alert('上传失败: ' + (error.response?.data?.message || error.message))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-container {
  max-width: 600px;
  margin: 0 auto;
}

.subtitle {
  color: #666;
  margin-bottom: 2rem;
}

.upload-form {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.upload-area {
  border: 2px dashed #ddd;
  border-radius: 12px;
  padding: 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.upload-area:hover, .upload-area.drag-over {
  border-color: #667eea;
  background: #f8f9ff;
}

.file-input {
  display: none;
}

.upload-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.upload-area p {
  margin: 0.5rem 0;
  color: #333;
}

.hint {
  color: #999 !important;
  font-size: 0.9rem !important;
}

.selected-file {
  background: #667eea;
  color: white;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
}

.remove-file {
  cursor: pointer;
  font-weight: bold;
}

.success-content {
  text-align: center;
  padding: 1rem;
}

.success-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.success-title {
  color: #667eea;
  font-weight: 600;
}
</style>