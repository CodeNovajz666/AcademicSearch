<template>
  <div class="home-container">
    <div class="hero-section">
      <h2>欢迎使用学术科研文献检索系统</h2>
      <p>基于阿里云百炼大模型和RAG技术，为您提供智能的文献检索和问答服务</p>
    </div>
    
    <div class="stats-section">
      <div class="stat-card">
        <div class="stat-icon">📚</div>
        <div class="stat-value">{{ literatureCount }}</div>
        <div class="stat-label">文献数量</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">🔍</div>
        <div class="stat-value">智能检索</div>
        <div class="stat-label">语义搜索</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">🤖</div>
        <div class="stat-value">AI问答</div>
        <div class="stat-label">RAG技术</div>
      </div>
    </div>

    <div class="quick-actions">
      <h3>快速操作</h3>
      <div class="action-grid">
        <router-link to="/upload" class="action-card">
          <div class="action-icon">📤</div>
          <div class="action-title">上传文献</div>
          <div class="action-desc">上传PDF格式的学术文献</div>
        </router-link>
        <router-link to="/search" class="action-card">
          <div class="action-icon">🔍</div>
          <div class="action-title">文献检索</div>
          <div class="action-desc">搜索相关学术文献</div>
        </router-link>
        <router-link to="/rag" class="action-card">
          <div class="action-icon">💬</div>
          <div class="action-title">智能问答</div>
          <div class="action-desc">基于文献内容提问</div>
        </router-link>
      </div>
    </div>

    <div class="recent-section">
      <h3>最近上传的文献</h3>
      <div v-if="recentLiteratures.length > 0" class="literature-list">
        <div v-for="lit in recentLiteratures" :key="lit.id" class="literature-item">
          <router-link :to="'/literature/' + lit.id" class="lit-title">{{ lit.title }}</router-link>
          <div class="lit-meta">{{ lit.authors }} · {{ lit.year }}</div>
        </div>
      </div>
      <div v-else class="empty-state">
        <p>暂无文献，请先上传</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const literatureCount = ref(0)
const recentLiteratures = ref([])

onMounted(async () => {
  try {
    const response = await axios.get('/api/literature')
    if (response.data.code === 200) {
      recentLiteratures.value = response.data.data.slice(0, 5)
      literatureCount.value = response.data.data.length
    }
  } catch (error) {
    console.error('Failed to fetch literature:', error)
  }
})
</script>

<style scoped>
.home-container {
  max-width: 1200px;
  margin: 0 auto;
}

.hero-section {
  text-align: center;
  padding: 3rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 12px;
  margin-bottom: 2rem;
}

.hero-section h2 {
  font-size: 2rem;
  margin-bottom: 1rem;
}

.hero-section p {
  font-size: 1.1rem;
  opacity: 0.9;
}

.stats-section {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.stat-card {
  background: white;
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  text-align: center;
}

.stat-icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 600;
  color: #667eea;
}

.stat-label {
  color: #666;
  margin-top: 0.25rem;
}

.quick-actions h3, .recent-section h3 {
  margin-bottom: 1rem;
  color: #333;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 2rem;
}

.action-card {
  background: white;
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  text-decoration: none;
  color: inherit;
  transition: transform 0.2s, box-shadow 0.2s;
}

.action-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.action-icon {
  font-size: 2rem;
  margin-bottom: 0.75rem;
}

.action-title {
  font-weight: 600;
  color: #333;
  margin-bottom: 0.25rem;
}

.action-desc {
  color: #666;
  font-size: 0.9rem;
}

.literature-list {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.literature-item {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #f0f0f0;
}

.literature-item:last-child {
  border-bottom: none;
}

.lit-title {
  text-decoration: none;
  color: #333;
  font-weight: 500;
  display: block;
  margin-bottom: 0.25rem;
}

.lit-title:hover {
  color: #667eea;
}

.lit-meta {
  color: #999;
  font-size: 0.9rem;
}

.empty-state {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  text-align: center;
  color: #999;
}
</style>