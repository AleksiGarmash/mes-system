package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "raw_material_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawMaterialBatch {

    @Id
    @GeneratedValue
    private UUID id;

    /** Оптимистичная блокировка: два одновременных списания с одного лота не потеряют друг друга. */
    @Version
    private Long version;

    @Column(name = "lot_no", nullable = false, length = 50)
    private String lotNo;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "material_code", length = 50)
    private String materialCode;

    @Column(name = "supplier", length = 255)
    private String supplier;

    @Column(name = "quantity_available", precision = 12, scale = 3)
    private BigDecimal quantityAvailable;

    @Column(name = "expiry")
    private LocalDate expiry;
}
