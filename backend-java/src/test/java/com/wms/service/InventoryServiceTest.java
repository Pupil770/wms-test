package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Long productId;
    private String locationCode;

    @BeforeEach
    void setUp() {
        // 创建测试商品
        Product product = Product.builder().name("测试商品").sku("TEST-SKU-001").unit("个").build();
        product = productRepository.save(product);
        productId = product.getId();

        // 创建测试仓库和库位
        Warehouse wh = Warehouse.builder().code("WH-TEST").name("测试仓库").build();
        wh = warehouseRepository.save(wh);

        Location loc = Location.builder().warehouseId(wh.getId()).code("LOC-TEST-01").status("FREE").build();
        locationRepository.save(loc);
        locationCode = loc.getCode();
    }

    @Test
    @DisplayName("正常创建入库单 - 新增库存记录")
    void createInboundOrder_newInventory() {
        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");

        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(100);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        // 验证返回结果
        assertNotNull(response.getId());
        assertTrue(response.getOrderNo().startsWith("IN-"));
        assertEquals("测试供应商", response.getSupplierName());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals(100, response.getItems().get(0).getQuantity());

        // 验证库存已创建
        Inventory inventory = inventoryRepository
                .findByProductIdAndLocationCode(productId, locationCode)
                .orElseThrow();
        assertEquals(100, inventory.getQuantity());
    }

    @Test
    @DisplayName("正常创建入库单 - 累加已有库存")
    void createInboundOrder_addToExistingInventory() {
        // 先创建一笔库存
        Inventory existing = Inventory.builder()
                .productId(productId).locationCode(locationCode).quantity(50).build();
        inventoryRepository.save(existing);

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(30);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        inventoryService.createInboundOrder(request);

        // 验证库存累加
        Inventory inventory = inventoryRepository
                .findByProductIdAndLocationCode(productId, locationCode)
                .orElseThrow();
        assertEquals(80, inventory.getQuantity());
    }

    @Test
    @DisplayName("正常创建入库单 - 多条明细")
    void createInboundOrder_multipleItems() {
        // 创建第二个商品
        Product p2 = Product.builder().name("商品B").sku("TEST-SKU-002").unit("个").build();
        p2 = productRepository.save(p2);

        // 创建第二个库位
        Location loc2 = Location.builder()
                .warehouseId(warehouseRepository.findAll().get(0).getId())
                .code("LOC-TEST-02").status("FREE").build();
        locationRepository.save(loc2);

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("多明细供应商");

        InboundItemRequest item1 = new InboundItemRequest();
        item1.setProductId(productId);
        item1.setQuantity(50);
        item1.setLocationCode(locationCode);

        InboundItemRequest item2 = new InboundItemRequest();
        item2.setProductId(p2.getId());
        item2.setQuantity(20);
        item2.setLocationCode(loc2.getCode());

        request.setItems(List.of(item1, item2));

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        assertEquals(2, response.getItems().size());
        assertEquals(50, inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).get().getQuantity());
        assertEquals(20, inventoryRepository.findByProductIdAndLocationCode(p2.getId(), loc2.getCode()).get().getQuantity());
    }

    @Test
    @DisplayName("异常 - 商品不存在")
    void createInboundOrder_productNotFound() {
        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");

        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(99999L);
        item.setQuantity(10);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.createInboundOrder(request));
        assertTrue(ex.getMessage().contains("商品不存在"));
    }

    @Test
    @DisplayName("异常 - 库位不存在")
    void createInboundOrder_locationNotFound() {
        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试供应商");

        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(10);
        item.setLocationCode("NOT-EXIST-LOC");
        request.setItems(List.of(item));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.createInboundOrder(request));
        assertTrue(ex.getMessage().contains("库位不存在"));
    }

    @Test
    @DisplayName("入库单号格式验证")
    void createInboundOrder_orderNoFormat() {
        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("格式验证");

        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        // 验证单号格式 IN-YYYYMMDD-XXX
        String orderNo = response.getOrderNo();
        assertTrue(orderNo.matches("IN-\\d{8}-\\d{3}"));
    }
}
