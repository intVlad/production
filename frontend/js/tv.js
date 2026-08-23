document.addEventListener('DOMContentLoaded', () => {
    initAuth();
    startClock();
});

function initAuth() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');

    if (token) {
        localStorage.setItem('token', token);
    }

    window.tvFilters = {
        seriesId: urlParams.get('seriesId') || null,
        workerId: urlParams.get('workerId') || null,
        status: urlParams.get('status') || null
    };

    // Strip credentials/filters from the visible URL but keep them applied in memory.
    window.history.replaceState({}, document.title, window.location.pathname);

    const savedToken = localStorage.getItem('token');
    // api.js only reads window.state.token at request time, but window.state was built
    // once when api.js loaded (before this token existed in localStorage) - without this,
    // every request below goes out unauthenticated and 401s forever.
    window.state.token = savedToken;

    if (!savedToken) {
        document.getElementById('unauthorized-msg').style.display = 'block';
    } else {
        document.getElementById('tv-dashboard').style.display = 'flex';
        document.getElementById('tv-dashboard').style.flexDirection = 'column';
        startDashboard();
        connectSSE();
    }
}

function connectSSE() {
    if (!window.state.token) return;
    const evtSource = new EventSource(API_BASE + '/events/stream?token=' + window.state.token);
    evtSource.onmessage = function(event) {
        if (event.data === 'dashboard_update') fetchData();
    };
    evtSource.onerror = function() {
        // EventSource auto-reconnects; the 30s poll in startDashboard covers us meanwhile.
    };
}

function startClock() {
    const clockEl = document.getElementById('clock');
    setInterval(() => {
        const now = new Date();
        clockEl.textContent = now.toLocaleTimeString('uk-UA');
    }, 1000);
}

let currentSlide = 1;
const totalSlides = 5;

function startDashboard() {
    const runFetchLoop = async () => {
        await fetchData();
        setTimeout(runFetchLoop, 30000);
    };
    runFetchLoop();

    setInterval(() => {
        document.getElementById(`slide-${currentSlide}`).classList.remove('active');
        currentSlide = currentSlide < totalSlides ? currentSlide + 1 : 1;
        document.getElementById(`slide-${currentSlide}`).classList.add('active');
    }, 15000);
}

let isFetching = false;

function buildDashboardQuery() {
    const params = new URLSearchParams();
    const f = window.tvFilters || {};
    if (f.seriesId) params.set('seriesId', f.seriesId);
    if (f.workerId) params.set('workerId', f.workerId);
    if (f.status) params.set('status', f.status);
    const qs = params.toString();
    return qs ? `/dashboard?${qs}` : '/dashboard';
}

async function fetchData() {
    if (isFetching) return;
    isFetching = true;
    let hadError = false;
    let dashboardData = null;
    let outsourceData = null;

    // Each source fails independently so one broken/slow endpoint doesn't blank every slide.
    try {
        dashboardData = await apiGet(buildDashboardQuery());
    } catch (error) {
        console.error('Помилка завантаження /dashboard для TV панелі:', error);
        hadError = true;
    }

    try {
        outsourceData = await apiGet('/outsource/active');
    } catch (error) {
        console.error('Помилка завантаження /outsource/active для TV панелі:', error);
        hadError = true;
    }

    if (dashboardData) {
        updateSlide1(dashboardData);
        updateSlide2(dashboardData);
        updateSlide3(dashboardData);
        updateSlide4Stats(dashboardData);
        updateSlide5(dashboardData);
    }
    updateSlide4Outsource(outsourceData);

    const errorMsg = document.getElementById('connection-error-msg');
    if (errorMsg) errorMsg.style.display = hadError ? 'block' : 'none';

    isFetching = false;
}

function updateSlide1(data) {
    document.getElementById('stat-active-series').textContent = data.activeSeries || 0;
    document.getElementById('stat-completed-today').textContent = data.productsReady || 0;
    document.getElementById('stat-in-progress').textContent = data.productsInProduction || 0;
}

function updateSlide2(data) {
    const container = document.getElementById('kanban-container');
    container.innerHTML = '';

    const productsByStage = data.productsByStage || {};
    const stages = Object.keys(productsByStage);

    if (stages.length === 0) {
        container.innerHTML = '<div class="glass-panel" style="padding:40px; text-align:center; grid-column: 1 / -1; font-size: 2rem;">Немає виробів у виробництві</div>';
        return;
    }

    stages.sort((a, b) => productsByStage[b] - productsByStage[a]);

    stages.forEach(stage => {
        const col = document.createElement('div');
        col.className = 'kanban-col glass-panel';
        col.innerHTML = `
            <h3>${escapeHtml(stage)}</h3>
            <div class="kanban-task">
                <strong>${productsByStage[stage]}</strong>
                виріб(ів)
            </div>
        `;
        container.appendChild(col);
    });
}

function updateSlide3(data) {
    const container = document.getElementById('post-load-container');
    container.innerHTML = '';

    const postLoads = data.postLoads || [];
    if (postLoads.length === 0) {
        container.innerHTML = '<div class="glass-panel" style="padding:20px; text-align:center;">Немає постів</div>';
        return;
    }

    postLoads.forEach(post => {
        const current = post.current || 0;
        const max = post.max || 1;
        const queue = post.queue || 0;
        const pct = Math.round((current / max) * 100);

        let colorClass = 'bg-primary';
        if (pct >= 85) colorClass = 'bg-danger';
        else if (pct >= 65) colorClass = 'bg-warning';
        else if (pct <= 35) colorClass = 'bg-success';

        const item = document.createElement('div');
        item.className = 'load-item glass-panel';
        item.innerHTML = `
            <div class="load-info">
                <span class="load-name">${escapeHtml(post.postName)}</span>
                <span class="load-value">${current}/${max} (${pct}%) · черга: ${queue}</span>
            </div>
            <div class="progress-bar-bg">
                <div class="progress-bar-fill ${colorClass}" style="width: ${Math.min(pct, 100)}%;"></div>
            </div>
        `;
        container.appendChild(item);
    });
}

function updateSlide4Stats(data) {
    document.getElementById('stat-material-deficits').textContent = data.materialDeficits || 0;
    document.getElementById('stat-defects-today').textContent = data.defectsToday || 0;
    document.getElementById('stat-outsource-active').textContent = data.activeOutsource || 0;
    document.getElementById('stat-outsource-overdue').textContent = data.overdueOutsource || 0;
}

function updateSlide4Outsource(records) {
    const container = document.getElementById('outsource-list-container');
    if (!Array.isArray(records)) return;
    container.innerHTML = '';

    if (records.length === 0) {
        container.innerHTML = '<div style="padding:20px; text-align:center; font-size: 1.6rem;">Немає активних записів аутсорсу</div>';
        return;
    }

    const now = new Date();
    records.forEach(record => {
        const expected = record.expectedReturnDate ? new Date(record.expectedReturnDate) : null;
        const isOverdue = expected && expected < now;

        const row = document.createElement('div');
        row.className = 'outsource-row glass-panel' + (isOverdue ? ' overdue' : '');
        row.innerHTML = `
            <div class="os-main">
                <strong>${escapeHtml(record.partner || 'Невідомий партнер')}</strong>
                <span>${escapeHtml(record.workType || '')}</span>
            </div>
            <div class="os-date">
                ${expected ? expected.toLocaleDateString('uk-UA') : 'Без терміну'}
                <span class="badge ${isOverdue ? 'badge-danger' : 'badge-primary'}">${isOverdue ? 'Прострочено' : record.status || ''}</span>
            </div>
        `;
        container.appendChild(row);
    });
}

// READY means "task created, waiting to be picked up" - NOT "work is done". Labelling it
// "Готово" made anyone walking past the TV read a not-yet-started task as finished work.
const STATUS_LABELS = {
    CREATED: 'Створено',
    READY: 'У черзі',
    ASSIGNED: 'Призначено',
    IN_PROGRESS: 'В процесі',
    PAUSED: 'На паузі',
    BLOCKED: 'Заблоковано',
    COMPLETED: 'Завершено',
    DAMAGED: 'Брак',
    CANCELLED: 'Скасовано'
};

function updateSlide5(data) {
    const tbody = document.getElementById('products-table-body');
    tbody.innerHTML = '';

    const tasks = data.activeTasks || [];
    if (tasks.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 30px;">Немає активних завдань</td></tr>';
        return;
    }

    tasks.forEach(task => {
        const tr = document.createElement('tr');
        const statusLabel = STATUS_LABELS[task.status] || task.status || '—';
        tr.innerHTML = `
            <td>${escapeHtml(task.productSerial || '—')}</td>
            <td>${escapeHtml(task.series ? task.series.name : '—')}</td>
            <td>${escapeHtml(task.operationName || '—')}</td>
            <td>${escapeHtml(task.worker ? task.worker.name : '—')}</td>
            <td><span class="badge ${task.status === 'IN_PROGRESS' ? 'badge-warning' : 'badge-primary'}">${statusLabel}</span></td>
            <td>${task.normativeTimeMinutes != null ? task.normativeTimeMinutes : '—'}</td>
            <td>${task.actualTimeMinutes != null ? task.actualTimeMinutes : '—'}</td>
        `;
        tbody.appendChild(tr);
    });
}
