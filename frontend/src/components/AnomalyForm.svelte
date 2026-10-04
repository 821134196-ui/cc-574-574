<script>
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();

  export let anomaly;
  let resolution = '';
  let completeReturn = true;
  let error = '';

  function submit() {
    if (!resolution.trim()) { error = '必须填写异常处理结论'; return; }
    dispatch('submit', { id: anomaly.id, resolution: resolution.trim(), completeReturn });
  }
</script>

<div class="errbox">
  <b>{anomaly.kind === 'DAMAGED' ? '原件损坏' : '缺页/附件或盖章页缺失'}</b>
  <div class="small mt8">{anomaly.description}</div>
  <div class="small muted mt8">登记时间：{new Date(anomaly.openedAt).toLocaleString('zh-CN', { hour12: false })}</div>
</div>

<div class="field">
  <label>异常处理结论（必填）</label>
  <textarea rows="3" bind:value={resolution}
    placeholder="例如：缺页已追回归档/盖章页补齐/责任认定与通报/赔偿处理…"></textarea>
</div>
<div class="field">
  <label class="row" style="gap:8px">
    <input type="checkbox" bind:checked={completeReturn} />
    闭环异常并完成归还结案（原件确认归位盒位）
  </label>
</div>
{#if error}<div class="errbox">{error}</div>{/if}

<div class="row" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  <button class="warn" on:click={submit}>确认闭环异常</button>
</div>
