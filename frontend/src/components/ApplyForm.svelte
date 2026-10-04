<script>
  import { createEventDispatcher } from 'svelte';
  import { todayPlus } from '../stores.js';

  export let contracts = [];
  export let me;
  const dispatch = createEventDispatcher();

  let contractId = '';
  let purpose = '';
  let dueDate = todayPlus(7);
  let error = '';

  function submit() {
    if (!contractId) { error = '请选择合同'; return; }
    if (!purpose.trim()) { error = '借阅用途不能为空'; return; }
    dispatch('submit', {
      contractId: Number(contractId), purpose: purpose.trim(), dueDate
    });
  }
</script>

<div class="field">
  <label>借阅合同（原件）</label>
  <select bind:value={contractId}>
    <option value="">— 请选择 —</option>
    {#each contracts as c}
      <option value={c.id}>
        {c.code} · {c.title}（{c.secrecy} / 盒位 {c.boxLocation || '—'}）
      </option>
    {/each}
  </select>
</div>
<div class="field">
  <label>借阅用途（必填）</label>
  <textarea rows="3" bind:value={purpose} placeholder="例如：法院举证开庭、审计核对…"></textarea>
</div>
<div class="field">
  <label>期望到期日</label>
  <input type="date" bind:value={dueDate} min={todayPlus(1)} />
</div>
{#if error}<div class="errbox">{error}</div>{/if}
<div class="row" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  <button on:click={submit}>提交借阅申请</button>
</div>
