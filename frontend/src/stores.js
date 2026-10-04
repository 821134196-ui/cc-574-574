import { writable } from 'svelte/store';

export const toasts = writable([]);
let seq = 0;

export function pushToast(message, type = 'info', ms = 4200) {
  const id = ++seq;
  toasts.update((list) => [...list, { id, message, type }]);
  setTimeout(() => {
    toasts.update((list) => list.filter((t) => t.id !== id));
  }, ms);
}

export const ok = (m) => pushToast(m, 'ok');
export const fail = (m) => pushToast(m, 'err', 6000);

export const SECRECY_LABEL = {
  PUBLIC: '公开', INTERNAL: '内部', CONFIDENTIAL: '秘密', SECRET: '机密'
};
export const ROLE_LABEL = { EMPLOYEE: '员工', ARCHIVIST: '档案员', ADMIN: '管理员' };

export function todayPlus(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function fmtDate(d) {
  return d || '—';
}

export function fmtInstant(t) {
  if (!t) return '—';
  return new Date(t).toLocaleString('zh-CN', { hour12: false });
}

export function dueClass(loan) {
  if (loan.status === 'RETURNED') return '';
  if (loan.overdue) return 'overdue-text';
  const days = Math.round((new Date(loan.dueDate) - new Date()) / 86400000);
  return days <= 1 ? 'due-soon' : '';
}
