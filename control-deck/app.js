const stages = [
  ['01','Prompt'],['02','AppSpec'],['03','Validator'],['04','ProjectPlanner'],
  ['05','Compose Generator'],['06','Gradle Build'],['07','Repair Loop'],['08','APK']
];

const $ = (id) => document.getElementById(id);
const log = (message, data) => {
  const stamp = new Date().toLocaleTimeString();
  const payload = data === undefined ? '' : `\n${JSON.stringify(data, null, 2)}`;
  $('log').textContent = `[${stamp}] ${message}${payload}\n\n${$('log').textContent}`;
};

function renderPipeline() {
  $('pipeline').innerHTML = stages.map(([num,name], index) => `
    <div class="stage" data-state="${index < 5 ? 'real' : 'live'}">
      <div class="num">${num}</div>
      <div class="name">${name}</div>
      <div class="status">${index < 5 ? 'CORE PRESENT' : 'RUNTIME STATUS'}</div>
    </div>`).join('');
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {'Content-Type':'application/json', ...(options.headers || {})},
  });
  const text = await response.text();
  let body;
  try { body = JSON.parse(text); } catch { body = {ok:false, raw:text}; }
  if (!response.ok) throw Object.assign(new Error(body.error || `HTTP ${response.status}`), {body, status:response.status});
  return body;
}

async function refreshHealth() {
  try {
    const health = await api('/api/health');
    $('sidecarStatus').textContent = `SIDECAR · ${health.ok ? 'ONLINE' : 'OFFLINE'}`;
    $('bridgeStatus').textContent = health.sessionActive ? 'BRIDGE · SESSION' : 'BRIDGE · LOCAL';
    log('Sidecar health', health);
  } catch (error) {
    $('sidecarStatus').textContent = 'SIDECAR · OFFLINE';
    log('Sidecar health failed', error.body || error.message);
  }
}

async function refreshState() {
  try {
    const state = await api('/api/state');
    $('masterState').textContent = state.masterEnabled === true ? 'enabled' : state.masterEnabled === false ? 'disabled' : 'unknown';
    $('bridgeAddress').textContent = state.bridge || '127.0.0.1:8765';
    $('presenceState').textContent = state.presenceState || 'unknown';
    log('Real X-88 state', state);
  } catch (error) {
    $('masterState').textContent = 'unavailable';
    log('State unavailable', error.body || error.message);
  }

  try {
    const apk = await api('/api/apk/status');
    $('apkState').textContent = apk.ok === false ? (apk.error || 'unavailable') : 'reachable';
    log('APK status', apk);
  } catch (error) {
    $('apkState').textContent = 'unavailable';
    log('APK status unavailable', error.body || error.message);
  }
}

$('pairForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const result = await api('/api/pair', {method:'POST', body:JSON.stringify({pairingCode:$('pairingCode').value.trim()})});
    $('pairResult').textContent = result.ok ? `Session aktiv bis ${new Date(result.expiresAtMs).toLocaleTimeString()}` : 'Pairing fehlgeschlagen';
    log('Dialogue session paired', {ok:result.ok, expiresAtMs:result.expiresAtMs});
    await refreshHealth();
  } catch (error) {
    $('pairResult').textContent = `Pairing fehlgeschlagen: ${error.body?.error || error.message}`;
    log('Pairing failed', error.body || error.message);
  }
});

$('dialogueForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const result = await api('/api/dialogue', {method:'POST', body:JSON.stringify({text:$('dialogueText').value.trim()})});
    $('dialogueResult').textContent = result.reply || JSON.stringify(result, null, 2);
    log('Dialogue result', {ok:result.ok, providerId:result.providerId});
  } catch (error) {
    $('dialogueResult').textContent = JSON.stringify(error.body || {error:error.message}, null, 2);
    log('Dialogue failed', error.body || error.message);
  }
});

$('actionForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const result = await api('/api/action/proposal', {method:'POST', body:JSON.stringify({action:$('actionName').value.trim()})});
    $('actionResult').textContent = JSON.stringify(result, null, 2);
    log('Action proposal', result);
  } catch (error) {
    $('actionResult').textContent = JSON.stringify(error.body || {error:error.message}, null, 2);
    log('Action proposal failed', error.body || error.message);
  }
});

$('refreshButton').addEventListener('click', refreshState);
renderPipeline();
refreshHealth();
refreshState();
setInterval(refreshHealth, 15000);
