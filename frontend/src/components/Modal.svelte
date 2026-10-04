<script>
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();
  export let title = '';
</script>

<div class="modal-mask" on:click={() => dispatch('close')} on:keydown role="presentation">
  <div class="modal" on:click|stopPropagation role="dialog">
    <div class="row" style="justify-content:space-between">
      <h2>{title}</h2>
      <button class="ghost sm" on:click={() => dispatch('close')}>关闭 ✕</button>
    </div>
    <slot />
  </div>
</div>
