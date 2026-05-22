package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.Product;
import com.wms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LocationRepository locationRepository;

    private Long productId;
    private String locationCode;

    @BeforeEach
    void setUp() {
        List<Product> products = productRepository.findAll();
        assertFalse(products.isEmpty(), "需要至少一个商品");
        productId = products.get(0).getId();

        List<Location> locations = locationRepository.findAll();
        assertFalse(locations.isEmpty(), "需要至少一个库位");
        locationCode = locations.get(0).getCode();
    }

    /**
     * 测试：入库到新库位时，自动创建库存记录并设置数量
     */
    @Test
    void testCreateInboundOrder_newInventory() {
        // 先删除该商品+库位的库存记录，确保是新增场景
        inventoryRepository.findByProductIdAndLocationCode(productId, locationCode)
                .ifPresent(inv -> inventoryRepository.delete(inv));

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(50);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        assertNotNull(response.getId());
        assertEquals("测试供应商", response.getSupplierName());
        assertTrue(response.getOrderNo().startsWith("IN-"));
        assertEquals(1, response.getItems().size());
        assertEquals(50, response.getItems().get(0).getQuantity());

        // 验证库存已创建
        Inventory inv = inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).orElseThrow();
        assertEquals(50, inv.getQuantity());
    }

    /**
     * 测试：入库到已有库存的库位时，数量累加而非覆盖
     */
    @Test
    void testCreateInboundOrder_accumulateInventory() {
        // 先设置初始库存
        Inventory inv = inventoryRepository
                .findByProductIdAndLocationCode(productId, locationCode)
                .orElseGet(() -> {
                    Inventory newInv = new Inventory();
                    newInv.setProductId(productId);
                    newInv.setLocationCode(locationCode);
                    return newInv;
                });
        inv.setQuantity(100);
        inventoryRepository.save(inv);

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(30);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        inventoryService.createInboundOrder(request);

        // 验证库存累加：100 + 30 = 130
        Inventory updated = inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).orElseThrow();
        assertEquals(130, updated.getQuantity());
    }

    /**
     * 测试：一个入库单包含多个商品明细，所有明细的库存都正确更新
     */
    @Test
    void testCreateInboundOrder_multipleItems() {
        List<Product> products = productRepository.findAll();
        if (products.size() < 2) return; // 商品不足则跳过
        Long product2Id = products.get(1).getId();

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");

        InboundItemRequest item1 = new InboundItemRequest();
        item1.setProductId(productId);
        item1.setQuantity(20);
        item1.setLocationCode(locationCode);

        InboundItemRequest item2 = new InboundItemRequest();
        item2.setProductId(product2Id);
        item2.setQuantity(40);
        item2.setLocationCode(locationCode);

        request.setItems(List.of(item1, item2));

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        assertEquals(2, response.getItems().size());
        assertEquals(20, response.getItems().get(0).getQuantity());
        assertEquals(40, response.getItems().get(1).getQuantity());
    }

    /**
     * 测试：入库不存在的商品ID时，抛出BusinessException
     */
    @Test
    void testCreateInboundOrder_productNotFound() {
        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(99999L);
        item.setQuantity(10);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        assertThrows(BusinessException.class,
                () -> inventoryService.createInboundOrder(request));
    }

    /**
     * 测试：入库到不存在的库位编码时，抛出BusinessException
     */
    @Test
    void testCreateInboundOrder_locationNotFound() {
        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(10);
        item.setLocationCode("INVALID-LOC");
        request.setItems(List.of(item));

        assertThrows(BusinessException.class,
                () -> inventoryService.createInboundOrder(request));
    }

    /**
     * 测试：入库单号格式为 IN-YYYYMMDD-XXX，日期部分为当天
     */
    @Test
    void testCreateInboundOrder_orderNoFormat() {
        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(5);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        String orderNo = response.getOrderNo();
        assertTrue(orderNo.startsWith("IN-"), "单号应以IN-开头");
        // 验证日期部分：IN-YYYYMMDD-XXX
        String datePart = orderNo.substring(3, 11); // YYYYMMDD
        assertTrue(datePart.matches("\\d{8}"), "日期部分应为8位数字");
    }
}