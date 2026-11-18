package com.mes.domain;

import com.mes.domain.enums.ControlPointKind;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Фактический замер в контрольной точке, зафиксированный при подтверждении чек-листа.
 * Допуск копируется в запись (снимок), чтобы история не менялась при смене версии рецептуры.
 */
@Entity
@Table(name = "process_control_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessControlRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private ProductionChecklist checklist;

    @Column(name = "control_point_id")
    private UUID controlPointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ControlPointKind kind;

    @Column(name = "stage_no")
    private Integer stageNo;

    @Column(name = "parameter_name", nullable = false, length = 255)
    private String parameterName;

    @Column(name = "expected_min", precision = 12, scale = 3)
    private BigDecimal expectedMin;

    @Column(name = "expected_max", precision = 12, scale = 3)
    private BigDecimal expectedMax;

    @Column(name = "measured_value", precision = 12, scale = 3)
    private BigDecimal measuredValue;

    @Column(name = "within_range", nullable = false)
    private boolean withinRange;
}
