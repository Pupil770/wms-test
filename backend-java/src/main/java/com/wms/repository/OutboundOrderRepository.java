package com.wms.repository;

import com.wms.entity.OutboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {

    @Query("SELECT COUNT(o) FROM OutboundOrder o WHERE o.orderNo LIKE CONCAT(:prefix, '%')")
    long countByOrderNoPrefix(@Param("prefix") String prefix);
}