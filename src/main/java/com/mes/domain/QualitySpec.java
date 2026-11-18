package com.mes.domain;

import com.mes.domain.enums.QualitySpecStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Версионируемая спецификация контроля качества под конкретный продукт —
 * фиксированный набор параметров с диапазонами допуска, которые всегда проверяются
 * для партий, выпущенных по связанной рецептуре. Наполнение может отличаться от
 * продукта к продукту — так же, как содержимое {@link Recipe} отличается между
 * видами смесей.
 * <p>
 * Версионируется независимо от рецептуры: {@link Recipe#getQualitySpecCode()} хранит
 * лишь код спецификации, актуальная ACTIVE-версия разрешается в момент создания
 * QC-чек-листа — так же, как рецептура разрешается в момент создания производственного
 * задания, а не жёстко фиксируется в связи между таблицами.
 */
@Entity
@Table(name = "quality_specs", uniqueConstraints = @UniqueConstraint(columnNames = {"code", "version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualitySpec {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QualitySpecStatus status;

    @OneToMany(mappedBy = "qualitySpec", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QualitySpecItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = QualitySpecStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(QualitySpecItem item) {
        items.add(item);
        item.setQualitySpec(this);
    }
}
