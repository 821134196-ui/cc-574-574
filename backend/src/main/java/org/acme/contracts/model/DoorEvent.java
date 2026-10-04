package org.acme.contracts.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * 门禁事件（本地模拟）：出库/归还交接时调用模拟门禁接口写入，
 * 用于核验实际经手人携原件出入档案库的记录。
 */
@Entity
@Table(name = "door_event")
public class DoorEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    public Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Enums.DoorDirection direction;

    /** 刷卡/登记人姓名，应与交接经手人一致 */
    @Column(nullable = false)
    public String person;

    /** 模拟读卡器返回的卡号 */
    @Column(nullable = false)
    public String cardNo;

    @Column(nullable = false)
    public boolean granted;

    public Instant at = Instant.now();
}
