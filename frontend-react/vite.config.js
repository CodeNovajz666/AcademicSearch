import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
<<<<<<< HEAD
        target: process.env.VITE_JAVA_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/agent-api': {
        target: process.env.VITE_AGENT_TARGET || 'http://localhost:8000',
=======
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/agent-api': {
        target: 'http://localhost:8000',
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/agent-api/, '')
      }
    }
  }
})
