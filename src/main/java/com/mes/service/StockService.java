package com.mes.service;

import com.mes.domain.RawMaterialBatch;
import com.mes.dto.request.ReceiveLotRequest;
import com.mes.exception.InvalidRequestException;
import com.mes.exception.InvalidStateTransitionException;
import com.mes.repository.RawMaterialBatchRepository;
import com.mes.stock.FefoAllocation;
import com.mes.stock.FefoAllocator;
import com.mes.stock.StockLot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Склад сырья: приёмка лотов, остатки, подбор и выдача по FEFO. */
@Service
@RequiredArgsConstructor
public class StockService {

    static final String AUDIT_LOT = "RAW_MATERIAL_BATCH";

    private final RawMaterialBatchRepository lotRepository;
    private final FefoAllocator fefoAllocator;
    private final AuditService auditService;

    @Transactional
    public RawMaterialBatch receive(ReceiveLotRequest request) {
        if (lotRepository.findByLotNo(request.lotNo()).isPresent()) {
            throw new InvalidRequestException("Лот с номером " + request.lotNo() + " уже принят на склад.");
        }
        if (request.expiry() != null && request.expiry().isBefore(today())) {
            throw new InvalidRequestException("Нельзя принять просроченный лот: срок годности "
                    + request.expiry() + " уже истёк.");
        }
        RawMaterialBatch lot = lotRepository.save(RawMaterialBatch.builder()
                .lotNo(request.lotNo())
                .materialId(request.materialId())
                .materialCode(request.materialCode())
                .supplier(request.supplier())
                .quantityAvailable(request.quantity())
                .expiry(request.expiry())
                .build());
        auditService.recordChange(AUDIT_LOT, lot.getId(), "quantityAvailable", null, request.quantity());
        return lot;
    }

    /** Лоты материала в порядке выдачи (по сроку годности), с остатком больше нуля. */
    @Transactional(readOnly = true)
    public List<RawMaterialBatch> availableLots(UUID materialId) {
        return lotRepository.findByMaterialId(materialId).stream()
                .filter(l -> l.getQuantityAvailable() != null && l.getQuantityAvailable().signum() > 0)
                .sorted(Comparator
                        .comparing(RawMaterialBatch::getExpiry, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RawMaterialBatch::getLotNo))
                .toList();
    }

    /** Расчёт выдачи без списания. */
    @Transactional(readOnly = true)
    public FefoAllocation preview(UUID materialId, BigDecimal quantity) {
        return fefoAllocator.allocate(toStockLots(materialId), quantity, today());
    }

    /**
     * Выдача со списанием остатков. Если сырья не хватает, не списывается ничего:
     * частичная выдача оставила бы склад и цех в разном понимании «сколько выдано».
     */
    @Transactional
    public FefoAllocation issue(UUID materialId, BigDecimal quantity) {
        FefoAllocation allocation = fefoAllocator.allocate(toStockLots(materialId), quantity, today());
        if (!allocation.isComplete()) {
            throw new InvalidStateTransitionException(
                    "Недостаточно сырья: не хватает " + allocation.shortage().stripTrailingZeros().toPlainString() + ".");
        }
        for (FefoAllocation.Pick pick : allocation.picks()) {
            RawMaterialBatch lot = lotRepository.findById(pick.lot().id())
                    .orElseThrow(() -> new InvalidStateTransitionException("Лот исчез во время выдачи: " + pick.lot().lotNo()));
            BigDecimal before = lot.getQuantityAvailable();
            BigDecimal after = before.subtract(pick.quantity());
            lot.setQuantityAvailable(after);
            lotRepository.save(lot);
            auditService.recordChange(AUDIT_LOT, lot.getId(), "quantityAvailable", before, after);
        }
        return allocation;
    }

    private List<StockLot> toStockLots(UUID materialId) {
        return lotRepository.findByMaterialId(materialId).stream()
                .map(l -> new StockLot(l.getId(), l.getLotNo(), l.getQuantityAvailable(), l.getExpiry()))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** Все лоты (в т.ч. с нулевым остатком) с необязательным фильтром по коду материала. */
    @Transactional(readOnly = true)
    public Page<RawMaterialBatch> listLots(String materialCode, Pageable pageable) {
        return materialCode != null && !materialCode.isBlank()
                ? lotRepository.findByMaterialCodeContainingIgnoreCase(materialCode.trim(), pageable)
                : lotRepository.findAll(pageable);
    }
}
