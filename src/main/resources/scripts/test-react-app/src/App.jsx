import React, { useState } from 'react'

const COLORS = ['#007acc', '#4caf50', '#ff9800', '#e91e63', '#9c27b0']

export default function App() {
  const [count, setCount] = useState(0)
  const [items, setItems] = useState([])
  const [input, setInput] = useState('')

  const addItem = () => {
    if (!input.trim()) return
    setItems([...items, { id: Date.now(), text: input, color: COLORS[items.length % COLORS.length] }])
    setInput('')
  }

  const removeItem = (id) => {
    setItems(items.filter(i => i.id !== id))
  }

  const isKeystone = typeof CR !== 'undefined'

  return (
    <div style={{ maxWidth: 500, margin: '40px auto', fontFamily: 'system-ui, sans-serif', padding: 20 }}>
      <h1 style={{ marginBottom: 4 }}>Test React App</h1>
      <p style={{ color: '#888', fontSize: 13, marginBottom: 20 }}>
        Running in {isKeystone ? 'Keystone' : 'standalone'} mode
      </p>

      {/* Counter */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
        <button onClick={() => setCount(c => c - 1)} style={btnStyle}>-</button>
        <span style={{ fontSize: 24, fontWeight: 'bold', minWidth: 40, textAlign: 'center' }}>{count}</span>
        <button onClick={() => setCount(c => c + 1)} style={btnStyle}>+</button>
        <button onClick={() => setCount(0)} style={{ ...btnStyle, background: '#666' }}>Reset</button>
      </div>

      {/* Todo list */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && addItem()}
          placeholder="Add an item..."
          style={{
            flex: 1, padding: '8px 12px', border: '1px solid #ddd',
            borderRadius: 4, fontSize: 14, outline: 'none'
          }}
        />
        <button onClick={addItem} style={{ ...btnStyle, background: '#4caf50' }}>Add</button>
      </div>

      {items.length === 0 && (
        <p style={{ color: '#aaa', fontSize: 13, textAlign: 'center', padding: 20 }}>No items yet</p>
      )}

      {items.map(item => (
        <div key={item.id} style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '8px 12px', marginBottom: 4, borderRadius: 4,
          borderLeft: `4px solid ${item.color}`, background: '#f9f9f9'
        }}>
          <span>{item.text}</span>
          <button
            onClick={() => removeItem(item.id)}
            style={{ background: 'none', border: 'none', color: '#ccc', cursor: 'pointer', fontSize: 18 }}
          >
            ×
          </button>
        </div>
      ))}

      {items.length > 0 && (
        <p style={{ color: '#888', fontSize: 12, marginTop: 8 }}>{items.length} item{items.length !== 1 ? 's' : ''}</p>
      )}
    </div>
  )
}

const btnStyle = {
  padding: '8px 16px',
  background: '#007acc',
  color: 'white',
  border: 'none',
  borderRadius: 4,
  cursor: 'pointer',
  fontSize: 14
}
