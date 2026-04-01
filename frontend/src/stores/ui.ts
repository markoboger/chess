import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useUIStore = defineStore('ui', () => {
  const theme = ref<'light' | 'dark'>('light')
  const mobileMenuOpen = ref(false)

  // Load theme from localStorage on init
  const savedTheme = localStorage.getItem('chess-theme')
  if (savedTheme === 'dark' || savedTheme === 'light') {
    theme.value = savedTheme
  } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
    theme.value = 'dark'
  }

  // Apply theme to document
  function applyTheme() {
    if (theme.value === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  // Watch theme changes
  watch(theme, (newTheme) => {
    localStorage.setItem('chess-theme', newTheme)
    applyTheme()
  })

  // Apply initial theme
  applyTheme()

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
  }

  function toggleMobileMenu() {
    mobileMenuOpen.value = !mobileMenuOpen.value
  }

  return {
    theme,
    mobileMenuOpen,
    toggleTheme,
    toggleMobileMenu,
  }
})
