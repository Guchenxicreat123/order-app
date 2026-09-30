package com.example.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.entity.Product;
import com.example.order.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductMapper productMapper;

    @GetMapping
    public Result<List<Product>> list(@RequestParam(required = false) Long categoryId) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<>();
        q.eq(Product::getStatus, 1);
        if (categoryId != null) q.eq(Product::getCategoryId, categoryId);
        q.orderByDesc(Product::getSales).orderByAsc(Product::getId);
        return Result.ok(productMapper.selectList(q));
    }

    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) return Result.error(404, "商品不存在");
        return Result.ok(p);
    }
}