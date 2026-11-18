package com.mes.controller;

import com.mes.dto.request.ConfirmChecklistRequest;
import com.mes.dto.request.CreateChecklistRequest;
import com.mes.dto.request.RejectChecklistRequest;
import com.mes.dto.response.ChecklistResponse;
import com.mes.mapper.ChecklistMapper;
import com.mes.service.ChecklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import com.mes.util.Paging;
import com.mes.dto.response.StoredFile;
import com.mes.dto.response.PageResponse;
import com.mes.dto.response.ChecklistSummary;
import com.mes.domain.enums.ChecklistStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Document-first workflow: бумажный чек-лист остаётся официальным документом,
 * этот контроллер лишь превращает его фото в проверенную электронную запись.
 */
@RestController
@RequestMapping("/api/checklists")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;
    private final ChecklistMapper checklistMapper;

    @Transactional
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER','OPERATOR')")
    public ResponseEntity<ChecklistResponse> create(@Valid @RequestBody CreateChecklistRequest request) {
        var checklist = checklistService.create(request.productionOrderId());
        return ResponseEntity.status(HttpStatus.CREATED).body(checklistMapper.toResponse(checklist));
    }

    @Transactional
    @PostMapping(value = "/{id}/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ChecklistResponse> upload(@PathVariable UUID id,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(required = false) String uploadedBy) throws IOException {
        // file может быть как изображением (jpg/png), так и PDF (в т.ч. многостраничным) —
        // тип определяется по contentType/сигнатуре файла в PdfPageRasterizer.isPdf().
        var checklist = checklistService.upload(id, file.getBytes(), file.getContentType(), uploadedBy);
        return ResponseEntity.ok(checklistMapper.toResponse(checklist));
    }

    /**
     * Запускает распознавание последнего загруженного файла: сначала ищет QR-код задания
     * на первой странице (для однозначной идентификации задания без риска ошибки OCR),
     * затем прогоняет OCR по всем страницам (для многостраничных PDF — по каждой).
     * Файл передаётся повторно в теле запроса (упрощение; в проде — читать из хранилища по imagePath).
     */
    @Transactional
    @PostMapping("/{id}/recognize")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ChecklistResponse> recognize(@PathVariable UUID id) {
        var checklist = checklistService.recognize(id);
        return ResponseEntity.ok(checklistMapper.toResponse(checklist));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<ChecklistResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(checklistMapper.toResponse(checklistService.getById(id)));
    }

    @Transactional
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ChecklistResponse> confirm(@PathVariable UUID id,
                                                       @RequestBody ConfirmChecklistRequest request) {
        var checklist = checklistService.confirm(id, request);
        return ResponseEntity.ok(checklistMapper.toResponse(checklist));
    }

    @Transactional
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ChecklistResponse> reject(@PathVariable UUID id,
                                                      @Valid @RequestBody RejectChecklistRequest request) {
        var checklist = checklistService.reject(id, request.reason());
        return ResponseEntity.ok(checklistMapper.toResponse(checklist));
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<PageResponse<ChecklistSummary>> list(@RequestParam(required = false) ChecklistStatus status,
                                                               @RequestParam(required = false) UUID orderId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(
                checklistService.list(status, orderId, Paging.newestFirst(page, size)), ChecklistSummary::from));
    }

    /** Оригинал загруженного фото или PDF — для просмотра рядом с распознанными строками. */
    @Transactional(readOnly = true)
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        StoredFile file = checklistService.getFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
