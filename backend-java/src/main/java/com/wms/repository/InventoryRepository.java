package com.wms.repository;

import com.wms.dto.InventoryResponse;
import com.wms.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationCode(Long productId, String locationCode);

    boolean existsByProductId(Long productId);

    @Query("SELECT new com.wms.dto.InventoryResponse(" +
           "i.productId, p.name, p.sku, i.locationCode, w.name, i.quantity, i.updatedAt) " +
           "FROM Inventory i " +
           "JOIN Product p ON i.productId = p.id " +
           "JOIN Location l ON i.locationCode = l.code " +
           "JOIN Warehouse w ON l.warehouseId = w.id " +
           "WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.sku LIKE %:keyword%) " +
           "AND (:warehouseId IS NULL OR l.warehouseId = :warehouseId)")
    Page<InventoryResponse> findInventoryPage(
            @Param("keyword") String keyword,
            @Param("warehouseId") Long warehouseId,
            Pageable pageable);
}
