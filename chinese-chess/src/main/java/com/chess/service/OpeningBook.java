package com.chess.service;

import com.chess.model.ChessBoard;
import com.chess.model.Move;
import java.util.*;

/**
 * 开局库 - 常见开局走法
 */
public class OpeningBook {
    
    public enum OpeningType {
        SHUNSHOU_POU("顺手炮", 1),
        PINGFENG_MA("屏风马", 3),
        XIANREN_ZHILU("仙人指路", 4),
        DEFAULT("默认", 0);
        
        private final String name;
        private final int priority;
        
        OpeningType(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
        
        public String getName() { return name; }
    }
    
    public static class OpeningMove {
        public int fromX, fromY, toX, toY;
        public String piece;
        public OpeningType type;
        public int weight;
        
        public OpeningMove(int fx, int fy, int tx, int ty, String p, OpeningType t, int w) {
            fromX = fx; fromY = fy; toX = tx; toY = ty;
            piece = p; type = t; weight = w;
        }
    }
    
    // 简化版开局库
    private static final List<OpeningMove> OPENINGS = new ArrayList<>();
    
    static {
        // 红方第一步
        OPENINGS.add(new OpeningMove(2, 1, 2, 4, "炮", OpeningType.SHUNSHOU_POU, 100));
        OPENINGS.add(new OpeningMove(2, 7, 2, 4, "炮", OpeningType.SHUNSHOU_POU, 95));
        OPENINGS.add(new OpeningMove(9, 1, 8, 2, "馬", OpeningType.PINGFENG_MA, 90));
        OPENINGS.add(new OpeningMove(9, 7, 8, 6, "馬", OpeningType.PINGFENG_MA, 88));
        OPENINGS.add(new OpeningMove(6, 2, 5, 2, "兵", OpeningType.XIANREN_ZHILU, 85));
        OPENINGS.add(new OpeningMove(6, 4, 5, 4, "兵", OpeningType.XIANREN_ZHILU, 82));
        
        // 黑方第一步
        OPENINGS.add(new OpeningMove(0, 1, 0, 4, "炮", OpeningType.SHUNSHOU_POU, 100));
        OPENINGS.add(new OpeningMove(0, 7, 0, 4, "炮", OpeningType.SHUNSHOU_POU, 95));
        OPENINGS.add(new OpeningMove(0, 1, 1, 2, "馬", OpeningType.PINGFENG_MA, 90));
        OPENINGS.add(new OpeningMove(3, 2, 4, 2, "卒", OpeningType.XIANREN_ZHILU, 85));
        OPENINGS.add(new OpeningMove(3, 4, 4, 4, "卒", OpeningType.XIANREN_ZHILU, 82));
    }
    
    public static OpeningMove getBookMove(ChessBoard board, String color, int moveCount) {
        if (moveCount > 4) return null;
        
        String[][] bd = board.getBoard();
        boolean[][] ir = board.getIsRed();
        List<OpeningMove> cand = new ArrayList<>();
        
        for (OpeningMove m : OPENINGS) {
            if (bd[m.fromX][m.fromY] == null) continue;
            
            boolean pieceRed = ir[m.fromX][m.fromY];
            boolean wantRed = color.equals("red");
            if (pieceRed != wantRed) continue;
            
            if (bd[m.toX][m.toY] != null) {
                boolean targetRed = ir[m.toX][m.toY];
                if (pieceRed == targetRed) continue;
            }
            cand.add(m);
        }
        
        if (cand.isEmpty()) return null;
        
        cand.sort((a, b) -> Integer.compare(b.weight, a.weight));
        int total = cand.stream().mapToInt(c -> c.weight).sum();
        int r = new Random().nextInt(total);
        int sum = 0;
        for (OpeningMove m : cand) {
            sum += m.weight;
            if (r < sum) return m;
        }
        return cand.get(0);
    }
    
    public static String getOpeningName(ChessBoard board, String color, int moveCount) {
        OpeningMove m = getBookMove(board, color, moveCount);
        return m != null ? m.type.getName() : "默认";
    }
}
