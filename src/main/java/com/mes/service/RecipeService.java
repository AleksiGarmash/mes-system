package com.mes.service;

import com.mes.domain.Recipe;
import com.mes.domain.RecipeControlPoint;
import com.mes.domain.RecipeItem;
import com.mes.domain.RecipeStage;
import com.mes.domain.enums.RecipeStatus;
import com.mes.dto.request.CreateRecipeRequest;
import com.mes.dto.request.RecipeControlPointRequest;
import com.mes.dto.request.RecipeItemRequest;
import com.mes.dto.request.RecipeStageRequest;
import com.mes.event.MesEvent;
import com.mes.event.MesEventPublisher;
import com.mes.exception.InvalidRequestException;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final MesEventPublisher eventPublisher;

    @Transactional
    public Recipe create(CreateRecipeRequest request) {
        int nextVersion = recipeRepository.findByCodeOrderByVersionDesc(request.code())
                .stream()
                .findFirst()
                .map(r -> r.getVersion() + 1)
                .orElse(1);

        Recipe recipe = Recipe.builder()
                .code(request.code())
                .name(request.name())
                .version(nextVersion)
                .status(RecipeStatus.DRAFT)
                .qualitySpecCode(request.qualitySpecCode())
                .requireRawMaterialLot(request.requireRawMaterialLot() == null || request.requireRawMaterialLot())
                .build();

        Map<Integer, RecipeStage> stagesByNo = buildStages(recipe, request.stages());

        for (RecipeItemRequest itemReq : request.items()) {
            RecipeStage stage = resolveStage(stagesByNo, itemReq);
            if (itemReq.sourceStageNo() != null) {
                int ownStageNo = stage != null ? stage.getStageNo() : 0;
                if (!stagesByNo.containsKey(itemReq.sourceStageNo()) || itemReq.sourceStageNo() >= ownStageNo) {
                    throw new InvalidRequestException(
                            "Компонент «%s»: sourceStageNo=%d должен указывать на более раннюю существующую стадию."
                                    .formatted(itemReq.materialCode(), itemReq.sourceStageNo()));
                }
            }

            RecipeItem item = RecipeItem.builder()
                    .materialId(itemReq.materialId())
                    .materialCode(itemReq.materialCode())
                    .targetQty(itemReq.targetQty())
                    .unit(itemReq.unit())
                    .tolerancePlus(itemReq.tolerancePlus())
                    .toleranceMinus(itemReq.toleranceMinus())
                    .sequenceNo(itemReq.sequenceNo())
                    .stage(stage)
                    .sourceStageNo(itemReq.sourceStageNo())
                    .mixSpeedNorm(itemReq.mixSpeedNorm())
                    .mixMinutesNorm(itemReq.mixMinutesNorm())
                    .note(itemReq.note())
                    .build();
            recipe.addItem(item);
        }

        if (request.controlPoints() != null) {
            for (RecipeControlPointRequest cp : request.controlPoints()) {
                if (cp.stageNo() != null && !stagesByNo.containsKey(cp.stageNo())) {
                    throw new InvalidRequestException(
                            "Контрольная точка «%s» ссылается на несуществующую стадию %d."
                                    .formatted(cp.parameterName(), cp.stageNo()));
                }
                if (cp.minValue() == null && cp.maxValue() == null) {
                    throw new InvalidRequestException(
                            "Контрольная точка «%s»: задайте minValue и/или maxValue.".formatted(cp.parameterName()));
                }
                recipe.addControlPoint(RecipeControlPoint.builder()
                        .stageNo(cp.stageNo())
                        .kind(cp.kind())
                        .parameterCode(cp.parameterCode())
                        .parameterName(cp.parameterName())
                        .minValue(cp.minValue())
                        .maxValue(cp.maxValue())
                        .unit(cp.unit())
                        .sequenceNo(cp.sequenceNo())
                        .build());
            }
        }

        return recipeRepository.save(recipe);
    }

    private Map<Integer, RecipeStage> buildStages(Recipe recipe, List<RecipeStageRequest> stageRequests) {
        Map<Integer, RecipeStage> stagesByNo = new HashMap<>();
        if (stageRequests == null) {
            return stagesByNo;
        }
        for (RecipeStageRequest sr : stageRequests) {
            if (stagesByNo.containsKey(sr.stageNo())) {
                throw new InvalidRequestException("Номер стадии " + sr.stageNo() + " указан дважды.");
            }
            RecipeStage stage = RecipeStage.builder()
                    .stageNo(sr.stageNo())
                    .name(sr.name())
                    .plannedQty(sr.plannedQty())
                    .build();
            recipe.addStage(stage);
            stagesByNo.put(sr.stageNo(), stage);
        }
        return stagesByNo;
    }

    /** Если у рецептуры есть стадии, каждый компонент обязан указать существующую стадию. */
    private RecipeStage resolveStage(Map<Integer, RecipeStage> stagesByNo, RecipeItemRequest itemReq) {
        if (stagesByNo.isEmpty()) {
            if (itemReq.stageNo() != null) {
                throw new InvalidRequestException(
                        "Компонент «%s» указывает стадию, но стадии в рецептуре не заданы.".formatted(itemReq.materialCode()));
            }
            return null;
        }
        if (itemReq.stageNo() == null || !stagesByNo.containsKey(itemReq.stageNo())) {
            throw new InvalidRequestException(
                    "Компонент «%s»: укажите существующую стадию (stageNo).".formatted(itemReq.materialCode()));
        }
        return stagesByNo.get(itemReq.stageNo());
    }

    @Transactional
    public Recipe activate(UUID recipeId) {
        Recipe recipe = getById(recipeId);

        // Архивируем предыдущую активную версию той же рецептуры
        recipeRepository.findByCodeAndStatus(recipe.getCode(), RecipeStatus.ACTIVE)
                .ifPresent(active -> {
                    if (!active.getId().equals(recipe.getId())) {
                        active.setStatus(RecipeStatus.ARCHIVED);
                        recipeRepository.save(active);
                    }
                });

        recipe.setStatus(RecipeStatus.ACTIVE);
        Recipe saved = recipeRepository.save(recipe);

        eventPublisher.publishRecipeEvent(saved.getCode(),
                new MesEvent.RecipeActivated(saved.getId(), saved.getCode(), saved.getVersion(), Instant.now()));

        return saved;
    }

    @Transactional(readOnly = true)
    public Recipe getById(UUID id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Рецептура не найдена: " + id));
    }

    @Transactional(readOnly = true)
    public List<Recipe> findAllVersions(String code) {
        return recipeRepository.findByCodeOrderByVersionDesc(code);
    }

    @Transactional(readOnly = true)
    public Recipe getActiveByCode(String code) {
        return recipeRepository.findByCodeAndStatus(code, RecipeStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Активная рецептура не найдена: " + code));
    }

    @Transactional(readOnly = true)
    public Page<Recipe> list(Pageable pageable) {
        return recipeRepository.findAll(pageable);
    }
}
