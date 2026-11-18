package com.mes.controller;

import com.mes.dto.request.QualityCheckRequest;
import com.mes.dto.response.QualityCheckResponse;
import com.mes.mapper.ProductionMapper;
import com.mes.service.QualityControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/qc")
@RequiredArgsConstructor
public class QualityControlController {

    private final QualityControlService qualityControlService;
    private final ProductionMapper productionMapper;

    @Transactional
    @PostMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECHNICIAN')")
    public ResponseEntity<QualityCheckResponse> recordCheck(@PathVariable UUID batchId,
                                                              @Valid @RequestBody QualityCheckRequest request) {
        return ResponseEntity.ok(productionMapper.toResponse(qualityControlService.recordCheck(batchId, request)));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{batchId}")
    public ResponseEntity<List<QualityCheckResponse>> getChecks(@PathVariable UUID batchId) {
        return ResponseEntity.ok(qualityControlService.findByBatch(batchId).stream()
                .map(productionMapper::toResponse)
                .toList());
    }
}
