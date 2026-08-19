<template>
  <div class="search-container">
    <div class="search-header">
      <h2>文献检索</h2>
      <p>输入关键词或问题，系统将基于语义相似度检索相关文献</p>
    </div>

    <div class="search-box">
      <el-input 
        v-model="query" 
        placeholder="输入检索关键词或问题..." 
        class="search-input"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch" :loading="searching">
        检索
      </el-button>
    </div>

    <div v-if="results.length > 0" class="search-results">
      <div class="results-header">
        <span>找到 {{ results.length }} 条相关文献</span>
      </div>
      
      <div v-for="result in results" :key="result.literature.id" class="result-item">
        <router-link :to="'/literature/' + result.literature.id" class="result-title">
          {{ result.literature.title }}
        </router-link>
        <div class="result-meta">
          <span v-if="result.literature.authors">{{ result.literature.authors }}</span>
          <span v-if="result.literature.year"> · {{ result.literature.year }}</span>
          <span v-if="result.literature.journal"> · {{ result.literature.journal }}</span>
        </div>
        <div v-if="result.snippet" class="result-snippet">
          {{ result.snippet }}
        </div>
        <div class="result-score">
          相似度: {{ (result.similarity * 100).toFixed(1) }}%
        </div>
      </div>
    </div>

    <div v-else-if="searching === false && query" class="empty-results">
      <div class="empty-icon">🔍</div>
      <p>未找到相关文献</p>
      <p class="empty-hint">请尝试其他关键词，或先上传文献</p>
    </div>

    <div v-else-if="!query" class="empty-state">
      <div class="empty-icon">📚</div>
      <p>请输入检索关键词</p>
      <router-link to="/upload" class="upload-link">上传文献</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'

const query = ref('')
const results = ref([])
const searching = ref(false)

const handleSearch = async () => {
  if (!query.value.trim()) return
  
  searching.value = true
  try {
    const response = await axios.get('/api/literature/search', {
      params: { query: query.value, topK: 10 }
    })
    
    if (response.data.code === 200) {
      results.value = response.data.data
    }
  } catch (error) {
    console.error('Search failed:', error)
    alert('检索失败: ' + (error.response?.data?.message || error.message))
  } finally {
    searching.value = false
  }
}
</script>

<style scoped>
.search-container {
  max-width: 800px;
  margin: 0 auto;
}

.search-header {
  text-align: center;
  margin-bottom: 2rem;
}

.search-header h2 {
  margin-bottom: 0.5rem;
}

.search-header p {
  color: #666;
}

.search-box {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
}

.search-input {
  flex: 1;
  font-size: 1rem;
}

.search-results {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.results-header {
  padding: 1rem 1.5rem;
  background: #f8f9ff;
  border-bottom: 1px solid #e0e0e0;
  font-weight: 500;
}

.result-item {
  padding: 1.5rem;
  border-bottom: 1px solid #f0f0f0;
}

.result-item:last-child {
  border-bottom: none;
}

.result-title {
  text-decoration: none;
  color: #333;
  font-weight: 600;
  font-size: 1.1rem;
  display: block;
  margin-bottom: 0.5rem;
}

.result-title:hover {
  color: #667eea;
}

.result-meta {
  color: #666;
  font-size: 0.9rem;
  margin-bottom: 0.5rem;
}

.result-snippet {
  background: #f8f9ff;
  padding: 0.75rem;
  border-radius: 6px;
  color: #555;
  font-size: 0.9rem;
  margin-bottom: 0.5rem;
}

.result-score {
  color: #667eea;
  font-size: 0.85rem;
}

.empty-results, .empty-state {
  text-align: center;
  padding: 3rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.empty-results p, .empty-state p {
  color: #666;
  margin: 0.5rem 0;
}

.empty-hint {
  font-size: 0.9rem !important;
}

.upload-link {
  display: inline-block;
  margin-top: 1rem;
  padding: 0.5rem 1.5rem;
  background: #667eea;
  color: white;
  text-decoration: none;
  border-radius: 6px;
}

.upload-link:hover {
  background: #5a6fd6;
}
</style>