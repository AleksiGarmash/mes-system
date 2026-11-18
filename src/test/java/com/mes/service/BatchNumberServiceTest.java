package com.mes.service;

import com.mes.repository.BatchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class BatchNumberServiceTest {

    @Test
    void format_padsSequenceToSixDigits() {
        assertEquals("B-2026-000123", BatchNumberService.format(2026, 123));
        assertEquals("B-2026-000001", BatchNumberService.format(2026, 1));
    }

    @Test
    void format_doesNotTruncateLargeSequences() {
        assertEquals("B-2026-1234567", BatchNumberService.format(2026, 1_234_567));
    }

    @Test
    void next_usesDatabaseSequence() {
        BatchRepository repository = Mockito.mock(BatchRepository.class);
        when(repository.nextBatchSequence()).thenReturn(42L, 43L);
        BatchNumberService service = new BatchNumberService(repository);

        String first = service.next();
        String second = service.next();

        assertTrue(first.matches("B-\\d{4}-000042"), first);
        assertTrue(second.matches("B-\\d{4}-000043"), second);
    }
}
