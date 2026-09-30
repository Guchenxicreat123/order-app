package com.example.order.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageVO<T> {
    private List<T> records;
    private long total;
    private long page;
    private long size;
}
