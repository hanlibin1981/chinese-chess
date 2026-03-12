package com.chess.model;

import lombok.Data;

@Data
public class Move {
    private int fromX;
    private int fromY;
    private int toX;
    private int toY;
    private String piece;
    private String captured;
    private String comment;
    
    public Move() {}
    
    public Move(int fromX, int fromY, int toX, int toY, String piece) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.piece = piece;
    }
}
