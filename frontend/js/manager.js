
const state = Object.assign(window.state, {
  currentView: 'dashboard',
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

// The sidebar's dot and "Підключено" label were fixed markup that nothing ever changed, so
// the dashboard claimed a live connection even with the backend down or the network gone.
// On a shop floor that is the one thing this indicator exists to be honest about: a manager
// acting on numbers that silently stopped updating is worse off than one who can see the feed
// dropped.
function setLiveStatus(connected, label) {
  const dot = document.getElementById('live-dot');
  const text = document.getElementById('live-text');
  if (text) text.innerText = label;
  if (dot) {
    dot.style.background = connected ? 'var(--success)' : 'var(--danger, #ef4444)';
    dot.style.boxShadow = connected ? '0 0 10px var(--success-glow)' : 'none';
    dot.style.animation = connected ? '' : 'none';
  }
}

function connectSSE() {
  if (!state.token) return;
  if (state.eventSource) {
    state.eventSource.close();
  }
  const evtSource = new EventSource(API_BASE + '/events/stream?token=' + state.token);
  state.eventSource = evtSource;

  evtSource.onopen = () => setLiveStatus(true, 'Підключено');
  evtSource.onerror = () => {
    // EventSource retries on its own; report the gap rather than tearing the stream down.
    setLiveStatus(false, navigator.onLine ? 'Немає зв\'язку з сервером' : 'Немає мережі');
  };

  evtSource.onmessage = function(event) {
    setLiveStatus(true, 'Підключено');
    if (event.data === "dashboard_update" && state.currentView === 'dashboard') {
      loadDashboardData();
    }
  };
}

window.addEventListener('offline', () => setLiveStatus(false, 'Немає мережі'));
window.addEventListener('online', () => {
  setLiveStatus(false, 'Відновлення…');
  connectSSE();
});

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

  // Models
  dom.modelsTable = document.querySelector('#models-table tbody');
  dom.newModelForm = document.getElementById('form-create-model');
  dom.assembliesTable = document.querySelector('.assemblies-container');
  
  // Series
  dom.seriesTable = document.getElementById('series-table-body');
  dom.newSeriesForm = document.getElementById('form-create-series');

  // Kanban
  dom.kanbanPlanned = document.getElementById('kb-col-planned');
  dom.kanbanInProgress = document.getElementById('kb-col-wip');
  dom.kanbanReady = document.getElementById('kb-col-done');

  // Pallets & Batches
  dom.palletsTable = document.getElementById('pallets-table-body');
  dom.batchesTable = document.getElementById('batches-table-body');
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
    getAssemblies: (modelId) => apiGet(`/assemblies/model/${modelId}`),
    addAssembly: (data) => apiPost('/assemblies', data),
    addOperation: (assemblyId, data) => apiPost(`/assemblies/${assemblyId}/operations`, data),
    getOperations: (assemblyId) => apiGet(`/assemblies/${assemblyId}/operations`),
    createNewVersion: (modelId, data) => apiPut(`/models/${modelId}`, data)
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
    setUrgent: (id) => apiPost(`/tasks/${id}/urgent`),
    reopen: (id, managerId) => apiPost(`/tasks/${id}/reopen`, { workerId: managerId })
  },
  Workers: {
    getAll: () => apiGet('/workers'),
    create: (data) => apiPost('/workers', data)
  },
  Auth: {
    login: (workerId, pin) => apiPost('/auth/login/pin', { workerId, pin }),
    setPin: (workerId, newPin) => apiPost('/auth/set-pin', { workerId: workerId, pin: newPin })
  },
  History: {
    getFiltered: (query) => apiGet(`/history${query}`)
  },
  Outsource: {
    getAll: () => apiGet('/outsource'),
    getActive: () => apiGet('/outsource/active'),
    create: (data) => apiPost('/outsource', data),
    send: (id) => apiPost(`/outsource/${id}/send`),
    receive: (id, receivedByWorkerId) => apiPost(`/outsource/${id}/receive`, { receivedByWorkerId })
  },
  Defects: {
    getAll: () => apiGet('/defects'),
    report: (data) => apiPost('/defects', data)
  },
  Sections: {
    getAll: () => apiGet('/sections'),
    create: (data) => apiPost('/sections', data)
  },
  Posts: {
    getAll: () => apiGet('/posts'),
    create: (data) => apiPost('/posts', data),
    getLoad: (id) => apiGet(`/posts/${id}/load`)
  },
  Materials: {
    getAll: () => apiGet('/materials'),
    getRequirements: (seriesId) => apiGet(`/materials/series/${seriesId}/requirements`),
    updateStock: (id, availableStock) => apiPost(`/materials/${id}/update-stock`, { availableStock })
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

  // The header breadcrumb was fixed markup reading "Дашборд", so it named the wrong section
  // on every screen except the one it happened to be written for. Taken from the nav item
  // itself so the two can't drift apart as sections are added or renamed.
  const breadcrumb = document.getElementById('current-view-title');
  const activeNav = document.querySelector(`.nav-item[data-view="${viewId}"]`);
  if (breadcrumb && activeNav) {
    breadcrumb.innerText = (activeNav.innerText || '').trim() || breadcrumb.innerText;
  }

  loadViewData(viewId);
}

const viewLoaders = {
  'dashboard': loadDashboardData,
  'models': loadModelsData,
  'series': loadSeriesData,
  'production': loadKanbanData,
  'pallets': loadPalletsData,
  'batches': loadBatchesData,
  'outsource': loadOutsourceData,
  'workers': loadWorkersData,
  'defects': loadDefectsData,
  'sections': loadSectionsData,
  'materials': loadMaterialsData,
  'history': loadHistoryData
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
    const stage = document.getElementById('dash-filter-stage')?.value || '';

    const params = new URLSearchParams();
    if (seriesId) params.append('seriesId', seriesId);
    if (workerId) params.append('workerId', workerId);
    if (status) params.append('status', status);
    if (stage) params.append('stage', stage);

    const query = params.toString() ? '?' + params.toString() : '';
    const data = await Services.Dashboard.getStats(query);

    if (data) {
      const setStat = (id, value) => {
        const el = document.getElementById(id);
        if (el) el.innerText = value ?? 0;
      };
      setStat('stat-in-production', data.productsInProduction);
      setStat('stat-ready', data.productsReady);
      setStat('stat-active-tasks', data.activeTasksCount);
      setStat('stat-overdue', data.overdueTasks);
      setStat('stat-blocked', data.blockedTasks);
      setStat('stat-deficit', data.materialDeficits);
    }

    const postLoadsEl = document.getElementById('dashboard-post-loads');
    if (postLoadsEl && data && Array.isArray(data.postLoads)) {
      postLoadsEl.innerHTML = data.postLoads.length === 0
        ? '<p class="text-muted">Немає постів</p>'
        : data.postLoads.map(p => `
            <div class="post-load-row mb-2">
              <div class="flex-row justify-between"><span>${escapeHtml(p.postName)}</span><span>${p.current}/${p.max} (черга: ${p.queue ?? 0})</span></div>
              <div class="progress-bar"><div class="progress-bar-fill" style="width:${p.max ? Math.min(100, (p.current / p.max) * 100) : 0}%"></div></div>
            </div>
          `).join('');
    }

    const historyEl = document.getElementById('dashboard-history');
    if (historyEl && data && Array.isArray(data.recentHistory)) {
      historyEl.innerHTML = data.recentHistory.length === 0
        ? '<p class="text-muted">Немає подій</p>'
        : data.recentHistory.map(h => `
            <div class="timeline-item">
              <strong>${escapeHtml(h.action || '')}</strong>
              <div class="text-muted" style="font-size:0.85rem;">${escapeHtml(h.worker || '-')} · ${escapeHtml(h.taskSerial || '')} · ${h.timestamp ? new Date(h.timestamp).toLocaleString() : ''}</div>
              ${h.action === 'Task Completed' && h.taskStatus === 'COMPLETED' && h.taskId ? `<button class="btn btn-sm btn-outline btn-reopen-task" data-task-id="${h.taskId}" style="margin-top:4px;">Скасувати завершення</button>` : ''}
            </div>
          `).join('');
    }

    const workersEl = document.getElementById('dashboard-workers');
    if (workersEl && data) {
      workersEl.innerHTML = `<p>Зараз активні: <strong>${data.activeWorkers ?? 0}</strong> працівник(и/ів)</p>`;
    }

    const seriesTableBody = document.querySelector('#dashboard-series-table tbody');
    if (seriesTableBody) {
      try {
        const activeSeries = await Services.Series.getActive();
        const progresses = await Promise.all(
          activeSeries.map(s => apiGet(`/series/${s.id}/progress`).catch(() => null))
        );
        seriesTableBody.innerHTML = activeSeries.length === 0 ? '<tr><td colspan="4" class="text-center">Немає активних серій</td></tr>' : activeSeries.map((s, idx) => {
          const progress = progresses[idx];
          const pct = progress ? Math.round(progress.percentage) : 0;
          return `
            <tr>
              <td>${escapeHtml(s.number)}</td>
              <td>${escapeHtml(s.productModel ? s.productModel.name : '-')}</td>
              <td>${pct}% (${progress ? progress.completed : 0}/${progress ? progress.totalProducts : s.plannedQuantity})</td>
              <td>${escapeHtml(s.status)}</td>
            </tr>
          `;
        }).join('');
      } catch (e) {
        console.error('Failed to load active series for dashboard:', e);
      }
    }

    const tasksTable = document.querySelector('#dashboard-tasks-table tbody');
    if (tasksTable && data && Array.isArray(data.activeTasks)) {
      const now = new Date();
      tasksTable.innerHTML = data.activeTasks.map(t => {
        let isOverdue = false;
        if (t.deadline) {
          isOverdue = new Date(t.deadline) < now && t.status !== 'COMPLETED';
        }
        const isUrgent = t.priority === 'URGENT';
        return `
          <tr class="${isOverdue ? 'overdue-task' : ''}">
            <td>${escapeHtml(t.series ? (t.series.name || t.series.id) : '-')}</td>
            <td>${escapeHtml(t.worker ? t.worker.name : '-')}</td>
            <td>${escapeHtml(t.status)}</td>
            <td>${isUrgent ? '<span class="badge badge-danger">Терміново</span>' : escapeHtml(t.priority || '-')}</td>
            <td>${t.deadline ? new Date(t.deadline).toLocaleDateString() : '-'}</td>
            <td>${isUrgent ? '' : `<button class="btn btn-sm btn-set-urgent" data-task-id="${t.id}">🔥 Терміново</button>`}</td>
          </tr>
        `;
      }).join('');
    }

    const workerSelect = document.getElementById('dash-filter-worker');
    if (workerSelect && workerSelect.options.length <= 1 && state.currentWorker?.systemRole !== 'SUPPLIER') {
      try {
        const workers = await Services.Workers.getAll();
        const currentVal = workerSelect.value;
        workerSelect.innerHTML = '<option value="">Всі працівники</option>' + workers.map(w => `<option value="${w.id}">${escapeHtml(w.name)}</option>`).join('');
        workerSelect.value = currentVal;
      } catch(e) {}
    }

    const stageSelect = document.getElementById('dash-filter-stage');
    if (stageSelect && stageSelect.options.length <= 1) {
      try {
        const operations = await Services.Operations.getAll();
        const stageNames = [...new Set((operations || []).map(o => o.name).filter(Boolean))];
        const currentVal = stageSelect.value;
        stageSelect.innerHTML = '<option value="">Всі етапи</option>' + stageNames.map(name => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`).join('');
        stageSelect.value = currentVal;
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
    const modelList = Array.isArray(models) ? models : [];
    state.dataCache.models = modelList;
    dom.modelsTable.innerHTML = modelList.length === 0 ? '<tr><td colspan="4" class="text-center">Немає активних моделей</td></tr>' : modelList.map(m => `
      <tr>
        <td>${escapeHtml(m.name)}</td>
        <td>${escapeHtml(m.version || '1.0')}</td>
        <td><button class="btn btn-outline btn-sm btn-load-assemblies" data-id="${m.id}" data-name="${escapeHtml(m.name)}">Складники / Операції</button></td>
        <td><button class="btn btn-outline btn-sm btn-new-version" data-id="${m.id}" data-name="${escapeHtml(m.name)}" data-version="${escapeHtml(m.version || '')}">Нова версія</button></td>
      </tr>
    `).join('');
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadAssemblies(modelId, modelName) {
  try {
    const assemblies = await Services.Models.getAssemblies(modelId);
    window.state.activeModelId = modelId;
    state.dataCache.assemblies = assemblies;
    
    // UI logic specific to the new layout
    const panel = document.getElementById('model-assemblies-panel');
    const nameSpan = document.getElementById('selected-model-name');
    if (panel) panel.style.display = 'block';
    if (nameSpan) nameSpan.innerText = modelName;
    
    if (dom.assembliesTable) {
      dom.assembliesTable.innerHTML = assemblies.map(a => `
        <div class="glass-card mb-2 p-2 btn-select-assembly" style="cursor:pointer;" data-id="${a.id}">
            <strong>${escapeHtml(a.code)}</strong> - ${escapeHtml(a.name)} (${escapeHtml(a.category)})
        </div>
      `).join('');
    }
  } catch (e) {
    // Error handled in API wrapper
  }
}

// ТЗ §3.1 lists "попередні операції (dependencies)" as a real operation attribute a
// dispatcher sets up - without seeing what already exists on this assembly there's no sane
// way to pick a correct "depends on" reference while building out the chain, and the
// dropdown below needs this same list to populate its options.
async function loadAssemblyOperations(assemblyId, assemblyName) {
  const panel = document.getElementById('assembly-operations-panel');
  const list = document.getElementById('assembly-operations-list');
  const nameSpan = document.getElementById('selected-assembly-name');
  const dependsOnSelect = document.getElementById('op-depends-on');
  try {
    const operations = await Services.Models.getOperations(assemblyId);
    const opList = Array.isArray(operations) ? operations : [];
    state.dataCache.assemblyOperations = opList;

    if (nameSpan) nameSpan.innerText = assemblyName || '';
    if (panel) panel.classList.remove('hidden');
    if (list) {
      list.innerHTML = opList.length === 0
        ? '<li class="text-muted">Ще немає операцій на цьому вузлі</li>'
        : opList.map(o => `<li>${escapeHtml(o.name)}${o.dependsOnOperation ? ` (після «${escapeHtml(o.dependsOnOperation.name)}»)` : ''} — ${o.normativeTimeMinutes ?? '?'} хв</li>`).join('');
    }
    if (dependsOnSelect) {
      dependsOnSelect.innerHTML = '<option value="">Не залежить від інших операцій</option>' +
        opList.map(o => `<option value="${o.id}">${escapeHtml(o.name)}</option>`).join('');
    }
  } catch (e) {
    console.error('Failed to load operations for assembly:', e);
  }
}

async function loadSeriesData() {
  if (!dom.seriesTable) return;
  try {
    const series = await Services.Series.getActive();
    const seriesList = Array.isArray(series) ? series : [];

    const progresses = await Promise.all(
      seriesList.map(s => apiGet(`/series/${s.id}/progress`).catch(() => null))
    );

    dom.seriesTable.innerHTML = seriesList.length === 0 ? '<tr><td colspan="8" class="text-center">Немає активних серій</td></tr>' : seriesList.map((s, idx) => {
      const progress = progresses[idx];
      const pct = progress ? Math.round(progress.percentage) : 0;
      const dates = [s.plannedStartDate, s.plannedEndDate]
        .map(d => d ? new Date(d).toLocaleDateString() : '?')
        .join(' – ');
      return `
      <tr>
        <td>${escapeHtml(s.number || s.id)}</td>
        <td>${escapeHtml(s.productModel ? s.productModel.name : '-')}</td>
        <td>${s.plannedQuantity || s.targetQuantity || '-'}</td>
        <td>${escapeHtml(s.status)}</td>
        <td>${escapeHtml(s.priority)}</td>
        <td><progress value="${pct}" max="100"></progress> ${pct}%</td>
        <td>${dates}</td>
        <td>-</td>
      </tr>
    `;
    }).join('');
  } catch (e) {
    console.error('Failed to load series:', e);
  }

  // Populate models dropdown independently so it works even if series loading fails
  try {
    const models = await Services.Models.getAll();
    const modelList = Array.isArray(models) ? models : [];
    const modelSelect = document.getElementById('series-model');
    if (modelSelect) {
      modelSelect.innerHTML = '<option value="">Оберіть модель...</option>' +
        modelList.map(m => `<option value="${m.id}">${escapeHtml(m.name)} (${escapeHtml(m.version || '1.0')})</option>`).join('');
    }
  } catch (e) {
    console.error('Failed to load models for dropdown:', e);
  }
}

async function loadKanbanData() {
  try {
    const products = await Services.Production.getKanban();
    if (dom.kanbanPlanned) dom.kanbanPlanned.innerHTML = '';
    if (dom.kanbanInProgress) dom.kanbanInProgress.innerHTML = '';
    if (dom.kanbanReady) dom.kanbanReady.innerHTML = '';

    let plannedCount = 0, wipCount = 0, doneCount = 0;
    if (products && Array.isArray(products)) {
        products.forEach(p => {
            const card = document.createElement('div');
            card.className = 'kanban-card';
            card.textContent = p.serialNumber || 'Невідомо';
            // InstanceStatus enum values are PLANNED/IN_PRODUCTION/READY (plus
            // DAMAGED/IN_OUTSOURCE/CANCELLED, which this board doesn't have a column for).
            if (p.status === 'PLANNED' && dom.kanbanPlanned) { dom.kanbanPlanned.appendChild(card); plannedCount++; }
            else if (p.status === 'IN_PRODUCTION' && dom.kanbanInProgress) { dom.kanbanInProgress.appendChild(card); wipCount++; }
            else if (p.status === 'READY' && dom.kanbanReady) { dom.kanbanReady.appendChild(card); doneCount++; }
        });
    }
    const plannedCountEl = document.getElementById('kb-planned-count');
    const wipCountEl = document.getElementById('kb-wip-count');
    const doneCountEl = document.getElementById('kb-done-count');
    if (plannedCountEl) plannedCountEl.textContent = plannedCount;
    if (wipCountEl) wipCountEl.textContent = wipCount;
    if (doneCountEl) doneCountEl.textContent = doneCount;
  } catch (e) {
    // Error handled in API wrapper
  }
}

async function loadPalletsData() {
  if (!dom.palletsTable) return;
  try {
    const pallets = await Services.Pallets.getAll();
    const palletList = Array.isArray(pallets) ? pallets : [];
    dom.palletsTable.innerHTML = palletList.length === 0 ? '<tr><td colspan="6" class="text-center">Немає активних піддонів</td></tr>' : palletList.map(p => `
      <tr>
        <td>${escapeHtml(p.qrCode || p.code || p.id)}</td>
        <td>${escapeHtml(p.ownerProduct ? (p.ownerProduct.serialNumber || 'N/A') : 'N/A')}</td>
        <td>${escapeHtml(p.category || 'N/A')}</td>
        <td>${escapeHtml(p.currentPost ? p.currentPost.name : '-')}</td>
        <td>${escapeHtml(p.status)}</td>
        <td>
          <button class="btn btn-sm btn-outline btn-generate-qr" data-code="${escapeHtml(p.qrCode || p.code || p.id)}" data-label="Піддон: ${escapeHtml(p.qrCode || p.code || p.id)}">QR</button>
          <button class="btn btn-sm btn-info btn-pallet-details" data-id="${p.id}">Деталі</button>
        </td>
      </tr>
    `).join('');
  } catch (e) {
    console.error('Failed to load pallets:', e);
  }

  // Populate product-instance dropdown: a pallet is collected for one specific
  // serialized unit (ТЗ §2.7), not just "some product of this model" — so this
  // lists actual instances from active series, not product models.
  try {
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
    console.error('Failed to load product instances for pallet dropdown:', e);
  }
}

async function loadBatchesData() {
  if (!dom.batchesTable) return;
  try {
    const batches = await Services.Batches.getAll();
    const batchList = Array.isArray(batches) ? batches : [];
    dom.batchesTable.innerHTML = batchList.length === 0 ? '<tr><td colspan="6" class="text-center">Немає активних партій</td></tr>' : batchList.map(b => `
      <tr>
        <td>${escapeHtml(b.number || b.id)}</td>
        <td>${escapeHtml(b.operationName || 'N/A')}</td>
        <td>${escapeHtml(b.workerName || 'N/A')}</td>
        <td>${b.actualQuantity || 0} / ${b.plannedQuantity || b.quantity || 0}${b.distributedQuantity ? ` (розподілено: ${b.distributedQuantity})` : ''}</td>
        <td>${escapeHtml(b.status)}</td>
        <td>
          ${b.status === 'AWAITING_DISTRIBUTION' ? `<button class="btn btn-sm btn-primary btn-distribute" data-id="${b.id}">Розподілити</button>` : ''}
        </td>
      </tr>
    `).join('');
  } catch (e) {
    console.error('Failed to load batches:', e);
  }

  // Populate operation and worker dropdowns independently
  try {
    const operations = await Services.Operations.getAll();
    const opList = Array.isArray(operations) ? operations : [];
    const opSelect = document.getElementById('batch-operation');
    if (opSelect) {
      opSelect.innerHTML = '<option value="">Оберіть операцію...</option>' +
        opList.map(o => `<option value="${o.id}">${escapeHtml(o.name)}</option>`).join('');
    }
  } catch (e) {
    console.error('Failed to load operations for dropdown:', e);
  }

  try {
    const workers = await Services.Workers.getAll();
    const workerList = Array.isArray(workers) ? workers : [];
    const workerSelect = document.getElementById('batch-worker');
    if (workerSelect) {
      workerSelect.innerHTML = '<option value="">Оберіть працівника...</option>' +
        workerList.map(w => `<option value="${w.id}">${escapeHtml(w.name)}</option>`).join('');
    }
  } catch (e) {
    console.error('Failed to load workers for dropdown:', e);
  }
}

// Quantities are doubles, so norm × quantity lands on values like 0.1 × 3 =
// 0.30000000000000004, which was printed to the manager verbatim. Round to a precision no
// warehouse cares beyond, then drop trailing zeros so whole numbers stay "6", not "6.000".
function formatQty(value) {
  if (value === null || value === undefined || value === '') return '0';
  const n = Number(value);
  if (!isFinite(n)) return escapeHtml(String(value));
  return String(Number(n.toFixed(3)));
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

const DEFECT_RESOLUTION_LABELS = {
  REWORK: 'Повторна обробка',
  REPLACE: 'Списання деталі',
  WRITE_OFF: 'Списання вузла',
  CONCESSION: 'Допуск з відхиленням'
};

async function loadDefectsData() {
  const tableBody = document.querySelector('#defects-table tbody');
  if (!tableBody) return;
  try {
    const defects = await Services.Defects.getAll();
    tableBody.innerHTML = defects.length === 0 ? '<tr><td colspan="5" class="text-center">Немає записів про брак</td></tr>' : defects.map(d => `
      <tr>
        <td>${d.createdAt ? new Date(d.createdAt).toLocaleString() : '-'}</td>
        <td>${escapeHtml(d.assemblyInstance?.assembly?.name || '-')}${d.assemblyInstance?.productInstance?.serialNumber ? ' (' + escapeHtml(d.assemblyInstance.productInstance.serialNumber) + ')' : ''}</td>
        <td>${escapeHtml(d.reason || '-')}</td>
        <td>${escapeHtml(DEFECT_RESOLUTION_LABELS[d.resolution] || d.resolution || '-')}</td>
        <td>${escapeHtml(d.confirmedBy?.name || '-')}</td>
      </tr>
    `).join('');

    renderDefectCounters(defects);
  } catch (e) {
    console.error(e);
  }

  await loadDefectAssemblyOptions();
}

// These three cards were static "0"s in the markup that nothing ever wrote to, so the defect
// screen reported zero defects today/this week/this month no matter how many the table below
// it was listing — a manager reading that would conclude there was no quality problem at all.
// Counted from the same records the table renders, so the headline figures can never disagree
// with the rows underneath them.
function renderDefectCounters(defects) {
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const weekAgo = new Date(startOfToday);
  weekAgo.setDate(weekAgo.getDate() - 7);
  const monthAgo = new Date(startOfToday);
  monthAgo.setMonth(monthAgo.getMonth() - 1);

  let today = 0, week = 0, month = 0;
  (defects || []).forEach(d => {
    if (!d.createdAt) return;
    const at = new Date(d.createdAt);
    if (isNaN(at)) return;
    if (at >= startOfToday) today++;
    if (at >= weekAgo) week++;
    if (at >= monthAgo) month++;
  });

  const set = (id, value) => {
    const el = document.getElementById(id);
    if (el) el.innerText = value;
  };
  set('defects-today', today);
  set('defects-week', week);
  set('defects-month', month);
}

// The "Вузол / Серійний номер" field used to be free text that the submit handler never
// even read - nothing tied the defect record to a real assembly. Populated the same way as
// the pallet-product dropdown: active series -> their product instances -> assembly instances.
async function loadDefectAssemblyOptions() {
  const select = document.getElementById('defect-item');
  if (!select) return;
  try {
    const activeSeries = await Services.Series.getActive();
    const seriesList = Array.isArray(activeSeries) ? activeSeries : [];
    const productsPerSeries = await Promise.all(
      seriesList.map(s => apiGet(`/series/${s.id}/products`).catch(() => []))
    );
    const products = productsPerSeries.flat();
    const assembliesPerProduct = await Promise.all(
      products.map(p => apiGet(`/products/${p.id}/assemblies`).catch(() => []))
    );
    const options = products.flatMap((p, idx) =>
      (assembliesPerProduct[idx] || []).map(a =>
        `<option value="${a.id}">${escapeHtml(p.serialNumber)} — ${escapeHtml(a.assembly ? a.assembly.name : 'Вузол')} (${escapeHtml(a.status)})</option>`)
    );
    select.innerHTML = '<option value="">Оберіть вузол...</option>' + options.join('');
  } catch (e) {
    console.error('Failed to load assembly options for defect form:', e);
  }
}

async function loadMaterialsData() {
  const tableBody = document.getElementById('materials-table-body');
  try {
    const materials = await Services.Materials.getAll();
    if (tableBody) {
      tableBody.innerHTML = materials.length === 0 ? '<tr><td colspan="8" class="text-center">Немає матеріалів</td></tr>' : materials.map(m => `
        <tr>
          <td>${escapeHtml(m.name)}</td>
          <td>${escapeHtml(m.unit || '-')}</td>
          <td>${formatQty(m.availableStock)}</td>
          <td>${formatQty(m.reservedQuantity)}</td>
          <td>${formatQty(m.usedQuantity)}</td>
          <td>${formatQty(m.minimumStock)}</td>
          <td>${escapeHtml(m.supplier || '-')}</td>
          <td><span class="badge badge-${m.supplyStatus === 'SUFFICIENT' ? 'success' : (m.supplyStatus === 'CRITICAL_DEFICIT' ? 'danger' : 'warning')}">${escapeHtml(m.supplyStatus || '-')}</span></td>
        </tr>
      `).join('');
    }
  } catch (e) {
    console.error('Failed to load materials:', e);
  }

  const seriesSelect = document.getElementById('mat-calc-series');
  if (seriesSelect) {
    try {
      const series = await Services.Series.getActive();
      seriesSelect.innerHTML = series.map(s => `<option value="${s.id}">${escapeHtml(s.number)}</option>`).join('');
    } catch (e) {
      console.error('Failed to load series for materials calculator:', e);
    }
  }
}

// Checklist §39/§65-68: a real audit screen, not just the dashboard's last-10-events panel -
// filterable by worker and date range so "хто, коли, що зробив" is actually answerable beyond
// the last few minutes of activity.
async function loadHistoryData() {
  const tbody = document.querySelector('#history-table tbody');
  if (!tbody) return;

  const workerSelect = document.getElementById('history-filter-worker');
  if (workerSelect && workerSelect.options.length <= 1) {
    try {
      const workers = await Services.Workers.getAll();
      workerSelect.innerHTML = '<option value="">Всі працівники</option>' +
        (workers || []).map(w => `<option value="${w.id}">${escapeHtml(w.name)}</option>`).join('');
    } catch (e) {
      console.error('Failed to load workers for history filter:', e);
    }
  }

  try {
    const workerId = document.getElementById('history-filter-worker')?.value || '';
    const since = document.getElementById('history-filter-since')?.value || '';
    const until = document.getElementById('history-filter-until')?.value || '';

    const params = new URLSearchParams();
    if (workerId) params.append('workerId', workerId);
    if (since) params.append('since', `${since}T00:00:00`);
    if (until) params.append('until', `${until}T23:59:59`);
    const query = params.toString() ? '?' + params.toString() : '';

    const events = await Services.History.getFiltered(query);
    const eventList = Array.isArray(events) ? events : [];

    tbody.innerHTML = eventList.length === 0 ? '<tr><td colspan="7" class="text-center">Немає подій</td></tr>' : eventList.map(h => `
      <tr>
        <td>${h.timestamp ? new Date(h.timestamp).toLocaleString() : '-'}</td>
        <td>${escapeHtml(h.action || '-')}</td>
        <td>${escapeHtml(h.workerName || 'Система')}</td>
        <td>${escapeHtml(h.productSerial || '-')}</td>
        <td>${escapeHtml(h.operationName || '-')}</td>
        <td>${escapeHtml(h.seriesNumber || '-')}</td>
        <td>${escapeHtml(h.batchNumber || '-')}</td>
      </tr>
    `).join('');
  } catch (e) {
    console.error('Failed to load history:', e);
    tbody.innerHTML = '<tr><td colspan="7" class="text-center">Не вдалося завантажити історію</td></tr>';
  }
}

async function loadSectionsData() {
  let sections = [];
  let posts = [];
  
  try {
    sections = await Services.Sections.getAll();
  } catch (e) {
    console.error("Failed to load sections:", e);
    showToast("Не вдалося завантажити дільниці. Перевірте консоль.", "error");
  }

  try {
    posts = await Services.Posts.getAll();
  } catch (e) {
    console.error("Failed to load posts:", e);
    showToast("Не вдалося завантажити пости. Перевірте консоль.", "error");
  }
    
  try {
    // Populate select
    const postSectionSelect = document.getElementById('post-section-select');
    if (postSectionSelect) {
      postSectionSelect.innerHTML = '<option value="">Оберіть дільницю...</option>' +
        sections.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');
    }

    // Render tree
    const treeContainer = document.getElementById('sections-tree-container');
    if (treeContainer) {
      treeContainer.innerHTML = sections.map(s => {
        const sectionPosts = posts.filter(p => p.section && p.section.id === s.id);
        return `
          <div class="mb-3 p-3 glass-card" style="border-left: 4px solid var(--primary-color);">
            <strong style="font-size: 1.1rem;">${escapeHtml(s.name)}</strong> ${s.location ? `<span class="text-muted">(${escapeHtml(s.location)})</span>` : ''}
            <ul style="margin-top: 10px; padding-left: 20px; list-style-type: disc;">
              ${sectionPosts.map(p => `<li style="padding-bottom: 5px; display: flex; align-items: center; gap: 8px;"><span>${escapeHtml(p.name)}</span> <span class="badge badge-sm badge-info">Ємність: ${p.maxCapacity || 1}</span> <button class="btn btn-sm btn-outline btn-generate-qr" data-code="${p.id}" data-label="Пост: ${escapeHtml(p.name)}">QR</button></li>`).join('')}
              ${sectionPosts.length === 0 ? '<li class="text-muted">Немає постів</li>' : ''}
            </ul>
          </div>
        `;
      }).join('');
    }

    // Render loads
    const loadsContainer = document.getElementById('sections-post-loads');
    if (loadsContainer && posts.length > 0) {
      const loadPromises = posts.map(p => Services.Posts.getLoad(p.id).catch(() => null));
      const loads = await Promise.all(loadPromises);
      
      loadsContainer.innerHTML = posts.map((p, i) => {
        const load = loads[i];
        if (!load) return '';
        const currentLoad = load.currentLoad || 0;
        const maxCapacity = p.maxCapacity || 1;
        const pct = Math.round((currentLoad / maxCapacity) * 100);
        return `
          <div class="mb-3 glass-card p-3">
            <div style="display: flex; justify-content: space-between; margin-bottom: 5px;">
              <strong>${escapeHtml(p.name)}</strong>
              <span>${currentLoad}/${maxCapacity} (${pct}%)</span>
            </div>
            <progress class="progress progress-primary w-full" value="${pct}" max="100"></progress>
          </div>
        `;
      }).join('');
    } else if (loadsContainer) {
      loadsContainer.innerHTML = '<p class="text-muted">Немає постів для відображення навантаження</p>';
    }
  } catch (e) {
    console.error("Error rendering sections UI:", e);
  }
}

function generatePalletQR(code, label) {
    const qrContainer = document.getElementById('qr-modal-code');
    if (!qrContainer) return;
    const titleEl = document.getElementById('qr-modal-title');
    if (titleEl) titleEl.innerText = label || code;
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

window.app = {
  openModal: function(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.add('active');
  },
  closeModal: function(id) {
    const modal = document.getElementById(id);
    if (modal) {
      modal.classList.remove('active');
      modal.style.display = '';
    }
  }
};

function setupEventListeners() {
  dom.navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const targetView = e.currentTarget.dataset.view;
      if (targetView) switchView(targetView);
    });
  });

  const btnCalcMaterials = document.getElementById('btn-calc-materials');
  if (btnCalcMaterials) {
    btnCalcMaterials.addEventListener('click', async () => {
      const seriesId = document.getElementById('mat-calc-series')?.value;
      const resultDiv = document.getElementById('material-requirements-result');
      if (!seriesId || !resultDiv) {
        showToast('Оберіть серію', 'warning');
        return;
      }
      try {
        const requirements = await Services.Materials.getRequirements(seriesId);
        resultDiv.classList.remove('hidden');
        if (!requirements || requirements.length === 0) {
          resultDiv.innerHTML = '<p class="text-muted">Немає потреби в матеріалах для цієї серії</p>';
          return;
        }
        resultDiv.innerHTML = `
          <table class="glass-table">
            <thead><tr><th>Матеріал</th><th>Потреба</th><th>На складі</th><th>Резерв</th><th>Дефіцит</th><th>Статус</th></tr></thead>
            <tbody>
              ${requirements.map(r => `
                <tr class="${r.status !== 'SUFFICIENT' ? 'overdue-row' : ''}">
                  <td>${escapeHtml(r.materialName)}</td>
                  <td>${formatQty(r.required)} ${escapeHtml(r.unit || '')}</td>
                  <td>${formatQty(r.available)}</td>
                  <td>${formatQty(r.reserved)}</td>
                  <td>${formatQty(r.deficit)}</td>
                  <td>${escapeHtml(r.status)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        `;
      } catch (e) {
        showToast('Помилка розрахунку потреби', 'error');
        console.error(e);
      }
    });
  }

  const btnUpdateStock = document.getElementById('btn-update-stock');
  if (btnUpdateStock) {
    btnUpdateStock.addEventListener('click', async () => {
      try {
        const materials = await Services.Materials.getAll();
        if (!materials.length) return;
        const names = materials.map((m, i) => `${i + 1}. ${m.name} (зараз: ${m.availableStock} ${m.unit || ''})`).join('\n');
        const choice = prompt(`Оберіть номер матеріалу:\n${names}`);
        const idx = parseInt(choice, 10) - 1;
        if (isNaN(idx) || !materials[idx]) return;
        const newStock = prompt(`Новий залишок для "${materials[idx].name}":`, materials[idx].availableStock);
        if (newStock === null || isNaN(parseFloat(newStock))) return;
        await Services.Materials.updateStock(materials[idx].id, parseFloat(newStock));
        showToast('Залишок оновлено', 'success');
        await loadMaterialsData();
      } catch (e) {
        showToast('Помилка оновлення залишку', 'error');
        console.error(e);
      }
    });
  }

  // Event delegation for dynamically generated buttons
  document.body.addEventListener('click', (e) => {


    if (e.target.matches('.btn-load-assemblies')) {
      const id = e.target.dataset.id;
      const name = e.target.dataset.name;
      loadAssemblies(id, name);
    }
    if (e.target.matches('.btn-new-version')) {
      const id = e.target.dataset.id;
      const name = e.target.dataset.name;
      const currentVersion = e.target.dataset.version;
      // ТЗ §2.2: change to a model creates a NEW version, archiving the old one - the product
      // already in production keeps its old version. Blank input auto-bumps the last version
      // segment (v.1.0 -> v.1.1), matching what the backend already does when no version is sent.
      const newVersion = prompt(`Нова версія моделі «${name}» (поточна: ${currentVersion || '?'}).\nЗалиште порожнім для автоматичного інкременту:`, '');
      if (newVersion === null) return;
      Services.Models.createNewVersion(id, newVersion ? { version: newVersion } : {}).then(() => {
        showToast('Нову версію моделі створено, попередню архівовано', 'success');
        loadModelsData();
      }).catch(err => showToast(err.message || 'Не вдалося створити нову версію', 'error'));
    }
    if (e.target.closest('.btn-select-assembly')) {
      const card = e.target.closest('.btn-select-assembly');
      const id = card.dataset.id;
      window.state.activeAssemblyId = id;
      const assembly = (state.dataCache.assemblies || []).find(a => a.id === id);
      showToast('Вузол обрано: ' + (assembly ? assembly.name : id), 'info');
      loadAssemblyOperations(id, assembly ? assembly.name : '');
    }
    if (e.target.matches('.btn-generate-qr')) {
      const code = e.target.dataset.code;
      const label = e.target.dataset.label;
      generatePalletQR(code, label);
    }
    
    if (e.target.matches('.btn-pallet-details')) {
      const id = e.target.dataset.id;
      showPalletDetails(id);
    }
    
    if (e.target.matches('.btn-distribute')) {
      const id = e.target.dataset.id;
      showDistributionModal(id);
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
      Services.Outsource.receive(id, window.state.user.id).then(() => {
        showToast('Отримано', 'success');
        loadOutsourceData();
      }).catch(console.error);
    }

    if (e.target.matches('.btn-set-urgent')) {
      const taskId = e.target.dataset.taskId;
      Services.Tasks.setUrgent(taskId).then(() => {
        showToast('Задачу позначено терміновою', 'success');
        loadDashboardData();
      }).catch(err => showToast(err.message || 'Не вдалося встановити пріоритет', 'error'));
    }

    if (e.target.matches('.btn-reopen-task')) {
      const taskId = e.target.dataset.taskId;
      if (!confirm('Скасувати завершення цієї задачі? Вона повернеться у статус "В роботі".')) return;
      const managerId = state.currentWorker ? state.currentWorker.id : null;
      Services.Tasks.reopen(taskId, managerId).then(() => {
        showToast('Завершення задачі скасовано', 'success');
        loadDashboardData();
      }).catch(err => showToast(err.message || 'Не вдалося скасувати завершення', 'error'));
    }
  });

  if (dom.newModelForm) {
    dom.newModelForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = document.getElementById('new-model-name').value;
      const descInput = document.getElementById('model-desc');
      const desc = descInput ? descInput.value : '';
      const versionInput = document.getElementById('model-version');
      const version = versionInput ? versionInput.value : '';
      
      const submitBtn = dom.newModelForm.querySelector('button[type="submit"]');
      if (submitBtn) submitBtn.disabled = true;

      try {
        await Services.Models.create({ name, description: desc, version });
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
            const modelId = document.getElementById('series-model').value;
            if (!modelId) {
              showToast('Оберіть модель для серії', 'error');
              return;
            }
            const seriesNumber = document.getElementById('series-number').value;
            const quantity = parseInt(document.getElementById('series-qty').value, 10);
            const priority = document.getElementById('series-priority') ? document.getElementById('series-priority').value : 'MEDIUM';
            await Services.Series.create({ modelId, number: seriesNumber, plannedQuantity: quantity, priority });
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

  const workerRegForm = document.getElementById('form-register-worker');
  if (workerRegForm) {
      workerRegForm.addEventListener('submit', async (e) => {
          e.preventDefault();
          const name = document.getElementById('worker-name').value;
          const role = document.getElementById('worker-role').value;
          const position = document.getElementById('worker-position').value;
          const sectionId = document.getElementById('worker-section').value;
          const pin = document.getElementById('worker-pin').value;
          const systemRole = document.getElementById('worker-systemRole').value;
          const qualifiedOperationIds = Array.from(document.getElementById('worker-operations').selectedOptions).map(o => o.value);

          try {
             await Services.Workers.create({ name, role, position, sectionId, pin, systemRole, qualifiedOperationIds });
             showToast('Працівника зареєстровано', 'success');
             workerRegForm.reset();
             await loadWorkersData();
          } catch(err) {
             showToast(err.message || 'Не вдалося зареєструвати працівника', 'error');
             console.error(err);
          }
      });
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

  if (dom.btnMobileMenu && dom.sidebar) {
    dom.btnMobileMenu.addEventListener('click', () => {
      dom.sidebar.classList.toggle('mobile-open');
    });
  }
  
  const formAddAssembly = document.getElementById('form-add-assembly');
  if (formAddAssembly) {
    formAddAssembly.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!window.state.activeModelId) {
        showToast('Спочатку виберіть модель!', 'error');
        return;
      }
      try {
        await Services.Models.addAssembly({
          productModelId: window.state.activeModelId,
          code: document.getElementById('assembly-code').value,
          name: document.getElementById('assembly-name').value,
          category: document.getElementById('assembly-category').value,
          parts: document.getElementById('assembly-parts').value,
          normativeTimeMinutes: 0
        });
        showToast('Вузол успішно додано', 'success');
        formAddAssembly.reset();
        loadAssemblies(window.state.activeModelId, document.getElementById('selected-model-name').innerText);
      } catch (err) {
        console.error(err);
      }
    });
  }
  
  const formAddOperation = document.getElementById('form-add-operation');
  if (formAddOperation) {
    formAddOperation.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!window.state.activeAssemblyId) {
        showToast('Спочатку виберіть вузол!', 'error');
        return;
      }
      try {
        const materialId = document.getElementById('op-material').value || null;
        const materialQty = document.getElementById('op-material-qty').value;
        await Services.Models.addOperation(window.state.activeAssemblyId, {
          name: document.getElementById('op-name').value,
          description: document.getElementById('op-desc').value || '',
          normativeTimeMinutes: parseInt(document.getElementById('op-norm-time').value, 10) || 0,
          orderIndex: (state.dataCache.assemblyOperations || []).length + 1,
          sectionId: document.getElementById('op-section').value || null,
          postId: document.getElementById('op-post').value || null,
          type: document.getElementById('op-type').value || null,
          dependsOnOperationId: document.getElementById('op-depends-on').value || null,
          equipment: document.getElementById('op-equipment').value || null,
          tools: document.getElementById('op-tools').value || null,
          requiredQualification: document.getElementById('op-qualification').value || null,
          materialId: materialId,
          materialQuantityPerUnit: materialId && materialQty ? parseFloat(materialQty) : null
        });
        showToast('Операцію успішно додано', 'success');
        formAddOperation.reset();
        loadAssemblyOperations(window.state.activeAssemblyId, document.getElementById('selected-assembly-name').innerText);
      } catch (err) {
        showToast(err.message || 'Не вдалося додати операцію', 'error');
        console.error(err);
      }
    });
  }

  const formWorkerPin = document.getElementById('form-change-worker-pin');
  if (formWorkerPin) {
    formWorkerPin.addEventListener('submit', async (e) => {
      e.preventDefault();
      const workerId = document.getElementById('worker-id-for-pin').value;
      const newPin = document.getElementById('new-worker-pin').value;
      try {
        await Services.Auth.setPin(workerId, newPin);
        showToast('PIN код успішно змінено', 'success');
        document.getElementById('modal-change-worker-pin').classList.remove('active');
        formWorkerPin.reset();
      } catch (err) {
        showToast('Помилка при зміні PIN', 'error');
        console.error(err);
      }
    });
  }

  const formDefect = document.getElementById('form-report-defect');
  if (formDefect) {
    // Backend DefectResolution enum is REWORK/REPLACE/WRITE_OFF/CONCESSION, not the
    // rework/scrap/concession values this form sends. "concession" used to map to REPLACE
    // (scrap-and-rebuild) - the opposite of what "Допуск з відхиленням" (accept as-is) means.
    const resolutionMap = { rework: 'REWORK', scrap: 'WRITE_OFF', concession: 'CONCESSION' };
    formDefect.addEventListener('submit', async (e) => {
      e.preventDefault();
      const assemblyInstanceId = document.getElementById('defect-item').value;
      if (!assemblyInstanceId) {
        showToast('Оберіть вузол', 'warning');
        return;
      }
      try {
        const rawResolution = document.getElementById('defect-resolution').value;
        // The report is about "right now" for this assembly, so the relevant task is
        // whichever one on it was created last (its current/most recent operation).
        let taskId = null;
        try {
          const tasks = await apiGet(`/tasks/assembly-instance/${assemblyInstanceId}`);
          if (Array.isArray(tasks) && tasks.length > 0) {
            taskId = tasks[tasks.length - 1].id;
          }
        } catch (e) { /* not fatal - defect can still be recorded without a task link */ }

        await Services.Defects.report({
          assemblyInstanceId,
          taskId,
          reason: document.getElementById('defect-reason').value,
          resolution: resolutionMap[rawResolution] || rawResolution,
          confirmedById: state.currentWorker ? state.currentWorker.id : null
        });
        showToast('Брак успішно зафіксовано', 'success');
        formDefect.reset();
        await loadDefectsData();
      } catch (err) {
        showToast('Помилка фіксації браку: ' + (err.message || ''), 'error');
        console.error(err);
      }
    });
  }

  const formCreateSection = document.getElementById('form-create-section');
  if (formCreateSection) {
    formCreateSection.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await Services.Sections.create({
          name: document.getElementById('section-name').value,
          location: document.getElementById('section-location').value || '',
          area: parseFloat(document.getElementById('section-area').value) || 0
        });
        showToast('Дільницю створено', 'success');
        formCreateSection.reset();
        await loadSectionsData();
      } catch (err) {
        console.error(err);
      }
    });
  }

  const formCreatePost = document.getElementById('form-create-post');
  if (formCreatePost) {
    formCreatePost.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await Services.Posts.create({
          name: document.getElementById('post-name').value,
          sectionId: document.getElementById('post-section-select').value,
          maxCapacity: 1
        });
        showToast('Пост створено', 'success');
        formCreatePost.reset();
        await loadSectionsData();
      } catch (err) {
        console.error(err);
      }
    });
  }

  const btnApplyDashFilters = document.getElementById('btn-apply-dash-filters');
  if (btnApplyDashFilters) {
    btnApplyDashFilters.addEventListener('click', loadDashboardData);
  }

  const btnApplyHistoryFilters = document.getElementById('btn-apply-history-filters');
  if (btnApplyHistoryFilters) {
    btnApplyHistoryFilters.addEventListener('click', loadHistoryData);
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
        if (updatedBatch.status === 'COMPLETED') {
          showToast('Партію повністю розподілено й завершено', 'success');
          document.getElementById('modal-distribution').classList.add('hidden');
        } else {
          showToast('Частину розподілено, лишилось ще', 'success');
          showDistributionModal(batchId);
        }
        await loadBatchesData();
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
    const infoEl = document.getElementById('dist-batch-info');
    if (infoEl) infoEl.innerText = `Партія ${batch.number || ''}: розподілено ${batch.distributedQuantity || 0} з ${batch.actualQuantity || 0} (лишилось ${remaining})`;
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

async function initApp() {
  if (!state.token) {
      window.location.href = 'index.html';
      return;
  }
  initDOM();
  setupEventListeners();
  loadOperationDropdowns();
  switchView('dashboard');

  // ТЗ §16: Постачальник's exclusive scope is "перегляд потреби і дефіциту, управління
  // запасами" - everything else in this UI (моделі, серії, партії, брак, працівники...) is
  // outside their role, so those nav items are hidden rather than just relying on the backend
  // 403s a Supplier would get if they tried to use them.
  if (state.currentWorker && state.currentWorker.systemRole === 'SUPPLIER') {
    const supplierAllowedViews = new Set(['dashboard', 'materials']);
    document.querySelectorAll('.nav-item[data-view]').forEach(item => {
      if (!supplierAllowedViews.has(item.dataset.view)) item.classList.add('hidden');
    });
  }

  // "Адміністратор" was hardcoded in the markup and never updated - the header claimed
  // every session belonged to an admin, regardless of who actually logged in.
  const currentUserNameEl = document.getElementById('current-user-name');
  if (currentUserNameEl && state.currentWorker) {
    currentUserNameEl.textContent = state.currentWorker.name || 'Користувач';
  }
  if (state.isWorkerLoggedIn) {
      connectSSE();
  }
}

document.addEventListener('DOMContentLoaded', initApp);

async function loadOperationDropdowns() {
  try {
    const opSection = document.getElementById('op-section');
    if (opSection) {
      const sections = await Services.Sections.getAll();
      const sectionList = Array.isArray(sections) ? sections : [];
      opSection.innerHTML = '<option value="">Оберіть дільницю...</option>' +
        sectionList.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');
    }

    const opPost = document.getElementById('op-post');
    if (opPost) {
      const posts = await Services.Posts.getAll();
      const postList = Array.isArray(posts) ? posts : [];
      opPost.innerHTML = '<option value="">Оберіть пост...</option>' +
        postList.map(p => `<option value="${p.id}">${escapeHtml(p.name)}</option>`).join('');
    }
    
    const opMaterial = document.getElementById('op-material');
    if (opMaterial) {
      const materials = await Services.Materials.getAll();
      const materialList = Array.isArray(materials) ? materials : [];
      opMaterial.innerHTML = '<option value="">Без матеріалу</option>' +
        materialList.map(m => `<option value="${m.id}">${escapeHtml(m.name)} (${escapeHtml(m.unit || '')})</option>`).join('');
    }

    const opType = document.getElementById('op-type');
    if (opType) {
      opType.innerHTML = '<option value="">Тип операції...</option>' +
        '<option value="INDIVIDUAL">Індивідуальна</option>' +
        '<option value="BATCH">Партійна</option>';
    }
  } catch(e) {
    console.error('Failed to load dropdowns for operation form', e);
  }
}


window.changeWorkerPin = async function(id) {
  document.getElementById('worker-id-for-pin').value = id;
  document.getElementById('new-worker-pin').value = '';
  document.getElementById('modal-change-worker-pin').classList.add('active');
};

function performLogout() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  window.location.href = 'index.html';
}

if (document.getElementById('btn-logout')) {
  document.getElementById('btn-logout').addEventListener('click', performLogout);
}

if (document.getElementById('btn-change-password')) {
  document.getElementById('btn-change-password').addEventListener('click', () => {
    document.getElementById('modal-change-password').classList.add('active');
  });
}

if (document.getElementById('form-change-password')) {
  document.getElementById('form-change-password').addEventListener('submit', async (e) => {
    e.preventDefault();
    const newPin = document.getElementById('new-password').value;
    const workerId = state.user?.id;
    if (!workerId) {
      showToast('Помилка: не знайдено ID користувача', 'error');
      return;
    }
    
    try {
      await apiPost('/auth/set-pin', { workerId: workerId, pin: newPin });
      showToast('Пароль успішно змінено', 'success');
      document.getElementById('modal-change-password').classList.remove('active');
      document.getElementById('form-change-password').reset();
    } catch (err) {
      showToast('Помилка при зміні пароля: ' + err.message, 'error');
    }
  });
}

if (document.getElementById('form-change-worker-pin')) {
  document.getElementById('form-change-worker-pin').addEventListener('submit', async (e) => {
    e.preventDefault();
    const newPin = document.getElementById('new-worker-pin').value;
    const workerId = document.getElementById('worker-id-for-pin').value;
    if (!workerId) {
      showToast('Помилка: не знайдено ID працівника', 'error');
      return;
    }
    
    try {
      await apiPost('/auth/set-pin', { workerId: workerId, pin: newPin });
      showToast('PIN-код працівника успішно змінено', 'success');
      document.getElementById('modal-change-worker-pin').classList.remove('active');
      document.getElementById('form-change-worker-pin').reset();
    } catch (err) {
      showToast('Помилка при зміні PIN-коду: ' + err.message, 'error');
    }
  });
}
async function loadWorkersData() {
  try {
    const workers = await Services.Workers.getAll();
    const workerList = Array.isArray(workers) ? workers : [];
    const tbody = document.querySelector('#workers-table tbody');
    if (!tbody) return;
    
    tbody.innerHTML = workerList.length === 0 ? '<tr><td colspan="7" class="text-center">Немає працівників</td></tr>' : workerList.map(w => `
      <tr>
        <td>${escapeHtml(w.name)}</td>
        <td>${escapeHtml(w.position || w.role || '-')}</td>
        <td>${escapeHtml(w.sectionName || '-')}</td>
        <td>${escapeHtml(w.postName || '-')}</td>
        <td>${(w.qualifiedOperations || []).map(o => escapeHtml(o.name)).join(', ') || '-'}</td>
        <td>
          <span class="status-badge ${w.systemRole === 'MANAGER' ? 'status-created' : 'status-in-progress'}">${escapeHtml(w.systemRole || 'WORKER')}</span>
        </td>
        <td>
          <button class="btn btn-outline btn-sm" onclick="changeWorkerPin('${w.id}')">Змінити пароль</button>
        </td>
      </tr>
    `).join('');

    // Populate worker-section dropdown
    const sectionSelect = document.getElementById('worker-section');
    if (sectionSelect) {
      try {
        const sections = await Services.Sections.getAll();
        const sectionList = Array.isArray(sections) ? sections : [];
        sectionSelect.innerHTML = '<option value="">Оберіть дільницю...</option>' +
          sectionList.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');
      } catch(e) {
        console.error('Failed to load sections for worker form:', e);
      }
    }

    // Populate worker-operations multi-select (ТЗ §2.1 "доступні операції")
    const operationsSelect = document.getElementById('worker-operations');
    if (operationsSelect) {
      try {
        const operations = await Services.Operations.getAll();
        const operationList = Array.isArray(operations) ? operations : [];
        operationsSelect.innerHTML = operationList.map(o => `<option value="${o.id}">${escapeHtml(o.name)}</option>`).join('');
      } catch(e) {
        console.error('Failed to load operations for worker form:', e);
      }
    }
  } catch (e) {
    console.error('Failed to load workers:', e);
  }
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { 
    Services, apiGet, apiPost, 
    state, dom, initDOM, showToast, connectSSE, 
    switchView, loadViewData, loadDashboardData, loadModelsData, 
    loadAssemblies, loadSeriesData, loadKanbanData, loadPalletsData, 
    loadBatchesData, formatElapsed, loadOutsourceData, generatePalletQR,
    setupEventListeners, showDistributionModal, showPalletDetails, initApp
  };
}
