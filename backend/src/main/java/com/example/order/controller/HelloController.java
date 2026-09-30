package com.example.order.controller;

import com.example.order.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Result<Map<String, Object>> hello() {
        return Result.ok(Map.of(
                "msg", "hello from order-app",
                "time", Instant.now().toString(),
                "version", "1.0.0"
        ));
    }

    @GetMapping("/")
    public Result<Map<String, Object>> index() {
        return Result.ok(Map.of("app", "order-app", "status", "running"));
    }
}
