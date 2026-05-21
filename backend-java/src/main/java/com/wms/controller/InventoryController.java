package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderResponse> createInboundOrder(
            @Valid @RequestBody InboundOrderCreateRequest request) {
        InboundOrderResponse response = inventoryService.createInboundOrder(request);
        return ApiResponse.success(response);
    }

    @GetMapping("/inventory")
    public ApiResponse<PageResult<InventoryResponse>> queryInventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<InventoryResponse> result = inventoryService.queryInventory(keyword, warehouseId, page, pageSize);
        return ApiResponse.success(result);
    }
}