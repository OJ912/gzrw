package com.chestnut.mailbox;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校长信箱模块配置类
 */
@SpringBootApplication
@MapperScan("com.chestnut.mailbox.mapper")
public class MailboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailboxApplication.class, args);
    }
} 