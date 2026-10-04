<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { fmtInstant } from '../stores.js';

  export let loan;
  export let me;

  let handovers = [], checks = [], doors = [], audits = [], renewals = [];
  let loadError = '';

  onMount(async () => {
    try {
      const [h, c, d, a, r] = await Promise.all([
        api.get(`/api/loans/${loan.id}/handovers`),
        api.get(`/api/loans/${loan.id}/checks`),
        api.get(`/api/loans/${loan.id}/door-events`),
        api.get(`/api/loans/${loan.id}/audit`),
        api.get(`/api/loans/${loan.id}/renewals`)
      ]);
      handovers = h; checks = c; doors = d; audits = a; renewals = r;
    } catch (e) {
      loadError = e.message;
    }
  });

  const ACTION_LABEL = {
    LOAN_APPLY: '提交借阅申请', LOAN_APPROVE: '审批通过', LOAN_REJECT: '审批驳回',
    LOAN_ISSUE: '档案员出库', RENEW_APPLY: '申请续借', RENEW_APPROVE: '续借批准',
    RENEW_REJECT: '续借驳回', REVERIFY_OK: '权限重核验通过', REVERIFY_FAIL: '权限重核验未通过',
    RETURN_ANOMALY: '归还核验异常', LOAN_RETURNED: '签为已还', ANOMALY_RESOLVE: '异常闭环',
    RETURN_CHECK_PASS_PENDING_ANOMALY: '核验通过但异常未闭环'
  };
  const label = (a) => ACTION_LABEL[a] || a;
</script>

{#if loadError}<div class="errbox">{loadError}</div>{/if}

<div class="loan-card" style="margin-bottom:14px">
  <div class="trio">
    <div><div class="k">借阅人</div><div class="v">{loan.borrowerName}</div></div>
    <div><div class="k">经手人</div><div class="v">{loan.issueHandler || loan.returnHandler || '—'}</div></div>
    <div><div class="k">到期日</div><div class="v">{loan.dueDate}</div></div>
  </div>
  <div class="small muted">用途：{loan.purpose}</div>
</div>

<h3>交接链条（出库 / 归还顺序登记，prevId 串联）</h3>
{#if handovers.length === 0}
  <div class="muted small">暂无交接记录</div>
{:else}
  <div class="timeline">
    {#each handovers as h}
      <div class="item {h.kind}">
        <div><b>#{h.seq} {h.kind === 'ISSUE' ? '出库交接' : '归还交接'}</b>
          <span class="chip role">{h.kind === 'ISSUE' ? '取走' : '送还'}</span></div>
        <div>经手人：{h.handler}；档案员：{h.archivistName}</div>
        <div class="meta">{h.note} · {fmtInstant(h.createdAt)} · 记录id {h.id} / 上一条 {h.prevId ?? '∅'}</div>
      </div>
    {/each}
  </div>
{/if}

<h3>归还逐页核验记录</h3>
{#if checks.length === 0}
  <div class="muted small">暂无核验记录</div>
{:else}
  <table>
    <tr><th>时间</th><th>结果</th><th>实到页</th><th>缺页</th><th>附件</th><th>盖章页</th><th>损坏</th><th>档案员</th></tr>
    {#each checks as c}
      <tr>
        <td>{fmtInstant(c.createdAt)}</td>
        <td><b class={c.result === 'PASS' ? '' : 'overdue-text'}>
          {c.result === 'PASS' ? '通过' : c.result === 'MISSING_PAGE' ? '缺页/盖章问题' : '损坏'}</b></td>
        <td>{c.pagesPresent}</td>
        <td>{c.missingPages || '—'}</td>
        <td>{c.missingAttachments || '—'}</td>
        <td>{c.stampedPagesOk ? '齐全' : '缺失'}</td>
        <td>{c.damageDesc || '—'}</td>
        <td>{c.archivistName}</td>
      </tr>
    {/each}
  </table>
{/if}

<h3>模拟门禁记录</h3>
{#if doors.length === 0}
  <div class="muted small">暂无门禁记录</div>
{:else}
  <table>
    <tr><th>时间</th><th>方向</th><th>登记人</th><th>卡号</th><th>放行</th></tr>
    {#each doors as d}
      <tr>
        <td>{fmtInstant(d.at)}</td>
        <td>{d.direction === 'OUT' ? '出库离开' : '归还进入'}</td>
        <td>{d.person}</td>
        <td>{d.cardNo}</td>
        <td>{d.granted ? '✓' : '✗'}</td>
      </tr>
    {/each}
  </table>
{/if}

{#if renewals.length > 0}
  <h3>续借记录</h3>
  <table>
    <tr><th>申请时间</th><th>原到期</th><th>新到期</th><th>状态</th><th>审批人</th></tr>
    {#each renewals as r}
      <tr>
        <td>{fmtInstant(r.requestedAt)}</td>
        <td>{r.oldDueDate}</td>
        <td>{r.newDueDate}</td>
        <td>{r.status === 'APPROVED' ? '已批准' : r.status === 'REJECTED' ? '已驳回' : '待审批'}</td>
        <td>{r.decidedByName || '—'}</td>
      </tr>
    {/each}
  </table>
{/if}

<h3>顺序审计链（不可篡改，按 seq / prevId 追查）</h3>
{#if audits.length === 0}
  <div class="muted small">暂无审计记录</div>
{:else}
  <div class="timeline">
    {#each audits as a}
      <div class="item {a.action.includes('ANOMALY') ? 'anomaly' : ''}">
        <div><b>seq {a.seq}</b> · {label(a.action)}</div>
        <div class="meta">操作人：{a.actorName} · {fmtInstant(a.at)} · id {a.id} ← 上一条 {a.prevId ?? '∅'}</div>
        {#if a.detail}<div class="small muted">{a.detail}</div>{/if}
      </div>
    {/each}
  </div>
{/if}
