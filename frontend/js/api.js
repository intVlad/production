const API_BASE = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' ? 'http://localhost:8080/api' : '/api';

// Free-text fields (worker names, defect reasons, outsource partners, comments, ...) get
// rendered into the DOM via innerHTML template literals throughout manager.js/worker.js/tv.js.
// Without this, anyone who can write one of those fields (any authenticated worker, for most
// of them) can inject markup that executes in a manager's or the TV display's browser.
function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

window.state = window.state || {
  token: localStorage.getItem('token') || null,
  user: (() => {
    try {
      return localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')) : null;
    } catch(e) {
      localStorage.removeItem('user');
      return null;
    }
  })()
};

async function apiFetch(endpoint, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };
  
  if (window.state.token) {
    headers['Authorization'] = `Bearer ${window.state.token}`;
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    // 401 means the token itself is invalid/expired - no session to salvage, force a fresh
    // login. 403 means the token is fine but this role can't do THIS one thing (e.g. a
    // Supplier session hitting a manager-only dropdown populate) - treating that the same as
    // 401 used to wipe an entirely valid session and bounce the user back to the login screen
    // over a single unrelated, non-fatal permission check, before they ever saw the screen
    // they actually had access to.
    if (response.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = 'index.html';
      throw new Error('Unauthorized');
    }
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
  }
  
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (e) {
    console.warn('API returned non-JSON response', text);
    return text;
  }
}

async function apiGet(endpoint) {
  return apiFetch(endpoint, { 
    method: 'GET',
    headers: {
      'Cache-Control': 'no-cache',
      'Pragma': 'no-cache'
    }
  });
}

async function apiPost(endpoint, data) {
  return apiFetch(endpoint, {
    method: 'POST',
    body: data ? JSON.stringify(data) : null
  });
}



async function apiPut(endpoint, data) {
  return apiFetch(endpoint, {
    method: 'PUT',
    body: data ? JSON.stringify(data) : null
  });
}

async function apiDelete(endpoint) {
  return apiFetch(endpoint, { method: 'DELETE' });
}
