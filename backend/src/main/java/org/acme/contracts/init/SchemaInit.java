package org.acme.contracts.init;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Hibernate 不支持 SQLite 的部分唯一索引，启动时直接通过 JDBC 建立：
 * 同一份合同在 active=1 时至多存在一条借阅单 —— 数据库层面强制
 * “一份原件不能同时借给两人”，并发申请也无法绕过。
 */
@ApplicationScoped
public class SchemaInit {

    private static final Logger LOG = Logger.getLogger(SchemaInit.class);

    @Inject
    AgroalDataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=10000");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_active "
                    + "ON loan(contract_id) WHERE active = 1");
            st.execute("CREATE INDEX IF NOT EXISTS ix_loan_borrower ON loan(borrower_id)");
            st.execute("CREATE INDEX IF NOT EXISTS ix_reminder_dedup ON reminder(dedupKey)");
            LOG.info("Schema extras ensured (partial unique index ux_loan_active)");
        } catch (Exception e) {
            throw new IllegalStateException("初始化数据库索引失败", e);
        }
    }
}
