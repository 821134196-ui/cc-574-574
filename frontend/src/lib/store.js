import { writable } from 'svelte/store';

export const currentUser = writable(null);
export const users = writable([]);
export const posts = writable([]);
// 全局刷新信号：任意页面完成操作后 bump，其他页重新拉取
export const refreshTick = writable(0);
export const bump = () => refreshTick.update(n => n + 1);

function createToast() {
  const { subscribe, update } = writable([]);
  let seq = 0;
  return {
    subscribe,
    push(type, text) {
      const id = ++seq;
      update(list => [...list, { id, type, text }]);
      setTimeout(() => update(list => list.filter(t => t.id !== id)), 4200);
    },
    ok(text) { this.push('ok', text); },
    err(text) { this.push('err', text); }
  };
}
export const toast = createToast();
