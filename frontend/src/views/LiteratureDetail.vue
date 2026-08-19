<template>
  <div class="detail-container" v-if="literature">
    <div class="detail-header">
      <button @click="goBack" class="back-button">← 返回</button>
      <h1>{{ literature.title }}</h1>
    </div>

    <div class="detail-content">
      <div class="meta-section">
        <div class="meta-item">
          <span class="meta-label">作者:</span>
          <span class="meta-value">{{ literature.authors || '未知' }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">期刊:</span>
          <span class="meta-value">{{ literature.journal || '未知' }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">年份:</span>
          <span class="meta-value">{{ literature.year || '未知' }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">DOI:</span>
          <span class="meta-value">{{ literature.doi || '未知' }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">文件名:</span>
          <span class="meta-value">{{ literature.fileName }}</span>
        </div>
      </div>

      <div class="abstract-section">
        <h3>摘要</h3>
        <div class="abstract-content">
          {{ literature.abstractText || '暂无摘要信息' }}
        </div>
      </div>

      <div class="actions-section">
        <el-button @click="deleteLiterature" type="danger">删除文献</el-button>
      </div>
    </div>
  </div>

  <div v-else class="loading-state">
    <div class="loading-icon">⏳</div>
    <p>加载中...</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const literature = ref(null)

onMounted(async () => {
  const id = route.params.id
  try {
    const response = await axios.get(`/api/literature/${id}`)
    if (response.data.code === 200) {
      literature.value = response.data.data
    }
  } catch (error) {
    console.error('Failed to fetch literature:', error)
    alert('获取文献详情失败')
  }
})

const goBack = () => {
  router.back()
}

const deleteLiterature = async () => {
  if (!confirm('确定要删除这篇文献吗？')) return

  try {
    const response = await axios.delete(`/api/literature/${literature.value.id}`)
    if (response.data.code === 200) {
      alert('删除成功')
      router.push('/')
    }
  } catch (error) {
    console.error('Delete failed:', error)
    alert('删除失败: ' + (error.response?.data?.message || error.message))
  }
}
</script>

<style scoped>
.detail-container {
  max-width: 800px;
  margin: 0 auto;
}

.back-button {
  background: none;
  border: none;
  color: #667eea;
  cursor: pointer;
  font-size: 1rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.back-button:hover {
  text-decoration: underline;
}

.detail-header h1 {
  margin: 1rem 0;
  font-size: 1.5rem;
}

.detail-content {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 2rem;
}

.meta-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 2rem;
  padding-bottom: 2rem;
  border-bottom: 1px solid #f0f0f0;
}

.meta-item {
  display: flex;
  flex-direction: column;
}

.meta-label {
  font-size: 0.9rem;
  color: #999;
  margin-bottom: 0.25rem;
}

.meta-value {
  font-weight: 500;
}

.abstract-section {
  margin-bottom: 2rem;
}

.abstract-section h3 {
  margin-bottom: 1rem;
  color: #333;
}

.abstract-content {
  background: #f8f9ff;
  padding: 1rem;
  border-radius: 8px;
  line-height: 1.8;
  color: #555;
}

.actions-section {
  text-align: right;
}

.loading-state {
  text-align: center;
  padding: 3rem;
}

.loading-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}
</style>