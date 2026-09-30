package com.example.order;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.order.mapper")
@Slf4j
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    /** 启动成功横幅（Spring 完全就绪后输出，避免 logger 初始化顺序问题） */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8006");
        String profiles = String.join(",", env.getActiveProfiles());
        log.info("========================================");
        log.info("  点餐小程序后端启动成功");
        log.info("  端口: http://localhost:{}", port);
        log.info("  Profile: {}", profiles.isEmpty() ? "default" : profiles);
        log.info("========================================");
    }
}
