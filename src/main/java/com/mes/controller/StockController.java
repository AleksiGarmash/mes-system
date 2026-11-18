package com.mes.controller;

import com.mes.dto.request.ReceiveLotRequest;
import com.mes.dto.request.StockQuantityRequest;
import com.mes.dto.response.AllocationResponse;
import com.mes.dto.response.LotResponse;
import com.mes.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Sort;
import com.mes.util.Paging;
import com.mes.dto.response.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Склад сырья: приёмка, остатки в порядке выдачи (FEFO), расчёт и выдача. */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/lots")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public ResponseEntity<LotResponse> receive(@Valid @RequestBody ReceiveLotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(LotResponse.from(stockService.receive(request)));
    }

    /** Лоты материала с остатком, по возрастанию срока годности. */
    @GetMapping("/materials/{materialId}/lots")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER','OPERATOR','TECHNOLOGIST','PLANNER')")
    public ResponseEntity<List<LotResponse>> lots(@PathVariable UUID materialId) {
        return ResponseEntity.ok(stockService.availableLots(materialId).stream().map(LotResponse::from).toList());
    }

    /** Расчёт выдачи по FEFO без списания. */
    @PostMapping("/allocate")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER','PLANNER','OPERATOR')")
    public ResponseEntity<AllocationResponse> allocate(@Valid @RequestBody StockQuantityRequest request) {
        return ResponseEntity.ok(AllocationResponse.from(stockService.preview(request.materialId(), request.quantity())));
    }

    /** Выдача со списанием остатков (всё или ничего). */
    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public ResponseEntity<AllocationResponse> issue(@Valid @RequestBody StockQuantityRequest request) {
        return ResponseEntity.ok(AllocationResponse.from(stockService.issue(request.materialId(), request.quantity())));
    }

    /** Все лоты с фильтром по коду материала; ближайший срок годности первым. */
    @GetMapping("/lots")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER','OPERATOR','TECHNOLOGIST','PLANNER')")
    public ResponseEntity<PageResponse<LotResponse>> allLots(@RequestParam(required = false) String materialCode,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(
                stockService.listLots(materialCode, Paging.of(page, size, Sort.Direction.ASC, "expiry")), LotResponse::from));
    }
}
