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
    // Only bounce out when there was a session to lose. A failed sign-in also answers 401,
    // and treating that as an expired session reloaded the login page mid-attempt: the error
    // vanished with the reload, and the operator was dropped back at role selection having to
    // pick their name again, with no idea what had gone wrong.
    if (response.status === 401 && window.state.token) {
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

/*
 * In-page replacements for window.confirm/prompt.
 *
 * The native dialogs render as a browser chrome box captioned with the raw hostname
 * ("production-xxxx.up.railway.app says"), which looks like a phishing warning rather than part
 * of the application, cannot be styled, and on a shop-floor tablet blocks the whole page behind
 * an OS-level modal. They also made "pick a material by typing its number" the only way to edit
 * stock, because a native prompt cannot hold a dropdown.
 *
 * Both return a promise: a string (or the chosen value) when confirmed, null when cancelled.
 */
function uiDialog({ title, message, fields = [], confirmText = 'Підтвердити', cancelText = 'Скасувати', danger = false }) {
  return new Promise(resolve => {
    const overlay = document.createElement('div');
    overlay.className = 'modal';

    const fieldsHtml = fields.map((f, i) => {
      const id = `ui-dialog-field-${i}`;
      if (f.type === 'select') {
        const options = (f.options || [])
          .map(o => `<option value="${escapeHtml(String(o.value))}">${escapeHtml(o.label)}</option>`)
          .join('');
        return `<div class="form-group"><label for="${id}">${escapeHtml(f.label || '')}</label>
                  <select id="${id}" class="form-control">${options}</select></div>`;
      }
      return `<div class="form-group"><label for="${id}">${escapeHtml(f.label || '')}</label>
                <input id="${id}" class="form-control" type="${f.type || 'text'}"
                       value="${escapeHtml(String(f.value ?? ''))}"
                       placeholder="${escapeHtml(f.placeholder || '')}"
                       ${f.min !== undefined ? `min="${escapeHtml(String(f.min))}"` : ''}
                       ${f.step !== undefined ? `step="${escapeHtml(String(f.step))}"` : ''}></div>`;
    }).join('');

    overlay.innerHTML = `
      <div class="modal-content" role="dialog" aria-modal="true">
        <div class="modal-header"><h2>${escapeHtml(title || '')}</h2></div>
        <div class="modal-body">
          ${message ? `<p style="margin-bottom:1.25rem; color: var(--text-secondary); white-space:pre-line;">${escapeHtml(message)}</p>` : ''}
          ${fieldsHtml}
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-outline" data-act="cancel">${escapeHtml(cancelText)}</button>
          <button type="button" class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-act="ok">${escapeHtml(confirmText)}</button>
        </div>
      </div>`;

    document.body.appendChild(overlay);
    requestAnimationFrame(() => overlay.classList.add('active'));

    const inputs = [...overlay.querySelectorAll('.form-control')];
    if (inputs.length) {
      inputs[0].focus();
      if (inputs[0].select) inputs[0].select();
    }

    const close = result => {
      document.removeEventListener('keydown', onKey);
      overlay.classList.remove('active');
      setTimeout(() => overlay.remove(), 200);
      resolve(result);
    };
    const submit = () => {
      if (!fields.length) return close(true);
      close(inputs.length === 1 ? inputs[0].value : inputs.map(i => i.value));
    };
    const onKey = e => {
      if (e.key === 'Escape') close(null);
      // Enter submits from a single-line field, so the keyboard flow matches the native dialog
      // people are replacing here.
      if (e.key === 'Enter' && e.target.tagName !== 'TEXTAREA') { e.preventDefault(); submit(); }
    };

    overlay.querySelector('[data-act="cancel"]').addEventListener('click', () => close(null));
    overlay.querySelector('[data-act="ok"]').addEventListener('click', submit);
    overlay.addEventListener('click', e => { if (e.target === overlay) close(null); });
    document.addEventListener('keydown', onKey);
  });
}

/** Yes/no. Resolves true when confirmed, null when dismissed. */
function uiConfirm(title, message, { confirmText = 'Підтвердити', danger = false } = {}) {
  return uiDialog({ title, message, confirmText, danger });
}

/** Single value. Resolves to the entered string, or null when dismissed. */
function uiPrompt(title, { message, label, value = '', placeholder = '', type = 'text', min, step, confirmText = 'Зберегти' } = {}) {
  return uiDialog({ title, message, confirmText, fields: [{ label, value, placeholder, type, min, step }] });
}
