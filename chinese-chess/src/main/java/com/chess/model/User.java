package com.chess.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private LocalDateTime createdAt;
    private Integer winCount = 0;
    private Integer loseCount = 0;
    private Integer drawCount = 0;
}
