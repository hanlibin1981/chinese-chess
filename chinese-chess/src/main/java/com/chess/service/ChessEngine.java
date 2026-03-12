package com.chess.service;

import com.chess.model.ChessBoard;
import com.chess.model.Move;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChessEngine {
    
    @Autowired
    private ChessAnalysisService analysisService;
    
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
    
    // AI 难度等级
    public enum Difficulty {
        EASY(1, "简单"),      // 随机落子 + 少量搜索
        MEDIUM(2, "中等"),    // 2层搜索
        HARD(3, "困难");      // 3层搜索
        
        private final int level;
        private final String name;
        
        Difficulty(int level, String name) {
            this.level = level;
            this.name = name;
        }
        
        public int getLevel() { return level; }
        public String getName() { return name; }
    }
    
    // 当前难度默认中等
    private Difficulty currentDifficulty = Difficulty.MEDIUM;
    
    public void setDifficulty(Difficulty difficulty) {
        this.currentDifficulty = difficulty;
    }
    
    public Difficulty getDifficulty() {
        return currentDifficulty;
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
    
    /**
     * 获取AI最佳走法（根据当前难度）
     * @param board 当前棋盘
     * @param color AI颜色 (red 或 black)
     * @return 最佳走法
     */
    public Move getBestMove(ChessBoard board, String color) {
        switch (currentDifficulty) {
            case EASY:
                return getEasyMove(board, color);
            case MEDIUM:
                return getMediumMove(board, color);
            case HARD:
                return getHardMove(board, color);
            default:
                return getMediumMove(board, color);
        }
    }
    
    /**
     * 简单难度：80%概率随机，20%概率考虑吃子
     */
    private Move getEasyMove(ChessBoard board, String color) {
        List<Move> allMoves = getAllValidMoves(board, color);
        if (allMoves.isEmpty()) return null;
        
        Random rand = new Random();
        
        // 20%概率选择最佳走法（吃子或将军）
        if (rand.nextInt(100) < 20) {
            List<Move> goodMoves = new ArrayList<>();
            for (Move move : allMoves) {
                if (move.getCaptured() != null && !move.getCaptured().isEmpty()) {
                    goodMoves.add(move);
                }
            }
            if (!goodMoves.isEmpty()) {
                return goodMoves.get(rand.nextInt(goodMoves.size()));
            }
            // 检查将军走法
            for (Move move : allMoves) {
                ChessBoard testBoard = makeMove(board, move, color);
                if (isKingInCheck(testBoard, color.equals("red") ? "black" : "red")) {
                    goodMoves.add(move);
                }
            }
            if (!goodMoves.isEmpty()) {
                return goodMoves.get(rand.nextInt(goodMoves.size()));
            }
        }
        
        // 随机选择
        return allMoves.get(rand.nextInt(allMoves.size()));
    }
    
    /**
     * 中等难度：2层Alpha-Beta搜索
     */
    private Move getMediumMove(ChessBoard board, String color) {
        return alphaBetaSearch(board, color, 2, Integer.MIN_VALUE, Integer.MAX_VALUE).move;
    }
    
    /**
     * 困难难度：3层Alpha-Beta搜索 + 走法排序
     */
    private Move getHardMove(ChessBoard board, String color) {
        // 先做走法排序，优先搜索好的走法
        List<Move> moves = getAllValidMoves(board, color);
        moves = orderMoves(board, moves, color);
        
        Move bestMove = null;
        int bestScore = color.equals("red") ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        
        for (Move move : moves) {
            ChessBoard newBoard = makeMove(board, move, color);
            int score;
            
            if (color.equals("red")) {
                score = alphaBetaSearch(newBoard, "black", 2, alpha, beta).score;
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
            } else {
                score = alphaBetaSearch(newBoard, "red", 2, alpha, beta).score;
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, score);
            }
            
            if (beta <= alpha) {
                break; // 剪枝
            }
        }
        
        return bestMove != null ? bestMove : (moves.isEmpty() ? null : moves.get(0));
    }
    
    /**
     * Alpha-Beta搜索（带置换表优化）
     */
    private SearchResult alphaBetaSearch(ChessBoard board, String color, int depth, int alpha, int beta) {
        // 计算当前局面哈希
        int hash = zobristHash(board);
        
        // 检查置换表
        TTEntry ttEntry = tt.get(hash);
        if (ttEntry != null && ttEntry.depth >= depth) {
            if (ttEntry.type == TTEntry.EXACT) {
                return new SearchResult(ttEntry.move, ttEntry.value);
            } else if (ttEntry.type == TTEntry.ALPHA && ttEntry.value <= alpha) {
                return new SearchResult(ttEntry.move, alpha);
            } else if (ttEntry.type == TTEntry.BETA && ttEntry.value >= beta) {
                return new SearchResult(ttEntry.move, beta);
            }
        }
        
        if (depth == 0) {
            int score = analysisService.evaluatePosition(board);
            // 存入置换表
            tt.put(hash, 0, score, TTEntry.EXACT, null);
            return new SearchResult(null, score);
        }
        
        List<Move> moves = getAllValidMoves(board, color);
        
        if (moves.isEmpty()) {
            // 检查是否被将死
            if (isKingInCheck(board, color)) {
                // 被将死，返回极低分数
                return new SearchResult(null, color.equals("red") ? -100000 : 100000);
            }
            // 无子可动，和棋
            tt.put(hash, depth, 0, TTEntry.EXACT, null);
            return new SearchResult(null, 0);
        }
        
        // 走法排序优化剪枝
        // 优先使用置换表中保存的好走法
        if (ttEntry != null && ttEntry.move != null) {
            moves = prioritizeMove(moves, ttEntry.move);
        }
        moves = orderMoves(board, moves, color);
        
        Move bestMove = moves.get(0);
        
        if (color.equals("red")) {
            int maxScore = Integer.MIN_VALUE;
            for (Move move : moves) {
                ChessBoard newBoard = makeMove(board, move, color);
                SearchResult result = alphaBetaSearch(newBoard, "black", depth - 1, alpha, beta);
                int score = result.score;
                
                if (score > maxScore) {
                    maxScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
                if (beta <= alpha) {
                    break; // 剪枝
                }
            }
            // 存入置换表
            if (maxScore <= alpha) {
                tt.put(hash, depth, maxScore, TTEntry.BETA, bestMove);
            } else if (maxScore >= beta) {
                tt.put(hash, depth, maxScore, TTEntry.ALPHA, bestMove);
            } else {
                tt.put(hash, depth, maxScore, TTEntry.EXACT, bestMove);
            }
            return new SearchResult(bestMove, maxScore);
        } else {
            int minScore = Integer.MAX_VALUE;
            for (Move move : moves) {
                ChessBoard newBoard = makeMove(board, move, color);
                SearchResult result = alphaBetaSearch(newBoard, "red", depth - 1, alpha, beta);
                int score = result.score;
                
                if (score < minScore) {
                    minScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, score);
                if (beta <= alpha) {
                    break; // 剪枝
                }
            }
            // 存入置换表
            if (minScore <= alpha) {
                tt.put(hash, depth, minScore, TTEntry.BETA, bestMove);
            } else if (minScore >= beta) {
                tt.put(hash, depth, minScore, TTEntry.ALPHA, bestMove);
            } else {
                tt.put(hash, depth, minScore, TTEntry.EXACT, bestMove);
            }
            return new SearchResult(bestMove, minScore);
        }
    }
    
    /**
     * 优先使用指定走法
     */
    private List<Move> prioritizeMove(List<Move> moves, Move priorityMove) {
        List<Move> result = new ArrayList<>();
        result.add(priorityMove);
        for (Move m : moves) {
            if (!m.equals(priorityMove)) {
                result.add(m);
            }
        }
        return result;
    }
    
    /**
     * 走法排序：优先搜索好的走法，提高剪枝效率
     */
    private List<Move> orderMoves(ChessBoard board, List<Move> moves, String color) {
        List<ScoredMove> scoredMoves = new ArrayList<>();
        
        for (Move move : moves) {
            int score = 0;
            // 吃子得分
            if (move.getCaptured() != null && !move.getCaptured().isEmpty()) {
                score += PIECE_VALUES.getOrDefault(move.getCaptured(), 0);
            }
            // 将军加分
            ChessBoard testBoard = makeMove(board, move, color);
            String enemyColor = color.equals("red") ? "black" : "red";
            if (isKingInCheck(testBoard, enemyColor)) {
                score += 50;
            }
            // 推进兵卒加分
            String piece = move.getPiece();
            if ((piece.equals("兵") && move.getToX() < move.getFromX()) ||
                (piece.equals("卒") && move.getToX() > move.getFromX())) {
                score += 10;
            }
            scoredMoves.add(new ScoredMove(move, score));
        }
        
        // 降序排序
        scoredMoves.sort((a, b) -> Integer.compare(b.score, a.score));
        
        List<OrderedMove> orderedMoves = new ArrayList<>();
        for (int i = 0; i < scoredMoves.size(); i++) {
            orderedMoves.add(new OrderedMove(scoredMoves.get(i).move, i));
        }
        
        // 按原始顺序返回，但前面是好的走法
        return scoredMoves.stream().map(s -> s.move).toList();
    }
    
    /**
     * 获取最佳走法提示（用于显示给玩家）
     */
    public Move getHint(ChessBoard board, String color) {
        return alphaBetaSearch(board, color, 2, Integer.MIN_VALUE, Integer.MAX_VALUE).move;
    }
    
    /**
     * 搜索结果内部类
     */
    private static class SearchResult {
        Move move;
        int score;
        
        SearchResult(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }
    
    /**
     * 带分数的走法
     */
    private static class ScoredMove {
        Move move;
        int score;
        
        ScoredMove(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }
    
    /**
     * 排序后的走法（用于保持稳定性）
     */
    private static class OrderedMove {
        Move move;
        int order;
        
        OrderedMove(Move move, int order) {
            this.move = move;
            this.order = order;
        }
    }
    
    // 保留原有的简单随机AI以兼容旧代码
    public Move getBestMove() {
        return getBestMove(new ChessBoard(), "black");
    }
    
    // ========== 置换表（Transposition Table）============
    // 用于缓存已经搜索过的局面，避免重复搜索
    private static final int TT_SIZE = 1 << 20; // 1M entry
    private final TranspositionTable tt = new TranspositionTable(TT_SIZE);
    
    /**
     * 置换表条目
     */
    private static class TTEntry {
        int hash;       // 局面哈希值
        short depth;    // 搜索深度
        byte type;      // 类型：EXACT, ALPHA, BETA
        int value;      // 评估值
        Move move;      // 最佳走法
        
        static final byte EXACT = 0;
        static final byte ALPHA = 1;
        static final byte BETA = 2;
    }
    
    /**
     * 置换表实现
     */
    private static class TranspositionTable {
        private final TTEntry[] table;
        
        public TranspositionTable(int size) {
            this.table = new TTEntry[size];
        }
        
        public TTEntry get(int hash) {
            return table[hash & (table.length - 1)];
        }
        
        public void put(int hash, int depth, int value, byte type, Move move) {
            int index = hash & (table.length - 1);
            TTEntry entry = table[index];
            
            // 只有当新结果更深或者值更好时才替换
            if (entry == null || entry.depth <= depth || type == TTEntry.EXACT) {
                table[index] = new TTEntry();
                table[index].hash = hash;
                table[index].depth = (short) depth;
                table[index].value = value;
                table[index].type = type;
                table[index].move = move;
            }
        }
        
        public void clear() {
            Arrays.fill(table, null);
        }
    }
    
    /**
     * 计算局面的Zobrist哈希值
     */
    private int zobristHash(ChessBoard board) {
        int hash = 0;
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    int pieceIdx = getPieceIndex(b[i][j], isRed[i][j]);
                    hash ^= ZOBRIST_PIECE[i][j][pieceIdx];
                }
            }
        }
        // 加上回合信息
        if (board.getCurrentTurn().equals("red")) {
            hash ^= ZOBRIST_TURN;
        }
        return hash;
    }
    
    /**
     * 获取棋子索引
     */
    private int getPieceIndex(String piece, boolean isRed) {
        return switch (piece) {
            case "帥", "將" -> 0;
            case "車" -> 1;
            case "馬" -> 2;
            case "炮" -> 3;
            case "相", "象" -> 4;
            case "仕", "士" -> 5;
            case "兵", "卒" -> 6;
            default -> 7;
        };
    }
    
    // Zobrist表 - 预计算随机数
    private static final int[][][] ZOBRIST_PIECE = new int[10][9][8];
    private static final int ZOBRIST_TURN;
    
    static {
        Random rand = new Random(123456);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                for (int k = 0; k < 8; k++) {
                    ZOBRIST_PIECE[i][j][k] = rand.nextInt();
                }
            }
        }
        ZOBRIST_TURN = rand.nextInt();
    }
    
    /**
     * 清除置换表（每步棋后调用）
     */
    public void clearTranspositionTable() {
        tt.clear();
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
