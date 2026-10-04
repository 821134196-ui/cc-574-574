-- 种子数据（Hibernate 建表后执行）

-- 岗位：许可密级 1内部 2秘密 3机密
INSERT INTO posts (id, name, clearance) VALUES (1, '法务档案岗', 3);
INSERT INTO posts (id, name, clearance) VALUES (2, '采购管理岗', 2);
INSERT INTO posts (id, name, clearance) VALUES (3, '采购执行岗', 1);
INSERT INTO posts (id, name, clearance) VALUES (4, '市场营销岗', 1);
INSERT INTO posts (id, name, clearance) VALUES (5, '行政管理岗', 2);

-- 用户：管理员 / 档案员 / 三个员工
INSERT INTO users (id, login, name, post_id, role, active) VALUES (1, 'wangfang', '王芳（管理员）', 2, 'ADMIN', 1);
INSERT INTO users (id, login, name, post_id, role, active) VALUES (2, 'chenjing', '陈静（档案员）', 1, 'ARCHIVIST', 1);
INSERT INTO users (id, login, name, post_id, role, active) VALUES (3, 'liqiang', '李强', 3, 'EMPLOYEE', 1);
INSERT INTO users (id, login, name, post_id, role, active) VALUES (4, 'zhaolei', '赵磊', 2, 'EMPLOYEE', 1);
INSERT INTO users (id, login, name, post_id, role, active) VALUES (5, 'sunli', '孙丽', 4, 'EMPLOYEE', 1);

-- 合同台账（密级 / 盒位 / 总页数 / 盖章页）
INSERT INTO contracts (id, code, title, counterparty, security_level, box_location, total_pages, seal_pages, scan_file, scan_granted_post_id, version)
VALUES (1, 'HT-2026-001', '华东数据中心服务器采购合同', '云创科技有限公司', 'INTERNAL', 'A-1-01', 5, '1,5', NULL, 1, 0);
INSERT INTO contracts (id, code, title, counterparty, security_level, box_location, total_pages, seal_pages, scan_file, scan_granted_post_id, version)
VALUES (2, 'HT-2026-002', '年度框架供货协议', '华盛实业股份有限公司', 'CONFIDENTIAL', 'A-1-02', 8, '1,8', NULL, 2, 0);
INSERT INTO contracts (id, code, title, counterparty, security_level, box_location, total_pages, seal_pages, scan_file, scan_granted_post_id, version)
VALUES (3, 'HT-2026-003', '并购项目保密协议', '鼎晖律师事务所', 'SECRET', 'B-2-03', 12, '1,12', NULL, 1, 0);
INSERT INTO contracts (id, code, title, counterparty, security_level, box_location, total_pages, seal_pages, scan_file, scan_granted_post_id, version)
VALUES (4, 'HT-2026-004', '办公楼租赁合同', '城投置地集团', 'CONFIDENTIAL', 'A-2-05', 6, '1,6', NULL, 5, 0);

-- 在册借阅：赵磊借 HT-2026-002（借阅中，未来到期）；李强借 HT-2026-004（已逾期，供跟进演示）
INSERT INTO contract_loan (id, contract_id, borrower_id, purpose, requested_days, status, requested_at,
    decided_by_id, decided_at, archivist_id, handover_at, picked_at, due_date,
    returned_by_id, returned_at, return_note, renewal_count, recheck_pending, anomaly_open, version)
VALUES (1, 2, 4, '履约谈判需要核对供货条款原件，与华盛实业年度会议使用', 10, 'PICKED_UP', '2026-09-28 09:10:00',
    1, '2026-09-28 09:30:00', 2, '2026-09-28 10:05:00', '2026-09-28 10:05:00', '2026-10-12 00:00:00',
    NULL, NULL, NULL, 0, 0, 0, 0);
INSERT INTO contract_loan (id, contract_id, borrower_id, purpose, requested_days, status, requested_at,
    decided_by_id, decided_at, archivist_id, handover_at, picked_at, due_date,
    returned_by_id, returned_at, return_note, renewal_count, recheck_pending, anomaly_open, version)
VALUES (2, 4, 3, '办理租赁备案，需携带原件到政务窗口核验', 7, 'OVERDUE', '2026-09-10 14:00:00',
    1, '2026-09-10 14:20:00', 2, '2026-09-10 15:00:00', '2026-09-10 15:00:00', '2026-09-20 00:00:00',
    NULL, NULL, NULL, 0, 0, 0, 0);

-- 审计种子（之后全部顺序追加）
INSERT INTO audit_log (id, ts, actor_name, action, entity_type, entity_id, state_change, detail)
VALUES (1, '2026-09-28 09:10:00', '赵磊', '申请借阅', 'Loan', 1, NULL, '合同《年度框架供货协议》(HT-2026-002)，用途：履约谈判核对条款');
INSERT INTO audit_log (id, ts, actor_name, action, entity_type, entity_id, state_change, detail)
VALUES (2, '2026-09-28 09:30:00', '王芳（管理员）', '审批通过', 'Loan', 1, 'PENDING->APPROVED', '应归还日期 2026-10-08');
INSERT INTO audit_log (id, ts, actor_name, action, entity_type, entity_id, state_change, detail)
VALUES (3, '2026-09-28 10:05:00', '陈静（档案员）', '原件交接出库', 'Loan', 1, 'APPROVED->PICKED_UP', '盒位 A-1-02 取出，实际经手：陈静（档案员）；借阅人：赵磊；到期日：2026-10-12');

-- 互斥保证：同一合同只允许存在一条在册借阅（含待交接/借阅中/逾期/权限中止）
CREATE UNIQUE INDEX ux_active_loan ON contract_loan(contract_id)
    WHERE status IN ('APPROVED','PICKED_UP','OVERDUE','REVOKED');
