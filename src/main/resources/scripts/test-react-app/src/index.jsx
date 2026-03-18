import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'

const container = document.getElementById('root') || document.createElement('div')
if (!container.parentElement) {
  container.id = 'root'
  document.body.appendChild(container)
}

createRoot(container).render(<App />)
