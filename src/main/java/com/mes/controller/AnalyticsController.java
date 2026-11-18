package com.mes.controller;

import com.mes.analytics.SpcResult;
import com.mes.service.SpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Аналитика качества: контрольные карты и показатели воспроизводимости процесса. */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SpcService spcService;

    /**
     * Контрольная карта и Cp/Cpk параметра по рецептуре.
     * {@code source=QC} — результаты протоколов качества; {@code source=PROCESS} — контрольные точки процесса.
     * Параметр {@code from} (ISO-8601, например 2026-01-01T00:00:00Z) ограничивает период.
     */
    @GetMapping("/spc")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST','LAB_TECHNICIAN')")
    public ResponseEntity<SpcResult> spc(@RequestParam String recipeCode,
                                         @RequestParam String parameter,
                                         @RequestParam(defaultValue = "QC") SpcService.Source source,
                                         @RequestParam(required = false) Instant from) {
        return ResponseEntity.ok(spcService.analyze(recipeCode, parameter, source, from));
    }
}
