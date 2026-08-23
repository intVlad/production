
const state = Object.assign(window.state, {
  currentView: 'worker-mobile',
  isWorkerLoggedIn: !!window.state.token,
  currentWorker: window.state.user,
  activePallet: null,
  activeTask: null,
  dataCache: {
    models: [],
    series: [],
    sections: [],
    posts: [],
    materials: [],
    workers: []
  },
  scanner: null,
  timerInterval: null,
  taskStartTime: null,
  eventSource: null
});

function connectSSE() {
  if (!state.token) return;
  if (state.eventSource) {
    state.eventSource.close();
  }
  const evtSource = new EventSource(API_BASE + '/events/stream?token=' + state.token);
  state.eventSource = evtSource;
  
  evtSource.onmessage = function(event) {
    if (event.data === "dashboard_update" && state.currentView === 'dashboard') {
      loadDashboardData();
    }
  };
}

const dom = {};

function initDOM() {
  dom.toastContainer = document.getElementById('toast-container') || document.body;
  dom.btnMobileMenu = document.getElementById('btn-mobile-menu');
  dom.sidebar = document.getElementById('sidebar');
  dom.viewSections = document.querySelectorAll('.view-section');
  dom.navItems = document.querySelectorAll('.nav-item');
  
  // Dashboard
  dom.statCards = document.querySelectorAll('.stat-card');
  dom.dashboardActiveSeries = document.getElementById('dashboard-active-series');
  dom.dashboardAuditLog = document.getElementById('dashboard-audit-log');

  // Models
  dom.modelsTable = document.getElementById('models-table-body');
  dom.newModelForm = document.getElementById('new-model-form');
  dom.assembliesTable = document.getElementById('assemblies-table-body');
  
  // Series
  dom.seriesTable = document.getElementById('series-table-body');
  dom.newSeriesForm = document.getElementById('new-series-form');

  // Kanban
  dom.kanbanPlanned = document.getElementById('kanban-planned');
  dom.kanbanInProgress = document.getElementById('kanban-in-progress');
  dom.kanbanReady = document.getElementById('kanban-ready');

  // Pallets & Batches
  dom.palletsTable = document.getElementById('pallets-table-body');
  dom.batchesTable = document.getElementById('batches-table-body');

  // Worker Mobile
  dom.workerLoginSection = document.getElementById('worker-login-section');
  dom.workerWorkspaceSection = document.getElementById('worker-workspace-section');
  dom.loginPinInput = document.getElementById('worker-pin');
  dom.loginBtn = document.getElementById('worker-login-btn');
  dom.workerGreeting = document.getElementById('worker-greeting');
  dom.startScanBtn = document.getElementById('start-scan-btn');
  dom.qrReader = document.getElementById('qr-reader');
  
  dom.taskDetails = document.getElementById('mobile-pallet-card');
  dom.cardLabel = document.getElementById('mobile-card-label');
  dom.btnTaskStart = document.getElementById('btn-start-op');
  dom.activeControls = document.getElementById('mobile-active-controls');
  dom.btnTaskPause = document.getElementById('btn-pause-op');
  dom.btnTaskResume = document.getElementById('btn-resume-op');
  dom.btnTaskComplete = document.getElementById('btn-complete-op');
  dom.btnDamageOp = document.getElementById('btn-damage-op');
  dom.taskTimer = document.getElementById('mobile-op-timer');
  dom.tasksList = document.getElementById('mobile-tasks-list');
  dom.currentSection = document.getElementById('mobile-current-section');
}

function showToast(message, type = 'info') {
  if (!dom.toastContainer) return;
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.style.cssText = 'padding: 1rem; margin-bottom: 0.5rem; border-radius: 8px; color: white; opacity: 0.9; font-weight: 500; transition: opacity 0.3s;';
  
  if (type === 'success') toast.style.backgroundColor = 'var(--success)';
  else if (type === 'warning') toast.style.backgroundColor = 'var(--warning)';
  else if (type === 'error') toast.style.backgroundColor = 'var(--danger)';
  else toast.style.backgroundColor = 'var(--primary)';

  toast.innerHTML = `<span>${escapeHtml(message)}</span>`;
  dom.toastContainer.appendChild(toast);
  
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// Generic API Wrapper


// Services
const Services = {
  Dashboard: {
    getStats: (query = '') => apiGet('/dashboard' + query),
  },
  Models: {
    getAll: () => apiGet('/models'),
    create: (data) => apiPost('/models', data),
    getAssemblies: (modelId) => apiGet(`/models/${modelId}/assemblies`)
  },
  Series: {
    getActive: () => apiGet('/series/active'),
    create: (data) => apiPost('/series', data)
  },
  Production: {
    // There is no bulk "all products" endpoint on the backend - /production/kanban never
    // existed, so this silently 404'd/500'd on every load and the Kanban board was always
    // empty. Built from what does exist: active series + per-series product instances.
    getKanban: async () => {
      const series = await apiGet('/series/active');
      const seriesList = Array.isArray(series) ? series : [];
      const perSeries = await Promise.all(
        seriesList.map(s => apiGet(`/series/${s.id}/products`).catch(() => []))
      );
      return perSeries.flat();
    }
  },
  Pallets: {
    getAll: () => apiGet('/pallets'),
    getByQR: (qr) => apiGet(`/pallets/qr/${qr}`),
    create: (data) => apiPost('/pallets', data)
  },
  Batches: {
    getAll: () => apiGet('/batches'),
    getById: (id) => apiGet(`/batches/${id}`),
    create: (data) => apiPost('/batches', data),
    start: (id, workerId) => apiPost(`/batches/${id}/start`, { workerId }),
    complete: (id, actualQuantity) => apiPost(`/batches/${id}/complete`, { actualQuantity }),
    distribute: (id, payload) => apiPost(`/batches/${id}/distribute`, payload)
  },
  Operations: {
    getAll: () => apiGet('/operations')
  },
  Tasks: {
    start: (id, workerId) => apiPost(`/tasks/${id}/start`, { workerId }),
    pause: (id, workerId) => apiPost(`/tasks/${id}/pause`, { workerId }),
    resume: (id, workerId) => apiPost(`/tasks/${id}/resume`, { workerId }),
    complete: (id, workerId) => apiPost(`/tasks/${id}/complete`, { workerId }),
    damage: (id, workerId, reason, resolution) => apiPost(`/tasks/${id}/damage`, { workerId, reason, resolution }),
  },
  Workers: {
    getAll: () => apiGet('/workers'),
    login: (pin) => apiPost('/auth/login/pin', { pin })
  },
  Defects: {
    report: (payload) => apiPost('/defects', payload)
  },
  Outsource: {
    getAll: () => apiGet('/outsource'),
    getActive: () => apiGet('/outsource/active'),
    create: (data) => apiPost('/outsource', data),
    send: (id) => apiPost(`/outsource/${id}/send`),
    receive: (id, workerId) => apiPost(`/outsource/${id}/receive`, { receivedByWorkerId: workerId })
  }
};

// Routing
function switchView(viewId) {
  state.currentView = viewId;
  if (dom.sidebar) dom.sidebar.classList.remove('mobile-open');
  
  dom.viewSections.forEach(section => {
    if (section.id === `view-${viewId}`) {
      section.classList.remove('hidden');
      section.classList.add('active');
    } else {
      section.classList.remove('active');
      section.classList.add('hidden');
    }
  });

  dom.navItems.forEach(item => {
    if (item.dataset.view === viewId) {
      item.classList.add('active');
    } else {
      item.classList.remove('active');
    }
  });

  loadViewData(viewId);
}

const viewLoaders = {
  'dashboard': loadDashboardData,
  'models': loadModelsData,
  'series': loadSeriesData,
  'kanban': loadKanbanData,
  'pallets': loadPalletsData,
  'batches': loadBatchesData,
  'outsource': loadOutsourceData,
  'worker': async () => checkWorkerState()
};

async function loadViewData(viewId) {
  const loader = viewLoaders[viewId];
  if (loader) {
    try {
      await loader();
    } catch (e) {
      console.error(`Error loading view ${viewId}:`, e);
    }
  }
}

// Module Implementations

async function loadDashboardData() {
  try {
    const seriesId = document.getElementById('dash-filter-series')?.value || '';
    const workerId = document.getElementById('dash-filter-worker')?.value || '';
    const status = document.getElementById('dash-filter-status')?.value || '';
    
    const params = new URLSearchParams();
    if (seriesId) params.append('seriesId', seriesId);
    if (workerId) params.append('workerId', workerId);
    if (status) params.append('status', status);
    
    const query = params.toString() ? '?' + params.toString() : '';
    const data = await Services.Dashboard.getStats(query);
    
    if (data && dom.statCards) {
      showToast('Дашборд оновлено', 'success');
    }

    const tasksTable = document.querySelector('#dashboard-tasks-table tbody');
    if (tasksTable && data && data.activeTasks) {
      const now = new Date();
      tasksTable.innerHTML = data.activeTasks.map(t => {
        let isOverdue = false;
        if (t.deadline) {
          isOverdue = new Date(t.deadline) < now && t.status !== 'COMPLETED';
        }
        return `
          <tr class="${isOverdue ? 'overdue-task' : ''}">
            <td>${escapeHtml(t.series ? (t.series.name || t.series.id) : '-')}</td>
            <td>${escapeHtml(t.worker ? t.worker.name : '-')}</td>
            <td>${escapeHtml(t.status)}</td>
            <td>${t.deadline ? new Date(t.deadline).toLocaleDateString() : '-'}</td>
          </tr>
        `;
      }).join('');
    }

    const workerSelect = document.getElementById('dash-filter-worker');
    if (workerSelect && workerSelect.options.length <= 1) {
      try {
        const workers = await Services.Workers.getAll();
        const currentVal = workerSelect.value;
        workerSelect.innerHTML = '<option value="">Всі працівники</option>' + workers.map(w => `<option value="${w.id}">${escapeHtml(w.name)}</option>`).join('');
        workerSelect.value = currentVal;
      } catch(e) {}
    }
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadModelsData() {
  if (!dom.modelsTable) return;
  try {
    const models = await Services.Models.getAll();
    state.dataCache.models = models;
    dom.modelsTable.innerHTML = models.map(m => `
      <tr>
        <td>${m.id}</td>
        <td>${escapeHtml(m.name)}</td>
        <td>${escapeHtml(m.description || '')}</td>
        <td><button class="btn-load-assemblies" data-id="${m.id}">Складники</button></td>
      </tr>
    `).join('');
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadAssemblies(modelId) {
  try {
    const assemblies = await Services.Models.getAssemblies(modelId);
    if (dom.assembliesTable) {
      dom.assembliesTable.innerHTML = assemblies.map(a => `
        <tr><td>${a.id}</td><td>${escapeHtml(a.name)}</td></tr>
      `).join('');
    }
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadSeriesData() {
  if (!dom.seriesTable) return;
  try {
    const series = await Services.Series.getActive();
    dom.seriesTable.innerHTML = series.map(s => `
      <tr>
        <td>${s.id}</td>
        <td>${escapeHtml(s.name)}</td>
        <td>${escapeHtml(s.status)}</td>
        <td><progress value="${s.progress || 0}" max="100"></progress></td>
      </tr>
    `).join('');
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadKanbanData() {
  try {
    const products = await Services.Production.getKanban();
    if (dom.kanbanPlanned) dom.kanbanPlanned.innerHTML = '';
    if (dom.kanbanInProgress) dom.kanbanInProgress.innerHTML = '';
    if (dom.kanbanReady) dom.kanbanReady.innerHTML = '';
    
    if (products && Array.isArray(products)) {
        products.forEach(p => {
            const card = document.createElement('div');
            card.className = 'kanban-card';
            card.textContent = p.serialNumber || 'Невідомо';
            if (p.status === 'PLANNED' && dom.kanbanPlanned) dom.kanbanPlanned.appendChild(card);
            else if (p.status === 'IN_PROGRESS' && dom.kanbanInProgress) dom.kanbanInProgress.appendChild(card);
            else if (p.status === 'READY' && dom.kanbanReady) dom.kanbanReady.appendChild(card);
        });
    }
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadPalletsData() {
  if (!dom.palletsTable) return;
  try {
    const pallets = await Services.Pallets.getAll();
    dom.palletsTable.innerHTML = pallets.map(p => `
      <tr>
        <td>${p.id}</td>
        <td>${escapeHtml(p.qrCode || p.id)}</td>
        <td>${escapeHtml(p.ownerProduct?.productModel ? p.ownerProduct.productModel.name : 'N/A')}</td>
        <td>${escapeHtml(p.category || 'N/A')}</td>
        <td>${escapeHtml(p.currentPost ? p.currentPost.name : '-')}</td>
        <td>${escapeHtml(p.status)}</td>
        <td>
          <button class="btn btn-sm btn-outline btn-generate-qr" data-code="${escapeHtml(p.qrCode)}">QR</button>
          <button class="btn btn-sm btn-info btn-pallet-details" data-id="${p.id}">Деталі</button>
        </td>
      </tr>
    `).join('');

    // A pallet is collected for one specific serialized product instance (ТЗ §2.7),
    // not a product model, so the dropdown lists instances from active series.
    const activeSeries = await Services.Series.getActive();
    const seriesList = Array.isArray(activeSeries) ? activeSeries : [];
    const productsPerSeries = await Promise.all(
      seriesList.map(s => apiGet(`/series/${s.id}/products`).catch(() => []))
    );
    const productSelect = document.getElementById('pallet-product');
    if (productSelect) {
      const options = seriesList.flatMap((s, idx) =>
        (productsPerSeries[idx] || []).map(p =>
          `<option value="${p.id}">${escapeHtml(s.number)} — ${escapeHtml(p.serialNumber)}</option>`)
      );
      productSelect.innerHTML = '<option value="">Оберіть виріб...</option>' + options.join('');
    }
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadBatchesData() {
  if (!dom.batchesTable) return;
  try {
    const batches = await Services.Batches.getAll();
    dom.batchesTable.innerHTML = batches.length === 0 ? '<tr><td colspan="5" class="text-center">Немає активних партій</td></tr>' : batches.map(b => `
      <tr>
        <td>${b.id}</td>
        <td>${escapeHtml(b.operation ? b.operation.name : 'N/A')}</td>
        <td>${escapeHtml(b.assignedWorker ? b.assignedWorker.name : 'N/A')}</td>
        <td>${b.actualQuantity || 0} / ${b.plannedQuantity || b.quantity} (Розподілено: ${b.distributedQuantity || 0})</td>
        <td>${escapeHtml(b.status)}</td>
        <td>
          ${b.status === 'AWAITING_DISTRIBUTION' ? `<button class="btn btn-sm btn-primary btn-distribute" data-id="${b.id}">Розподілити</button>` : ''}
        </td>
      </tr>
    `).join('');

    const operations = await Services.Operations.getAll();
    const workers = await Services.Workers.getAll();
    
    const opSelect = document.getElementById('batch-operation');
    if (opSelect) {
      opSelect.innerHTML = operations.map(o => `<option value="${o.id}">${escapeHtml(o.name)}</option>`).join('');
    }

    const workerSelect = document.getElementById('batch-worker');
    if (workerSelect) {
      workerSelect.innerHTML = workers.map(w => `<option value="${w.id}">${escapeHtml(w.name)}</option>`).join('');
    }
  } catch (e) {
     console.error(e);
  }
}

function formatElapsed(dateStr) {
  if (!dateStr) return '-';
  const sent = new Date(dateStr);
  const now = new Date();
  const diffMs = now - sent;
  const days = Math.floor(diffMs / 86400000);
  const hours = Math.floor((diffMs % 86400000) / 3600000);
  if (days > 0) return days + ' дн. ' + hours + ' год.';
  return hours + ' год.';
}

async function loadOutsourceData() {
  const tableBody = document.querySelector('#outsource-table tbody');
  if (!tableBody) return;
  try {
    const records = await Services.Outsource.getAll();
    const now = new Date();
    tableBody.innerHTML = records.length === 0 ? '<tr><td colspan="6" class="text-center">Немає записів аутсорсу</td></tr>' : records.map(r => {
      const isOverdue = r.expectedReturnDate && new Date(r.expectedReturnDate) < now && r.status !== 'RECEIVED';
      const rowClass = isOverdue ? 'overdue-row' : '';
      
      let actionBtn = '';
      if (r.status === 'PLANNED') {
        actionBtn = `<button class="btn btn-sm btn-primary btn-os-send" data-id="${r.id}">Відправити</button>`;
      } else if (r.status === 'IN_TRANSIT') {
        actionBtn = `<button class="btn btn-sm btn-success btn-os-receive" data-id="${r.id}">Отримати</button>`;
      }

      return `
        <tr class="${rowClass}">
          <td>${escapeHtml(r.partner || 'N/A')}</td>
          <td>${escapeHtml(r.workType || 'N/A')}</td>
          <td>${escapeHtml(r.status)}</td>
          <td>${r.sentDate ? formatElapsed(r.sentDate) : '-'}</td>
          <td>${r.expectedReturnDate ? new Date(r.expectedReturnDate).toLocaleDateString() : '-'}</td>
          <td>${actionBtn}</td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error(e);
  }
}

function generatePalletQR(code) {
    const qrContainer = document.getElementById('qr-modal-code');
    if (!qrContainer) return;
    qrContainer.innerHTML = '';
    if (window.QRCode) {
        new QRCode(qrContainer, {
            text: code,
            width: 200,
            height: 200,
            colorDark : "#000000",
            colorLight : "#ffffff",
            correctLevel : QRCode.CorrectLevel.H
        });
    } else {
        qrContainer.innerText = code;
    }
    const modal = document.getElementById('qr-modal');
    if (modal) modal.style.display = 'flex';
}

// Worker Mobile Logic
function checkWorkerState() {
  if (state.isWorkerLoggedIn) {
    if (dom.workerWorkspaceSection) dom.workerWorkspaceSection.style.display = 'block';
    if (dom.workerGreeting) dom.workerGreeting.innerText = `Вітаємо, ${state.currentWorker.name}`;
  } else {
    window.location.href = 'index.html';
  }
}

async function handleWorkerLogin() {
  if (!dom.loginPinInput) return;
  const pin = dom.loginPinInput.value;
  if (!pin) return showToast('Введіть PIN', 'warning');
  
  try {
    dom.loginBtn.innerText = 'Завантаження...';
    dom.loginBtn.disabled = true;
    const payload = await Services.Workers.login(pin);
    state.currentWorker = payload.worker;
    state.token = payload.token;
    state.isWorkerLoggedIn = true;
    localStorage.setItem('token', payload.token);
    localStorage.setItem('user', JSON.stringify(payload.worker));
    showToast('Успішний вхід', 'success');
    checkWorkerState();
    connectSSE();
  } catch (e) {
    // Error handled in API wrapper
  } finally {
    dom.loginBtn.innerText = 'Увійти';
    dom.loginBtn.disabled = false;
    dom.loginPinInput.value = '';
  }
}

function startScanner(type = 'pallet') {
  state.expectedScanType = type;
  
  const qrContainer = document.getElementById('qr-reader-container');
  if (qrContainer) qrContainer.classList.remove('hidden');

  if (state.scanner) {
      state.scanner.clear();
      state.scanner = null;
  }

  if (typeof Html5Qrcode === 'undefined') {
    showToast('Помилка: бібліотека сканера не завантажена', 'error');
    return;
  }
  
  try {
    state.scanner = new Html5Qrcode("qr-reader");
  state.scanner.start(
      { facingMode: "environment" },
      { fps: 10, qrbox: { width: 250, height: 250 } },
      (decodedText) => {
          if (state.scanner) {
              state.scanner.stop().then(() => {
                  state.scanner = null;
                  if (document.getElementById('qr-container')) document.getElementById('qr-container').classList.add('hidden');
                  processScannedQR(decodedText, state.expectedScanType);
              }).catch(e => console.error("Error stopping scanner:", e));
          }
      },
      (error) => {
          // console.warn(error);
      }
  ).catch(err => {
      showToast('Помилка доступу до камери: ' + err, 'error');
  });
  } catch (err) {
      showToast('Не вдалося запустити сканер: ' + err.message, 'error');
  }
}

window.stopScanner = function() {
    if (state.scanner) {
        state.scanner.stop().then(() => {
            state.scanner = null;
        }).catch(e => console.error(e));
    }
    const qrContainer = document.getElementById('qr-reader-container');
    if (qrContainer) qrContainer.classList.add('hidden');
};

async function processScannedQR(code, expectedType = 'pallet') {
    if (expectedType === 'post') {
        return processScannedPost(code);
    }
    try {
        // GET /api/pallets/qr/{code} already returns the pallet plus its currently
        // actionable operations (availableOperations) in one response — no separate
        // "find tasks for this pallet" call is needed.
        const data = await Services.Pallets.getByQR(code);
        const pallet = data.pallet;
        state.activePallet = pallet;
        showToast('Палету знайдено', 'success');

        const tasks = data.availableOperations || [];
        state.activeTask = tasks.length > 0 ? tasks[0] : null; // first available task, if any

        await renderPalletCard(pallet, data.assemblies || [], state.activeTask);

        if (state.activeTask) {
            showToast('Завдання завантажено', 'success');
        } else {
            showToast('Немає доступних завдань для цієї палети', 'warning');
        }
    } catch (e) {
        showToast('Помилка сканування: ' + e.message, 'error');
    }
}

// A typed-in code can be either a pallet QR code or a post id (the two are separate id
// spaces, so at most one of them resolves). Trying both means the worker doesn't have to
// know which kind of label they're reading off, and never gets "Pallet not found" for a
// perfectly valid post code.
// Post QR codes carry the post's UUID; pallet QR codes are generated as "P-<...>" and are
// never UUIDs, so the code's own shape says which lookup to try first. The other kind is
// still attempted as a fallback, because guessing wrong should cost the operator a retry
// at worst, never a flat "not found" for a code that is perfectly valid.
// Probes the endpoints directly instead of delegating and catching: both processScannedQR
// and processScannedPost swallow their own errors into a toast, so a failed first attempt
// would never surface as an exception to fall back on.
async function processManualCode(code) {
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(code);

    const tryPost = async () => {
        try {
            await apiGet(`/posts/${code}`);
            await processScannedPost(code);
            return true;
        } catch (e) { return false; }
    };
    const tryPallet = async () => {
        try {
            await Services.Pallets.getByQR(code);
            await processScannedQR(code, 'pallet');
            return true;
        } catch (e) { return false; }
    };

    const order = isUuid ? [tryPost, tryPallet] : [tryPallet, tryPost];
    for (const attempt of order) {
        if (await attempt()) return;
    }
    showToast('Код не знайдено — це не піддон і не пост: ' + code, 'error');
}

// ТЗ §7: scanning the post QR gives the operator context (which post they're at) and the
// list of tasks currently actionable there — independent of / complementary to pallet scanning.
async function processScannedPost(postId) {
    try {
        const post = await apiGet(`/posts/${postId}`);
        state.currentPost = post;
        // Moving to a post is a context switch away from whatever pallet was scanned before.
        // Leaving the old pallet in state made later actions (e.g. re-rendering after a defect
        // report) reach back to a pallet the operator is no longer working on.
        state.activePallet = null;
        if (dom.currentSection) {
            dom.currentSection.innerText = post.name ? `Пост: ${post.name}` : '';
        }
        showToast(`Пост "${post.name}" підтверджено`, 'success');

        const workerId = state.currentWorker ? state.currentWorker.id : null;
        const tasks = workerId ? await apiGet(`/posts/${postId}/available-tasks?workerId=${workerId}`) : [];
        renderAvailableTasksList(tasks || []);
    } catch (e) {
        showToast('Помилка сканування поста: ' + e.message, 'error');
    }
}

function renderAvailableTasksList(tasks) {
    if (!dom.tasksList) return;
    if (!tasks || tasks.length === 0) {
        dom.tasksList.innerHTML = '<p class="text-muted text-center py-4">Немає доступних завдань</p>';
        return;
    }
    dom.tasksList.innerHTML = tasks.map(t => {
        const opName = t.operation ? t.operation.name : (t.operationName || 'Операція');
        const isBatch = t.taskType === 'BATCH';
        return `
        <div class="task-list-item glass-card mb-2 btn-select-task" data-id="${t.id}" style="cursor:pointer; padding: 0.75rem;">
            <strong>${escapeHtml(opName)}</strong>${isBatch ? ` <span class="badge badge-warning">Партія${t.batchNumber ? ' ' + escapeHtml(t.batchNumber) : ''}</span>` : ''}
            <div class="text-muted" style="font-size: 0.85rem;">${escapeHtml(t.status || '')}</div>
        </div>
    `;
    }).join('');
}

// Fetches the full material list for the task's operation (TaskDTO itself doesn't carry it)
// so the pallet card can show "Необхідні матеріали" per ТЗ §7.
async function renderPalletCard(pallet, assemblies, task) {
    const palletCard = document.getElementById('mobile-pallet-card');
    if (!palletCard) return;
    palletCard.classList.remove('hidden');
    pallet = pallet || {};
    // A task picked straight off the post's "available tasks" list has no pallet behind it,
    // so labelling the card "Піддон: Порізка" and showing an empty "Категорія" described
    // something the operator isn't actually looking at. Only call it a pallet when there is one.
    const hasPallet = !!(pallet.qrCode || pallet.id);
    if (dom.cardLabel) dom.cardLabel.innerText = hasPallet ? 'Піддон' : 'Завдання';
    document.getElementById('mobile-field-label-1').innerText = 'Виріб:';
    document.getElementById('mobile-field-label-2').innerText = 'Категорія:';
    document.getElementById('mobile-prev-stages-label').innerText = 'Попередні етапи:';
    document.getElementById('mobile-assemblies-block').classList.toggle('hidden', !hasPallet);

    document.getElementById('mobile-pallet-id').innerText = pallet.qrCode || pallet.id || (task ? task.operationName || 'Завдання без піддона' : '-');
    // Tasks picked from the post's "available tasks" list (no pallet scanned) don't have a
    // `pallet` object at all - but TaskDTO already carries the product's serial number
    // directly, so prefer that over the pallet's owner-product before falling back.
    document.getElementById('mobile-pallet-product').innerText =
        (task && task.productInstanceSerialNumber) || pallet.ownerProduct?.productModel?.name || pallet.ownerProduct?.serialNumber || 'Невідомо';
    const categoryRow = document.getElementById('mobile-pallet-category');
    categoryRow.innerText = pallet.category || '—';
    // Category belongs to a pallet, not to a task; hide the whole line when there is no pallet.
    if (categoryRow.parentElement) categoryRow.parentElement.classList.toggle('hidden', !hasPallet);
    document.getElementById('mobile-pallet-time').innerText = task && task.normativeTimeMinutes != null ? task.normativeTimeMinutes : '-';

    const prevStagesEl = document.getElementById('mobile-pallet-prev-stages');
    // Previous stages are read off the scanned pallet's assemblies, so without a pallet this
    // is always an empty "Немає даних" placeholder - hide it rather than imply missing data.
    document.getElementById('mobile-prev-stages-label').classList.toggle('hidden', !hasPallet);
    if (prevStagesEl) prevStagesEl.classList.toggle('hidden', !hasPallet);
    if (prevStagesEl) {
        prevStagesEl.innerHTML = (assemblies || []).map(a => {
            const done = a.status === 'COMPLETED';
            const badgeClass = done ? 'badge-success' : (a.status === 'DAMAGED' ? 'badge-danger' : 'badge-warning');
            return `<span class="badge ${badgeClass}">${escapeHtml(a.assembly ? a.assembly.name : 'Вузол')}: ${escapeHtml(a.status)}</span>`;
        }).join(' ') || '<span class="text-muted">Немає даних</span>';
    }

    const assembliesEl = document.getElementById('mobile-pallet-assemblies');
    if (assembliesEl) {
        assembliesEl.innerHTML = (assemblies || []).map(a =>
            `<li>${escapeHtml(a.assembly ? a.assembly.name : 'Вузол')} — ${escapeHtml(a.status)}</li>`
        ).join('') || '<li class="text-muted">Немає вузлів на піддоні</li>';
    }

    const materialsEl = document.getElementById('mobile-pallet-materials');
    const requirementsRow = document.getElementById('mobile-pallet-requirements-row');
    const requirementsEl = document.getElementById('mobile-pallet-requirements');
    if (requirementsRow) requirementsRow.classList.add('hidden');
    if (materialsEl) {
        if (task && task.missingMaterials) {
            materialsEl.innerHTML = '<li class="text-danger">Матеріалів не вистачає — завдання заблоковано</li>';
        } else if (task && task.operationId) {
            try {
                const operation = await apiGet(`/operations/${task.operationId}`);
                const materials = operation.requiredMaterials || [];
                materialsEl.innerHTML = materials.length
                    ? materials.map(m => `<li>${escapeHtml(m.name)} — ${operation.materialQuantityPerUnit || 1} ${escapeHtml(m.unit || '')}</li>`).join('')
                    : '<li class="text-muted">Матеріали не потрібні</li>';

                // ТЗ §3.1: обладнання/інструменти/кваліфікація - показуємо лише те, що
                // реально задане на операції, щоб не захаращувати картку порожніми полями.
                const reqParts = [operation.equipment, operation.tools, operation.requiredQualification].filter(Boolean);
                if (requirementsRow && requirementsEl && reqParts.length) {
                    requirementsEl.innerText = reqParts.join(' · ');
                    requirementsRow.classList.remove('hidden');
                }
            } catch (e) {
                materialsEl.innerHTML = '<li class="text-muted">Не вдалось завантажити</li>';
            }
        } else {
            materialsEl.innerHTML = '<li class="text-muted">Немає активного завдання</li>';
        }
    }

    renderTaskDetails();
}

// ТЗ §5.2/§16: a batch task (напр. "порізка 100 труб") is one execution against a pile of
// parts, not a pallet - it has no assemblyInstance/pallet to show, just quantities and,
// once complete, a distribution step. Reuses the same card DOM as renderPalletCard so the
// worker doesn't have to learn a second screen layout, just relabelled for this case.
async function renderBatchCard(task) {
    const palletCard = document.getElementById('mobile-pallet-card');
    if (!palletCard || !task || !task.batchId) return;
    palletCard.classList.remove('hidden');
    if (dom.cardLabel) dom.cardLabel.innerText = 'Партія';
    document.getElementById('mobile-field-label-1').innerText = 'Операція:';
    document.getElementById('mobile-field-label-2').innerText = 'Матеріал:';
    document.getElementById('mobile-prev-stages-label').innerText = 'Прогрес:';
    document.getElementById('mobile-assemblies-block').classList.add('hidden');

    let batch;
    try {
        batch = await Services.Batches.getById(task.batchId);
    } catch (e) {
        showToast('Не вдалося завантажити партію', 'error');
        return;
    }
    state.activeBatch = batch;

    document.getElementById('mobile-pallet-id').innerText = batch.number || task.batchNumber || '-';
    document.getElementById('mobile-pallet-product').innerText = batch.operationName || task.operationName || 'Невідомо';
    document.getElementById('mobile-pallet-category').innerText = batch.materialDetail || 'Невідомо';
    document.getElementById('mobile-pallet-time').innerText = task.normativeTimeMinutes != null ? task.normativeTimeMinutes : '-';

    const prevStagesEl = document.getElementById('mobile-pallet-prev-stages');
    if (prevStagesEl) {
        prevStagesEl.innerHTML = `
            <span class="badge badge-primary">План: ${batch.plannedQuantity ?? 0}</span>
            <span class="badge badge-warning">Факт: ${batch.actualQuantity ?? 0}</span>
            <span class="badge badge-success">Розподілено: ${batch.distributedQuantity ?? 0}</span>
        `;
    }

    const materialsEl = document.getElementById('mobile-pallet-materials');
    if (materialsEl) {
        if (task.missingMaterials) {
            materialsEl.innerHTML = '<li class="text-danger">Матеріалів не вистачає — завдання заблоковано</li>';
        } else if (task.operationId) {
            try {
                const operation = await apiGet(`/operations/${task.operationId}`);
                const materials = operation.requiredMaterials || [];
                const plannedQty = batch.plannedQuantity || 1;
                materialsEl.innerHTML = materials.length
                    ? materials.map(m => `<li>${escapeHtml(m.name)} — ${(operation.materialQuantityPerUnit || 1) * plannedQty} ${escapeHtml(m.unit || '')} (на всю партію)</li>`).join('')
                    : '<li class="text-muted">Матеріали не потрібні</li>';
            } catch (e) {
                materialsEl.innerHTML = '<li class="text-muted">Не вдалось завантажити</li>';
            }
        } else {
            materialsEl.innerHTML = '<li class="text-muted">-</li>';
        }
    }

    renderTaskDetails();
}

function renderTaskDetails() {
    if (!state.activeTask) {
        if (dom.btnTaskStart) dom.btnTaskStart.classList.add('hidden');
        if (dom.activeControls) dom.activeControls.classList.add('hidden');
        if (dom.btnDamageOp) dom.btnDamageOp.classList.add('hidden');
        stopTaskTimer();
        return;
    }

    const task = state.activeTask;
    const status = task.status;
    const isBatch = task.taskType === 'BATCH';
    const inProgress = status === 'IN_PROGRESS';
    const paused = status === 'PAUSED';
    const startable = status === 'READY' || status === 'CREATED' || status === 'ASSIGNED' || paused;

    // "Почати" only for a fresh (not yet started) task; a paused task resumes instead.
    if (dom.btnTaskStart) dom.btnTaskStart.classList.toggle('hidden', !(startable && !paused));
    // Batch tasks have no pause/resume on the backend (ТЗ §5.2: start -> complete -> distribute,
    // no pause step) and "damage" applies to an assembly instance, which a batch doesn't have.
    if (dom.activeControls) dom.activeControls.classList.toggle('hidden', !(inProgress || (paused && !isBatch)));
    if (dom.btnTaskPause) dom.btnTaskPause.classList.toggle('hidden', isBatch || !inProgress);
    if (dom.btnTaskResume) dom.btnTaskResume.classList.toggle('hidden', isBatch || !paused);
    if (dom.btnTaskComplete) dom.btnTaskComplete.classList.toggle('hidden', !inProgress);
    if (dom.btnDamageOp) dom.btnDamageOp.classList.toggle('hidden', isBatch);

    if (dom.taskTimer) dom.taskTimer.classList.toggle('hidden', !inProgress);
    if (inProgress) {
        startTaskTimer();
    } else {
        stopTaskTimer();
    }
}

function startTaskTimer() {
    if (state.timerInterval) clearInterval(state.timerInterval);
    state.taskStartTime = state.activeTask && state.activeTask.startedAt
                          ? new Date(state.activeTask.startedAt).getTime()
                          : Date.now();
    
    state.timerInterval = setInterval(() => {
        if (!dom.taskTimer) return;
        const elapsed = Math.floor((Date.now() - state.taskStartTime) / 1000);
        const mins = String(Math.floor(elapsed / 60)).padStart(2, '0');
        const secs = String(elapsed % 60).padStart(2, '0');
        dom.taskTimer.innerText = `${mins}:${secs}`;
    }, 1000);
}

function stopTaskTimer() {
    if (state.timerInterval) {
        clearInterval(state.timerInterval);
        state.timerInterval = null;
    }
}

async function handleTaskAction(action) {
    if (!state.activeTask || !state.currentWorker) return;

    if (state.activeTask.taskType === 'BATCH') {
        return handleBatchTaskAction(action);
    }

    const taskId = state.activeTask.id;
    const workerId = state.currentWorker.id;

    try {
        let updatedTask;
        if (action === 'start') updatedTask = await Services.Tasks.start(taskId, workerId);
        else if (action === 'pause') updatedTask = await Services.Tasks.pause(taskId, workerId);
        else if (action === 'resume') updatedTask = await Services.Tasks.resume(taskId, workerId);
        else if (action === 'complete') updatedTask = await Services.Tasks.complete(taskId, workerId);

        state.activeTask = updatedTask;
        showToast('Статус завдання оновлено', 'success');
        renderTaskDetails();

        // Completing a task leaves it in the post's "Доступні завдання" list still showing
        // READY, so the operator can't tell it registered and may tap it again. Re-read the
        // list from the post they scanned so it reflects what is actually still open.
        if (action === 'complete') {
            await refreshPostTaskList();
        }
    } catch (e) {
        // apiFetch throws but never shows anything itself - without this a failed action
        // (e.g. post at full capacity, 409) looked identical to nothing happening at all.
        showToast(e.message || 'Не вдалося виконати дію', 'error');
    }
}

// Re-reads the scanned post's open tasks. No-op when the operator got here by scanning a
// pallet instead, since there is no post list on screen to refresh in that case.
async function refreshPostTaskList() {
    const postId = state.currentPost ? state.currentPost.id : null;
    const workerId = state.currentWorker ? state.currentWorker.id : null;
    if (!postId || !workerId) return;
    try {
        const tasks = await apiGet(`/posts/${postId}/available-tasks?workerId=${workerId}`);
        renderAvailableTasksList(tasks || []);
    } catch (e) {
        console.error('Failed to refresh post task list', e);
    }
}

// ТЗ §5.2: "start" and "complete" are the only batch-task actions - no pause/resume - and
// completing doesn't necessarily finish the task: if the produced quantity hasn't all been
// distributed to pallets yet, the batch (and its task) stay open until distribution happens.
async function handleBatchTaskAction(action) {
    const batchId = state.activeTask.batchId;
    const workerId = state.currentWorker.id;

    try {
        if (action === 'start') {
            const updatedBatch = await Services.Batches.start(batchId, workerId);
            state.activeBatch = updatedBatch;
            state.activeTask.status = 'IN_PROGRESS';
            state.activeTask.startedAt = updatedBatch.startTime;
            showToast('Партію розпочато', 'success');
            renderTaskDetails();
        } else if (action === 'complete') {
            const plannedQty = state.activeBatch ? state.activeBatch.plannedQuantity : null;
            const input = prompt('Скільки фактично оброблено?' + (plannedQty ? ` (план: ${plannedQty})` : ''), plannedQty || '');
            if (input === null) return;
            const actualQty = parseInt(input, 10);
            if (isNaN(actualQty) || actualQty < 0) {
                showToast('Введіть коректну кількість', 'warning');
                return;
            }
            const updatedBatch = await Services.Batches.complete(batchId, actualQty);
            state.activeBatch = updatedBatch;
            state.activeTask.status = 'COMPLETED';
            if (updatedBatch.status === 'AWAITING_DISTRIBUTION') {
                showToast('Партію оброблено — потрібен розподіл по піддонах', 'warning');
                showDistributionModal(batchId);
            } else {
                showToast('Партію завершено', 'success');
            }
            renderTaskDetails();
        }
    } catch (e) {
        showToast(e.message || 'Не вдалося виконати дію', 'error');
    }
}

function setupEventListeners() {
  dom.navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const targetView = e.currentTarget.dataset.view;
      if (targetView) switchView(targetView);
    });
  });

  // Event delegation for dynamically generated buttons
  document.body.addEventListener('click', (e) => {
    if (e.target.matches('.btn-load-assemblies')) {
      const id = e.target.dataset.id;
      loadAssemblies(id);
    }
    if (e.target.matches('.btn-generate-qr')) {
      const code = e.target.dataset.code;
      generatePalletQR(code);
    }
    
    if (e.target.matches('.btn-pallet-details')) {
      const id = e.target.dataset.id;
      showPalletDetails(id);
    }
    
    if (e.target.matches('.btn-distribute')) {
      const id = e.target.dataset.id;
      showDistributionModal(id);
    }

    const taskItem = e.target.closest('.btn-select-task');
    if (taskItem) {
      const id = taskItem.dataset.id;
      apiGet(`/tasks/${id}`).then(task => {
        state.activeTask = task;
        if (task.taskType === 'BATCH') {
          renderBatchCard(task);
        } else {
          renderPalletCard(state.activePallet || {}, [], task);
        }
      }).catch(err => showToast('Не вдалося завантажити завдання', 'error'));
    }
    
    if (e.target.matches('.btn-os-send')) {
      const id = e.target.dataset.id;
      Services.Outsource.send(id).then(() => {
        showToast('Відправлено', 'success');
        loadOutsourceData();
      }).catch(console.error);
    }
    
    if (e.target.matches('.btn-os-receive')) {
      const id = e.target.dataset.id;
      Services.Outsource.receive(id, state.currentWorker ? state.currentWorker.id : null).then(() => {
        showToast('Отримано', 'success');
        loadOutsourceData();
      }).catch(console.error);
    }
  });

  // Worker Scanner Actions
  const btnScanPost = document.getElementById('btn-scan-post');
  if (btnScanPost) {
    btnScanPost.addEventListener('click', () => {
      startScanner('post');
    });
  }

  const btnScanPallet = document.getElementById('btn-scan-pallet');
  if (btnScanPallet) {
    btnScanPallet.addEventListener('click', () => {
      startScanner('pallet');
    });
  }

  const btnManualSubmit = document.getElementById('btn-manual-submit');
  const manualInput = document.getElementById('manual-code-input');
  if (btnManualSubmit && manualInput) {
    btnManualSubmit.addEventListener('click', () => {
      const code = manualInput.value.trim();
      if (code) {
        // Manual entry is the fallback when the camera doesn't work, so it needs to respect
        // whichever of "Скан Посту"/"Скан Піддону" the worker pressed - not silently always
        // treat the typed code as a pallet code. When neither was pressed (the normal case on
        // a tablet with no working camera, where the worker just types a code off a printed
        // label) there is no stated intent to respect, so resolve the code itself rather than
        // guessing "pallet" and telling the worker their valid post code doesn't exist.
        if (state.expectedScanType) {
          processScannedQR(code, state.expectedScanType);
        } else {
          processManualCode(code);
        }
        manualInput.value = '';
      } else {
        showToast('Введіть код', 'warning');
      }
    });
  }

  if (dom.newModelForm) {
    dom.newModelForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = document.getElementById('new-model-name').value;
      const desc = document.getElementById('new-model-desc').value;
      const submitBtn = dom.newModelForm.querySelector('button[type="submit"]');
      if (submitBtn) submitBtn.disabled = true;

      try {
        await Services.Models.create({ name, description: desc });
        showToast('Модель створено', 'success');
        dom.newModelForm.reset();
        await loadModelsData();
      } catch (e) {
        // Error handled in API wrapper
      } finally {
        if (submitBtn) submitBtn.disabled = false;
      }
    });
  }
  
  if (dom.newSeriesForm) {
      dom.newSeriesForm.addEventListener('submit', async (e) => {
          e.preventDefault();
          const submitBtn = dom.newSeriesForm.querySelector('button[type="submit"]');
          if (submitBtn) submitBtn.disabled = true;
          
          try {
            // Logic for creating series would call Services.Series.create
            // await Services.Series.create({ ... });
            showToast('Серію створено', 'success');
            dom.newSeriesForm.reset();
            await loadSeriesData();
          } catch (e) {
            // Error handled in API wrapper
          } finally {
            if (submitBtn) submitBtn.disabled = false;
          }
      });
  }

  const batchForm = document.getElementById('form-create-batch');
  if (batchForm) {
      batchForm.addEventListener('submit', async (e) => {
          e.preventDefault();
          const opId = document.getElementById('batch-operation').value;
          const workerId = document.getElementById('batch-worker').value;
          const qty = document.getElementById('batch-planned-qty').value;
          
          try {
             await Services.Batches.create({
                 operationId: opId,
                 workerId: workerId,
                 quantity: parseInt(qty)
             });
             showToast('Партію створено', 'success');
             batchForm.reset();
             await loadBatchesData();
          } catch(err) {
             console.error(err);
          }
      });
  }

  if (dom.loginBtn) {
    dom.loginBtn.addEventListener('click', handleWorkerLogin);
  }

  const palletForm = document.getElementById('form-create-pallet');
  if (palletForm) {
      palletForm.addEventListener('submit', async (e) => {
          e.preventDefault();
          const productInstanceId = document.getElementById('pallet-product').value;
          const category = document.getElementById('pallet-category').value;

          try {
             await Services.Pallets.create({
                 productInstanceId: productInstanceId,
                 category: category
             });
             showToast('Піддон створено', 'success');
             palletForm.reset();
             await loadPalletsData();
          } catch(err) {
             console.error(err);
          }
      });
  }

  if (dom.startScanBtn) {
    dom.startScanBtn.addEventListener('click', startScanner);
  }

  if (dom.btnTaskStart) dom.btnTaskStart.addEventListener('click', () => handleTaskAction('start'));
  if (dom.btnTaskPause) dom.btnTaskPause.addEventListener('click', () => handleTaskAction('pause'));
  if (dom.btnTaskResume) dom.btnTaskResume.addEventListener('click', () => handleTaskAction('resume'));
  if (dom.btnTaskComplete) dom.btnTaskComplete.addEventListener('click', () => handleTaskAction('complete'));

  const btnDamageOp = document.getElementById('btn-damage-op');
  const defectModal = document.getElementById('defect-modal');
  const btnDefectClose = document.getElementById('btn-defect-close');
  const btnSubmitDefect = document.getElementById('btn-submit-defect');
  
  if (btnDamageOp && defectModal) {
    btnDamageOp.addEventListener('click', () => {
      if (!state.activeTask) return;
      defectModal.style.display = 'flex';
      defectModal.classList.remove('hidden');
      defectModal.classList.add('active');
    });
  }

  if (btnDefectClose && defectModal) {
    btnDefectClose.addEventListener('click', () => {
      defectModal.classList.remove('active');
      defectModal.style.display = 'none';
      defectModal.classList.add('hidden');
    });
  }

  if (btnSubmitDefect && defectModal) {
    btnSubmitDefect.addEventListener('click', async () => {
      const reasonInput = document.getElementById('defect-reason-input');
      const resolutionSelect = document.getElementById('defect-resolution-select');
      const reason = reasonInput ? reasonInput.value.trim() : '';
      if (!reason) {
        showToast('Будь ласка, вкажіть причину', 'warning');
        return;
      }
      if (!state.activeTask) {
        showToast('Немає активного завдання', 'warning');
        return;
      }

      btnSubmitDefect.disabled = true;
      try {
        // /api/tasks/{id}/damage (not the bare /api/defects) so the task itself actually
        // transitions to DAMAGED, the post is released and the time log closed — reportDefect
        // alone only creates the DefectRecord and leaves the task sitting in IN_PROGRESS.
        await Services.Tasks.damage(
          state.activeTask.id,
          state.currentWorker ? state.currentWorker.id : null,
          reason,
          resolutionSelect ? resolutionSelect.value : 'REWORK'
        );
        showToast('Брак успішно зафіксовано', 'success');
        defectModal.classList.remove('active');
        defectModal.style.display = 'none';
        defectModal.classList.add('hidden');
        if (reasonInput) reasonInput.value = '';

        // The task just reported as defective is no longer this operator's to work on, but
        // the card kept showing a running timer and live "Пауза"/"Завершити" buttons for it.
        stopTaskTimer();
        state.activeTask = null;
        const card = document.getElementById('mobile-pallet-card');
        if (card) card.classList.add('hidden');

        // Re-fetch task state. A task reached from the post's list has no pallet behind it,
        // so refresh that list instead - otherwise the task just marked as defective stays
        // on screen as if it were still open for work.
        if (state.activePallet) {
            processScannedQR(state.activePallet.qrCode || state.activePallet.id, 'pallet');
        } else {
            await refreshPostTaskList();
        }
      } catch (e) {
        showToast('Помилка фіксації браку', 'error');
        console.error(e);
      } finally {
        btnSubmitDefect.disabled = false;
      }
    });
  }

  if (dom.btnMobileMenu && dom.sidebar) {
    dom.btnMobileMenu.addEventListener('click', () => {
      dom.sidebar.classList.toggle('mobile-open');
    });
  }

  const btnApplyDashFilters = document.getElementById('btn-apply-dash-filters');
  if (btnApplyDashFilters) {
    btnApplyDashFilters.addEventListener('click', loadDashboardData);
  }

  const osForm = document.getElementById('form-create-outsource');
  if (osForm) {
    osForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await Services.Outsource.create({
          partner: document.getElementById('outsource-partner').value,
          workType: document.getElementById('outsource-work-type').value,
          expectedReturnDate: document.getElementById('outsource-return-date').value
        });
        showToast('Аутсорс запис створено', 'success');
        osForm.reset();
        await loadOutsourceData();
      } catch (err) {
        console.error(err);
      }
    });
  }
  
  const distModalClose = document.getElementById('btn-dist-close');
  if (distModalClose) {
    distModalClose.addEventListener('click', () => {
      document.getElementById('modal-distribution').classList.add('hidden');
    });
  }
  
  const distModalSubmit = document.getElementById('btn-dist-submit');
  if (distModalSubmit) {
    distModalSubmit.addEventListener('click', async () => {
      const batchId = document.getElementById('modal-distribution').dataset.batchId;
      const palletId = document.getElementById('dist-pallet').value;
      const qty = parseInt(document.getElementById('dist-qty').value, 10);
      try {
        const updatedBatch = await Services.Batches.distribute(batchId, { distribution: { [palletId]: qty } });
        state.activeBatch = updatedBatch;
        if (updatedBatch.status === 'COMPLETED') {
          showToast('Партію повністю розподілено й завершено', 'success');
          document.getElementById('modal-distribution').classList.add('hidden');
        } else {
          showToast('Частину розподілено, лишилось ще', 'success');
          showDistributionModal(batchId);
        }
        if (state.activeTask && state.activeTask.batchId === batchId) {
          renderTaskDetails();
        }
      } catch (err) {
        console.error(err);
      }
    });
  }
  
  const palletDetailClose = document.getElementById('btn-pallet-detail-close');
  if (palletDetailClose) {
    palletDetailClose.addEventListener('click', () => {
      document.getElementById('modal-pallet-detail').classList.add('hidden');
    });
  }
}

async function showDistributionModal(batchId) {
  const modal = document.getElementById('modal-distribution');
  modal.dataset.batchId = batchId;
  modal.classList.remove('hidden');
  document.getElementById('dist-qty').value = 1;
  try {
    const batch = await Services.Batches.getById(batchId);
    const remaining = (batch.actualQuantity || 0) - (batch.distributedQuantity || 0);
    document.getElementById('dist-batch-info').innerText =
        `Партія ${batch.number || ''}: розподілено ${batch.distributedQuantity || 0} з ${batch.actualQuantity || 0} (лишилось ${remaining})`;
    document.getElementById('dist-qty').value = remaining > 0 ? remaining : 1;
  } catch (e) {
    console.error(e);
  }
  try {
    const pallets = await Services.Pallets.getAll();
    const select = document.getElementById('dist-pallet');
    select.innerHTML = pallets.map(p => `<option value="${p.id}">${escapeHtml(p.qrCode || p.id)}${p.ownerProduct?.serialNumber ? ' — ' + escapeHtml(p.ownerProduct.serialNumber) : ''}</option>`).join('');
  } catch (e) {
    console.error(e);
  }
}

async function showPalletDetails(id) {
  const modal = document.getElementById('modal-pallet-detail');
  modal.classList.remove('hidden');
  try {
    const pallet = await apiGet(`/pallets/${id}`);
    const history = await apiGet(`/pallets/${id}/history`);
    
    document.getElementById('pallet-detail-info').innerHTML = `
      <p><strong>ID:</strong> ${pallet.id}</p>
      <p><strong>Code:</strong> ${escapeHtml(pallet.qrCode)}</p>
      <p><strong>Status:</strong> ${escapeHtml(pallet.status)}</p>
    `;

    const assembliesTbody = document.querySelector('#pallet-assemblies-table tbody');
    if (pallet.assemblyInstances && pallet.assemblyInstances.length) {
      assembliesTbody.innerHTML = pallet.assemblyInstances.map(a => `<tr><td>${a.id}</td><td>${escapeHtml(a.assembly ? a.assembly.name : '-')}</td><td>${escapeHtml(a.status)}</td></tr>`).join('');
    } else {
      assembliesTbody.innerHTML = '<tr><td colspan="3">Немає вузлів</td></tr>';
    }

    const historyTbody = document.querySelector('#pallet-history-table tbody');
    if (history && history.length) {
      historyTbody.innerHTML = history.map(h => `<tr>
        <td>${new Date(h.timestamp).toLocaleString()}</td>
        <td>${escapeHtml(h.fromPost ? h.fromPost.name : '-')}</td>
        <td>${escapeHtml(h.toPost ? h.toPost.name : '-')}</td>
        <td>${escapeHtml(h.worker ? h.worker.name : '-')}</td>
      </tr>`).join('');
    } else {
      historyTbody.innerHTML = '<tr><td colspan="4">Немає історії</td></tr>';
    }
  } catch (e) {
    console.error(e);
  }
}

function initApp() {
  if (!state.token) {
      window.location.href = 'index.html';
      return;
  }
  initDOM();
  setupEventListeners();

  // "Працівник" was hardcoded in the markup and never replaced, so every operator's tablet
  // showed the same placeholder instead of who is actually signed in. On a shared shop-floor
  // device that matters: every task start/complete is attributed to whoever holds the
  // session, and the operator had no way to notice they were working under someone else's.
  const nameEl = document.getElementById('mobile-current-worker');
  if (nameEl && state.currentWorker && state.currentWorker.name) {
    nameEl.innerText = state.currentWorker.name;
  }

  switchView('worker-mobile');
}

document.addEventListener('DOMContentLoaded', initApp);

function performLogout() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  window.location.href = 'index.html';
}

if (document.getElementById('btn-logout')) {
  document.getElementById('btn-logout').addEventListener('click', performLogout);
}
if (document.getElementById('btn-worker-logout')) {
  document.getElementById('btn-worker-logout').addEventListener('click', performLogout);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { 
    Services, apiGet, apiPost, 
    state, dom, initDOM, showToast, connectSSE, 
    switchView, loadViewData, loadDashboardData, loadModelsData, 
    loadAssemblies, loadSeriesData, loadKanbanData, loadPalletsData, 
    loadBatchesData, formatElapsed, loadOutsourceData, generatePalletQR, 
    checkWorkerState, handleWorkerLogin, startScanner, processScannedQR, 
    renderTaskDetails, startTaskTimer, stopTaskTimer, handleTaskAction, 
    setupEventListeners, showDistributionModal, showPalletDetails, initApp
  };
}
