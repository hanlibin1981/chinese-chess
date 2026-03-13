package com.chess.service;

import com.chess.model.ChessBoard;
import com.chess.model.Move;
import java.util.*;

/**
 * 开局库 - 常见开局走法
 * 扩展到8步以上
 */
public class OpeningBook {
    
    public enum OpeningType {
        SHUNSHOU_POU("顺手炮", 1),
        PINGFENG_MA("屏风马", 2),
        XIANREN_ZHILU("仙人指路", 3),
        JU_JIAO_MA("聚角马", 4),
        SHUN_JU("顺炮", 5),
        WAN_JIA_YAN("万金炮", 6),
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
    
    // 扩展版开局库 - 8步
    private static final List<OpeningMove> OPENINGS = new ArrayList<>();
    
    static {
        // ==================== 红方第一步 ====================
        // 顺手炮
        OPENINGS.add(new OpeningMove(2, 1, 2, 4, "炮", OpeningType.SHUNSHOU_POU, 100));
        OPENINGS.add(new OpeningMove(2, 7, 2, 4, "炮", OpeningType.SHUNSHOU_POU, 95));
        
        // 屏风马
        OPENINGS.add(new OpeningMove(9, 1, 8, 2, "馬", OpeningType.PINGFENG_MA, 92));
        OPENINGS.add(new OpeningMove(9, 7, 8, 6, "馬", OpeningType.PINGFENG_MA, 90));
        
        // 仙人指路
        OPENINGS.add(new OpeningMove(6, 2, 5, 2, "兵", OpeningType.XIANREN_ZHILU, 88));
        OPENINGS.add(new OpeningMove(6, 4, 5, 4, "兵", OpeningType.XIANREN_ZHILU, 85));
        
        // 飞相
        OPENINGS.add(new OpeningMove(9, 2, 8, 3, "相", OpeningType.DEFAULT, 70));
        OPENINGS.add(new OpeningMove(9, 6, 8, 5, "相", OpeningType.DEFAULT, 68));
        
        // 士角炮
        OPENINGS.add(new OpeningMove(2, 4, 1, 5, "炮", OpeningType.WAN_JIA_YAN, 75));
        
        // ==================== 黑方第一步（对应红方顺手炮） ====================
        OPENINGS.add(new OpeningMove(0, 1, 0, 4, "炮", OpeningType.SHUNSHOU_POU, 100));
        OPENINGS.add(new OpeningMove(0, 7, 0, 4, "炮", OpeningType.SHUNSHOU_POU, 95));
        
        // ==================== 黑方第一步（对应红方屏风马） ====================
        OPENINGS.add(new OpeningMove(0, 1, 1, 2, "馬", OpeningType.PINGFENG_MA, 90));
        OPENINGS.add(new OpeningMove(0, 7, 1, 6, "馬", OpeningType.PINGFENG_MA, 88));
        
        // ==================== 黑方第一步（对应红方仙人指路） ====================
        OPENINGS.add(new OpeningMove(3, 2, 4, 2, "卒", OpeningType.XIANREN_ZHILU, 85));
        OPENINGS.add(new OpeningMove(3, 4, 4, 4, "卒", OpeningType.XIANREN_ZHILU, 82));
        
        // ==================== 红方第二步 ====================
        // 屏风马续招
        OPENINGS.add(new OpeningMove(9, 0, 8, 1, "車", OpeningType.PINGFENG_MA, 90));
        OPENINGS.add(new OpeningMove(9, 8, 8, 7, "車", OpeningType.PINGFENG_MA, 88));
        OPENINGS.add(new OpeningMove(9, 1, 8, 3, "馬", OpeningType.PINGFENG_MA, 85));
        OPENINGS.add(new OpeningMove(9, 7, 8, 5, "馬", OpeningType.PINGFENG_MA, 83));
        
        // 仙人指路续招
        OPENINGS.add(new OpeningMove(9, 1, 8, 2, "馬", OpeningType.XIANREN_ZHILU, 80));
        OPENINGS.add(new OpeningMove(9, 7, 8, 6, "馬", OpeningType.XIANREN_ZHILU, 78));
        
        // 顺手炮续招
        OPENINGS.add(new OpeningMove(9, 0, 8, 0, "車", OpeningType.SHUNSHOU_POU, 85));
        OPENINGS.add(new OpeningMove(9, 8, 8, 8, "車", OpeningType.SHUNSHOU_POU, 83));
        
        // ==================== 黑方第二步 ====================
        OPENINGS.add(new OpeningMove(0, 0, 1, 0, "車", OpeningType.DEFAULT, 80));
        OPENINGS.add(new OpeningMove(0, 8, 1, 8, "車", OpeningType.DEFAULT, 78));
        
        // ==================== 红方第三步 ====================
        OPENINGS.add(new OpeningMove(8, 0, 8, 1, "車", OpeningType.PINGFENG_MA, 75));
        OPENINGS.add(new OpeningMove(8, 8, 8, 7, "車", OpeningType.PINGFENG_MA, 73));
        
        // ==================== 红方第四步 ====================
        OPENINGS.add(new OpeningMove(8, 1, 8, 4, "車", OpeningType.PINGFENG_MA, 70));
        OPENINGS.add(new OpeningMove(8, 7, 8, 4, "車", OpeningType.PINGFENG_MA, 68));
        
        // ==================== 常见开局变例 ====================
        // 中炮对屏风马
        OPENINGS.add(new OpeningMove(2, 4, 2, 5, "炮", OpeningType.PINGFENG_MA, 65));
        
        // 仙人指路对卒底炮
        OPENINGS.add(new OpeningMove(2, 1, 2, 4, "炮", OpeningType.XIANREN_ZHILU, 60));
        
        // 飞相局
        OPENINGS.add(new OpeningMove(8, 3, 6, 5, "相", OpeningType.DEFAULT, 55));
        
        // 士角炮对局
        OPENINGS.add(new OpeningMove(9, 4, 9, 3, "仕", OpeningType.WAN_JIA_YAN, 50));
        OPENINGS.add(new OpeningMove(9, 4, 9, 5, "仕", OpeningType.WAN_JIA_YAN, 48));
        
        // ==================== 第5-8步续招 ====================
        // 出车占肋
        OPENINGS.add(new OpeningMove(8, 4, 8, 3, "車", OpeningType.DEFAULT, 60));
        OPENINGS.add(new OpeningMove(8, 4, 8, 5, "車", OpeningType.DEFAULT, 58));
        
        // 补士
        OPENINGS.add(new OpeningMove(9, 3, 8, 4, "仕", OpeningType.DEFAULT, 45));
        OPENINGS.add(new OpeningMove(9, 5, 8, 4, "仕", OpeningType.DEFAULT, 43));
        
        // 跃马
        OPENINGS.add(new OpeningMove(8, 2, 6, 1, "馬", OpeningType.DEFAULT, 50));
        OPENINGS.add(new OpeningMove(8, 6, 6, 7, "馬", OpeningType.DEFAULT, 48));
        
        // 挺兵
        OPENINGS.add(new OpeningMove(6, 0, 5, 0, "兵", OpeningType.DEFAULT, 40));
        OPENINGS.add(new OpeningMove(6, 8, 5, 8, "兵", OpeningType.DEFAULT, 38));
    }
    
    /**
     * 获取开局库走法
     * @param board 当前棋盘
     * @param color 颜色
     * @param moveCount 当前是第几步
     * @return 开局走法，如果没有则返回null
     */
    public static OpeningMove getBookMove(ChessBoard board, String color, int moveCount) {
        if (moveCount > 10) return null;
        
        String[][] bd = board.getBoard();
        boolean[][] ir = board.getIsRed();
        List<OpeningMove> cand = new ArrayList<>();
        
        for (OpeningMove m : OPENINGS) {
            // 检查起始位置是否有棋子
            if (bd[m.fromX][m.fromY] == null) continue;
            if (!bd[m.fromX][m.fromY].equals(m.piece)) continue;
            
            boolean pieceRed = ir[m.fromX][m.fromY];
            boolean wantRed = color.equals("red");
            if (pieceRed != wantRed) continue;
            
            // 检查目标位置
            if (bd[m.toX][m.toY] != null) {
                boolean targetRed = ir[m.toX][m.toY];
                if (pieceRed == targetRed) continue; // 不能吃己方棋子
            }
            
            cand.add(m);
        }
        
        if (cand.isEmpty()) return null;
        
        // 按权重排序并随机选择
        cand.sort((a, b) -> Integer.compare(b.weight, a.weight));
        
        // 权重越高越容易被选中，但也保留一定随机性
        int total = cand.stream().mapToInt(c -> c.weight).sum();
        int r = new Random().nextInt(total);
        int sum = 0;
        for (OpeningMove m : cand) {
            sum += m.weight;
            if (r < sum) return m;
        }
        return cand.get(0);
    }
    
    /**
     * 获取开局名称
     */
    public static String getOpeningName(ChessBoard board, String color, int moveCount) {
        OpeningMove m = getBookMove(board, color, moveCount);
        return m != null ? m.type.getName() : "默认";
    }
    
    /**
     * 识别当前局面属于什么开局
     */
    public static String identifyOpening(ChessBoard board) {
        String[][] bd = board.getBoard();
        boolean[][] ir = board.getIsRed();
        
        // 识别顺手炮
        if (bd[2][4] != null && bd[2][4].equals("炮") && ir[2][4] &&
            bd[0][4] != null && bd[0][4].equals("炮") && !ir[0][4]) {
            return "顺炮局";
        }
        
        // 识别屏风马
        if (bd[9][2] != null && bd[9][2].equals("馬") && ir[9][2] &&
            bd[9][6] != null && bd[9][6].equals("馬") && ir[9][6]) {
            return "屏风马";
        }
        
        // 识别仙人指路
        if (bd[6][2] != null && bd[6][2].equals("兵") && ir[6][2] &&
            bd[6][4] != null && bd[6][4].equals("兵") && ir[6][4]) {
            return "仙人指路";
        }
        
        // 识别飞相局
        if (bd[8][3] != null && bd[8][3].equals("相") && ir[8][3]) {
            return "飞相局";
        }
        
        return "散手局";
    }
}
