package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundOrderResponse {

    private Long id;
    private String orderNo;
    private String supplierName;
    private String status;
    private LocalDateTime createdAt;
    private List<ItemDetail> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDetail {
        private Long productId;
        private String productName;
        private Integer quantity;
        private String locationCode;
    }
}
