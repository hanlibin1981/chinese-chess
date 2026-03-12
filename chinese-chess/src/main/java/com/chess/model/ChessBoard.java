package com.chess.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ChessBoard {
    private String[][] board;
    private boolean[][] isRed; // 追踪每个位置是否是红方棋子
    private String currentTurn; // red or black
    private List<Move> moves;
    private int moveCount;
    
    public ChessBoard() {
        this.board = new String[10][9];
        this.isRed = new boolean[10][9];
        this.moves = new ArrayList<>();
        this.currentTurn = "red";
        this.moveCount = 0;
        initializeBoard();
    }
    
    private void initializeBoard() {
        // 初始化空棋盘
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                board[i][j] = null;
                isRed[i][j] = false;
            }
        }
        
        // 初始化棋子 - 使用单个汉字，通过位置区分颜色
        // 黑方 (上方 0-4行) - isRed = false
        // 车
        board[0][0] = "車"; isRed[0][0] = false;
        board[0][8] = "車"; isRed[0][8] = false;
        board[9][0] = "車"; isRed[9][0] = true;
        board[9][8] = "車"; isRed[9][8] = true;
        
        // 马
        board[0][1] = "馬"; isRed[0][1] = false;
        board[0][7] = "馬"; isRed[0][7] = false;
        board[9][1] = "馬"; isRed[9][1] = true;
        board[9][7] = "馬"; isRed[9][7] = true;
        
        // 相
        board[0][2] = "相"; isRed[0][2] = false;
        board[0][6] = "相"; isRed[0][6] = false;
        board[9][2] = "相"; isRed[9][2] = true;
        board[9][6] = "相"; isRed[9][6] = true;
        
        // 士
        board[0][3] = "士"; isRed[0][3] = false;
        board[0][5] = "士"; isRed[0][5] = false;
        board[9][3] = "仕"; isRed[9][3] = true;
        board[9][5] = "仕"; isRed[9][5] = true;
        
        // 帅/将
        board[0][4] = "將"; isRed[0][4] = false;
        board[9][4] = "帥"; isRed[9][4] = true;
        
        // 炮
        board[2][1] = "炮"; isRed[2][1] = false;
        board[2][7] = "炮"; isRed[2][7] = false;
        board[7][1] = "炮"; isRed[7][1] = true;
        board[7][7] = "炮"; isRed[7][7] = true;
        
        // 兵/卒
        board[3][0] = "卒"; isRed[3][0] = false;
        board[3][2] = "卒"; isRed[3][2] = false;
        board[3][4] = "卒"; isRed[3][4] = false;
        board[3][6] = "卒"; isRed[3][6] = false;
        board[3][8] = "卒"; isRed[3][8] = false;
        
        board[6][0] = "兵"; isRed[6][0] = true;
        board[6][2] = "兵"; isRed[6][2] = true;
        board[6][4] = "兵"; isRed[6][4] = true;
        board[6][6] = "兵"; isRed[6][6] = true;
        board[6][8] = "兵"; isRed[6][8] = true;
    }
    
    public ChessBoard copy() {
        ChessBoard newBoard = new ChessBoard();
        // 清空棋盘再复制
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                newBoard.board[i][j] = null;
                newBoard.isRed[i][j] = false;
            }
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                newBoard.board[i][j] = this.board[i][j];
                newBoard.isRed[i][j] = this.isRed[i][j];
            }
        }
        newBoard.setCurrentTurn(this.currentTurn);
        newBoard.setMoveCount(this.moveCount);
        // 复制走法历史
        newBoard.setMoves(new ArrayList<>(this.moves));
        return newBoard;
    }
}
