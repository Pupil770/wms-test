package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.OutboundItemRequest;
import com.wms.dto.OutboundOrderCreateRequest;
import com.wms.dto.OutboundOrderResponse;
import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.OutboundOrder;
import com.wms.entity.Product;
import com.wms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OutboundOrderServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productId;
    private String locationCode;

    @BeforeEach
    void setUp() {
        // 使用 DataInitializer 初始化的数据
        // 取第一个商品和第一个库位
        List<Product> products = productRepository.findAll();
        assertFalse(products.isEmpty(), "需要至少一个商品");
        productId = products.get(0).getId();

        List<Location> locations = locationRepository.findAll();
        assertFalse(locations.isEmpty(), "需要至少一个库位");
        locationCode = locations.get(0).getCode();

        // 确保库存充足：设置为 100
        Inventory inv = inventoryRepository
                .findByProductIdAndLocationCode(productId, locationCode)
                .orElseGet(() -> {
                    Inventory newInv = new Inventory();
                    newInv.setProductId(productId);
                    newInv.setLocationCode(locationCode);
                    newInv.setQuantity(0);
                    return newInv;
                });
        inv.setQuantity(100);
        inventoryRepository.save(inv);
    }

    @Test
    void testCreateOutboundOrder_success() {
        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("测试客户");
        OutboundItemRequest item = new OutboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(10);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        OutboundOrderResponse response = inventoryService.createOutboundOrder(request);

        assertNotNull(response.getId());
        assertEquals("测试客户", response.getCustomerName());
        assertTrue(response.getOrderNo().startsWith("OUT-"));
        assertEquals(1, response.getItems().size());
        assertEquals(10, response.getItems().get(0).getQuantity());

        // 验证库存已扣减
        Inventory inv = inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).orElseThrow();
        assertEquals(90, inv.getQuantity());
    }

    @Test
    void testCreateOutboundOrder_insufficientStock() {
        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("测试客户");
        OutboundItemRequest item = new OutboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(200); // 库存只有 100
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.createOutboundOrder(request));
        assertTrue(ex.getMessage().contains("库存不足"));

        // 验证库存未被扣减
        Inventory inv = inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).orElseThrow();
        assertEquals(100, inv.getQuantity());
    }

    @Test
    void testCreateOutboundOrder_productNotFound() {
        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("测试客户");
        OutboundItemRequest item = new OutboundItemRequest();
        item.setProductId(99999L);
        item.setQuantity(1);
        item.setLocationCode(locationCode);
        request.setItems(List.of(item));

        assertThrows(BusinessException.class,
                () -> inventoryService.createOutboundOrder(request));
    }

    @Test
    void testCreateOutboundOrder_locationNotFound() {
        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("测试客户");
        OutboundItemRequest item = new OutboundItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        item.setLocationCode("INVALID-LOC");
        request.setItems(List.of(item));

        assertThrows(BusinessException.class,
                () -> inventoryService.createOutboundOrder(request));
    }

    @Test
    void testCreateOutboundOrder_duplicateItems() {
        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("测试客户");

        OutboundItemRequest item1 = new OutboundItemRequest();
        item1.setProductId(productId);
        item1.setQuantity(10);
        item1.setLocationCode(locationCode);

        OutboundItemRequest item2 = new OutboundItemRequest();
        item2.setProductId(productId);
        item2.setQuantity(20);
        item2.setLocationCode(locationCode);

        request.setItems(List.of(item1, item2));

        assertThrows(BusinessException.class,
                () -> inventoryService.createOutboundOrder(request));
    }

    @Test
    void testCreateOutboundOrder_partialFailureRollback() {
        // 准备第二个商品
        List<Product> products = productRepository.findAll();
        if (products.size() < 2) return;
        Long product2Id = products.get(1).getId();

        // 给第二个商品设置少量库存
        Inventory inv2 = inventoryRepository
                .findByProductIdAndLocationCode(product2Id, locationCode)
                .orElseGet(() -> {
                    Inventory newInv = new Inventory();
                    newInv.setProductId(product2Id);
                    newInv.setLocationCode(locationCode);
                    newInv.setQuantity(0);
                    return newInv;
                });
        inv2.setQuantity(5);
        inventoryRepository.save(inv2);

        OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
        request.setCustomerName("测试客户");

        // 第一项可以成功，第二项库存不足
        OutboundItemRequest item1 = new OutboundItemRequest();
        item1.setProductId(productId);
        item1.setQuantity(10);
        item1.setLocationCode(locationCode);

        OutboundItemRequest item2 = new OutboundItemRequest();
        item2.setProductId(product2Id);
        item2.setQuantity(50); // 库存只有 5
        item2.setLocationCode(locationCode);

        request.setItems(List.of(item1, item2));

        assertThrows(BusinessException.class,
                () -> inventoryService.createOutboundOrder(request));

        // 验证第一项的库存也被回滚
        Inventory inv1 = inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).orElseThrow();
        assertEquals(100, inv1.getQuantity());

        Inventory inv2After = inventoryRepository.findByProductIdAndLocationCode(product2Id, locationCode).orElseThrow();
        assertEquals(5, inv2After.getQuantity());
    }

    @Test
    void testConcurrentOutbound_noOversell() throws InterruptedException {
        // 库存设为 100
        Inventory inv = inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).orElseThrow();
        inv.setQuantity(100);
        inventoryRepository.save(inv);

        int threadCount = 10;
        int quantityPerThread = 15; // 每个线程出库 15，总共需要 150，但库存只有 100
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // 所有线程同时开始

                    OutboundOrderCreateRequest request = new OutboundOrderCreateRequest();
                    request.setCustomerName("并发客户" + idx);
                    OutboundItemRequest item = new OutboundItemRequest();
                    item.setProductId(productId);
                    item.setQuantity(quantityPerThread);
                    item.setLocationCode(locationCode);
                    request.setItems(List.of(item));

                    inventoryService.createOutboundOrder(request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    // 其他异常也算失败
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(terminated, "线程池未在规定时间内结束");

        // 验证：成功次数 * 15 <= 100，不会超卖
        int totalDeducted = successCount.get() * quantityPerThread;
        assertTrue(totalDeducted <= 100,
                "总扣减量 " + totalDeducted + " 超过库存 100，发生超卖！成功次数=" + successCount.get());

        // 验证数据库中库存不为负
        Inventory afterInv = inventoryRepository.findByProductIdAndLocationCode(productId, locationCode).orElseThrow();
        assertTrue(afterInv.getQuantity() >= 0, "库存为负数，发生超卖！当前库存=" + afterInv.getQuantity());

        // 验证：剩余库存 = 100 - 成功次数 * 15
        assertEquals(100 - totalDeducted, afterInv.getQuantity());
    }
}