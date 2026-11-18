package com.mes.controller;

import com.mes.dto.request.QuarantineRequest;
import com.mes.dto.response.BatchGenealogy;
import com.mes.dto.response.QuarantineResult;
import com.mes.dto.response.RecallReport;
import com.mes.service.TraceabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Прослеживаемость: родословная партии и отзыв лота сырья. */
@RestController
@RequestMapping("/api/traceability")
@RequiredArgsConstructor
public class TraceabilityController {

    private final TraceabilityService traceabilityService;

    /** Назад: из чего сделана партия и каковы результаты контроля. */
    @GetMapping("/batches/{batchNo}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST','LAB_TECHNICIAN','PLANNER')")
    public ResponseEntity<BatchGenealogy> genealogy(@PathVariable String batchNo) {
        return ResponseEntity.ok(traceabilityService.genealogy(batchNo));
    }

    /** Вперёд: в каких партиях продукции использован лот сырья (для отзыва). */
    @GetMapping("/lots/{lotNo}/batches")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST','LAB_TECHNICIAN','PLANNER')")
    public ResponseEntity<RecallReport> recall(@PathVariable String lotNo) {
        return ResponseEntity.ok(traceabilityService.recallReport(lotNo));
    }

    /** Перевод всех затронутых лотом партий на карантин. */
    @PostMapping("/lots/{lotNo}/quarantine")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST')")
    public ResponseEntity<QuarantineResult> quarantine(@PathVariable String lotNo,
                                                       @RequestBody(required = false) QuarantineRequest request) {
        return ResponseEntity.ok(traceabilityService.quarantineByRawLot(lotNo, request != null ? request.reason() : null));
    }
}
