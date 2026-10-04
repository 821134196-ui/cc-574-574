// 简单 API 客户端：所有请求带当前登录人 X-User-Id
let currentUserId = localStorage.getItem('userId') || '';

export function setCurrentUserId(id) {
  currentUserId = String(id || '');
  localStorage.setItem('userId', currentUserId);
}

export function getCurrentUserId() {
  return currentUserId;
}

async function request(method, path, body) {
  const headers = { 'X-User-Id': currentUserId };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!res.ok) {
    const msg = data && data.error ? data.error : `请求失败 (${res.status})`;
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, b) => request('POST', p, b ?? {}),
  put: (p, b) => request('PUT', p, b ?? {})
};

// 扫描件下载（二进制，带授权头）
export function scanUrl(contractId) {
  return `/api/contracts/${contractId}/scan?_uid=${currentUserId}`;
}

export async function fetchScanBlob(contractId) {
  const res = await fetch(`/api/contracts/${contractId}/scan`, {
    headers: { 'X-User-Id': currentUserId }
  });
  if (!res.ok) {
    const j = await res.json().catch(() => ({}));
    throw new Error(j.error || `无法查看扫描件 (${res.status})`);
  }
  return res.blob();
}
