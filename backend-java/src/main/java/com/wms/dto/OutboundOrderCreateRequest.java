package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class OutboundOrderCreateRequest {

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 200, message = "客户名称不能超过200字符")
    private String customerName;

    @NotEmpty(message = "出库明细不能为空")
    @Size(max = 100, message = "出库明细不能超过100条")
    @Valid
    private List<OutboundItemRequest> items;
}