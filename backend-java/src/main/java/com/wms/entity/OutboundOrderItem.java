package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "outbound_order_item")
@Data
@EqualsAndHashCode(exclude = "order")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboundOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OutboundOrder order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "location_code", nullable = false, length = 50)
    private String locationCode;
}
