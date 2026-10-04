<script>
  import { createEventDispatcher } from 'svelte';
  import LoanCard from './LoanCard.svelte';

  export let loans = [];
  export let me;
  const dispatch = createEventDispatcher();

  const toIssue = loans.filter((l) => l.status === 'APPROVED');
  const toReturn = loans.filter((l) =>
    ['ISSUED', 'OVERDUE', 'ANOMALY'].includes(l.status));
</script>

<div class="panel">
  <h2>档案员工作台 · 待出库（{toIssue.length}）</h2>
  <p class="muted small" style="margin:0">从盒位取出原件，登记实际经手人，出库即写交接链与模拟门禁。</p>
</div>
<div class="grid cols2">
  {#each toIssue as loan (loan.id)}
    <LoanCard {loan} {me}
      on:detail={(e) => dispatch('detail', e.detail)}
      on:issue={(e) => dispatch('issue', e.detail)} />
  {/each}
</div>
{#if toIssue.length === 0}<div class="panel muted">暂无待出库借阅单。</div>{/if}

<div class="panel mt16">
  <h2>待归还逐页核验（{toReturn.length}）</h2>
  <p class="muted small" style="margin:0">
    逐页核对正文、附件与盖章页。发现缺页/损坏时系统转入异常处理，<b>不能直接签为已还</b>。
  </p>
</div>
<div class="grid cols2">
  {#each toReturn as loan (loan.id)}
    <LoanCard {loan} {me}
      on:detail={(e) => dispatch('detail', e.detail)}
      on:return={(e) => dispatch('return', e.detail)} />
  {/each}
</div>
{#if toReturn.length === 0}<div class="panel muted">暂无待归还原件。</div>{/if}
