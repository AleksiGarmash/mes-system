package com.mes.controller;

import com.mes.domain.ProductionOrder;
import com.mes.dto.request.CreateOrderRequest;
import com.mes.dto.response.BatchResponse;
import com.mes.dto.response.OrderResponse;
import com.mes.mapper.ProductionMapper;
import com.mes.pdf.ChecklistPdfGenerator;
import com.mes.service.ProductionOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.mes.util.Paging;
import com.mes.dto.response.PageResponse;
import com.mes.domain.enums.OrderStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class ProductionOrderController {

    private final ProductionOrderService productionOrderService;
    private final ChecklistPdfGenerator checklistPdfGenerator;
    private final ProductionMapper productionMapper;

    @Transactional
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionMapper.toResponse(productionOrderService.create(request)));
    }

    @Transactional
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','PLANNER')")
    public ResponseEntity<BatchResponse> start(@PathVariable UUID id) {
        return ResponseEntity.ok(productionMapper.toResponse(productionOrderService.start(id)));
    }

    @Transactional
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','PLANNER')")
    public ResponseEntity<OrderResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(productionMapper.toResponse(productionOrderService.complete(id)));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(productionMapper.toResponse(productionOrderService.getById(id)));
    }

    /**
     * Печатный бланк чек-листа под задание: заголовок + QR-код задания + таблица
     * компонентов рецептуры с пустыми полями под рукописное заполнение.
     */
    @Transactional(readOnly = true)
    @GetMapping(value = "/{id}/checklist-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER','OPERATOR')")
    public ResponseEntity<byte[]> printChecklist(@PathVariable UUID id) {
        ProductionOrder order = productionOrderService.getById(id);
        byte[] pdf = checklistPdfGenerator.generate(order);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"checklist-" + order.getOrderNo() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> list(@RequestParam(required = false) OrderStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(
                productionOrderService.list(status, Paging.newestFirst(page, size)), productionMapper::toResponse));
    }
}
