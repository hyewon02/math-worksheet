import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 빌드 결과는 Spring Boot의 static 폴더로 들어가 한 프로그램으로 배포된다(4장).
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
