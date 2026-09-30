package com.example.order.dto;

import lombok.Data;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotEmpty
    private List<OrderItemDto> items;

    /** 就餐方式：dine_in=堂食, takeout=自取, delivery=配送 */
    private String mealType = "dine_in";
    private String address;
    private String remark;

    @Data
    public static class OrderItemDto {
        @NotNull
        private Long productId;
        @NotNull
        private Integer quantity;
    }
}
