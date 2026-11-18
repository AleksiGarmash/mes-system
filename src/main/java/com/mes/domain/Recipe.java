package com.mes.domain;

import com.mes.domain.enums.RecipeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipes", uniqueConstraints = @UniqueConstraint(columnNames = {"code", "version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue
    private UUID id;

    /** Защита от гонки при активации версий (см. также частичный уникальный индекс в БД). */
    @Version
    @Column(name = "version_lock")
    private Long lockVersion;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeStatus status;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecipeItem> items = new ArrayList<>();

    /**
     * Код QC-спецификации ({@link QualitySpec#getCode()}), применяемой к партиям,
     * произведённым по этой рецептуре. Хранится как код, а не FK на конкретную версию —
     * актуальная ACTIVE-версия разрешается в момент создания QC-чек-листа. Может быть
     * {@code null}, если для продукта QC-спецификация ещё не заведена.
     */
    @Column(name = "quality_spec_code", length = 50)
    private String qualitySpecCode;

    /**
     * Нужно ли фиксировать партию сырья по каждому компоненту. Если на бланке продукта нет колонки
     * «партия сырья», флаг выставляется в {@code false}: тогда {@code confirm} не требует лот, а
     * прослеживаемость по сырью для этого продукта ограничена (честно отражено в паспорте партии).
     */
    @Column(name = "require_raw_material_lot", nullable = false)
    @Builder.Default
    private Boolean requireRawMaterialLot = Boolean.TRUE;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageNo ASC")
    @Builder.Default
    private List<RecipeStage> stages = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    @Builder.Default
    private List<RecipeControlPoint> controlPoints = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = RecipeStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(RecipeItem item) {
        items.add(item);
        item.setRecipe(this);
    }

    public void addStage(RecipeStage stage) {
        stages.add(stage);
        stage.setRecipe(this);
    }

    public void addControlPoint(RecipeControlPoint controlPoint) {
        controlPoints.add(controlPoint);
        controlPoint.setRecipe(this);
    }
}
