<script>
  import { createEventDispatcher } from 'svelte';
  import LoanCard from './LoanCard.svelte';

  export let loans = [];
  export let me;
  export let contracts = [];
  const dispatch = createEventDispatcher();

  const mine = loans.filter((l) => l.borrowerId === me.id);
  const active = mine.filter((l) => l.status !== 'RETURNED' && l.status !== 'REJECTED');
  const history = mine.filter((l) => l.status === 'RETURNED' || l.status === 'REJECTED');
  const available = contracts.filter((c) => {
    const lvl = { PUBLIC: 1, INTERNAL: 2, CONFIDENTIAL: 3, SECRET: 4 };
    return lvl[me.clearance] >= lvl[c.secrecy];
  });
</script>

<div class="panel">
  <div class="row" style="justify-content:space-between">
    <h2 style="margin:0">我的借阅</h2>
    <button on:click={() => dispatch('apply')}>＋ 申请借阅原件</button>
  </div>
  <p class="muted small mt8">
    你可接触密级：<b>{me.clearance}</b>；可借合同 {available.length} 份。
    续借须在到期前申请；调岗或合同密级变更后需管理员重新核验权限。
  </p>
</div>

<div class="grid cols2">
  {#each active as loan (loan.id)}
    <LoanCard {loan} {me}
      on:detail={(e) => dispatch('detail', e.detail)}
      on:renew={(e) => dispatch('renew', e.detail)} />
  {/each}
</div>
{#if active.length === 0}<div class="panel muted">当前没有在途借阅。</div>{/if}

{#if history.length > 0}
  <div class="panel">
    <h2>历史记录</h2>
    <table>
      <tr><th>合同</th><th>用途</th><th>状态</th><th>到期日</th><th>归还时间</th><th></th></tr>
      {#each history as l}
        <tr>
          <td>{l.contractCode}</td>
          <td>{l.purpose}</td>
          <td><span class="chip {l.status}">{l.statusLabel}</span></td>
          <td>{l.dueDate}</td>
          <td>{l.returnedAt ? new Date(l.returnedAt).toLocaleString('zh-CN', { hour12: false }) : '—'}</td>
          <td><button class="ghost sm" on:click={() => dispatch('detail', l)}>追溯</button></td>
        </tr>
      {/each}
    </table>
  </div>
{/if}
