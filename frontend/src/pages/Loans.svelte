<script>
  import { onMount } from 'svelte';
  import { api, post, daysUntil } from '../lib/api.js';
  import { currentUser, refreshTick, bump, toast } from '../lib/store.js';
  import StatusBadge from '../lib/StatusBadge.svelte';
  import Modal from '../lib/Modal.svelte';

  let me = $currentUser;
  let loans = [];
  let renewals = [];
  let scope = 'mine';
  let detail = null;
  let detailTrace = [];
  let rejectTarget = null;
  let renewTarget = null;
  let remindTarget = null;
  let rejectReason = '';
  let renewDays = 5;
  let remindMsg = '';
  let busy = false;

  async function load() {
    me = $currentUser;
    if (me && me.role === 'EMPLOYEE') scope = 'mine';
    const [ls, rs] = await Promise.all([
      api('/loans?scope=' + scope + '&active=true'),
      api('/renewals?scope=' + (scope === 'all' ? 'all' : 'mine')).catch(() => [])
    ]);
    loans = ls;
    renewals = rs;
  }

  async function approve(l) {
    try { await post(`/loans/${l.id}/approve`); toast.ok('已批准，等待档案员交接'); bump(); }
    catch (e) { toast.err(e.message); }
  }
  async function doReject() {
    busy = true;
    try { await post(`/loans/${rejectTarget.id}/reject`, { reason: rejectReason }); toast.ok('已拒绝'); rejectTarget = null; bump(); }
    catch (e) { toast.err(e.message); } finally { busy = false; }
  }
  async function handover(l) {
    try {
      await post(`/loans/${l.id}/handover`);
      toast.ok('交接完成：已记录实际经手人，门禁出库放行（模拟）');
      bump();
    } catch (e) { toast.err(e.message); }
  }
  async function doRenew() {
    busy = true;
    try {
      await post(`/loans/${renewTarget.id}/renew`, { days: Number(renewDays) });
      toast.ok('续借申请已提交（到期日前申请有效）');
      renewTarget = null; bump();
    } catch (e) { toast.err(e.message); } finally { busy = false; }
  }
  async function decideRenewal(r, approve) {
    try {
      await post(`/renewals/${r.id}/decision`, { approve, note: approve ? '同意续借' : '不同意' });
      toast.ok(approve ? '续借已批准，到期日已顺延' : '已拒绝续借'); bump();
    } catch (e) { toast.err(e.message); }
  }
  async function doRemind() {
    busy = true;
    try {
      await post(`/loans/${remindTarget.id}/remind`, { message: remindMsg });
      toast.ok('催还提醒已发送（本地模拟）');
      remindTarget = null; bump();
    } catch (e) { toast.err(e.message); } finally { busy = false; }
  }
  async function cancel(l) {
    try { await post(`/loans/${l.id}/cancel`); toast.ok('申请已撤回'); bump(); }
    catch (e) { toast.err(e.message); }
  }
  async function sweep() {
    try { const r = await post('/admin/overdue-sweep'); toast.ok(`逾期扫描完成，新标记 ${r.overdueMarked} 笔`); bump(); }
    catch (e) { toast.err(e.message); }
  }
  async function openDetail(l) {
    detail = l;
    detailTrace = (await api('/audit/trace/' + l.id)).trace;
  }

  const pendingApprovals = () => loans.filter(l => l.status === 'PENDING' && (me.role !== 'EMPLOYEE'));
  const pendingRenewals = () => renewals.filter(r => r.status === 'PENDING');
  const isOwn = l => me && l.borrower.id === me.id;

  onMount(load);
  $: $refreshTick, load();
  $: me = $currentUser;
</script>

<div class="panel">
  <h2>借阅管理 <span class="sub">借阅人 · 实际经手档案员 · 到期日集中展示，交接与审批留痕可顺序追查</span></h2>
  <div class="row" style="justify-content:space-between">
    <div class="row">
      {#if me && me.role !== 'EMPLOYEE'}
        <button class="btn sm ghost" class:ghost={scope !== 'mine'} on:click={() => { scope = 'mine'; load(); }}>我经手的</button>
        <button class="btn sm ghost" class:ghost={scope !== 'all'} on:click={() => { scope = 'all'; load(); }}>全部在借</button>
      {/if}
      <button class="btn sm ghost" on:click={sweep}>立即执行逾期扫描</button>
    </div>
    <span class="muted small">定时任务每 60 秒自动扫描临期/逾期</span>
  </div>
</div>

{#if me && me.role !== 'EMPLOYEE' && pendingApprovals().length}
  <div class="panel">
    <h2>待审批申请 <span class="sub">管理人员不能批准本人的借阅申请</span></h2>
    <table>
      <thead><tr><th>借阅人</th><th>合同</th><th>用途</th><th>申请天数</th><th>提交时间</th><th>操作</th></tr></thead>
      <tbody>
        {#each pendingApprovals() as l}
          <tr>
            <td><b>{l.borrower.name}</b><div class="muted small">{l.borrower.post.name}</div></td>
            <td>{l.contractTitle}<div class="muted small mono">{l.contractCode}</div></td>
            <td class="small" style="max-width:280px">{l.purpose}</td>
            <td>{l.requestedDays} 天</td>
            <td class="small muted">{l.requestedAt}</td>
            <td class="row" style="gap:6px">
              {#if isOwn(l)}
                <span class="badge red">禁止自批</span>
              {:else}
                <button class="btn sm" on:click={() => approve(l)}>批准</button>
                <button class="btn sm warn" on:click={() => { rejectTarget = l; rejectReason = ''; }}>拒绝</button>
              {/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

{#if me && me.role !== 'EMPLOYEE' && pendingRenewals().length}
  <div class="panel">
    <h2>待审批续借 <span class="sub">仅到期日前提出的续借有效；审批时再次校验是否已逾期</span></h2>
    <table>
      <thead><tr><th>借阅单</th><th>申请人</th><th>原到期日</th><th>申请续至</th><th>操作</th></tr></thead>
      <tbody>
        {#each pendingRenewals() as r}
          <tr>
            <td class="mono small">#{r.loanId} {r.contractTitle || ''}</td>
            <td>{r.requestedBy}</td>
            <td><span class="mono">{r.oldDueDate}</span></td>
            <td><b class="mono">{r.requestedDueDate}</b></td>
            <td class="row" style="gap:6px">
              <button class="btn sm" on:click={() => decideRenewal(r, true)}>批准续借</button>
              <button class="btn sm warn" on:click={() => decideRenewal(r, false)}>拒绝</button>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

<div class="panel" style="padding:6px 8px">
  <h2 style="padding:10px 10px 0">
    {scope === 'all' ? '全部在册借阅' : '我的借阅'}
  </h2>
  <table>
    <thead>
      <tr>
        <th>#</th><th>合同</th><th>借阅人</th><th>经手档案员</th><th>到期日</th><th>状态</th><th>操作</th>
      </tr>
    </thead>
    <tbody>
      {#each loans as l}
        <tr>
          <td class="mono small">#{l.id}</td>
          <td style="min-width:170px">
            <b>{l.contractTitle}</b>
            <div class="muted small mono">{l.contractCode} · 盒位 {l.boxLocation}</div>
            {#if l.anomalyOpen}<span class="badge red">归还异常未闭环</span>{/if}
            {#if l.recheckPending}<span class="badge orange">权限重检待处理</span>{/if}
          </td>
          <td><b>{l.borrower.name}</b><div class="muted small">{l.borrower.post.name}（许可{l.borrower.post.clearance}级）</div></td>
          <td>
            {#if l.archivist}{l.archivist.name}
              <div class="muted small">{l.handoverAt}</div>
            {:else}<span class="muted small">待交接</span>{/if}
          </td>
          <td>
            {#if l.dueDate}
              <b class="mono">{l.dueDate}</b>
              {#if l.status === 'PICKED_UP'}
                {#if daysUntil(l.dueDate) < 0}<div><span class="badge red">已逾期</span></div>
                {:else if daysUntil(l.dueDate) <= 2}<div><span class="badge orange">{daysUntil(l.dueDate)} 天后到期</span></div>
                {:else}<div class="muted small">剩余 {daysUntil(l.dueDate)} 天</div>{/if}
              {/if}
              {#if l.status === 'OVERDUE'}<div><span class="badge red">逾期 {-daysUntil(l.dueDate)} 天</span></div>{/if}
            {:else}<span class="muted small">批准后确定</span>{/if}
          </td>
          <td><StatusBadge status={l.status} /></td>
          <td>
            <div class="row" style="gap:6px">
              <button class="btn sm ghost" on:click={() => openDetail(l)}>链路</button>
              {#if l.status === 'PENDING'}
                {#if isOwn(l)}<button class="btn sm ghost" on:click={() => cancel(l)}>撤回</button>{/if}
              {/if}
              {#if l.status === 'APPROVED' && me.role === 'ARCHIVIST'}
                <button class="btn sm" on:click={() => handover(l)}>登记交接出库</button>
              {/if}
              {#if l.status === 'PICKED_UP' && isOwn(l)}
                <button class="btn sm ghost"
                        disabled={daysUntil(l.dueDate) < 0}
                        title={daysUntil(l.dueDate) < 0 ? '已逾期，不能续借' : '到期日前可续借'}
                        on:click={() => { renewTarget = l; renewDays = 5; }}>
                  {daysUntil(l.dueDate) < 0 ? '逾期不可续借' : '申请续借'}
                </button>
              {/if}
              {#if (l.status === 'OVERDUE' || l.status === 'REVOKED') && me.role === 'ADMIN'}
                <button class="btn sm danger" on:click={() => { remindTarget = l; remindMsg = ''; }}>催还跟进</button>
              {/if}
            </div>
          </td>
        </tr>
      {/each}
      {#if loans.length === 0}
        <tr><td colspan="7" class="muted" style="text-align:center;padding:26px">暂无在册借阅</td></tr>
      {/if}
    </tbody>
  </table>
</div>

{#if rejectTarget}
  <Modal title={`拒绝借阅申请 #${rejectTarget.id}`} on:close={() => rejectTarget = null}>
    <label class="field"><span>拒绝原因</span><textarea bind:value={rejectReason} /></label>
    <div class="row" style="justify-content:flex-end">
      <button class="btn ghost" on:click={() => rejectTarget = null}>取消</button>
      <button class="btn danger" disabled={busy} on:click={doReject}>确认拒绝</button>
    </div>
  </Modal>
{/if}

{#if renewTarget}
  <Modal title={`申请续借 #${renewTarget.id}`} on:close={() => renewTarget = null}>
    <div class="alert warn small">当前到期日 <b class="mono">{renewTarget.dueDate}</b>。续借必须在到期日前申请，逾期单据无法续借。</div>
    <label class="field"><span>续借天数（在原到期日上顺延）</span>
      <input type="number" min="1" max="15" bind:value={renewDays} />
    </label>
    <div class="row" style="justify-content:flex-end">
      <button class="btn ghost" on:click={() => renewTarget = null}>取消</button>
      <button class="btn" disabled={busy} on:click={doRenew}>提交续借申请</button>
    </div>
  </Modal>
{/if}

{#if remindTarget}
  <Modal title={`催还跟进 #${remindTarget.id}`} on:close={() => remindTarget = null}>
    <p class="small">借阅人 <b>{remindTarget.borrower.name}</b>，合同《{remindTarget.contractTitle}》，
      到期日 <b class="mono">{remindTarget.dueDate}</b>，状态 <StatusBadge status={remindTarget.status} /></p>
    <label class="field"><span>催还留言（通过本地模拟提醒中心发送）</span>
      <textarea bind:value={remindMsg} placeholder="请立即归还纸质原件并到档案室办理核验" />
    </label>
    <div class="row" style="justify-content:flex-end">
      <button class="btn ghost" on:click={() => remindTarget = null}>取消</button>
      <button class="btn danger" disabled={busy} on:click={doRemind}>发送催还</button>
    </div>
  </Modal>
{/if}

{#if detail}
  <Modal title={`借阅单 #${detail.id} 交接核验链路`} wide on:close={() => detail = null}>
    <div class="kvline">
      <span><b>合同</b>{detail.contractTitle}（{detail.contractCode}）</span>
      <span><b>借阅人</b>{detail.borrower.name}</span>
      <span><b>经手档案员</b>{detail.archivist ? detail.archivist.name : '待交接'}</span>
      <span><b>到期日</b><span class="mono">{detail.dueDate}</span></span>
      <span><b>状态</b><StatusBadge status={detail.status} /></span>
    </div>
    <div class="muted small" style="margin:4px 0 10px">用途：{detail.purpose}</div>
    <ul class="timeline">
      {#each detailTrace as a}
        <li>
          <div class="ts">{a.ts} · {a.actor}{#if a.stateChange} <span class="sc">{a.stateChange}</span>{/if}</div>
          <div class="act">{a.action}</div>
          {#if a.detail}<div class="dt">{a.detail}</div>{/if}
        </li>
      {/each}
    </ul>
  </Modal>
{/if}
