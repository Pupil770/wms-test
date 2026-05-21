package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public InboundOrderResponse createInboundOrder(InboundOrderCreateRequest request) {
        // 1. 生成入库单号 IN-YYYYMMDD-XXX
        String orderNo = generateOrderNo();

        // 2. 创建入库单主记录
        InboundOrder order = new InboundOrder();
        order.setOrderNo(orderNo);
        order.setSupplierName(request.getSupplierName());
        order.setStatus("COMPLETED");
        order.setItems(new ArrayList<>());

        // 3. 处理每个入库明细
        for (InboundItemRequest itemReq : request.getItems()) {
            // 校验商品存在
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new BusinessException("商品不存在，ID: " + itemReq.getProductId()));

            // 校验库位存在
            Location location = locationRepository.findByCode(itemReq.getLocationCode())
                    .orElseThrow(() -> new BusinessException("库位不存在，编码: " + itemReq.getLocationCode()));

            // 创建入库明细
            InboundOrderItem item = new InboundOrderItem();
            item.setProductId(product.getId());
            item.setQuantity(itemReq.getQuantity());
            item.setLocationCode(location.getCode());
            item.setOrder(order);
            order.getItems().add(item);

            // 更新库存：查找已有库存记录，存在则累加，不存在则新建
            Inventory inventory = inventoryRepository
                    .findByProductIdAndLocationCode(product.getId(), location.getCode())
                    .orElseGet(() -> {
                        Inventory inv = new Inventory();
                        inv.setProductId(product.getId());
                        inv.setLocationCode(location.getCode());
                        inv.setQuantity(0);
                        return inv;
                    });
            inventory.setQuantity(inventory.getQuantity() + itemReq.getQuantity());
            inventoryRepository.save(inventory);
        }

        // 4. 保存入库单（级联保存明细）
        InboundOrder saved = inboundOrderRepository.save(order);
        log.info("入库单创建成功: orderNo={}, supplier={}", saved.getOrderNo(), saved.getSupplierName());

        // 5. 构建响应
        return buildResponse(saved);
    }

    private String generateOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "IN-" + dateStr + "-";
        long count = inboundOrderRepository.countByOrderNoPrefix(prefix);
        return prefix + String.format("%03d", count + 1);
    }

    private InboundOrderResponse buildResponse(InboundOrder order) {
        List<InboundOrderResponse.ItemDetail> itemDetails = order.getItems().stream()
                .map(item -> {
                    String productName = productRepository.findById(item.getProductId())
                            .map(Product::getName).orElse("未知商品");
                    return InboundOrderResponse.ItemDetail.builder()
                            .productId(item.getProductId())
                            .productName(productName)
                            .quantity(item.getQuantity())
                            .locationCode(item.getLocationCode())
                            .build();
                })
                .toList();

        return InboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierName(order.getSupplierName())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(itemDetails)
                .build();
    }

    public List<InventoryResponse> queryInventory(String keyword, Long warehouseId,
                                                   int page, int pageSize) {
        throw new UnsupportedOperationException("请实现库存查询功能（任务2）");
    }
}
