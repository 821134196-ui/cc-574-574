<script>
  import { onMount } from 'svelte';
  import { api, getUserId, setUserId } from './lib/api.js';
  import { currentUser, users, posts, toast } from './lib/store.js';
  import Contracts from './pages/Contracts.svelte';
  import Loans from './pages/Loans.svelte';
  import ReturnCheck from './pages/ReturnCheck.svelte';
  import Anomalies from './pages/Anomalies.svelte';
  import Permissions from './pages/Permissions.svelte';
  import Audit from './pages/Audit.svelte';

  const tabs = [
    { key: 'contracts', label: '合同台账', roles: ['EMPLOYEE', 'ARCHIVIST', 'ADMIN'] },
    { key: 'loans', label: '借阅管理', roles: ['EMPLOYEE', 'ARCHIVIST', 'ADMIN'] },
    { key: 'return', label: '归还核验', roles: ['ARCHIVIST', 'ADMIN'] },
    { key: 'anomaly', label: '异常处理', roles: ['EMPLOYEE', 'ARCHIVIST', 'ADMIN'] },
    { key: 'perm', label: '权限管理', roles: ['ADMIN'] },
    { key: 'audit', label: '顺序审计', roles: ['ARCHIVIST', 'ADMIN'] }
  ];
  let tab = 'contracts';

  let me = null;
  let notifs = { unread: 0, items: [] };
  let showBell = false;

  $: visibleTabs = tabs.filter(t => !me || t.roles.includes(me.role));

  async function loadBase() {
    try {
      const [{ user }, ul, pl] = await Promise.all([
        api('/me'), api('/users').catch(() => []), api('/posts')
      ]);
      me = user;
      currentUser.set(user);
      users.set(ul);
      posts.set(pl);
    } catch (e) {
      toast.err('初始化失败：' + e.message);
    }
  }

  async function loadNotifs() {
    try { notifs = await api('/notifications'); } catch { /* ignore */ }
  }

  async function switchUser(e) {
    setUserId(e.target.value);
    showBell = false;
    await loadBase();
    await loadNotifs();
    toast.ok('已切换身份');
  }

  async function readAll() {
    await api('/notifications/read-all', { method: 'POST' });
    await loadNotifs();
  }

  function typeBadge(type) {
    return { OVERDUE: 'red', DUE_SOON: 'orange', GATE: 'blue', RECHECK: 'orange', ANOMALY: 'red' }[type] || 'gray';
  }

  onMount(() => {
    loadBase();
    loadNotifs();
    const t = setInterval(loadNotifs, 15000);
    return () => clearInterval(t);
  });
</script>

<style global src="./app.css"></style>

<div class="topbar">
  <div class="logo">📋 合同原件借阅与归还核验系统</div>
  <nav>
    {#each visibleTabs as t}
      <button class={tab === t.key ? 'active' : ''} on:click={() => tab = t.key}>{t.label}</button>
    {/each}
  </nav>
  <div class="spacer"></div>

  <div style="position:relative">
    <button class="bell" on:click={() => showBell = !showBell} title="提醒中心（本地模拟）">
      🔔{#if notifs.unread > 0}<span class="dot">{notifs.unread}</span>{/if}
    </button>
    {#if showBell}
      <div class="notif-panel">
        <div class="row" style="justify-content:space-between;padding:4px 8px 8px">
          <b>提醒中心</b>
          <button class="btn sm ghost" on:click={readAll}>全部已读</button>
        </div>
        {#if notifs.items.length === 0}
          <div class="muted small" style="padding:12px">暂无提醒</div>
        {/if}
        {#each notifs.items as n}
          <div class="notif-item">
            <div class="t"><span class="badge {typeBadge(n.type)}">{n.typeLabel}</span> {n.title}
              {#if !n.read}<span style="color:#f04438">●</span>{/if}
            </div>
            <div class="m">{n.message}</div>
            <div class="muted small">{n.createdAt}</div>
          </div>
        {/each}
      </div>
    {/if}
  </div>

  <div class="identity">
    <span>当前身份</span>
    <select value={getUserId()} on:change={switchUser}>
      {#each $users as u}
        <option value={u.id}>{u.name}｜{u.roleLabel}｜{u.post.name}</option>
      {/each}
    </select>
  </div>
</div>

<div class="wrap">
  {#if me}
    <div class="alert info" style="margin-top:0">
      当前登录：<b>{me.name}</b>（{me.roleLabel}，岗位「{me.post.name}」，密级许可 {me.post.clearance} 级）
      —— 身份仅为演示模拟，通过顶部下拉切换。
    </div>
  {/if}

  {#if tab === 'contracts'}<Contracts />
  {:else if tab === 'loans'}<Loans />
  {:else if tab === 'return'}<ReturnCheck />
  {:else if tab === 'anomaly'}<Anomalies />
  {:else if tab === 'perm'}<Permissions />
  {:else if tab === 'audit'}<Audit />
  {/if}
</div>

<div class="toasts">
  {#each $toast as t}
    <div class="toast {t.type}">{t.text}</div>
  {/each}
</div>
