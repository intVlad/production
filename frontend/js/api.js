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

/*
 * Turns a <select> into something usable when it has more than a screenful of options.
 *
 * A native select gives no way to search: with a series of a thousand units, choosing the right
 * one means scrolling a list of near-identical serial numbers. This keeps the original <select>
 * as the value holder - so every form that reads .value keeps working unchanged - and puts a
 * filter box and a filtered list in front of it.
 */
function makeSearchable(select, { placeholder = 'Почніть вводити для пошуку…', threshold = 12 } = {}) {
  if (!select) return;

  // Re-runs whenever the list is repopulated; drop the previous wrapper first.
  if (select.dataset.searchable === 'true' && select.parentElement?.classList.contains('searchable')) {
    select.parentElement.replaceWith(select);
  }
  select.dataset.searchable = '';
  const options = [...select.options];
  if (options.length <= threshold) return;   // a short list is easier as a plain dropdown

  select.dataset.searchable = 'true';
  select.style.display = 'none';

  const wrap = document.createElement('div');
  wrap.className = 'searchable';
  select.parentNode.insertBefore(wrap, select);
  wrap.appendChild(select);

  const field = document.createElement('input');
  field.type = 'text';
  field.className = 'form-control';
  field.autocomplete = 'off';
  field.placeholder = placeholder;
  field.value = select.selectedIndex > 0 ? options[select.selectedIndex].text : '';

  const panel = document.createElement('div');
  panel.className = 'searchable-panel';
  wrap.append(field, panel);

  const MAX_SHOWN = 100;   // a thousand nodes in the DOM helps nobody; narrow the filter instead
  let active = -1;

  const render = (query = '') => {
    const q = query.trim().toLowerCase();
    const matches = options
      .filter(o => o.value && o.text.toLowerCase().includes(q))
      .slice(0, MAX_SHOWN);
    active = -1;
    if (!matches.length) {
      panel.innerHTML = `<div class="searchable-empty">Нічого не знайдено</div>`;
      return;
    }
    panel.innerHTML = matches
      .map(o => `<div class="searchable-option" data-value="${escapeHtml(o.value)}">${escapeHtml(o.text)}</div>`)
      .join('') + (matches.length === MAX_SHOWN
        ? `<div class="searchable-empty">Показано перші ${MAX_SHOWN}. Уточніть пошук.</div>` : '');
  };

  const open = () => { render(field.value === pickedText() ? '' : field.value); wrap.classList.add('open'); };
  const close = () => { wrap.classList.remove('open'); field.value = pickedText(); };
  const pickedText = () => (select.selectedIndex > 0 ? options[select.selectedIndex].text : '');

  const choose = value => {
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    close();
  };

  field.addEventListener('focus', open);
  field.addEventListener('input', () => { wrap.classList.add('open'); render(field.value); });
  field.addEventListener('keydown', e => {
    const items = [...panel.querySelectorAll('.searchable-option')];
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      if (!items.length) return;
      active = e.key === 'ArrowDown'
        ? Math.min(active + 1, items.length - 1)
        : Math.max(active - 1, 0);
      items.forEach((el, i) => el.classList.toggle('active', i === active));
      items[active].scrollIntoView({ block: 'nearest' });
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (items[active >= 0 ? active : 0]) choose(items[active >= 0 ? active : 0].dataset.value);
    } else if (e.key === 'Escape') {
      close();
      field.blur();
    }
  });
  panel.addEventListener('mousedown', e => {
    const opt = e.target.closest('.searchable-option');
    if (opt) { e.preventDefault(); choose(opt.dataset.value); }
  });
  document.addEventListener('click', e => { if (!wrap.contains(e.target)) close(); });
}
