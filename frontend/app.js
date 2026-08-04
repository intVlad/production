// Determine API URL based on current environment
const isLocal = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
const API_BASE_URL = isLocal ? 'http://localhost:8080/api' : 'https://YOUR_BACKEND_URL.onrender.com/api';

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '❌';
    
    toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
    
    container.appendChild(toast);
    
    setTimeout(() => {
        toast.classList.add('fade-out');
        toast.addEventListener('animationend', () => toast.remove());
    }, 4000);
}

// Worker ID will be fetched dynamically from the dropdown
function getWorkerId() {
    return document.getElementById('worker-select').value;
}

document.getElementById('worker-select').addEventListener('change', () => {
    app.loadWorkerTasks();
    const taskDetails = document.getElementById('task-details');
    if (taskDetails) taskDetails.classList.add('hidden');
    app.state.currentTask = null;
    document.getElementById('task-input').value = '';
});

const statusTranslations = {
    'PENDING': 'ОЧІКУЄ',
    'IN_PROGRESS': 'В ПРОЦЕСІ',
    'PAUSED': 'НА ПАУЗІ',
    'COMPLETED': 'ЗАВЕРШЕНО',
    'BLOCKED': 'ЗАБЛОКОВАНО'
};

const app = {
    state: {
        currentTask: null,
        dashboardInterval: null,
        html5QrcodeScanner: null
    },

    switchView(viewId) {
        document.querySelectorAll('.view-section').forEach(el => el.classList.remove('active'));
        document.querySelectorAll('.nav-btn').forEach(el => el.classList.remove('active'));
        
        document.getElementById(`${viewId}-view`).classList.add('active');
        document.getElementById(`nav-${viewId}`).classList.add('active');

        if (this.state.dashboardInterval) {
            clearInterval(this.state.dashboardInterval);
            this.state.dashboardInterval = null;
        }

        if (viewId === 'dashboard') {
            this.loadDashboard();
            this.loadWorkersForSelect();
            this.loadModelsForSelect();
            this.state.dashboardInterval = setInterval(() => this.loadDashboard(), 3000);
        } else if (viewId === 'worker') {
            this.loadWorkersForSelect().then(() => this.loadWorkerTasks());
        }
    },

    async loadTask() {
        const inputVal = document.getElementById('task-input').value.trim();
        if (!inputVal) return showToast('Будь ласка, введіть ID завдання або Серійний номер', 'warning');

        // Check if input is a valid UUID
        const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(inputVal);
        let url = `${API_BASE_URL}/tasks/${inputVal}`;
        if (!isUUID) {
            url = `${API_BASE_URL}/tasks/search?serialNumber=${encodeURIComponent(inputVal)}`;
        }

        try {
            const res = await fetch(url);
            if (!res.ok) throw new Error("Завдання не знайдено (можливо, воно вже завершене)");
            const taskData = await res.json();
            
            this.state.currentTask = taskData;
            
            document.getElementById('task-details').classList.remove('hidden');
            document.getElementById('td-product').innerText = 
                (taskData.productInstance && taskData.productInstance.serialNumber) ? taskData.productInstance.serialNumber : "Невідомо";
            document.getElementById('td-stage').innerText = 
                (taskData.stage && taskData.stage.name) ? taskData.stage.name : "Невідомо";
            this.updateTaskUI(taskData.status);
        } catch (err) {
            showToast(err.message, 'error');
        }
    },

    startScanner() {
        const qrReader = document.getElementById('qr-reader');
        if (qrReader.style.display === 'block') {
            // Stop scanner if already open
            if (this.state.html5QrcodeScanner) {
                this.state.html5QrcodeScanner.clear();
            }
            qrReader.style.display = 'none';
            return;
        }

        qrReader.style.display = 'block';
        this.state.html5QrcodeScanner = new Html5QrcodeScanner(
            "qr-reader", { fps: 10, qrbox: 250 }
        );
        
        this.state.html5QrcodeScanner.render((decodedText, decodedResult) => {
            document.getElementById('task-input').value = decodedText;
            this.state.html5QrcodeScanner.clear();
            qrReader.style.display = 'none';
            this.loadTask();
        }, (errorMessage) => {
            // parse errors are normal while scanning
        });
    },

    updateTaskUI(status) {
        document.getElementById('td-status').innerText = statusTranslations[status] || status;
        
        const btnStart = document.getElementById('btn-start');
        const btnPause = document.getElementById('btn-pause');
        const btnComplete = document.getElementById('btn-complete');

        btnStart.disabled = status === 'COMPLETED' || status === 'IN_PROGRESS' || status === 'BLOCKED';
        btnPause.disabled = status !== 'IN_PROGRESS';
        btnComplete.disabled = status !== 'IN_PROGRESS';

        const btnMissing = document.getElementById('btn-missing');
        const btnResolveMaterials = document.getElementById('btn-resolve-materials');
        const btnBlock = document.getElementById('btn-block');
        const btnUnblock = document.getElementById('btn-unblock');
        const btnRework = document.getElementById('btn-rework');
        
        if(btnMissing && btnBlock && btnUnblock && btnResolveMaterials) {
            btnMissing.style.display = status === 'COMPLETED' ? 'none' : 'inline-block';
            btnResolveMaterials.style.display = (status !== 'COMPLETED' && this.state.currentTask.missingMaterials) ? 'inline-block' : 'none';
            btnBlock.style.display = (status === 'COMPLETED' || status === 'BLOCKED') ? 'none' : 'inline-block';
            btnUnblock.style.display = status === 'BLOCKED' ? 'inline-block' : 'none';
        }
        if(btnRework) {
            btnRework.style.display = status === 'COMPLETED' ? 'inline-block' : 'none';
        }
    },

    async handleTaskAction(action) {
        if (!this.state.currentTask) return;

        let btnId = `btn-${action}`;
        if (action === 'missing-materials') btnId = 'btn-missing';
        if (action === 'materials-resolved') btnId = 'btn-resolve-materials';
        const btn = document.getElementById(btnId);
        
        const originalText = btn ? btn.innerHTML : '';

        try {
            const bodyPayload = { workerId: getWorkerId() };
            if (action === 'simulate-time') {
                bodyPayload.seconds = "3600";
            }
            if (action === 'missing-materials') {
                const matId = document.getElementById('material-select').value;
                if (!matId) {
                    showToast("Будь ласка, оберіть матеріал зі списку.", 'warning');
                    return;
                }
                bodyPayload.materialId = matId;
            }
            
            if (btn) {
                btn.classList.add('loading');
                btn.innerHTML = `<span style="display:inline-block; animation:spin 1s linear infinite;">⏳</span> Завантаження...`;
            }

            const response = await fetch(`${API_BASE_URL}/tasks/${this.state.currentTask.id}/${action}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(bodyPayload)
            });

            if (!response.ok) {
                const text = await response.text();
                throw new Error(text || 'Помилка виконання дії');
            }

            const updatedTask = await response.json();
            this.state.currentTask = updatedTask;
            
            if (action === 'complete') {
                showToast(`🎉 Етап успішно завершено! Виріб ${updatedTask.productInstance ? updatedTask.productInstance.serialNumber : ''} передано на наступний етап.`, 'success');
                document.getElementById('task-details').classList.add('hidden');
                document.getElementById('task-input').value = '';
                this.state.currentTask = null;
                this.loadWorkerTasks();
            } else {
                this.updateTaskUI(updatedTask.status);
                this.loadWorkerTasks();
                showToast('Дію успішно виконано', 'success');
            }
            
        } catch (error) {
            console.error(error);
            showToast(`Помилка: ${error.message}. (Переконайтеся, що бекенд працює)`, 'error');
        } finally {
            if (btn) {
                btn.classList.remove('loading');
                btn.innerHTML = originalText;
            }
            
            if (this.state.currentTask) {
                this.updateTaskUI(this.state.currentTask.status);
            }
        }
    },

    startTask() { this.handleTaskAction('start'); },
    pauseTask() { this.handleTaskAction('pause'); },
    completeTask() { this.handleTaskAction('complete'); },
    blockTask() { this.handleTaskAction('block'); },
    unblockTask() { this.handleTaskAction('unblock'); },
    reportMissingMaterials() { this.handleTaskAction('missing-materials'); },
    resolveMissingMaterials() { this.handleTaskAction('materials-resolved'); },
    simulateTime() { this.handleTaskAction('simulate-time'); },
    reworkTask() { this.handleTaskAction('rework'); },

    async loadDashboard() {
        try {
            const response = await fetch(`${API_BASE_URL}/dashboard`);
            if (!response.ok) throw new Error('Failed to fetch dashboard data');
            const data = await response.json();
            
            const liveIndicator = document.getElementById('live-indicator');
            if (liveIndicator) liveIndicator.style.display = 'flex';
            
            document.getElementById('stat-active-products').innerText = data.activeProductsCount;
            document.getElementById('stat-total-hours').innerText = data.totalHoursSpent.toFixed(1);
            document.getElementById('stat-bottleneck').innerText = data.bottleneckStage || 'Немає';
            
            document.getElementById('issue-blocked').innerText = data.flaggedIssues.blockedTasksCount;
            document.getElementById('issue-materials').innerText = data.flaggedIssues.missingMaterialsCount;
            
            const materialsDetailsList = document.getElementById('issue-materials-details');
            materialsDetailsList.innerHTML = '';
            if (data.missingMaterialsDetails && data.missingMaterialsDetails.length > 0) {
                data.missingMaterialsDetails.forEach(detail => {
                    const mats = detail.materials.join(', ') || 'Не вказано';
                    materialsDetailsList.innerHTML += `<li>Завдання ${detail.taskId.substring(0,8)}... (Етап: ${detail.stage}): ${mats}</li>`;
                });
            }

            document.getElementById('issue-overdue').innerText = data.flaggedIssues.overdueTasksCount;

            const tbody = document.getElementById('active-tasks-body');
            tbody.innerHTML = '';
            
            if (data.activeTasks.length === 0) {
                tbody.innerHTML = `<tr><td colspan="3" style="text-align: center">Немає активних завдань</td></tr>`;
            } else {
                data.activeTasks.forEach(task => {
                    tbody.innerHTML += `
                        <tr>
                            <td><strong>${task.productSerialNumber}</strong></td>
                            <td>${task.stage}</td>
                            <td>${task.worker}</td>
                        </tr>
                    `;
                });
            }

            const historyBody = document.getElementById('recent-history-body');
            if (historyBody) {
                historyBody.innerHTML = '';
                if (!data.recentHistory || data.recentHistory.length === 0) {
                    historyBody.innerHTML = `<tr><td colspan="5" style="text-align: center">Історія порожня</td></tr>`;
                } else {
                    data.recentHistory.forEach(item => {
                        const date = new Date(item.timestamp);
                        const timeStr = date.toLocaleString('uk-UA', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute:'2-digit' });
                        historyBody.innerHTML += `
                            <tr>
                                <td>${timeStr}</td>
                                <td><span class="badge ${item.action.includes('Завершено') ? 'badge-success' : 'badge-warning'}">${item.action}</span></td>
                                <td><strong>${item.taskSerial}</strong></td>
                                <td>${item.stage}</td>
                                <td>${item.worker}</td>
                            </tr>
                        `;
                    });
                }
            }
            
        } catch (error) {
            console.error(error);
            console.log("Не вдалося завантажити дані дашборда.");
        }
    },

    async loadWorkersForSelect() {
        try {
            const res = await fetch(`${API_BASE_URL}/workers`);
            if (!res.ok) return;
            const workers = await res.json();
            
            const workerSelect = document.getElementById('worker-select');
            const newProdWorker = document.getElementById('new-prod-worker');
            
            // Keep selection if exists
            const currentSelection = workerSelect.value;
            
            workerSelect.innerHTML = '';
            newProdWorker.innerHTML = '<option value="">Не призначати (автоматично)</option>';
            
            workers.forEach(w => {
                const optStr = `<option value="${w.id}">${w.name} (${w.role || 'Працівник'})</option>`;
                workerSelect.innerHTML += optStr;
                newProdWorker.innerHTML += optStr;
            });
            
            if (currentSelection) {
                workerSelect.value = currentSelection;
            }
        } catch (err) {
            console.error(err);
        }
    },
    
    async loadModelsForSelect() {
        try {
            const res = await fetch(`${API_BASE_URL}/products/models`);
            if (!res.ok) return;
            const models = await res.json();
            
            const select = document.getElementById('new-prod-model');
            select.innerHTML = '<option value="">Оберіть модель...</option>';
            
            models.forEach(m => {
                select.innerHTML += `<option value="${m.id}">${m.name}</option>`;
            });
        } catch (err) {
            console.error(err);
        }
    },

    async loadWorkerTasks() {
        const workerId = getWorkerId();
        if (!workerId) return;
        
        try {
            const res = await fetch(`${API_BASE_URL}/tasks/worker/${workerId}`);
            if (!res.ok) return;
            const tasks = await res.json();
            
            const container = document.getElementById('worker-tasks-container');
            container.innerHTML = '';
            
            if (tasks.length === 0) {
                container.innerHTML = '<p style="color: var(--text-muted); font-size: 0.9em;">Немає активних завдань</p>';
                return;
            }
            
            tasks.forEach(t => {
                const sn = t.productInstance ? t.productInstance.serialNumber : 'Невідомо';
                const st = t.stage ? t.stage.name : 'Невідомо';
                const statusStr = statusTranslations[t.status] || t.status;
                const div = document.createElement('div');
                div.className = 'task-item';
                div.style = 'padding: 0.5rem; background: rgba(0,0,0,0.2); border: 1px solid var(--glass-border); border-radius: 8px; cursor: pointer; transition: background 0.2s;';
                div.onmouseover = () => div.style.background = 'rgba(255,255,255,0.1)';
                div.onmouseout = () => div.style.background = 'rgba(0,0,0,0.2)';
                div.innerHTML = `<strong>SN: ${sn}</strong> - ${st} <span style="float: right; font-size: 0.8em; color: var(--accent-color);">${statusStr}</span>`;
                div.onclick = () => {
                    document.getElementById('task-input').value = t.id;
                    app.loadTask();
                };
                container.appendChild(div);
            });
        } catch (err) {
            console.error(err);
        }
    },

    async registerWorker() {
        const name = document.getElementById('new-worker-name').value.trim();
        const role = document.getElementById('new-worker-role').value.trim();
        if (!name) return showToast('Ім`я працівника обов`язкове', 'warning');
        
        try {
            const res = await fetch(`${API_BASE_URL}/workers`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, role })
            });
            if (res.ok) {
                showToast('Працівника успішно додано!', 'success');
                document.getElementById('new-worker-name').value = '';
                document.getElementById('new-worker-role').value = '';
                this.loadWorkersForSelect();
            } else {
                showToast('Помилка при додаванні працівника', 'error');
            }
        } catch (err) {
            console.error(err);
            showToast('Помилка з\'єднання', 'error');
        }
    },

    async startProduction() {
        const modelId = document.getElementById('new-prod-model').value;
        const serialNumber = document.getElementById('new-prod-sn').value.trim();
        const workerId = document.getElementById('new-prod-worker').value;
        
        if (!modelId || !serialNumber) {
            showToast('Будь ласка, оберіть модель і введіть серійний номер', 'warning');
            return;
        }
        
        try {
            const res = await fetch(`${API_BASE_URL}/products/start`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ modelId, serialNumber, workerId })
            });
            
            if (res.ok) {
                showToast('Виріб запущено у виробництво!', 'success');
                
                // Show QR Code
                const qrModal = document.getElementById('qr-modal');
                const qrContainer = document.getElementById('qr-modal-code');
                const snEl = document.getElementById('qr-modal-sn');
                
                qrContainer.innerHTML = '';
                snEl.innerText = serialNumber;
                
                new QRCode(qrContainer, {
                    text: serialNumber,
                    width: 200,
                    height: 200,
                    colorDark : "#000000",
                    colorLight : "#ffffff",
                    correctLevel : QRCode.CorrectLevel.H
                });
                
                qrModal.style.display = 'flex';
                
                document.getElementById('new-prod-sn').value = '';
                this.loadDashboard();
            } else {
                showToast('Помилка при створенні виробу', 'error');
            }
        } catch (err) {
            console.error(err);
            showToast('Помилка з\'єднання', 'error');
        }
    },

    async loadProblemTasks(type) {
        try {
            const table = document.getElementById('problem-tasks-table');
            const tbody = document.getElementById('problem-tasks-body');

            if (table.dataset.currentType === type && table.style.display === 'table') {
                table.style.display = 'none';
                return;
            }

            const res = await fetch(`${API_BASE_URL}/tasks/${type}`);
            if (!res.ok) return;
            const tasks = await res.json();
            
            tbody.innerHTML = '';
            table.dataset.currentType = type;
            table.style.display = 'table';
            
            if (tasks.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3" style="text-align: center;">Завдань не знайдено</td></tr>';
                return;
            }
            
            tasks.forEach(t => {
                const sn = t.productInstance ? t.productInstance.serialNumber : 'Невідомо';
                const st = t.stage ? t.stage.name : 'Невідомо';
                const statusStr = statusTranslations[t.status] || t.status;
                tbody.innerHTML += `
                    <tr>
                        <td><strong>${sn}</strong></td>
                        <td>${st}</td>
                        <td><span class="status-badge">${statusStr}</span></td>
                    </tr>
                `;
            });
        } catch (err) {
            console.error(err);
        }
    },

    toggleMissingMaterials() {
        const details = document.getElementById('issue-materials-details');
        const chevron = document.getElementById('issue-materials-chevron');
        if (details) details.classList.toggle('expanded');
        if (chevron) {
            chevron.style.transform = details.classList.contains('expanded') ? 'rotate(90deg)' : 'rotate(0deg)';
        }
    },

    async createProductModel() {
        const btn = document.getElementById('btn-create-model');
        const nameInput = document.getElementById('new-model-name');
        const descInput = document.getElementById('new-model-desc');
        const name = nameInput.value.trim();
        const description = descInput.value.trim();

        nameInput.style.borderColor = '';

        if (!name) {
            nameInput.style.borderColor = 'var(--danger)';
            showToast('Введіть назву моделі', 'warning');
            return;
        }

        if (btn) {
            btn.classList.add('loading');
            btn.innerHTML = `<span style="display:inline-block; animation:spin 1s linear infinite;">⏳</span> Створення...`;
        }

        try {
            const res = await fetch(`${API_BASE_URL}/models`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, description })
            });

            if (!res.ok) throw new Error('Помилка при створенні моделі');
            
            showToast('Модель успішно створено', 'success');
            nameInput.value = '';
            descInput.value = '';
            
            // Reload models dropdown
            this.loadModelsForSelect();
        } catch (err) {
            showToast(err.message, 'error');
        } finally {
            if (btn) {
                btn.classList.remove('loading');
                btn.innerText = 'Створити модель';
            }
        }
    }
};

document.addEventListener('DOMContentLoaded', () => {
    app.loadWorkersForSelect().then(() => app.loadWorkerTasks());
});
