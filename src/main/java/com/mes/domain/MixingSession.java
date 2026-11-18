package com.mes.domain;

import com.mes.domain.enums.MixingSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mixing_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MixingSession {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(name = "mixer_id")
    private UUID mixerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MixingSessionStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "mixingSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WeighingRecord> weighingRecords = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (status == null) status = MixingSessionStatus.STARTED;
        if (startedAt == null) startedAt = Instant.now();
    }
}
