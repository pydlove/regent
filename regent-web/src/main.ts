import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { initTheme } from './composables/useTheme'
import router from './router'
import './styles/tokens.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

function dismissAppLoader() {
  const loader = document.getElementById('app-loader')
  if (!loader) return
  window.setTimeout(() => {
    loader.classList.add('fade-out')
    window.setTimeout(() => loader.remove(), 500)
  }, 800)
}

// 等首次导航完成再挂载：否则 App 会先用尚未初始化的根路径渲染管理布局，
// 未登录时的子请求一旦返回 401，会在 /register 等深链接上抢先跳到登录页。
router.isReady().then(() => {
  app.mount('#app')
  initTheme()
  window.dispatchEvent(new Event('regent:app-ready'))
  dismissAppLoader()
})
