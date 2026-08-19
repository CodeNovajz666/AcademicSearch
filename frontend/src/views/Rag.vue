<template>
  <div class="rag-container">
    <div class="rag-header">
      <h2>智能问答</h2>
      <p>基于RAG技术，系统将检索相关文献并生成准确回答</p>
    </div>

    <div class="chat-container">
      <div class="chat-messages">
        <div v-for="(msg, index) in messages" :key="index" class="message-item" :class="{ 'user-message': msg.isUser }">
          <div class="message-avatar">
            {{ msg.isUser ? '👤' : '🤖' }}
          </div>
          <div class="message-content">
            <div class="message-text">{{ msg.text }}</div>
            <div v-if="msg.references && msg.references.length > 0" class="message-references">
              <p class="references-title">参考文献:</p>
              <div v-for="ref in msg.references" :key="ref.id" class="reference-item">
                <router-link :to="'/literature/' + ref.id">{{ ref.title }}</router-link>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input 
          v-model="question" 
          placeholder="输入您的问题..." 
          class="question-input"
          @keyup.enter="sendQuestion"
        />
        <el-button type="primary" @click="sendQuestion" :loading="isAnswering">
          {{ isAnswering ? '思考中...' : '提问' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'

const question = ref('')
const isAnswering = ref(false)
const messages = ref([
  {
    isUser: false,
    text: '您好！我是学术文献助手。请输入您的问题，我会基于文献内容为您提供准确的回答。',
    references: []
  }
])

const sendQuestion = async () => {
  if (!question.value.trim() || isAnswering.value) return

  messages.value.push({
    isUser: true,
    text: question.value,
    references: []
  })

  isAnswering.value = true
  const currentQuestion = question.value
  question.value = ''

  try {
    const response = await axios.post('/api/rag/query', {
      question: currentQuestion,
      topK: 5
    })

    if (response.data.code === 200) {
      const data = response.data.data
      messages.value.push({
        isUser: false,
        text: data.answer,
        references: data.references
      })
    }
  } catch (error) {
    console.error('RAG query failed:', error)
    messages.value.push({
      isUser: false,
      text: '抱歉，处理您的问题时出现错误。',
      references: []
    })
  } finally {
    isAnswering.value = false
  }
}
</script>

<style scoped>
.rag-container {
  max-width: 800px;
  margin: 0 auto;
}

.rag-header {
  text-align: center;
  margin-bottom: 2rem;
}

.rag-header h2 {
  margin-bottom: 0.5rem;
}

.rag-header p {
  color: #666;
}

.chat-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 500px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}

.message-item {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.message-item.user-message {
  flex-direction: row-reverse;
}

.message-item.user-message .message-content {
  background: #667eea;
  color: white;
  border-radius: 12px 12px 0 12px;
}

.message-avatar {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.message-content {
  background: #f5f5f5;
  padding: 0.75rem 1rem;
  border-radius: 12px 12px 12px 0;
  max-width: 80%;
}

.message-text {
  line-height: 1.6;
}

.message-references {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px dashed rgba(0, 0, 0, 0.1);
}

.references-title {
  font-size: 0.85rem;
  margin-bottom: 0.5rem;
  opacity: 0.7;
}

.reference-item {
  font-size: 0.85rem;
  margin-bottom: 0.25rem;
}

.reference-item a {
  color: #667eea;
  text-decoration: none;
}

.reference-item a:hover {
  text-decoration: underline;
}

.chat-input {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  border-top: 1px solid #f0f0f0;
}

.question-input {
  flex: 1;
}
</style>