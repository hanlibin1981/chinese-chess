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
        EASY(1, "简单", 1),      // 随机落子 + 1层搜索
        MEDIUM(2, "中等", 2),    // 2层搜索
        HARD(3, "困难", 3),      // 3层搜索
        EXPERT(4, "大师", 4);     // 4层搜索
        
        private final int level;
        private final String name;
        private final int searchDepth;
        
        Difficulty(int level, String name, int searchDepth) {
            this.level = level;
            this.name = name;
            this.searchDepth = searchDepth;
        }
        
        public int getLevel() { return level; }
        public String getName() { return name; }
        public int getSearchDepth() { return searchDepth; }
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
    
    /**
     * 检查将对将（将帅对面）是否非法
     */
    private boolean isKingsFacing(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        int redKingX = -1, redKingY = -1;
        int blackKingX = -1, blackKingY = -1;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    if (b[i][j].equals("帥") && isRed[i][j]) {
                        redKingX = i; redKingY = j;
                    } else if (b[i][j].equals("將") && !isRed[i][j]) {
                        blackKingX = i; blackKingY = j;
                    }
                }
            }
        }
        
        if (redKingX < 0 || blackKingX < 0) {
            return false;
        }
        
        if (redKingY != blackKingY) {
            return false;
        }
        
        int minX = Math.min(redKingX, blackKingX);
        int maxX = Math.max(redKingX, blackKingX);
        
        for (int x = minX + 1; x < maxX; x++) {
            if (b[x][redKingY] != null) {
                return false;
            }
        }
        
        return true;
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
                        ChessBoard testBoard = makeMoveWithoutRecording(board, move);
                        
                        if (isKingsFacing(testBoard)) {
                            continue;
                        }
                        
                        if (isKingInCheck(testBoard, color)) {
                            continue;
                        }
                        
                        moves.add(move);
                    }
                }
            }
        }
        return moves;
    }
    
    private ChessBoard makeMoveWithoutRecording(ChessBoard board, Move move) {
        ChessBoard newBoard = board.copy();
        String[][] b = newBoard.getBoard();
        boolean[][] isRed = newBoard.getIsRed();
        
        String piece = b[move.getFromX()][move.getFromY()];
        boolean pieceIsRed = isRed[move.getFromX()][move.getFromY()];
        
        b[move.getFromX()][move.getFromY()] = null;
        isRed[move.getFromX()][move.getFromY()] = false;
        
        b[move.getToX()][move.getToY()] = piece;
        isRed[move.getToX()][move.getToY()] = pieceIsRed;
        
        return newBoard;
    }
    
    private List<Move> getValidMovesForPiece(ChessBoard board, int x, int y, String piece) {
        List<Move> moves = new ArrayList<>();
        
        switch (piece) {
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
            if (bx < 0 || bx >= 10 || by < 0 || by >= 9) continue;
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
        
        // 前进
        int nx = x + forward;
        if (nx >= 0 && nx < 10) {
            if (b[nx][y] == null || !isSameSide(board, x, y, nx, y)) {
                moves.add(new Move(x, y, nx, y, piece));
            }
        }
        
        // 过河后可左右走
        boolean crossed = isRed ? (x <= 4) : (x >= 5);
        if (crossed) {
            // 左走
            if (y > 0) {
                if (b[x][y-1] == null || !isSameSide(board, x, y, x, y-1)) {
                    moves.add(new Move(x, y, x, y-1, piece));
                }
            }
            // 右走
            if (y < 8) {
                if (b[x][y+1] == null || !isSameSide(board, x, y, x, y+1)) {
                    moves.add(new Move(x, y, x, y+1, piece));
                }
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
        
        String captured = b[move.getToX()][move.getToY()];
        move.setCaptured(captured);
        
        b[move.getFromX()][move.getFromY()] = null;
        isRed[move.getFromX()][move.getFromY()] = false;
        
        b[move.getToX()][move.getToY()] = piece;
        isRed[move.getToX()][move.getToY()] = pieceIsRed;
        
        newBoard.getMoves().add(move);
        
        newBoard.setCurrentTurn(color.equals("red") ? "black" : "red");
        newBoard.setMoveCount(newBoard.getMoveCount() + 1);
        
        // 每次走棋后清理置换表
        clearTranspositionTable();
        
        return newBoard;
    }
    
    /**
     * 获取AI最佳走法
     */
    public Move getBestMove(ChessBoard board, String color) {
        int moveCount = board.getMoveCount();
        int myMoveCount = color.equals("red") ? (moveCount / 2 + 1) : ((moveCount + 1) / 2 + 1);
        
        // 困难及以上难度在前8步优先使用开局库
        if (currentDifficulty.getSearchDepth() >= 3 && myMoveCount <= 8) {
            OpeningBook.OpeningMove openingMove = OpeningBook.getBookMove(board, color, myMoveCount);
            if (openingMove != null) {
                Move move = new Move(openingMove.fromX, openingMove.fromY, 
                                     openingMove.toX, openingMove.toY, openingMove.piece);
                if (isLegalMove(board, move, color)) {
                    System.out.println("AI使用开局库: " + OpeningBook.getOpeningName(board, color, myMoveCount));
                    return move;
                }
            }
        }
        
        switch (currentDifficulty) {
            case EASY: return getEasyMove(board, color);
            case MEDIUM: return getMediumMove(board, color);
            case HARD: return getHardMove(board, color);
            case EXPERT: return getExpertMove(board, color);
            default: return getMediumMove(board, color);
        }
    }
    
    private boolean isLegalMove(ChessBoard board, Move move, String color) {
        List<Move> validMoves = getAllValidMoves(board, color);
        return validMoves.stream().anyMatch(m -> 
            m.getFromX() == move.getFromX() && 
            m.getFromY() == move.getFromY() && 
            m.getToX() == move.getToX() && 
            m.getToY() == move.getToY()
        );
    }
    
    private Move getEasyMove(ChessBoard board, String color) {
        List<Move> allMoves = getAllValidMoves(board, color);
        if (allMoves.isEmpty()) return null;
        
        Random rand = new Random();
        
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
        
        return allMoves.get(rand.nextInt(allMoves.size()));
    }
    
    private Move getMediumMove(ChessBoard board, String color) {
        return alphaBetaSearch(board, color, currentDifficulty.getSearchDepth(), Integer.MIN_VALUE, Integer.MAX_VALUE).move;
    }
    
    private Move getHardMove(ChessBoard board, String color) {
        // 困难难度使用3层搜索
        return alphaBetaSearch(board, color, currentDifficulty.getSearchDepth(), Integer.MIN_VALUE, Integer.MAX_VALUE).move;
    }

    private Move getExpertMove(ChessBoard board, String color) {
        // 大师难度使用4层搜索，并启用更激进的走法排序
        List<Move> moves = getAllValidMoves(board, color);
        moves = orderMoves(board, moves, color);

        Move bestMove = null;
        int bestScore = color.equals("red") ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Move move : moves) {
            ChessBoard newBoard = makeMove(board, move, color);
            int score;

            // 大师难度使用指定深度的搜索
            if (color.equals("red")) {
                score = alphaBetaSearch(newBoard, "black", currentDifficulty.getSearchDepth(), alpha, beta).score;
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
            } else {
                score = alphaBetaSearch(newBoard, "red", currentDifficulty.getSearchDepth(), alpha, beta).score;
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, score);
            }

            if (beta <= alpha) break;
        }

        return bestMove != null ? bestMove : (moves.isEmpty() ? null : moves.get(0));
    }
    
    private SearchResult alphaBetaSearch(ChessBoard board, String color, int depth, int alpha, int beta) {
        int hash = zobristHash(board);
        
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
            int score;
            if (analysisService != null) {
                score = analysisService.evaluatePosition(board);
            } else {
                // 简单评估函数（当analysisService未注入时使用）
                score = simpleEvaluate(board);
            }
            tt.put(hash, 0, score, TTEntry.EXACT, null);
            return new SearchResult(null, score);
        }
        
        List<Move> moves = getAllValidMoves(board, color);
        
        if (moves.isEmpty()) {
            if (isKingInCheck(board, color)) {
                return new SearchResult(null, color.equals("red") ? -100000 : 100000);
            }
            tt.put(hash, depth, 0, TTEntry.EXACT, null);
            return new SearchResult(null, 0);
        }
        
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
                if (beta <= alpha) break;
            }
            
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
                if (beta <= alpha) break;
            }
            
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
    
    private List<Move> orderMoves(ChessBoard board, List<Move> moves, String color) {
        List<ScoredMove> scoredMoves = new ArrayList<>();
        
        for (Move move : moves) {
            int score = 0;
            if (move.getCaptured() != null && !move.getCaptured().isEmpty()) {
                score += PIECE_VALUES.getOrDefault(move.getCaptured(), 0);
            }
            ChessBoard testBoard = makeMove(board, move, color);
            String enemyColor = color.equals("red") ? "black" : "red";
            if (isKingInCheck(testBoard, enemyColor)) {
                score += 50;
            }
            String piece = move.getPiece();
            if ((piece.equals("兵") && move.getToX() < move.getFromX()) ||
                (piece.equals("卒") && move.getToX() > move.getFromX())) {
                score += 10;
            }
            if ((piece.equals("車") && move.getFromY() == 0 && move.getToY() != 0) ||
                (piece.equals("車") && move.getFromY() == 8 && move.getToY() != 8)) {
                score += 20;
            }
            scoredMoves.add(new ScoredMove(move, score));
        }
        
        scoredMoves.sort((a, b) -> Integer.compare(b.score, a.score));
        
        return scoredMoves.stream().map(s -> s.move).toList();
    }
    
    public Move getHint(ChessBoard board, String color) {
        return alphaBetaSearch(board, color, 2, Integer.MIN_VALUE, Integer.MAX_VALUE).move;
    }
    
    private static class SearchResult {
        Move move;
        int score;
        
        SearchResult(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }
    
    private static class ScoredMove {
        Move move;
        int score;
        
        ScoredMove(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }
    
    public Move getBestMove() {
        return getBestMove(new ChessBoard(), "black");
    }
    
    // 置换表
    private static final int TT_SIZE = 1 << 20;
    private final TranspositionTable tt = new TranspositionTable(TT_SIZE);
    
    private static class TTEntry {
        int hash;
        short depth;
        byte type;
        int value;
        Move move;
        
        static final byte EXACT = 0;
        static final byte ALPHA = 1;
        static final byte BETA = 2;
    }
    
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
            
            // 改进的置换表替换策略：
            // 1. 如果条目为空，直接插入
            // 2. 如果新搜索深度更深，替换
            // 3. 如果深度相同但新结果是精确值或Beta截断（说明走法更好），替换
            // 4. 如果是Alpha截断但旧条目是EXACT，不替换
            if (entry == null) {
                table[index] = new TTEntry();
                table[index].hash = hash;
                table[index].depth = (short) depth;
                table[index].value = value;
                table[index].type = type;
                table[index].move = move;
            } else if (depth > entry.depth) {
                // 更深的搜索结果优先
                table[index] = new TTEntry();
                table[index].hash = hash;
                table[index].depth = (short) depth;
                table[index].value = value;
                table[index].type = type;
                table[index].move = move;
            } else if (depth == entry.depth) {
                // 深度相同时，精确结果优先替换，其次是Beta（好的走法）
                if (type == TTEntry.EXACT || (type == TTEntry.BETA && entry.type != TTEntry.EXACT)) {
                    table[index] = new TTEntry();
                    table[index].hash = hash;
                    table[index].depth = (short) depth;
                    table[index].value = value;
                    table[index].type = type;
                    table[index].move = move;
                }
            }
        }
        
        public void clear() {
            Arrays.fill(table, null);
        }
    }
    
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
        if (board.getCurrentTurn().equals("red")) {
            hash ^= ZOBRIST_TURN;
        }
        return hash;
    }
    
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
    
    private static final int[][][] ZOBRIST_PIECE = new int[10][9][8];
    private static final int ZOBRIST_TURN;
    
    static {
        // 使用固定种子确保每次运行生成相同的Zobrist表，保证置换表一致性
        Random rand = new Random(12345678);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                for (int k = 0; k < 8; k++) {
                    ZOBRIST_PIECE[i][j][k] = rand.nextInt();
                }
            }
        }
        ZOBRIST_TURN = 987654321;
    }
    
    public void clearTranspositionTable() {
        tt.clear();
    }
    
    /**
     * 简单评估函数（当analysisService未注入时使用）
     */
    private int simpleEvaluate(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    int value = PIECE_VALUES.getOrDefault(b[i][j], 0);
                    if (isRed[i][j]) {
                        score += value;
                    } else {
                        score -= value;
                    }
                }
            }
        }
        
        return score;
    }
    
    public boolean isKingInCheck(ChessBoard board, String color) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
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
        
        if (kingX < 0) return false;
        
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
    
    /**
     * 检查是否将死
     */
    public boolean isCheckmate(ChessBoard board, String color) {
        return isKingInCheck(board, color) && getAllValidMoves(board, color).isEmpty();
    }
    
    /**
     * 检查是否困毙
     */
    public boolean isStalemate(ChessBoard board, String color) {
        return !isKingInCheck(board, color) && getAllValidMoves(board, color).isEmpty();
    }
    
    /**
     * 检查是否和棋（三次重复局面）
     */
    public boolean isDrawByRepetition(List<String> positionHistory, String currentPos) {
        int count = 0;
        for (String pos : positionHistory) {
            if (pos.equals(currentPos)) {
                count++;
            }
        }
        return count >= 3;
    }
    
    /**
     * 生成局面签名
     */
    public String generatePositionSignature(ChessBoard board) {
        StringBuilder sb = new StringBuilder();
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    sb.append(b[i][j]).append(isRed[i][j] ? "R" : "B").append(i).append(j);
                }
            }
        }
        sb.append("|").append(board.getCurrentTurn());
        return sb.toString();
    }
    
    /**
     * 检查是否长捉（同一棋子连续追捉对方价值最高的棋子）
     */
    public boolean isLongCapture(List<Move> recentMoves, int maxTurns) {
        if (recentMoves.size() < maxTurns * 2) return false;
        
        Move lastMove = recentMoves.get(recentMoves.size() - 1);
        String lastPiece = lastMove.getPiece();
        int lastToX = lastMove.getToX();
        int lastToY = lastMove.getToY();
        
        int consecutiveChases = 1;
        
        for (int i = recentMoves.size() - 3; i >= 0; i -= 2) {
            Move m = recentMoves.get(i);
            if (m.getPiece().equals(lastPiece) && m.getToX() == lastToX && m.getToY() == lastToY) {
                consecutiveChases++;
            } else {
                break;
            }
        }
        
        return consecutiveChases >= maxTurns;
    }
    
    /**
     * 检查是否长兑（双方不断进行相同的兑换）
     */
    public boolean isLongExchange(ChessBoard board, List<Move> recentMoves) {
        if (recentMoves.size() < 4) return false;
        
        Move last = recentMoves.get(recentMoves.size() - 1);
        Move prev = recentMoves.get(recentMoves.size() - 2);
        
        // 检查是否互相吃子
        if (last.getCaptured() != null && prev.getCaptured() != null) {
            if (last.getCaptured().equals(prev.getPiece()) && prev.getCaptured().equals(last.getPiece())) {
                return true;
            }
        }
        
        return false;
    }
}
