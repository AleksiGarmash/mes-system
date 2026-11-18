package com.mes.controller;

import com.mes.domain.QualitySpec;
import com.mes.dto.request.CreateQualitySpecRequest;
import com.mes.dto.response.QualitySpecResponse;
import com.mes.mapper.QualitySpecMapper;
import com.mes.service.QualitySpecService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.mes.util.Paging;
import com.mes.dto.response.QualitySpecSummary;
import com.mes.dto.response.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/quality-specs")
@RequiredArgsConstructor
public class QualitySpecController {

    private final QualitySpecService qualitySpecService;
    private final QualitySpecMapper qualitySpecMapper;

    @Transactional
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST')")
    public ResponseEntity<QualitySpecResponse> create(@Valid @RequestBody CreateQualitySpecRequest request) {
        QualitySpec spec = qualitySpecService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(qualitySpecMapper.toResponse(spec));
    }

    @Transactional
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST')")
    public ResponseEntity<QualitySpecResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(qualitySpecMapper.toResponse(qualitySpecService.activate(id)));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<QualitySpecResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(qualitySpecMapper.toResponse(qualitySpecService.getById(id)));
    }

    @Transactional(readOnly = true)
    @GetMapping(params = "code")
    public ResponseEntity<List<QualitySpecResponse>> getVersions(@RequestParam String code) {
        List<QualitySpecResponse> versions = qualitySpecService.findAllVersions(code).stream()
                .map(qualitySpecMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(versions);
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<PageResponse<QualitySpecSummary>> list(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(
                qualitySpecService.list(Paging.newestFirst(page, size)), QualitySpecSummary::from));
    }
}
