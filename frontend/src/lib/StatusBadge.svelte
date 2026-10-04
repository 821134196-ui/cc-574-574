<script>
  export let status;
  const map = {
    PENDING: ['orange', '待审批'],
    APPROVED: ['blue', '已批准待交接'],
    PICKED_UP: ['blue', '借阅中'],
    OVERDUE: ['red', '逾期未还'],
    REVOKED: ['red', '权限中止·催还中'],
    RETURNED: ['green', '已归还'],
    REJECTED: ['gray', '已拒绝'],
    CANCELLED: ['gray', '已撤回']
  };
</script>
{#if map[status]}
  <span class="badge {map[status][0]}">{map[status][1]}</span>
{/if}
