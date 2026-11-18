package com.mes.controller;

import com.mes.dto.response.AuditLogResponse;
import com.mes.mapper.ProductionMapper;
import com.mes.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Журнал изменений: ручные правки результатов OCR и переходы статусов документов.
 * Доступен администратору и технологу. {@code entityType}: PRODUCTION_CHECKLIST,
 * PRODUCTION_CHECKLIST_ITEM, QC_CHECKLIST, QC_CHECKLIST_ITEM.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final ProductionMapper productionMapper;

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST')")
    public ResponseEntity<List<AuditLogResponse>> history(@PathVariable String entityType, @PathVariable UUID entityId) {
        return ResponseEntity.ok(auditService.history(entityType, entityId).stream()
                .map(productionMapper::toResponse)
                .toList());
    }
}
