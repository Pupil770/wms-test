package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.common.PageResult;
import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final OutboundOrderRepository outboundOrderRepository;
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

    public PageResult<InventoryResponse> queryInventory(String keyword, Long warehouseId,
                                                    int page, int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<InventoryResponse> pageData = inventoryRepository.findInventoryPage(
                keyword, warehouseId, PageRequest.of(page - 1, pageSize));
        return new PageResult<>(pageData.getContent(), pageData.getTotalElements(), page, pageSize);
    }

    @Transactional
    public OutboundOrderResponse createOutboundOrder(OutboundOrderCreateRequest request) {
        // 1. 校验明细去重：同一 productId + locationCode 不允许重复
        Set<String> keySet = new HashSet<>();
        for (OutboundItemRequest itemReq : request.getItems()) {
            String key = itemReq.getProductId() + "@" + itemReq.getLocationCode();
            if (keySet.contains(key)) {
                throw new BusinessException("出库明细中存在重复的商品+库位组合");
            }
            keySet.add(key);
        }

        // 2. 生成出库单号 OUT-YYYYMMDD-XXX
        String orderNo = generateOutboundOrderNo();

        // 3. 创建出库单主记录
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(orderNo);
        order.setCustomerName(request.getCustomerName());
        order.setItems(new ArrayList<>());

        // 4. 处理每个出库明细：校验 + 原子扣减库存
        for (OutboundItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new BusinessException("商品不存在，ID: " + itemReq.getProductId()));

            locationRepository.findByCode(itemReq.getLocationCode())
                    .orElseThrow(() -> new BusinessException("库位不存在，编码: " + itemReq.getLocationCode()));

            // 原子扣减：UPDATE inventory SET quantity = quantity - :qty WHERE quantity >= :qty
            int affected = inventoryRepository.deductStock(
                    itemReq.getProductId(), itemReq.getLocationCode(), itemReq.getQuantity());
            if (affected == 0) {
                // 查询当前库存用于友好提示
                Inventory inv = inventoryRepository
                        .findByProductIdAndLocationCode(itemReq.getProductId(), itemReq.getLocationCode())
                        .orElse(null);
                String currentQty = inv != null ? String.valueOf(inv.getQuantity()) : "0";
                throw new BusinessException(
                        "库存不足：商品[" + product.getName() + "]在库位[" + itemReq.getLocationCode()
                                + "]仅剩 " + currentQty + "，需出库 " + itemReq.getQuantity());
            }

            OutboundOrderItem item = new OutboundOrderItem();
            item.setProductId(product.getId());
            item.setQuantity(itemReq.getQuantity());
            item.setLocationCode(itemReq.getLocationCode());
            item.setOrder(order);
            order.getItems().add(item);
        }

        // 5. 保存出库单（级联保存明细）
        OutboundOrder saved = outboundOrderRepository.save(order);
        log.info("出库单创建成功: orderNo={}, customer={}", saved.getOrderNo(), saved.getCustomerName());

        // 6. 构建响应
        return buildOutboundResponse(saved);
    }

    private synchronized String generateOutboundOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "OUT-" + dateStr + "-";
        long count = outboundOrderRepository.countByOrderNoPrefix(prefix);
        return prefix + String.format("%03d", count + 1);
    }

    private OutboundOrderResponse buildOutboundResponse(OutboundOrder order) {
        List<OutboundOrderResponse.ItemDetail> itemDetails = order.getItems().stream()
                .map(item -> {
                    String productName = productRepository.findById(item.getProductId())
                            .map(Product::getName).orElse("未知商品");
                    return OutboundOrderResponse.ItemDetail.builder()
                            .productId(item.getProductId())
                            .productName(productName)
                            .quantity(item.getQuantity())
                            .locationCode(item.getLocationCode())
                            .build();
                })
                .toList();

        return OutboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .customerName(order.getCustomerName())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(itemDetails)
                .build();
    }
}
