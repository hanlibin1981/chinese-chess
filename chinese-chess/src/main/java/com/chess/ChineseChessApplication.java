package com.chess;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.chess.dao")
@EnableScheduling
public class ChineseChessApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChineseChessApplication.class, args);
    }
}
