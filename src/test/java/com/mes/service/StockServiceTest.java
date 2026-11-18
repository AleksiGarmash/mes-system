package com.mes.service;

import com.mes.domain.RawMaterialBatch;
import com.mes.dto.request.ReceiveLotRequest;
import com.mes.exception.InvalidRequestException;
import com.mes.exception.InvalidStateTransitionException;
import com.mes.repository.RawMaterialBatchRepository;
import com.mes.stock.FefoAllocation;
import com.mes.stock.FefoAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StockServiceTest {

    private RawMaterialBatchRepository repository;
    private AuditService auditService;
    private StockService service;
    private final UUID materialId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RawMaterialBatchRepository.class);
        auditService = Mockito.mock(AuditService.class);
        service = new StockService(repository, new FefoAllocator(), auditService);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RawMaterialBatch lot(String no, String qty, LocalDate expiry) {
        RawMaterialBatch lot = RawMaterialBatch.builder().id(UUID.randomUUID()).lotNo(no).materialId(materialId)
                .quantityAvailable(new BigDecimal(qty)).expiry(expiry).build();
        when(repository.findById(lot.getId())).thenReturn(Optional.of(lot));
        return lot;
    }

    @Test
    void receive_createsLotAndLogsInitialQuantity() {
        when(repository.findByLotNo("RM-1")).thenReturn(Optional.empty());

        RawMaterialBatch saved = service.receive(new ReceiveLotRequest(
                materialId, "Песок", "RM-1", "ООО Карьер", new BigDecimal("500"), today.plusDays(100)));

        assertEquals("RM-1", saved.getLotNo());
        assertEquals(0, new BigDecimal("500").compareTo(saved.getQuantityAvailable()));
        verify(auditService).recordChange(eq("RAW_MATERIAL_BATCH"), any(), eq("quantityAvailable"), eq(null), eq(new BigDecimal("500")));
    }

    @Test
    void receive_duplicateLotNumber_isRejected() {
        when(repository.findByLotNo("RM-1")).thenReturn(Optional.of(RawMaterialBatch.builder().build()));

        assertThrows(InvalidRequestException.class, () -> service.receive(new ReceiveLotRequest(
                materialId, "Песок", "RM-1", null, BigDecimal.TEN, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void receive_alreadyExpiredLot_isRejected() {
        when(repository.findByLotNo("RM-2")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class, () -> service.receive(new ReceiveLotRequest(
                materialId, "Песок", "RM-2", null, BigDecimal.TEN, today.minusDays(1))));
    }

    @Test
    void availableLots_areSortedByExpiryAndHideEmptyOnes() {
        RawMaterialBatch late = lot("LATE", "10", today.plusDays(50));
        RawMaterialBatch soon = lot("SOON", "10", today.plusDays(5));
        RawMaterialBatch empty = lot("EMPTY", "0", today.plusDays(1));
        when(repository.findByMaterialId(materialId)).thenReturn(List.of(late, empty, soon));

        List<RawMaterialBatch> result = service.availableLots(materialId);

        assertEquals(List.of("SOON", "LATE"), result.stream().map(RawMaterialBatch::getLotNo).toList());
    }

    @Test
    void preview_doesNotChangeStock() {
        RawMaterialBatch a = lot("A", "100", today.plusDays(5));
        when(repository.findByMaterialId(materialId)).thenReturn(List.of(a));

        FefoAllocation allocation = service.preview(materialId, new BigDecimal("40"));

        assertTrue(allocation.isComplete());
        assertEquals(0, new BigDecimal("100").compareTo(a.getQuantityAvailable()));
        verify(repository, never()).save(any());
    }

    @Test
    void issue_decrementsLotsInFefoOrderAndLogsEachChange() {
        RawMaterialBatch soon = lot("SOON", "30", today.plusDays(5));
        RawMaterialBatch later = lot("LATER", "100", today.plusDays(40));
        when(repository.findByMaterialId(materialId)).thenReturn(List.of(later, soon));

        service.issue(materialId, new BigDecimal("50"));

        assertEquals(0, BigDecimal.ZERO.compareTo(soon.getQuantityAvailable()));
        assertEquals(0, new BigDecimal("80").compareTo(later.getQuantityAvailable()));
        verify(auditService).recordChange("RAW_MATERIAL_BATCH", soon.getId(), "quantityAvailable",
                new BigDecimal("30"), BigDecimal.ZERO.add(new BigDecimal("0")));
        verify(auditService).recordChange("RAW_MATERIAL_BATCH", later.getId(), "quantityAvailable",
                new BigDecimal("100"), new BigDecimal("80"));
    }

    @Test
    void issue_insufficientStock_changesNothing() {
        RawMaterialBatch a = lot("A", "30", today.plusDays(5));
        when(repository.findByMaterialId(materialId)).thenReturn(List.of(a));

        assertThrows(InvalidStateTransitionException.class, () -> service.issue(materialId, new BigDecimal("31")));

        assertEquals(0, new BigDecimal("30").compareTo(a.getQuantityAvailable()));
        verify(repository, never()).save(any());
        verify(auditService, never()).recordChange(any(), any(), any(), any(), any());
    }

    @Test
    void issue_ignoresExpiredLotEvenIfItWouldCoverTheDemand() {
        RawMaterialBatch expired = lot("EXP", "1000", today.minusDays(2));
        RawMaterialBatch good = lot("GOOD", "10", today.plusDays(30));
        when(repository.findByMaterialId(materialId)).thenReturn(List.of(expired, good));

        assertThrows(InvalidStateTransitionException.class, () -> service.issue(materialId, new BigDecimal("50")));
        assertEquals(0, new BigDecimal("1000").compareTo(expired.getQuantityAvailable()));
    }
}
