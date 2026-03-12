package com.chess.service;

import com.chess.model.ChessBoard;
import com.chess.model.Move;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChessEngine {
    
    // 棋子分值
    private static final Map<String, Integer> PIECE_VALUES;
    static {
        PIECE_VALUES = new HashMap<>();
        PIECE_VALUES.put("帥", 10000);
        PIECE_VALUES.put("將", 10000);
        PIECE_VALUES.put("車", 500);
        PIECE_VALUES.put("馬", 300);
        PIECE_VALUES.put("炮", 300);
        PIECE_VALUES.put("相", 200);
        PIECE_VALUES.put("象", 200);
        PIECE_VALUES.put("仕", 200);
        PIECE_VALUES.put("士", 200);
        PIECE_VALUES.put("兵", 100);
        PIECE_VALUES.put("卒", 100);
    }
    
    private boolean isSameSide(ChessBoard board, int x1, int y1, int x2, int y2) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        if (b[x1][y1] == null || b[x2][y2] == null) {
            return false;
        }
        return isRed[x1][y1] == isRed[x2][y2];
    }
    
    public List<Move> getAllValidMoves(ChessBoard board, String color) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null && isRed[i][j] == color.equals("red")) {
                    List<Move> pieceMoves = getValidMovesForPiece(board, i, j, b[i][j]);
                    for (Move move : pieceMoves) {
                        if (!wouldFaceKing(board, move)) {
                            moves.add(move);
                        }
                    }
                }
            }
        }
        return moves;
    }
    
    private boolean wouldFaceKing(ChessBoard board, Move move) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        String piece = b[move.getFromX()][move.getFromY()];
        if (!piece.equals("帥") && !piece.equals("將")) {
            return false;
        }
        
        int kingY = move.getToY();
        int kingX = move.getToX();
        boolean kingIsRed = isRed[move.getFromX()][move.getFromY()];
        
        int otherKingStartX = kingIsRed ? 0 : 7;
        int otherKingEndX = kingIsRed ? 2 : 9;
        
        for (int x = otherKingStartX; x <= otherKingEndX; x++) {
            if (b[x][kingY] != null) {
                if ((kingIsRed && b[x][kingY].equals("將")) || (!kingIsRed && b[x][kingY].equals("帥"))) {
                    for (int kx = Math.min(kingX, x) + 1; kx < Math.max(kingX, x); kx++) {
                        if (b[kx][kingY] != null) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
    
    private List<Move> getValidMovesForPiece(ChessBoard board, int x, int y, String piece) {
        List<Move> moves = new ArrayList<>();
        String type = piece;
        
        switch (type) {
            case "車": moves.addAll(getRookMoves(board, x, y)); break;
            case "馬": moves.addAll(getKnightMoves(board, x, y)); break;
            case "炮": moves.addAll(getCannonMoves(board, x, y)); break;
            case "帥": case "將": moves.addAll(getKingMoves(board, x, y)); break;
            case "仕": case "士": moves.addAll(getAdvisorMoves(board, x, y)); break;
            case "相": case "象": moves.addAll(getElephantMoves(board, x, y)); break;
            case "兵": case "卒": moves.addAll(getPawnMoves(board, x, y)); break;
        }
        return moves;
    }
    
    private List<Move> getRookMoves(ChessBoard board, int x, int y) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        String piece = b[x][y];
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] dir : dirs) {
            int nx = x + dir[0], ny = y + dir[1];
            while (nx >= 0 && nx < 10 && ny >= 0 && ny < 9) {
                if (b[nx][ny] == null) {
                    moves.add(new Move(x, y, nx, ny, piece));
                } else {
                    if (!isSameSide(board, x, y, nx, ny)) {
                        moves.add(new Move(x, y, nx, ny, piece));
                    }
                    break;
                }
                nx += dir[0]; ny += dir[1];
            }
        }
        return moves;
    }
    
    private List<Move> getKnightMoves(ChessBoard board, int x, int y) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        String piece = b[x][y];
        int[][] jumps = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        int[][] legs = {{-1,0},{-1,0},{0,-1},{0,1},{0,-1},{0,1},{1,0},{1,0}};
        for (int i = 0; i < jumps.length; i++) {
            int nx = x + jumps[i][0], ny = y + jumps[i][1];
            int lx = x + legs[i][0], ly = y + legs[i][1];
            if (nx >= 0 && nx < 10 && ny >= 0 && ny < 9 && lx >= 0 && lx < 10 && ly >= 0 && ly < 9 && b[lx][ly] == null) {
                if (b[nx][ny] == null || !isSameSide(board, x, y, nx, ny)) {
                    moves.add(new Move(x, y, nx, ny, piece));
                }
            }
        }
        return moves;
    }
    
    private List<Move> getCannonMoves(ChessBoard board, int x, int y) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        String piece = b[x][y];
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        
        for (int[] dir : dirs) {
            int nx = x + dir[0], ny = y + dir[1];
            boolean jumped = false;
            while (nx >= 0 && nx < 10 && ny >= 0 && ny < 9) {
                if (!jumped) {
                    if (b[nx][ny] == null) {
                        moves.add(new Move(x, y, nx, ny, piece));
                    } else {
                        jumped = true;
                    }
                } else {
                    if (b[nx][ny] != null) {
                        if (!isSameSide(board, x, y, nx, ny)) {
                            moves.add(new Move(x, y, nx, ny, piece));
                        }
                        break;
                    }
                }
                nx += dir[0]; ny += dir[1];
            }
        }
        return moves;
    }
    
    private List<Move> getKingMoves(ChessBoard board, int x, int y) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        String piece = b[x][y];
        boolean isRed = board.getIsRed()[x][y];
        int minX = isRed ? 7 : 0, maxX = isRed ? 9 : 2;
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] dir : dirs) {
            int nx = x + dir[0], ny = y + dir[1];
            if (nx >= minX && nx <= maxX && ny >= 3 && ny <= 5) {
                if (b[nx][ny] == null || !isSameSide(board, x, y, nx, ny)) {
                    moves.add(new Move(x, y, nx, ny, piece));
                }
            }
        }
        return moves;
    }
    
    private List<Move> getAdvisorMoves(ChessBoard board, int x, int y) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        String piece = b[x][y];
        boolean isRed = board.getIsRed()[x][y];
        int minX = isRed ? 7 : 0, maxX = isRed ? 9 : 2;
        int[][] dirs = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] dir : dirs) {
            int nx = x + dir[0], ny = y + dir[1];
            if (nx >= minX && nx <= maxX && ny >= 3 && ny <= 5) {
                if (b[nx][ny] == null || !isSameSide(board, x, y, nx, ny)) {
                    moves.add(new Move(x, y, nx, ny, piece));
                }
            }
        }
        return moves;
    }
    
    private List<Move> getElephantMoves(ChessBoard board, int x, int y) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        String piece = b[x][y];
        boolean isRed = board.getIsRed()[x][y];
        int[][] dirs = {{-2,-2},{-2,2},{2,-2},{2,2}};
        int[][] blocks = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int i = 0; i < dirs.length; i++) {
            int nx = x + dirs[i][0], ny = y + dirs[i][1];
            int bx = x + blocks[i][0], by = y + blocks[i][1];
            boolean validRow = isRed ? (nx >= 5 && nx <= 9) : (nx >= 0 && nx <= 4);
            if (validRow && ny >= 0 && ny < 9 && b[bx][by] == null) {
                if (b[nx][ny] == null || !isSameSide(board, x, y, nx, ny)) {
                    moves.add(new Move(x, y, nx, ny, piece));
                }
            }
        }
        return moves;
    }
    
    private List<Move> getPawnMoves(ChessBoard board, int x, int y) {
        List<Move> moves = new ArrayList<>();
        String[][] b = board.getBoard();
        String piece = b[x][y];
        boolean isRed = board.getIsRed()[x][y];
        int forward = isRed ? -1 : 1;
        int nx = x + forward;
        if (nx >= 0 && nx < 10) {
            if (b[nx][y] == null || !isSameSide(board, x, y, nx, y)) {
                moves.add(new Move(x, y, nx, y, piece));
            }
        }
        boolean crossed = isRed ? (x <= 4) : (x >= 5);
        if (crossed) {
            if (y > 0 && (b[x][y-1] == null || !isSameSide(board, x, y, x, y-1))) {
                moves.add(new Move(x, y, x, y-1, piece));
            }
            if (y < 8 && (b[x][y+1] == null || !isSameSide(board, x, y, x, y+1))) {
                moves.add(new Move(x, y, x, y+1, piece));
            }
        }
        return moves;
    }
    
    public ChessBoard makeMove(ChessBoard board, Move move, String color) {
        ChessBoard newBoard = board.copy();
        String[][] b = newBoard.getBoard();
        boolean[][] isRed = newBoard.getIsRed();
        String piece = b[move.getFromX()][move.getFromY()];
        boolean pieceIsRed = isRed[move.getFromX()][move.getFromY()];
        
        // 记录被吃的棋子
        String captured = b[move.getToX()][move.getToY()];
        move.setCaptured(captured);
        
        b[move.getFromX()][move.getFromY()] = null;
        isRed[move.getFromX()][move.getFromY()] = false;
        
        b[move.getToX()][move.getToY()] = piece;
        isRed[move.getToX()][move.getToY()] = pieceIsRed;
        
        // 保存走法到历史记录
        newBoard.getMoves().add(move);
        
        newBoard.setCurrentTurn(color.equals("red") ? "black" : "red");
        newBoard.setMoveCount(newBoard.getMoveCount() + 1);
        return newBoard;
    }
    
    public Move getBestMove(ChessBoard board) {
        List<Move> moves = getAllValidMoves(board, "black");
        if (moves.isEmpty()) return null;
        Random rand = new Random();
        return moves.get(rand.nextInt(moves.size()));
    }
    
    // 检测指定颜色的王是否被将军
    public boolean isKingInCheck(ChessBoard board, String color) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        // 找到指定颜色的王的位置
        String kingPiece = color.equals("red") ? "帥" : "將";
        int kingX = -1, kingY = -1;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null && b[i][j].equals(kingPiece) && isRed[i][j] == color.equals("red")) {
                    kingX = i;
                    kingY = j;
                    break;
                }
            }
            if (kingX >= 0) break;
        }
        
        if (kingX < 0) return false; // 王不存在
        
        // 检查敌方棋子是否能攻击到王
        String enemyColor = color.equals("red") ? "black" : "red";
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null && isRed[i][j] == enemyColor.equals("red")) {
                    List<Move> enemyMoves = getValidMovesForPiece(board, i, j, b[i][j]);
                    for (Move m : enemyMoves) {
                        if (m.getToX() == kingX && m.getToY() == kingY) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
}
