package com.mes.service;

import com.mes.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Номера партий вида {@code B-2026-000123}. Порядковая часть — последовательность БД
 * ({@code batch_no_seq}): уникальна между потоками создания партий и экземплярами приложения,
 * переживает рестарт. Год в номере — год создания; счётчик общий и не сбрасывается по годам
 * (так проще и номер остаётся уникальным в любом случае).
 */
@Service
@RequiredArgsConstructor
public class BatchNumberService {

    private final BatchRepository batchRepository;

    @Transactional
    public String next() {
        return format(LocalDate.now(ZoneOffset.UTC).getYear(), batchRepository.nextBatchSequence());
    }

    static String format(int year, long sequence) {
        return "B-%d-%06d".formatted(year, sequence);
    }
}
