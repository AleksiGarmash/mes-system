package com.mes.controller;

import com.mes.domain.RecipeItem;
import com.mes.domain.WeighingRecord;
import com.mes.dto.request.WeighRequest;
import com.mes.dto.response.MixingSessionResponse;
import com.mes.dto.response.WeighingRecordResponse;
import com.mes.mapper.ProductionMapper;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.RecipeItemRepository;
import com.mes.service.MixingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * {id} в путях контроллера — это UUID сессии смешивания (MixingSession).
 * Сессия создаётся неявно при первом взвешивании по партии через batch/mixer в теле запроса,
 * либо используется существующий id, полученный от /api/orders/{id}/start.
 */
@RestController
@RequestMapping("/api/mixing")
@RequiredArgsConstructor
public class MixingController {

    private final MixingService mixingService;
    private final RecipeItemRepository recipeItemRepository;
    private final ProductionMapper productionMapper;

    @Transactional
    @PostMapping("/{id}/weigh")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<WeighingRecordResponse> weigh(@PathVariable("id") UUID mixingSessionId,
                                                         @Valid @RequestBody WeighRequest request) {
        RecipeItem recipeItem = recipeItemRepository.findById(request.recipeItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Компонент рецептуры не найден: " + request.recipeItemId()));

        WeighingRecord record = mixingService.weigh(mixingSessionId, request, recipeItem);
        return ResponseEntity.ok(productionMapper.toResponse(record));
    }

    @Transactional
    @PostMapping("/{id}/finish")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<MixingSessionResponse> finish(@PathVariable("id") UUID mixingSessionId) {
        return ResponseEntity.ok(productionMapper.toResponse(mixingService.finish(mixingSessionId)));
    }

    @Transactional
    @PostMapping("/session")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<MixingSessionResponse> createSession(@RequestParam UUID batchId, @RequestParam UUID mixerId) {
        return ResponseEntity.ok(productionMapper.toResponse(mixingService.getOrCreateSession(batchId, mixerId)));
    }
}
