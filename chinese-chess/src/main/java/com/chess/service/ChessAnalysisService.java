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
    
    /**
     * 评估局面分数（正数红优，负数黑优）
     */
    public int evaluatePosition(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        int score = 0;
        
        // 1. 棋子分值
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    int value = PIECE_VALUES.getOrDefault(b[i][j], 0);
                    // 位置修正：兵卒过河加分
                    if (b[i][j].equals("兵") && i <= 4) {
                        value += (4 - i) * 20;
                    } else if (b[i][j].equals("卒") && i >= 5) {
                        value += (i - 5) * 20;
                    }
                    // 中心控制加分
                    if (i >= 3 && i <= 6 && j >= 3 && j <= 5) {
                        value += 10;
                    }
                    // 肋道加分
                    if (j == 3 || j == 5) {
                        value += 5;
                    }
                    
                    if (isRed[i][j]) {
                        score += value;
                    } else {
                        score -= value;
                    }
                }
            }
        }
        
        // 2. 士象位置修正
        score += evaluateAdvisorPosition(board);
        
        // 3. 兵卒结构修正
        score += evaluatePawnStructure(board);
        
        // 4. 出子效率修正
        score += evaluateDevelopment(board);
        
        // 5. 将帅安全评估
        score += evaluateKingSafety(board);
        
        return score;
    }
    
    private int evaluateAdvisorPosition(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 红方士象位置
        for (int i = 7; i <= 9; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && (b[i][j].equals("仕") || b[i][j].equals("相")) && isRed[i][j]) {
                    score += 10;
                }
            }
        }
        
        // 黑方士象位置
        for (int i = 0; i <= 2; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && (b[i][j].equals("士") || b[i][j].equals("象")) && !isRed[i][j]) {
                    score -= 10;
                }
            }
        }
        
        return score;
    }
    
    private int evaluatePawnStructure(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 检查有无根兵（保护）
        int[] pawnOffsets = {0, -1, 1};
        
        // 红兵
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null && b[i][j].equals("兵") && isRed[i][j]) {
                    boolean hasSupport = false;
                    for (int off : pawnOffsets) {
                        int nx = i - 1, ny = j + off;
                        if (nx >= 0 && nx < 10 && ny >= 0 && ny < 9) {
                            if (b[nx][ny] != null && isRed[nx][ny] && 
                                (b[nx][ny].equals("兵") || b[nx][ny].equals("相") || b[nx][ny].equals("仕") || b[nx][ny].equals("帥"))) {
                                hasSupport = true;
                                break;
                            }
                        }
                    }
                    if (!hasSupport && i <= 4) {
                        score -= 15; // 孤兵扣分
                    }
                }
            }
        }
        
        // 黑卒
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null && b[i][j].equals("卒") && !isRed[i][j]) {
                    boolean hasSupport = false;
                    for (int off : pawnOffsets) {
                        int nx = i + 1, ny = j + off;
                        if (nx >= 0 && nx < 10 && ny >= 0 && ny < 9) {
                            if (b[nx][ny] != null && !isRed[nx][ny] && 
                                (b[nx][ny].equals("卒") || b[nx][ny].equals("象") || b[nx][ny].equals("士") || b[nx][ny].equals("將"))) {
                                hasSupport = true;
                                break;
                            }
                        }
                    }
                    if (!hasSupport && i >= 5) {
                        score += 15;
                    }
                }
            }
        }
        
        return score;
    }
    
    private int evaluateDevelopment(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 马的出势程度
        int[] horseBonusRed = {0, -20, -10, 0, 5, 10, 15, 20, 15, 10};
        int[] horseBonusBlack = {10, 15, 20, 15, 5, 0, -10, -20, 0, 0};
        
        for (int j = 0; j < 9; j++) {
            if (b[9][j] != null && b[9][j].equals("馬") && isRed[9][j]) {
                score -= horseBonusRed[j];
            }
            if (b[0][j] != null && b[0][j].equals("馬") && !isRed[0][j]) {
                score += horseBonusBlack[j];
            }
        }
        
        // 车的重要性：尽快出车
        if (b[9][0] != null && b[9][0].equals("車") && isRed[9][0]) {
            score -= 10; // 未出车
        }
        if (b[9][8] != null && b[9][8].equals("車") && isRed[9][8]) {
            score -= 10;
        }
        if (b[0][0] != null && b[0][0].equals("車") && !isRed[0][0]) {
            score += 10;
        }
        if (b[0][8] != null && b[0][8].equals("車") && !isRed[0][8]) {
            score += 10;
        }
        
        return score;
    }
    
    private int evaluateKingSafety(ChessBoard board) {
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        int score = 0;
        
        // 检查将帅是否暴露
        // 红帅
        int kingX = -1, kingY = -1;
        for (int i = 7; i <= 9; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && b[i][j].equals("帥") && isRed[i][j]) {
                    kingX = i; kingY = j;
                    break;
                }
            }
        }
        if (kingX >= 0) {
            // 检查是否无士
            boolean hasAdvisor = false;
            for (int i = 7; i <= 9; i++) {
                for (int j = 3; j <= 5; j++) {
                    if (b[i][j] != null && (b[i][j].equals("仕") || b[i][j].equals("相")) && isRed[i][j]) {
                        hasAdvisor = true;
                        break;
                    }
                }
            }
            if (!hasAdvisor) score -= 20;
        }
        
        // 黑将
        kingX = -1; kingY = -1;
        for (int i = 0; i <= 2; i++) {
            for (int j = 3; j <= 5; j++) {
                if (b[i][j] != null && b[i][j].equals("將") && !isRed[i][j]) {
                    kingX = i; kingY = j;
                    break;
                }
            }
        }
        if (kingX >= 0) {
            boolean hasAdvisor = false;
            for (int i = 0; i <= 2; i++) {
                for (int j = 3; j <= 5; j++) {
                    if (b[i][j] != null && (b[i][j].equals("士") || b[i][j].equals("象")) && !isRed[i][j]) {
                        hasAdvisor = true;
                        break;
                    }
                }
            }
            if (!hasAdvisor) score += 20;
        }
        
        return score;
    }
    
    /**
     * 分析单步走法的质量
     */
    public MoveAnalysis analyzeMove(ChessBoard board, Move move, String color) {
        MoveAnalysis analysis = new MoveAnalysis();
        analysis.setMove(move);
        
        // 获取走法前后的局面分数
        int beforeScore = evaluatePosition(board);
        
        // 模拟走法
        ChessBoard newBoard = chessEngine.makeMove(board, move, color);
        int afterScore = evaluatePosition(newBoard);
        
        // 红方视角：走完后分数增加红优
        int scoreDiff;
        if (color.equals("red")) {
            scoreDiff = afterScore - beforeScore;
        } else {
            scoreDiff = beforeScore - afterScore;
        }
        
        analysis.setScoreBefore(beforeScore);
        analysis.setScoreAfter(afterScore);
        analysis.setScoreChange(scoreDiff);
        
        // 评估走法质量
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
        } else if (scoreDiff >= -200) {
            analysis.setQuality(MoveQuality.BLUNDER);
            analysis.setComment("昏招！明显亏损");
        } else {
            analysis.setQuality(MoveQuality.BLUNDER);
            analysis.setComment("严重失误，可能导致失子或被杀");
        }
        
        // 检测特殊局面
        analysis.setCheck(chessEngine.isKingInCheck(newBoard, color.equals("red") ? "black" : "red"));
        
        // 检测是否吃子
        if (move.getCaptured() != null && !move.getCaptured().isEmpty()) {
            analysis.setCaptured(true);
            int capturedValue = PIECE_VALUES.getOrDefault(move.getCaptured(), 0);
            analysis.setCapturedValue(capturedValue);
            if (capturedValue >= 500) {
                analysis.setComment("吃车好棋！子力优势扩大");
            }
        }
        
        return analysis;
    }
    
    /**
     * 获取当前局面的最佳走法建议
     */
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
        
        // 按分数排序
        suggestions.sort((a, b) -> {
            if (color.equals("red")) {
                return Integer.compare(b.getScore(), a.getScore());
            } else {
                return Integer.compare(a.getScore(), b.getScore());
            }
        });
        
        return suggestions.stream().limit(maxSuggestions).toList();
    }
    
    /**
     * 完整分析整局棋
     */
    public GameAnalysis analyzeGame(List<Move> moves, boolean isAiGame) {
        GameAnalysis gameAnalysis = new GameAnalysis();
        List<MoveAnalysis> moveAnalyses = new ArrayList<>();
        
        ChessBoard board = new ChessBoard();
        int currentScore = 0;
        List<Integer> scoreHistory = new ArrayList<>();
        
        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            String color = (i % 2 == 0) ? "red" : "black";
            
            MoveAnalysis analysis = analyzeMove(board, move, color);
            moveAnalyses.add(analysis);
            
            // 更新局面
            board = chessEngine.makeMove(board, move, color);
            
            // 记录分数（转换为红方视角）
            int score = evaluatePosition(board);
            scoreHistory.add(score);
            
            // 检测关键局面
            if (Math.abs(analysis.getScoreChange()) >= 150) {
                analysis.setKeyMoment(true);
                analysis.setKeyMomentReason(detectKeyMomentReason(analysis));
            }
        }
        
        gameAnalysis.setMoveAnalyses(moveAnalyses);
        gameAnalysis.setScoreHistory(scoreHistory);
        gameAnalysis.setTotalMoves(moves.size());
        
        // 计算整体评估
        if (!scoreHistory.isEmpty()) {
            int finalScore = scoreHistory.get(scoreHistory.size() - 1);
            if (finalScore > 300) {
                gameAnalysis.setOverallResult("红方大优");
            } else if (finalScore > 100) {
                gameAnalysis.setOverallResult("红方稍优");
            } else if (finalScore < -300) {
                gameAnalysis.setOverallResult("黑方大优");
            } else if (finalScore < -100) {
                gameAnalysis.setOverallResult("黑方稍优");
            } else {
                gameAnalysis.setOverallResult("局面相当");
            }
        }
        
        // 统计各类走法
        int excellentCount = 0, goodCount = 0, mistakeCount = 0, blunderCount = 0;
        for (MoveAnalysis ma : moveAnalyses) {
            switch (ma.getQuality()) {
                case EXCELLENT: excellentCount++; break;
                case GOOD: goodCount++; break;
                case MISTAKE: mistakeCount++; break;
                case BLUNDER: blunderCount++; break;
            }
        }
        
        gameAnalysis.setExcellentMoves(excellentCount);
        gameAnalysis.setGoodMoves(goodCount);
        gameAnalysis.setMistakes(mistakeCount);
        gameAnalysis.setBlunders(blunderCount);
        
        // AI 对局特殊分析
        if (isAiGame) {
            gameAnalysis.setAiAnalysis(analyzeAiMoves(moveAnalyses));
        }
        
        return gameAnalysis;
    }
    
    private String detectKeyMomentReason(MoveAnalysis analysis) {
        if (analysis.getQuality() == MoveQuality.EXCELLENT || analysis.getQuality() == MoveQuality.GOOD) {
            if (analysis.isCheck()) {
                return "将军！关键的攻击机会";
            }
            if (analysis.isCaptured()) {
                return "关键吃子，局面转折点";
            }
            return "优势扩大的关键一步";
        } else {
            if (analysis.isCheck()) {
                return "被将军，防守出现漏洞";
            }
            if (analysis.isCaptured()) {
                return "被吃子，重大损失";
            }
            return "亏损！局面天平倾斜";
        }
    }
    
    /**
     * 分析 AI 走法
     */
    private AiAnalysis analyzeAiMoves(List<MoveAnalysis> analyses) {
        AiAnalysis aiAnalysis = new AiAnalysis();
        List<String> observations = new ArrayList<>();
        
        int aiMistakes = 0;
        int aiBlunders = 0;
        int aiGoodMoves = 0;
        
        // 假设 AI 是黑方（后手）
        for (int i = 1; i < analyses.size(); i += 2) {
            MoveAnalysis ma = analyses.get(i);
            if (ma.getQuality() == MoveQuality.MISTAKE || ma.getQuality() == MoveQuality.BLUNDER) {
                aiMistakes++;
                observations.add("第" + (i/2 + 1) + "步（AI）走出" + ma.getQuality().getDescription() + 
                    "：" + ma.getComment());
            } else if (ma.getQuality() == MoveQuality.EXCELLENT || ma.getQuality() == MoveQuality.GOOD) {
                aiGoodMoves++;
            }
        }
        
        aiAnalysis.setTotalMistakes(aiMistakes);
        aiAnalysis.setTotalBlunders(aiBlunders);
        aiAnalysis.setGoodMoves(aiGoodMoves);
        aiAnalysis.setObservations(observations);
        
        if (aiMistakes == 0 && aiBlunders == 0) {
            aiAnalysis.setOverallAssessment("AI 发挥出色，局势把控得当");
        } else if (aiMistakes <= 2) {
            aiAnalysis.setOverallAssessment("AI 表现正常，存在少量失误");
        } else {
            aiAnalysis.setOverallAssessment("AI 存在明显短板，建议优化算法");
        }
        
        return aiAnalysis;
    }
    
    // ========== 内部类 ==========
    
    public enum MoveQuality {
        EXCELLENT("好棋", 5),
        GOOD("不错", 4),
        ACCEPTABLE("可接受", 3),
        EQUAL("均势", 2),
        MISTAKE("疑问手", 1),
        BLUNDER("昏招", 0);
        
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
        private int scoreBefore;
        private int scoreAfter;
        private int scoreChange;
        private MoveQuality quality;
        private String comment;
        private boolean isCheck;
        private boolean isCaptured;
        private int capturedValue;
        private boolean isKeyMoment;
        private String keyMomentReason;
        
        // getters and setters
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
        private int excellentMoves;
        private int goodMoves;
        private int mistakes;
        private int blunders;
        private AiAnalysis aiAnalysis;
        
        // getters and setters
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
        private int totalMistakes;
        private int totalBlunders;
        private int goodMoves;
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
    
    public ChessEngine getChessEngine() {
        return chessEngine;
    }
    
    /**
     * 获取实时局面分析
     */
    public Map<String, Object> getRealtimeAnalysis(ChessBoard board) {
        Map<String, Object> analysis = new HashMap<>();
        
        int score = evaluatePosition(board);
        analysis.put("score", score);
        
        // 局面评估描述
        String evaluation;
        if (Math.abs(score) < 50) {
            evaluation = "均势";
        } else if (score > 0) {
            evaluation = score > 200 ? "红方大优" : "红方稍占优";
        } else {
            evaluation = score < -200 ? "黑方大优" : "黑方稍占优";
        }
        analysis.put("evaluation", evaluation);
        
        // 棋子价值统计
        Map<String, Integer> pieceCount = countPieces(board);
        analysis.put("pieceCount", pieceCount);
        
        // 威胁分析
        List<String> threats = analyzeThreats(board);
        analysis.put("threats", threats);
        
        // 建议
        String suggestion = getSuggestion(board, score);
        analysis.put("suggestion", suggestion);
        
        return analysis;
    }
    
    /**
     * 统计棋子数量
     */
    private Map<String, Integer> countPieces(ChessBoard board) {
        Map<String, Integer> count = new HashMap<>();
        String[][] b = board.getBoard();
        boolean[][] isRed = board.getIsRed();
        
        int redPieces = 0, blackPieces = 0;
        int redValue = 0, blackValue = 0;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (b[i][j] != null) {
                    int value = PIECE_VALUES.getOrDefault(b[i][j], 0);
                    if (isRed[i][j]) {
                        redPieces++;
                        redValue += value;
                    } else {
                        blackPieces++;
                        blackValue += value;
                    }
                }
            }
        }
        
        count.put("redPieces", redPieces);
        count.put("blackPieces", blackPieces);
        count.put("redValue", redValue);
        count.put("blackValue", blackValue);
        count.put("materialDiff", redValue - blackValue);
        
        return count;
    }
    
    /**
     * 分析威胁
     */
    private List<String> analyzeThreats(ChessBoard board) {
        List<String> threats = new ArrayList<>();
        
        // 检查是否被将军
        if (chessEngine.isKingInCheck(board, "red")) {
            threats.add("红方被将军！");
        }
        if (chessEngine.isKingInCheck(board, "black")) {
            threats.add("黑方被将军！");
        }
        
        return threats;
    }
    
    /**
     * 获取建议
     */
    private String getSuggestion(ChessBoard board, int score) {
        if (chessEngine.isKingInCheck(board, board.getCurrentTurn())) {
            return "当前被将军，应先解除将军！";
        }
        
        if (score > 100) {
            return "局面占优，建议主动进攻！";
        } else if (score < -100) {
            return "局面被动，建议稳固防守！";
        } else {
            return "局面均衡，稳步推进！";
        }
    }
}
