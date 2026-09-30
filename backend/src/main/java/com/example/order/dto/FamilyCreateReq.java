package com.example.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FamilyCreateReq {
    @NotBlank
    private String name;
}