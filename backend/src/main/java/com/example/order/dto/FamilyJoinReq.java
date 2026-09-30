package com.example.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FamilyJoinReq {
    @NotBlank
    private String code;
}