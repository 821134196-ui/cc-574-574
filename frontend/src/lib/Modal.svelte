<script>
  import { createEventDispatcher } from 'svelte';
  export let title = '';
  export let wide = false;
  const dispatch = createEventDispatcher();
</script>

<div style="position:fixed;inset:0;background:rgba(20,30,45,.42);z-index:50;display:flex;align-items:flex-start;justify-content:center;padding:46px 16px;overflow:auto"
     on:click|self={() => dispatch('close')}>
  <div class="panel" style="width:{wide ? 860 : 520}px;max-width:100%;margin:0">
    <div class="row" style="justify-content:space-between;margin-bottom:12px">
      <h2 style="margin:0">{title}</h2>
      <button class="btn sm ghost" on:click={() => dispatch('close')}>✕ 关闭</button>
    </div>
    <slot />
  </div>
</div>
