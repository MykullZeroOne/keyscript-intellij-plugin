// @keyscript

// React + Keystone Example — Account Lookup Tool
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

Ext.onReady(function() {
    var container = document.createElement('div');
    container.id = 'root';
    document.body.appendChild(container);
    ReactDOM.createRoot(container).render(<App />);
});
