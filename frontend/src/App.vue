<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-content">
        <h1 class="app-title">学术科研文献检索系统</h1>
        <nav class="app-nav">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/upload" class="nav-link">上传文献</router-link>
          <router-link to="/search" class="nav-link">文献检索</router-link>
          <router-link to="/rag" class="nav-link">智能问答</router-link>
        </nav>
      </div>
    </header>
    <main class="app-main">
      <router-view />
    </main>
    <footer class="app-footer">
      <p>基于RAG技术的学术文献检索系统</p>
    </footer>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1rem 0;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.app-title {
  font-size: 1.5rem;
  font-weight: 600;
}

.app-nav {
  display: flex;
  gap: 2rem;
}

.nav-link {
  color: rgba(255, 255, 255, 0.9);
  text-decoration: none;
  font-weight: 500;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  transition: all 0.3s;
}

.nav-link:hover {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.nav-link.router-link-active {
  background: rgba(255, 255, 255, 0.2);
}

.app-main {
  flex: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
  width: 100%;
}

.app-footer {
  text-align: center;
  padding: 1.5rem;
  background: #f5f5f5;
  color: #666;
  margin-top: auto;
}
</style>