package com.wms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OutboundItemRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    @Max(value = 99999, message = "数量不能超过99999")
    private Integer quantity;

    @NotBlank(message = "库位编码不能为空")
    @Size(max = 50, message = "库位编码不能超过50字符")
    private String locationCode;
}