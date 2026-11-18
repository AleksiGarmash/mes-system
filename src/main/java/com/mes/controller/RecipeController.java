package com.mes.controller;

import com.mes.domain.Recipe;
import com.mes.dto.request.CreateRecipeRequest;
import com.mes.dto.response.RecipeResponse;
import com.mes.mapper.RecipeMapper;
import com.mes.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.mes.util.Paging;
import com.mes.dto.response.RecipeSummary;
import com.mes.dto.response.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeMapper recipeMapper;

    @Transactional
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST')")
    public ResponseEntity<RecipeResponse> create(@Valid @RequestBody CreateRecipeRequest request) {
        Recipe recipe = recipeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(recipeMapper.toResponse(recipe));
    }

    @Transactional
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNOLOGIST')")
    public ResponseEntity<RecipeResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(recipeMapper.toResponse(recipeService.activate(id)));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(recipeMapper.toResponse(recipeService.getById(id)));
    }

    @Transactional(readOnly = true)
    @GetMapping(params = "code")
    public ResponseEntity<List<RecipeResponse>> getVersions(@RequestParam String code) {
        List<RecipeResponse> versions = recipeService.findAllVersions(code).stream()
                .map(recipeMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(versions);
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<PageResponse<RecipeSummary>> list(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(recipeService.list(Paging.newestFirst(page, size)), RecipeSummary::from));
    }
}
