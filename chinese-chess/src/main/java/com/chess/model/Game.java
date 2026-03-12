package com.chess.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Game {
    private Long id;
    private Long playerRed;
    private Long playerBlack;
    private String winner; // red, black, draw
    private String pgn; // 棋谱 JSON
    private Boolean isAi;
    private String status; // playing, finished
    private LocalDateTime createdAt;
    private LocalDateTime endedAt;
    
    // 非数据库字段
    private String redUsername;
    private String blackUsername;
}
