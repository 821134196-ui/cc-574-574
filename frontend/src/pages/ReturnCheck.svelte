<script>
  import { onMount } from 'svelte';
  import { api, post, daysUntil } from '../lib/api.js';
  import { currentUser, refreshTick, bump, toast } from '../lib/store.js';
  import StatusBadge from '../lib/StatusBadge.svelte';

  let me = $currentUser;
  let loans = [];
  let selectedId = '';
  let loan = null;
  let pages = [];
  let sealSet = new Set();
  let result = null;
  let history = [];
  let busy = false;

  async function loadLoans() {
    me = $currentUser;
    loans = await api('/loans?scope=all');
    // 只保留持有原件、可办理归还的单据
    loans = loans.filter(l => ['APPROVED', 'PICKED_UP', 'OVERDUE', 'REVOKED'].includes(l.status));
    if (selectedId && !loans.find(l => String(l.id) === selectedId)) selectedId = '';
    if (!selectedId && loans.length) selectedId = String(loans[0].id);
    if (selectedId) await loadDetail();
  }

  async function loadDetail() {
    result = null;
    loan = await api('/loans/' + selectedId);
    sealSet = new Set((loan.sealPages || '').split(',').map(s => Number(s.trim())).filter(Boolean));
    pages = Array.from({ length: loan.totalPages }, (_, i) => ({
      pageNo: i + 1, status: 'OK', note: ''
    }));
    history = await api(`/loans/${loan.id}/page-checks`).catch(() => []);
  }

  $: if (selectedId) {/* 触发由 onchange 显式调用 */}

  function setAll(status) {
    pages = pages.map(p => ({ ...p, status }));
  }

  const issueCount = () => pages.filter(p => p.status !== 'OK').length;

  async function submit() {
    busy = true;
    try {
      const res = await post('/return-check', { loanId: Number(selectedId), pages });
      result = res;
      if (res.blockedReturn) toast.err('发现缺页/损坏：已进入异常处理，不能签为已还');
      else toast.ok('已签为已还，原件回库');
      await loadDetail();
      bump();
    } catch (e) { toast.err(e.message); }
    finally { busy = false; }
  }

  onMount(loadLoans);
  $: $refreshTick, loadLoans();
</script>

<div class="panel">
  <h2>归还核验 <span class="sub">档案员逐页核对附件与盖章页；缺页/损坏进入异常处理，系统禁止直接签为已还</span></h2>
  <div class="row">
    <label class="field" style="margin:0;min-width:420px">
      <span>选择归还中的借阅单</span>
      <select bind:value={selectedId} on:change={loadDetail}>
        {#each loans as l}
          <option value={l.id}>#{l.id} 《{l.contractTitle}》借阅人 {l.borrower.name} · 到期 {l.dueDate} · {l.statusLabel}</option>
        {/each}
      </select>
    </label>
  </div>
</div>

{#if loan}
  <div class="panel">
    <h2>借阅信息 <span class="sub">借阅人、实际经手人、到期日同屏核对</span></h2>
    <div class="kvline">
      <span><b>借阅人</b>{loan.borrower.name}（{loan.borrower.post.name}）</span>
      <span><b>实际经手档案员</b>{loan.archivist ? loan.archivist.name : '—'}</span>
      <span><b>交接时间</b>{loan.handoverAt || '—'}</span>
      <span><b>到期日</b><b class="mono">{loan.dueDate}</b>
        {#if daysUntil(loan.dueDate) < 0 && loan.status !== 'RETURNED'}<span class="badge red">逾期 {-daysUntil(loan.dueDate)} 天</span>{/if}
      </span>
      <span><b>当前状态</b><StatusBadge status={loan.status} /></span>
    </div>
    <div class="kvline">
      <span><b>合同</b>{loan.contractTitle}（<span class="mono">{loan.contractCode}</span>）</span>
      <span><b>盒位</b><span class="mono">{loan.boxLocation}</span></span>
      <span><b>总页数</b>{loan.totalPages} 页</span>
      <span><b>盖章页</b><b style="color:#b35a00">{loan.sealPages}</b></span>
    </div>
    {#if loan.anomalyOpen}
      <div class="alert err"><b>该单存在未闭环的归还异常。</b>异常处理完成后须重新逐页核验，全部完好才能签为已还。</div>
    {/if}
    {#if loan.recheckPending}
      <div class="alert warn">该单存在待处理的权限重新核验，请先到「权限管理」处理。</div>
    {/if}
  </div>

  <div class="panel">
    <h2>逐页核对（共 {loan.totalPages} 页）
      <span class="sub">盖章页重点核对骑缝/落款签章是否完整</span>
    </h2>
    <div class="row" style="margin-bottom:10px">
      <button class="btn sm ghost" on:click={() => setAll('OK')}>全部置为完好</button>
      <span class="muted small">再把问题页单独改为缺页/损坏</span>
      <span style="flex:1"></span>
      {#if issueCount() > 0}<span class="badge red">已标记 {issueCount()} 页异常</span>{/if}
    </div>

    <table>
      <thead><tr><th style="width:90px">页码</th><th style="width:150px">核验结论</th><th>备注（附件/污损描述）</th></tr></thead>
      <tbody>
        {#each pages as p, i}
          <tr class="pagerow">
            <td>
              <b>第 {p.pageNo} 页</b>
              {#if sealSet.has(p.pageNo)}<span class="seal-tag">盖章页</span>{/if}
            </td>
            <td>
              <select class={p.status === 'OK' ? 'ok' : p.status === 'MISSING' ? 'missing' : 'damaged'}
                      bind:value={pages[i].status}>
                <option value="OK">完好</option>
                <option value="MISSING">缺页</option>
                <option value="DAMAGED">损坏</option>
              </select>
            </td>
            <td><input type="text" bind:value={pages[i].note} placeholder={sealSet.has(p.pageNo) ? '核对盖章/骑缝章' : '附件是否齐全、有无涂改水渍'} /></td>
          </tr>
        {/each}
      </tbody>
    </table>

    <div class="row" style="justify-content:flex-end;margin-top:14px">
      {#if issueCount() > 0}
        <div class="alert err" style="margin:0;flex:1">存在缺页/损坏：提交后将<b>生成异常单并保持借阅在册</b>，不能签为已还。</div>
      {/if}
      <button class="btn" disabled={busy} on:click={submit}>
        {issueCount() > 0 ? '提交并转入异常处理' : '提交核验 · 全部完好则签为已还'}
      </button>
    </div>

    {#if result}
      <div class="alert {result.blockedReturn ? 'err' : 'ok'}">
        {#if result.blockedReturn}
          <b>已拦截签还。</b>{result.message}。问题：{result.issues.join('；')}（第 {result.round} 轮核验）
        {:else}
          <b>{result.message}</b>（第 {result.round} 轮，回库盒位 {result.boxLocation}，门禁已核销·模拟）
        {/if}
      </div>
    {/if}
  </div>

  {#if history.length}
    <div class="panel">
      <h2>历史核验记录 <span class="sub">各轮逐页结果全部保留，顺序可追查</span></h2>
      <table>
        <thead><tr><th>轮次</th><th>页码</th><th>盖章页</th><th>结论</th><th>备注</th><th>核验时间</th></tr></thead>
        <tbody>
          {#each history as h}
            <tr>
              <td>第 {h.round} 轮</td>
              <td>第 {h.pageNo} 页</td>
              <td>{h.sealPage ? '是' : ''}</td>
              <td>
                <span class="badge {h.status === 'OK' ? 'green' : h.status === 'MISSING' ? 'red' : 'orange'}">{h.statusLabel}</span>
              </td>
              <td class="small muted">{h.note || ''}</td>
              <td class="small muted">{h.checkedAt}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
{/if}
