<script>
  import { createEventDispatcher } from 'svelte';
  import { SECRECY_LABEL, ROLE_LABEL } from '../stores.js';

  export let employees = [];
  export let me;
  const dispatch = createEventDispatcher();
</script>

<div class="panel">
  <h2>员工与岗位权限</h2>
  <p class="muted small" style="margin:0">
    调岗或调整密级会累加岗位版本，在途借阅单将被标记为“需重新核验权限”；
    扫描件授权单独控制，仅勾选岗位可查看扫描件。
  </p>
</div>

<div class="panel" style="padding:0;overflow-x:auto">
  <table>
    <tr>
      <th>工号</th><th>姓名</th><th>岗位</th><th>角色</th><th>可接触密级</th>
      <th>扫描件授权</th><th>状态</th><th>操作</th>
    </tr>
    {#each employees as e}
      <tr>
        <td>{e.employeeNo}</td>
        <td><b>{e.name}</b></td>
        <td>{e.post}</td>
        <td><span class="chip role">{ROLE_LABEL[e.role]}</span></td>
        <td>{SECRECY_LABEL[e.clearance]}</td>
        <td>{e.scanAuthorized ? '✓ 已授权' : '—'}</td>
        <td>{e.active ? '在职' : '停用'}</td>
        <td>
          {#if me.role === 'ADMIN'}
            <button class="ghost sm" on:click={() => dispatch('transfer', e)}>调岗/改权限</button>
          {/if}
        </td>
      </tr>
    {/each}
  </table>
</div>
