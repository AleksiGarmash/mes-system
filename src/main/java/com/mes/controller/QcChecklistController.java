package com.mes.controller;

import com.mes.dto.request.ConfirmQcChecklistRequest;
import com.mes.dto.request.CreateQcChecklistRequest;
import com.mes.dto.request.RejectChecklistRequest;
import com.mes.dto.response.QcChecklistResponse;
import com.mes.mapper.QcChecklistMapper;
import com.mes.service.QcChecklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import com.mes.util.Paging;
import com.mes.dto.response.StoredFile;
import com.mes.dto.response.QcChecklistSummary;
import com.mes.dto.response.PageResponse;
import com.mes.domain.enums.QcChecklistStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Document-first поток для контроля качества: бумажный протокол остаётся официальным
 * документом, этот контроллер превращает его фото/скан в проверенную электронную запись.
 * Структура эндпоинтов зеркалирует {@link ChecklistController} (производственный чек-лист).
 */
@RestController
@RequestMapping("/api/qc-checklists")
@RequiredArgsConstructor
public class QcChecklistController {

    private final QcChecklistService qcChecklistService;
    private final QcChecklistMapper qcChecklistMapper;

    @Transactional
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECHNICIAN')")
    public ResponseEntity<QcChecklistResponse> create(@Valid @RequestBody CreateQcChecklistRequest request) {
        var checklist = qcChecklistService.create(request.batchId());
        return ResponseEntity.status(HttpStatus.CREATED).body(qcChecklistMapper.toResponse(checklist));
    }

    @Transactional
    @PostMapping(value = "/{id}/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECHNICIAN')")
    public ResponseEntity<QcChecklistResponse> upload(@PathVariable UUID id,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) String uploadedBy) throws IOException {
        var checklist = qcChecklistService.upload(id, file.getBytes(), file.getContentType(), uploadedBy);
        return ResponseEntity.ok(qcChecklistMapper.toResponse(checklist));
    }

    @Transactional
    @PostMapping("/{id}/recognize")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECHNICIAN')")
    public ResponseEntity<QcChecklistResponse> recognize(@PathVariable UUID id) {
        var checklist = qcChecklistService.recognize(id);
        return ResponseEntity.ok(qcChecklistMapper.toResponse(checklist));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<QcChecklistResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(qcChecklistMapper.toResponse(qcChecklistService.getById(id)));
    }

    @Transactional
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECHNICIAN')")
    public ResponseEntity<QcChecklistResponse> confirm(@PathVariable UUID id,
                                                         @RequestBody ConfirmQcChecklistRequest request) {
        var checklist = qcChecklistService.confirm(id, request);
        return ResponseEntity.ok(qcChecklistMapper.toResponse(checklist));
    }

    @Transactional
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECHNICIAN')")
    public ResponseEntity<QcChecklistResponse> reject(@PathVariable UUID id,
                                                        @Valid @RequestBody RejectChecklistRequest request) {
        var checklist = qcChecklistService.reject(id, request.reason());
        return ResponseEntity.ok(qcChecklistMapper.toResponse(checklist));
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<PageResponse<QcChecklistSummary>> list(@RequestParam(required = false) QcChecklistStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(
                qcChecklistService.list(status, Paging.newestFirst(page, size)), QcChecklistSummary::from));
    }

    /** Оригинал загруженного фото или PDF — для просмотра рядом с распознанными строками. */
    @Transactional(readOnly = true)
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        StoredFile file = qcChecklistService.getFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
