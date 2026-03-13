package com.chess.service;

import com.chess.model.ChessBoard;
import com.chess.model.Move;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChessAnalysisService {
    
    @Autowired
    private ChessEngine chessEngine;
    
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
    
    // 位置分值表
    private static final int[][] RED_PAWN_POS = {
        {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0},
        {0,0,0,0,0,0,0,0,0}, {2,4,6,8,10,8,6,4,2}, {4,8,12,16,20,16,12,8,4},
        {6,12,18,24,30,24,18,12,6}, {8,16,24,32,40,32,24,16,8},
        {10,20,30,40,50,40,30,20,10}, {10,20,30,40,50,40,30,20,10}
    };
    
    private static final int[][] BLACK_PAWN_POS = {
        {10,20,30,40,50,40,30,20,10}, {10,20,30,40,50,40,30,20,10},
        {8,16,24,32,40,32,24,16,8}, {6,12,18,24,30,24,18,12,6},
        {4,8,12,16,20,16,12,8,4}, {2,4,6,8,10,8,6,4,2},
        {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0},
        {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0}
    };
    
    private static final int[][] HORSE_POS = {
        {0,-4,0,0,0,0,0,-4,0}, {0,2,4,4,0,4,4,2,0},
        {4,2,8,8,4,8,8,2,4}, {2,6,8,6,10,6,8,6,2},
        {4,10,14,16,14,16,14,10,4}, {6,14,16,20,22,20,16,14,6},
        {8,16,22,26,24,26,22,16,8}, {12,14,16,22,20,22,16,14,12},
        {4,8,12,14,12,14,12,8,4}, {4,6,8,8,6,8,8,6,4}
    };
    
    private static final int[][] CAR_POS = {
        {14,14,12,16,10,16,12,14,14}, {14,18,16,18,14,18,16,18,14},
        {12,12,12,14,12,14,12,12,12}, {14,20,18,20,20,20,18,20,14},
        {14,16,14,16,14,16,14,16,14}, {14,14,12,14,12,14,12,14,14},
        {6,10,8,10,8,10,8,10,6}, {8,12,10,12,10,12,10,12,8},
        {6,8,6,8,6,8,6,8,6}, {6,8,6,8,6,8,6,8,6}
    };
    
    private static final int[][] CANNON_POS = {
        {0,2,4,6,6,6,4,2,0}, {0,2,4,6,6,6,4,2,0},
        {4,2,8,8,4,8,8,2,4}, {2,0,6,8,4,8,6,0,2},
        {4,2,10,12,8,12,10,2,4}, {6,4,8,10,12,10,8,4,6},
        {8,10,14,16,14,16,14,10,8}, {12,12,10,14,12,14,10,12,12},
        {14,16,14,14,12,14,14,16,14}, {14,14,12,12,10,12,12,14,14}
    };
    
    public int evaluatePosition(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        int score = 0;
        
        // 1. 棋子分值 + 位置分值
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    int baseValue = PIECE_VALUES.getOrDefault(b[i][j], 0);
                    int posValue = getPositionValue(b[i][j], i, j, isRed[i][j]);
                    int value = baseValue + posValue;
                    
                    if (isRed[i][j]) score += value;
                    else score -= value;
                }
            }
        }
        
        score += evaluateAdvisorPosition(board);
        score += evaluatePawnStructure(board);
        score += evaluateDevelopment(board);
        score += evaluateKingSafety(board);
        score += evaluatePieceCoordination(board);
        score += evaluateCenterControl(board);
        
        return score;
    }
    
    private int getPositionValue(String piece, int x, int y, boolean isRed) {
        return switch (piece) {
            case "兵", "卒" -> isRed ? RED_PAWN_POS[x][y] : BLACK_PAWN_POS[x][y];
            case "馬" -> isRed ? HORSE_POS[x][y] : HORSE_POS[9 - x][y];
            case "車" -> isRed ? CAR_POS[x][y] : CAR_POS[9 - x][y];
            case "炮" -> isRed ? CANNON_POS[x][y] : CANNON_POS[9 - x][y];
            case "相", "象" -> ((isRed && x >= 5) || (!isRed && x <= 4)) ? 30 : 0;
            case "仕", "士" -> (x >= 1 && x <= 2 || x >= 7 && x <= 8) && (y >= 3 && y <= 5) ? 20 : 0;
            default -> 0;
        };
    }
    
    private int evaluateAdvisorPosition(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 仕位
        if (b[8][3] != null && b[8][3].equals("仕") && isRed[8][3]) score += 15;
        if (b[8][5] != null && b[8][5].equals("仕") && isRed[8][5]) score += 15;
        if (b[9][4] != null && b[9][4].equals("仕") && isRed[9][4]) score += 10;
        if (b[1][3] != null && b[1][3].equals("士") && !isRed[1][3]) score -= 15;
        if (b[1][5] != null && b[1][5].equals("士") && !isRed[1][5]) score -= 15;
        if (b[0][4] != null && b[0][4].equals("士") && !isRed[0][4]) score -= 10;
        
        // 相位
        if (b[7][2] != null && b[7][2].equals("相") && isRed[7][2]) score += 12;
        if (b[7][6] != null && b[7][6].equals("相") && isRed[7][6]) score += 12;
        if (b[2][2] != null && b[2][2].equals("象") && !isRed[2][2]) score -= 12;
        if (b[2][6] != null && b[2][6].equals("象") && !isRed[2][6]) score -= 12;
        
        // 士象全
        int redAdvisor = 0, redElephant = 0, blackAdvisor = 0, blackElephant = 0;
        for (int i = 7; i <= 9; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && isRed[i][j]) {
                    if (b[i][j].equals("仕")) redAdvisor++;
                    if (b[i][j].equals("相")) redElephant++;
                }
            }
        }
        for (int i = 0; i <= 2; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && !isRed[i][j]) {
                    if (b[i][j].equals("士")) blackAdvisor++;
                    if (b[i][j].equals("象")) blackElephant++;
                }
            }
        }
        if (redAdvisor == 2) score += 20;
        if (redElephant == 2) score += 15;
        if (blackAdvisor == 2) score -= 20;
        if (blackElephant == 2) score -= 15;
        
        return score;
    }
    
    private int evaluatePawnStructure(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 红兵并列
        List<int[]> redPawns = new ArrayList<>();
        List<int[]> blackPawns = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null && b[i][j].equals("兵") && isRed[i][j]) redPawns.add(new int[]{i, j});
                if (b[i][j] != null && b[i][j].equals("卒") && !isRed[i][j]) blackPawns.add(new int[]{i, j});
            }
        }
        
        // 并列兵
        for (int i = 0; i < redPawns.size(); i++) {
            for (int j = i + 1; j < redPawns.size(); j++) {
                if (redPawns.get(i)[0] == redPawns.get(j)[0] && Math.abs(redPawns.get(i)[1] - redPawns.get(j)[1]) == 1) score += 8;
            }
            if (redPawns.get(i)[0] <= 4) {
                score += (4 - redPawns.get(i)[0]) * 10;
            }
        }
        for (int i = 0; i < blackPawns.size(); i++) {
            for (int j = i + 1; j < blackPawns.size(); j++) {
                if (blackPawns.get(i)[0] == blackPawns.get(j)[0] && Math.abs(blackPawns.get(i)[1] - blackPawns.get(j)[1]) == 1) score -= 8;
            }
            if (blackPawns.get(i)[0] >= 5) {
                score -= (blackPawns.get(i)[0] - 5) * 10;
            }
        }
        
        return score;
    }
    
    private int evaluateDevelopment(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        if (b[9][0] != null && b[9][0].equals("車") && isRed[9][0]) score -= 20;
        if (b[9][8] != null && b[9][8].equals("車") && isRed[9][8]) score -= 20;
        if (b[0][0] != null && b[0][0].equals("車") && !isRed[0][0]) score += 20;
        if (b[0][8] != null && b[0][8].equals("車") && !isRed[0][8]) score += 20;
        if (b[9][4] != null && b[9][4].equals("車") && isRed[9][4]) score += 15;
        if (b[0][4] != null && b[0][4].equals("車") && !isRed[0][4]) score -= 15;
        
        return score;
    }
    
    private int evaluateKingSafety(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 士保护
        boolean redHasAdvisor = false, blackHasAdvisor = false;
        for (int i = 7; i <= 9; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && isRed[i][j] && (b[i][j].equals("仕") || b[i][j].equals("相"))) redHasAdvisor = true;
            }
        }
        for (int i = 0; i <= 2; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && !isRed[i][j] && (b[i][j].equals("士") || b[i][j].equals("象"))) blackHasAdvisor = true;
            }
        }
        if (!redHasAdvisor) score -= 25;
        if (!blackHasAdvisor) score += 25;
        
        return score;
    }
    
    private int evaluatePieceCoordination(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 车马配合
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null && b[i][j].equals("車") && isRed[i][j]) {
                    for (int k = 0; k < 10; k++) {
                        if (b[k][j] != null && isRed[k][j] && b[k][j].equals("馬")) score += 10;
                    }
                }
                if (b[i][j] != null && b[i][j].equals("車") && !isRed[i][j]) {
                    for (int k = 0; k < 10; k++) {
                        if (b[k][j] != null && !isRed[k][j] && b[k][j].equals("馬")) score -= 10;
                    }
                }
            }
        }
        
        return score;
    }
    
    private int evaluateCenterControl(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        int[][] center = {{3,3},{3,4},{3,5},{4,3},{4,4},{4,5},{5,3},{5,4},{5,5}};
        for (int[] pos : center) {
            if (b[pos[0]][pos[1]] != null) {
                score += isRed[pos[0]][pos[1]] ? 5 : -5;
            }
        }
        
        return score;
    }
    
    public MoveAnalysis analyzeMove(ChessBoard board, Move move, String color) {
        MoveAnalysis analysis = new MoveAnalysis();
        analysis.setMove(move);
        
        int beforeScore = evaluatePosition(board);
        ChessBoard newBoard = chessEngine.makeMove(board, move, color);
        int afterScore = evaluatePosition(newBoard);
        
        int scoreDiff = color.equals("red") ? afterScore - beforeScore : beforeScore - afterScore;
        
        analysis.setScoreBefore(beforeScore);
        analysis.setScoreAfter(afterScore);
        analysis.setScoreChange(scoreDiff);
        
        if (scoreDiff >= 200) {
            analysis.setQuality(MoveQuality.EXCELLENT);
            analysis.setComment("好棋！局面优势明显");
        } else if (scoreDiff >= 100) {
            analysis.setQuality(MoveQuality.GOOD);
            analysis.setComment("不错的一步，扩大了优势");
        } else if (scoreDiff >= 30) {
            analysis.setQuality(MoveQuality.ACCEPTABLE);
            analysis.setComment("可接受的走法");
        } else if (scoreDiff >= -30) {
            analysis.setQuality(MoveQuality.EQUAL);
            analysis.setComment("平稳的走法，局面相当");
        } else if (scoreDiff >= -100) {
            analysis.setQuality(MoveQuality.MISTAKE);
            analysis.setComment("疑问手，略微亏损");
        } else {
            analysis.setQuality(MoveQuality.BLUNDER);
            analysis.setComment("昏招！明显亏损");
        }
        
        analysis.setCheck(chessEngine.isKingInCheck(newBoard, color.equals("red") ? "black" : "red"));
        
        if (move.getCaptured() != null && !move.getCaptured().isEmpty()) {
            analysis.setCaptured(true);
            analysis.setCapturedValue(PIECE_VALUES.getOrDefault(move.getCaptured(), 0));
        }
        
        return analysis;
    }
    
    public List<MoveSuggestion> getSuggestedMoves(ChessBoard board, String color, int maxSuggestions) {
        List<Move> moves = chessEngine.getAllValidMoves(board, color);
        List<MoveSuggestion> suggestions = new ArrayList<>();
        
        for (Move move : moves) {
            MoveAnalysis analysis = analyzeMove(board, move, color);
            MoveSuggestion suggestion = new MoveSuggestion();
            suggestion.setMove(move);
            suggestion.setScore(analysis.getScoreAfter());
            suggestion.setReason(analysis.getComment());
            suggestions.add(suggestion);
        }
        
        suggestions.sort((a, b) -> color.equals("red") ? 
            Integer.compare(b.getScore(), a.getScore()) : Integer.compare(a.getScore(), b.getScore()));
        
        return suggestions.stream().limit(maxSuggestions).toList();
    }
    
    public GameAnalysis analyzeGame(List<Move> moves, boolean isAiGame) {
        GameAnalysis gameAnalysis = new GameAnalysis();
        List<MoveAnalysis> moveAnalyses = new ArrayList<>();
        
        ChessBoard board = new ChessBoard();
        List<Integer> scoreHistory = new ArrayList<>();
        
        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            String color = (i % 2 == 0) ? "red" : "black";
            
            MoveAnalysis analysis = analyzeMove(board, move, color);
            moveAnalyses.add(analysis);
            
            board = chessEngine.makeMove(board, move, color);
            scoreHistory.add(evaluatePosition(board));
        }
        
        gameAnalysis.setMoveAnalyses(moveAnalyses);
        gameAnalysis.setScoreHistory(scoreHistory);
        gameAnalysis.setTotalMoves(moves.size());
        
        if (!scoreHistory.isEmpty()) {
            int finalScore = scoreHistory.get(scoreHistory.size() - 1);
            if (finalScore > 300) gameAnalysis.setOverallResult("红方大优");
            else if (finalScore > 100) gameAnalysis.setOverallResult("红方稍优");
            else if (finalScore < -300) gameAnalysis.setOverallResult("黑方大优");
            else if (finalScore < -100) gameAnalysis.setOverallResult("黑方稍优");
            else gameAnalysis.setOverallResult("局面相当");
        }
        
        int excellent = 0, good = 0, mistake = 0, blunder = 0;
        for (MoveAnalysis ma : moveAnalyses) {
            switch (ma.getQuality()) {
                case EXCELLENT: excellent++; break;
                case GOOD: good++; break;
                case MISTAKE: mistake++; break;
                case BLUNDER: blunder++; break;
            }
        }
        
        gameAnalysis.setExcellentMoves(excellent);
        gameAnalysis.setGoodMoves(good);
        gameAnalysis.setMistakes(mistake);
        gameAnalysis.setBlunders(blunder);
        
        return gameAnalysis;
    }
    
    // ========== 内部类 ==========
    
    public enum MoveQuality {
        EXCELLENT("好棋", 5), GOOD("不错", 4), ACCEPTABLE("可接受", 3),
        EQUAL("均势", 2), MISTAKE("疑问手", 1), BLUNDER("昏招", 0);
        
        private final String description;
        private final int level;
        
        MoveQuality(String description, int level) {
            this.description = description;
            this.level = level;
        }
        
        public String getDescription() { return description; }
        public int getLevel() { return level; }
    }
    
    public static class MoveAnalysis {
        private Move move;
        private int scoreBefore, scoreAfter, scoreChange;
        private MoveQuality quality;
        private String comment;
        private boolean isCheck, isCaptured;
        private int capturedValue;
        private boolean isKeyMoment;
        private String keyMomentReason;
        
        public Move getMove() { return move; }
        public void setMove(Move move) { this.move = move; }
        public int getScoreBefore() { return scoreBefore; }
        public void setScoreBefore(int scoreBefore) { this.scoreBefore = scoreBefore; }
        public int getScoreAfter() { return scoreAfter; }
        public void setScoreAfter(int scoreAfter) { this.scoreAfter = scoreAfter; }
        public int getScoreChange() { return scoreChange; }
        public void setScoreChange(int scoreChange) { this.scoreChange = scoreChange; }
        public MoveQuality getQuality() { return quality; }
        public void setQuality(MoveQuality quality) { this.quality = quality; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public boolean isCheck() { return isCheck; }
        public void setCheck(boolean check) { isCheck = check; }
        public boolean isCaptured() { return isCaptured; }
        public void setCaptured(boolean captured) { isCaptured = captured; }
        public int getCapturedValue() { return capturedValue; }
        public void setCapturedValue(int capturedValue) { this.capturedValue = capturedValue; }
        public boolean isKeyMoment() { return isKeyMoment; }
        public void setKeyMoment(boolean keyMoment) { isKeyMoment = keyMoment; }
        public String getKeyMomentReason() { return keyMomentReason; }
        public void setKeyMomentReason(String keyMomentReason) { this.keyMomentReason = keyMomentReason; }
    }
    
    public static class MoveSuggestion {
        private Move move;
        private int score;
        private String reason;
        
        public Move getMove() { return move; }
        public void setMove(Move move) { this.move = move; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    public static class GameAnalysis {
        private List<MoveAnalysis> moveAnalyses;
        private List<Integer> scoreHistory;
        private int totalMoves;
        private String overallResult;
        private int excellentMoves, goodMoves, mistakes, blunders;
        private AiAnalysis aiAnalysis;
        
        public List<MoveAnalysis> getMoveAnalyses() { return moveAnalyses; }
        public void setMoveAnalyses(List<MoveAnalysis> moveAnalyses) { this.moveAnalyses = moveAnalyses; }
        public List<Integer> getScoreHistory() { return scoreHistory; }
        public void setScoreHistory(List<Integer> scoreHistory) { this.scoreHistory = scoreHistory; }
        public int getTotalMoves() { return totalMoves; }
        public void setTotalMoves(int totalMoves) { this.totalMoves = totalMoves; }
        public String getOverallResult() { return overallResult; }
        public void setOverallResult(String overallResult) { this.overallResult = overallResult; }
        public int getExcellentMoves() { return excellentMoves; }
        public void setExcellentMoves(int excellentMoves) { this.excellentMoves = excellentMoves; }
        public int getGoodMoves() { return goodMoves; }
        public void setGoodMoves(int goodMoves) { this.goodMoves = goodMoves; }
        public int getMistakes() { return mistakes; }
        public void setMistakes(int mistakes) { this.mistakes = mistakes; }
        public int getBlunders() { return blunders; }
        public void setBlunders(int blunders) { this.blunders = blunders; }
        public AiAnalysis getAiAnalysis() { return aiAnalysis; }
        public void setAiAnalysis(AiAnalysis aiAnalysis) { this.aiAnalysis = aiAnalysis; }
    }
    
    public static class AiAnalysis {
        private int totalMistakes, totalBlunders, goodMoves;
        private String overallAssessment;
        private List<String> observations;
        
        public int getTotalMistakes() { return totalMistakes; }
        public void setTotalMistakes(int totalMistakes) { this.totalMistakes = totalMistakes; }
        public int getTotalBlunders() { return totalBlunders; }
        public void setTotalBlunders(int totalBlunders) { this.totalBlunders = totalBlunders; }
        public int getGoodMoves() { return goodMoves; }
        public void setGoodMoves(int goodMoves) { this.goodMoves = goodMoves; }
        public String getOverallAssessment() { return overallAssessment; }
        public void setOverallAssessment(String overallAssessment) { this.overallAssessment = overallAssessment; }
        public List<String> getObservations() { return observations; }
        public void setObservations(List<String> observations) { this.observations = observations; }
    }
    
    public ChessEngine getChessEngine() { return chessEngine; }
    
    public Map<String, Object> getRealtimeAnalysis(ChessBoard board) {
        Map<String, Object> analysis = new HashMap<>();
        
        int score = evaluatePosition(board);
        analysis.put("score", score);
        
        String evaluation;
        if (Math.abs(score) < 50) evaluation = "均势";
        else if (score > 0) evaluation = score > 200 ? "红方大优" : "红方稍占优";
        else evaluation = score < -200 ? "黑方大优" : "黑方稍占优";
        
        analysis.put("evaluation", evaluation);
        analysis.put("pieceCount", countPieces(board));
        analysis.put("threats", analyzeThreats(board));
        
        if (chessEngine.isKingInCheck(board, board.getCurrentTurn())) {
            analysis.put("suggestion", "当前被将军，应先解除将军！");
        } else if (score > 100) {
            analysis.put("suggestion", "局面占优，建议主动进攻！");
        } else if (score < -100) {
            analysis.put("suggestion", "局面被动，建议稳固防守！");
        } else {
            analysis.put("suggestion", "局面均衡，稳步推进！");
        }
        
        return analysis;
    }
    
    private Map<String, Integer> countPieces(ChessBoard board) {
        Map<String, Integer> count = new HashMap<>();
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        int redValue = 0, blackValue = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    int value = PIECE_VALUES.getOrDefault(b[i][j], 0);
                    if (isRed[i][j]) redValue += value;
                    else blackValue += value;
                }
            }
        }
        
        count.put("redValue", redValue);
        count.put("blackValue", blackValue);
        count.put("materialDiff", redValue - blackValue);
        
        return count;
    }
    
    private List<String> analyzeThreats(ChessBoard board) {
        List<String> threats = new ArrayList<>();
        
        if (chessEngine.isKingInCheck(board, "red")) threats.add("红方被将军！");
        if (chessEngine.isKingInCheck(board, "black")) threats.add("黑方被将军！");
        
        return threats;
    }
}
