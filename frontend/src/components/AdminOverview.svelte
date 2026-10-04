<script>
  import { createEventDispatcher } from 'svelte';

  export let custody = [];
  export let anomalies = [];
  const dispatch = createEventDispatcher();

  const held = custody.filter((c) => c.borrowerName);
  const overdue = held.filter((c) => c.overdue);
</script>

<div class="panel">
  <h2>合同原件保管总览</h2>
  <p class="muted small" style="margin:0">每份合同现在由谁保管、实际经手人是谁、何时应归还。</p>
</div>

{#if overdue.length > 0}
  <div class="errbox">
    <b>{overdue.length} 份原件已逾期，须立即跟进：</b>
    {#each overdue as c}{c.contractCode}（{c.borrowerName}，应还 {c.dueDate}）；{/each}
  </div>
{/if}

<div class="panel" style="padding:0;overflow-x:auto">
  <table>
    <tr>
      <th>合同编号</th><th>名称</th><th>密级</th><th>盒位</th>
      <th>当前状态</th><th>保管/借阅人</th><th>实际经手人</th><th>应归还</th><th></th>
    </tr>
    {#each custody as c}
      <tr>
        <td><b>{c.contractCode}</b></td>
        <td>{c.contractTitle}</td>
        <td>{c.secrecy}</td>
        <td>{c.boxLocation || '—'}</td>
        <td>
          <span class="chip {c.status === '在库' ? 'RETURNED' :
            c.overdue ? 'OVERDUE' : c.status.includes('异常') ? 'ANOMALY' : 'ISSUED'}">{c.status}</span>
        </td>
        <td>{c.borrowerName || '—（在库）'}</td>
        <td>{c.handler || '—'}</td>
        <td class={c.overdue ? 'overdue-text' : ''}>{c.dueDate || '—'}</td>
        <td>
          {#if c.loanId}
            <button class="ghost sm" on:click={() => dispatch('detail', { id: c.loanId })}>追溯</button>
          {/if}
        </td>
      </tr>
    {/each}
  </table>
</div>

<div class="panel mt16">
  <h2>异常处理（{anomalies.filter((a) => a.status === 'OPEN').length} 件待处理）</h2>
</div>
<div class="grid cols2">
  {#each anomalies as a (a.id)}
    <div class="loan-card">
      <div class="row" style="justify-content:space-between">
        <span class="code">异常单 #{a.id}</span>
        <span class="chip {a.status === 'OPEN' ? 'ANOMALY' : 'RETURNED'}">
          {a.status === 'OPEN' ? '待处理' : '已闭环'}</span>
      </div>
      <div class="small muted mt8">借阅单 #{a.loanId} · {a.kind === 'DAMAGED' ? '原件损坏' : '缺页/盖章缺失'}</div>
      <div class="purpose">{a.description}</div>
      {#if a.status === 'OPEN'}
        <div class="actions">
          <button class="warn sm" on:click={() => dispatch('resolve', a)}>处理并闭环</button>
          <button class="ghost sm" on:click={() => dispatch('detail', { id: a.loanId })}>查看追溯</button>
        </div>
      {:else}
        <div class="okbox small">处理结论：{a.resolution}（{a.resolvedByName}）</div>
      {/if}
    </div>
  {/each}
</div>
{#if anomalies.length === 0}<div class="panel muted">暂无异常记录。</div>{/if}
