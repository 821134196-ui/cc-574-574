<script>
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();

  export let loan;
  let handler = loan.borrowerName || '';
  let error = '';

  function submit() {
    if (!handler.trim()) { error = '必须登记实际经手人'; return; }
    dispatch('submit', { handler: handler.trim() });
  }
</script>

<div class="okbox">
  将从盒位 <b>{loan.boxLocation}</b> 取出合同 <b>{loan.contractCode}</b> 原件（共 {loan.contractPages} 页）。
</div>
<div class="field">
  <label>实际经手人（现场取走原件者，可不同于借阅人）</label>
  <input bind:value={handler} placeholder="例如：部门助理、司机…" />
</div>
{#if error}<div class="errbox">{error}</div>{/if}
<div class="row" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  <button on:click={submit}>确认出库（模拟门禁记录）</button>
</div>
