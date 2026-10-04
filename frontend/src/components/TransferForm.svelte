<script>
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();
  export let employee;
  export let error = '';

  let post = employee.post;
  let clearance = employee.clearance;
  let role = employee.role;
  let scanAuthorized = employee.scanAuthorized;
</script>

<div class="field">
  <label>新岗位（变更即视为调岗，触发在途借阅重核验）</label>
  <input bind:value={post} placeholder="例如：法务部-高级专员" />
</div>
<div class="grid cols2">
  <div class="field">
    <label>可接触密级</label>
    <select bind:value={clearance}>
      <option value="PUBLIC">公开</option>
      <option value="INTERNAL">内部</option>
      <option value="CONFIDENTIAL">秘密</option>
      <option value="SECRET">机密</option>
    </select>
  </div>
  <div class="field">
    <label>系统角色</label>
    <select bind:value={role}>
      <option value="EMPLOYEE">员工</option>
      <option value="ARCHIVIST">档案员</option>
      <option value="ADMIN">管理员</option>
    </select>
  </div>
</div>
<div class="field">
  <label class="row" style="gap:8px">
    <input type="checkbox" bind:checked={scanAuthorized} /> 授权查看合同扫描件
  </label>
</div>
{#if error}<div class="errbox">{error}</div>{/if}
<div class="row" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  <button on:click={() => dispatch('submit', { post, clearance, role, scanAuthorized })}>保存调岗/权限</button>
</div>
