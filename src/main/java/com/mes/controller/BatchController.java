package com.mes.controller;

import com.mes.domain.Batch;
import com.mes.domain.QualitySpec;
import com.mes.dto.response.BatchPassportResponse;
import com.mes.dto.response.BatchResponse;
import com.mes.mapper.ProductionMapper;
import com.mes.exception.ResourceNotFoundException;
import com.mes.pdf.QcChecklistPdfGenerator;
import com.mes.repository.BatchRepository;
import com.mes.service.BatchPassportService;
import com.mes.service.QualitySpecService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.mes.util.Paging;
import com.mes.dto.response.PageResponse;
import com.mes.domain.enums.BatchStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchPassportService batchPassportService;
    private final BatchRepository batchRepository;
    private final QcChecklistPdfGenerator qcChecklistPdfGenerator;
    private final QualitySpecService qualitySpecService;
    private final ProductionMapper productionMapper;

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> getById(@PathVariable UUID id) {
        return batchRepository.findById(id)
                .map(productionMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Партия не найдена: " + id));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}/passport")
    public ResponseEntity<BatchPassportResponse> getPassport(@PathVariable UUID id) {
        return ResponseEntity.ok(batchPassportService.generate(id));
    }

    /**
     * Печатный протокол контроля качества под партию: заголовок + QR-код партии +
     * таблица параметров QC-спецификации с пустыми полями под рукописное заполнение.
     */
    @Transactional(readOnly = true)
    @GetMapping(value = "/{id}/qc-checklist-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECHNICIAN')")
    public ResponseEntity<byte[]> printQcChecklist(@PathVariable UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Партия не найдена: " + id));

        String specCode = batch.getProductionOrder().getRecipe().getQualitySpecCode();
        QualitySpec spec = specCode != null ? qualitySpecService.getActiveByCode(specCode) : null;

        byte[] pdf = qcChecklistPdfGenerator.generate(batch, spec);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"qc-protocol-" + batch.getBatchNo() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<PageResponse<BatchResponse>> list(@RequestParam(required = false) BatchStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        var pageable = Paging.newestFirst(page, size);
        var result = status != null ? batchRepository.findByStatus(status, pageable) : batchRepository.findAll(pageable);
        return ResponseEntity.ok(PageResponse.of(result, productionMapper::toResponse));
    }
}
