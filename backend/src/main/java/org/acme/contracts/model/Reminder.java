package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** 提醒：定时任务对临期/逾期借阅单生成，本地模拟（仅入库 + 控制台日志）。 */
@Entity
@Table(name = "reminder")
public class Reminder extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.ReminderKind kind;

    @Column(nullable = false)
    public String message;

    public LocalDate dueDate;

    /** 同一天同一借阅单同类提醒只生成一次 */
    @Column(nullable = false)
    public String dedupKey;

    @Column(nullable = false)
    public boolean acknowledged = false;

    public Instant createdAt = Instant.now();
}
