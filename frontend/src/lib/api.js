// 接口封装：开发态通过 X-User-Id 模拟登录身份
export const getUserId = () => localStorage.getItem('cloans.userId') || '3';
export const setUserId = (id) => localStorage.setItem('cloans.userId', String(id));

export async function api(path, opts = {}) {
  const headers = { 'X-User-Id': getUserId(), ...(opts.headers || {}) };
  if (opts.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.json);
    delete opts.json;
  }
  const res = await fetch('/api' + path, { headers, ...opts });
  const ct = res.headers.get('content-type') || '';
  if (!res.ok) {
    let msg = '请求失败 HTTP ' + res.status;
    if (ct.includes('json')) {
      try { const j = await res.json(); msg = j.message || msg; } catch { /* ignore */ }
    }
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return ct.includes('json') ? res.json() : res;
}

export const post = (path, json) => api(path, { method: 'POST', json });

// 扫描件带鉴权头查看：取 blob 后在新窗口打开
export async function viewScan(contractId) {
  const res = await fetch('/api/contracts/' + contractId + '/scan', {
    headers: { 'X-User-Id': getUserId() }
  });
  if (!res.ok) {
    let msg = '无权查看';
    try { msg = (await res.json()).message; } catch { /* ignore */ }
    throw new Error(msg);
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank');
}

export async function uploadScan(contractId, file) {
  const buf = await file.arrayBuffer();
  return api('/contracts/' + contractId + '/scan', {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream', 'X-Filename': encodeURIComponent(file.name) },
    body: buf
  });
}

export function todayStr() {
  // 与后端 LocalDate 对齐（本地日期）
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function daysUntil(dateStr) {
  if (!dateStr) return null;
  const t = new Date(todayStr() + 'T00:00:00');
  const d = new Date(dateStr + 'T00:00:00');
  return Math.round((d - t) / 86400000);
}
