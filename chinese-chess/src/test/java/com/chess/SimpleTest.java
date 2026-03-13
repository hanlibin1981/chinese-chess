package com.chess;

import com.chess.model.ChessBoard;
import com.chess.model.Move;
import com.chess.service.ChessEngine;
import com.chess.service.ChessAnalysisService;
import com.chess.service.OpeningBook;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleTest {
    
    private static ChessEngine chessEngine;
    private static ChessAnalysisService analysisService;
    
    @BeforeAll
    static void setup() {
        chessEngine = new ChessEngine();
        analysisService = new ChessAnalysisService();
        // 使用反射注入依赖
        try {
            var field = ChessAnalysisService.class.getDeclaredField("chessEngine");
            field.setAccessible(true);
            field.set(analysisService, chessEngine);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    @DisplayName("测试1: AI搜索深度")
    void testAiSearchDepth() {
        chessEngine.setDifficulty(ChessEngine.Difficulty.EASY);
        assertEquals(1, chessEngine.getDifficulty().getSearchDepth());
        
        chessEngine.setDifficulty(ChessEngine.Difficulty.MEDIUM);
        assertEquals(2, chessEngine.getDifficulty().getSearchDepth());
        
        chessEngine.setDifficulty(ChessEngine.Difficulty.HARD);
        assertEquals(3, chessEngine.getDifficulty().getSearchDepth());
        
        chessEngine.setDifficulty(ChessEngine.Difficulty.EXPERT);
        assertEquals(4, chessEngine.getDifficulty().getSearchDepth());
        
        ChessBoard board = new ChessBoard();
        Move move = chessEngine.getBestMove(board, "red");
        assertNotNull(move);
        
        System.out.println("✅ 测试1通过: AI搜索深度");
    }
    
    @Test
    @DisplayName("测试2: 走法合法性")
    void testValidMoves() {
        ChessBoard board = new ChessBoard();
        
        var redMoves = chessEngine.getAllValidMoves(board, "red");
        assertFalse(redMoves.isEmpty());
        
        var blackMoves = chessEngine.getAllValidMoves(board, "black");
        assertFalse(blackMoves.isEmpty());
        
        for (Move m : redMoves) {
            ChessBoard test = chessEngine.makeMove(board, m, "red");
            assertFalse(chessEngine.isKingInCheck(test, "red"));
        }
        
        System.out.println("✅ 测试2通过: 走法合法性");
    }
    
    @Test
    @DisplayName("测试3: 将军检测")
    void testCheckDetection() {
        ChessBoard board = new ChessBoard();
        
        assertFalse(chessEngine.isKingInCheck(board, "red"));
        assertFalse(chessEngine.isKingInCheck(board, "black"));
        
        System.out.println("✅ 测试3通过: 将军检测");
    }
    
    @Test
    @DisplayName("测试4: 将死检测")
    void testCheckmate() {
        ChessBoard board = new ChessBoard();
        
        assertFalse(chessEngine.isCheckmate(board, "red"));
        assertFalse(chessEngine.isCheckmate(board, "black"));
        
        System.out.println("✅ 测试4通过: 将死检测");
    }
    
    @Test
    @DisplayName("测试5: 困毙检测")
    void testStalemate() {
        ChessBoard board = new ChessBoard();
        
        assertFalse(chessEngine.isStalemate(board, "red"));
        assertFalse(chessEngine.isStalemate(board, "black"));
        
        System.out.println("✅ 测试5通过: 困毙检测");
    }
    
    @Test
    @DisplayName("测试6: 三次重复检测")
    void testRepetition() {
        java.util.List<String> history = java.util.List.of("pos1", "pos2", "pos1", "pos2", "pos1");
        assertTrue(chessEngine.isDrawByRepetition(history, "pos1"));
        
        history = java.util.List.of("pos1", "pos2", "pos1");
        assertFalse(chessEngine.isDrawByRepetition(history, "pos1"));
        
        System.out.println("✅ 测试6通过: 三次重复检测");
    }
    
    @Test
    @DisplayName("测试7: 局面评估")
    void testEvaluation() {
        ChessBoard board = new ChessBoard();
        
        int score = analysisService.evaluatePosition(board);
        assertTrue(score > -100 && score < 200);
        
        System.out.println("✅ 测试7通过: 局面评估, 初始分数: " + score);
    }
    
    @Test
    @DisplayName("测试8: 开局库")
    void testOpeningBook() {
        ChessBoard board = new ChessBoard();
        
        var move1 = OpeningBook.getBookMove(board, "red", 1);
        assertNotNull(move1);
        
        if (move1 != null) {
            board = chessEngine.makeMove(board, new Move(move1.fromX, move1.fromY, move1.toX, move1.toY, move1.piece), "red");
        }
        
        var move2 = OpeningBook.getBookMove(board, "black", 2);
        assertNotNull(move2);
        
        String opening = OpeningBook.identifyOpening(board);
        assertNotNull(opening);
        
        System.out.println("✅ 测试8通过: 开局库, 开局: " + opening);
    }
    
    @Test
    @DisplayName("测试9: AI提示")
    void testHint() {
        ChessBoard board = new ChessBoard();
        
        var hint = chessEngine.getHint(board, "red");
        assertNotNull(hint);
        
        var valid = chessEngine.getAllValidMoves(board, "red");
        boolean found = valid.stream().anyMatch(m -> m.getFromX() == hint.getFromX() && m.getFromY() == hint.getFromY());
        assertTrue(found);
        
        System.out.println("✅ 测试9通过: AI提示");
    }
    
    @Test
    @DisplayName("测试10: 走法分析")
    void testMoveAnalysis() {
        ChessBoard board = new ChessBoard();
        
        var moves = chessEngine.getAllValidMoves(board, "red");
        var move = moves.get(0);
        
        var analysis = analysisService.analyzeMove(board, move, "red");
        assertNotNull(analysis.getQuality());
        assertNotNull(analysis.getComment());
        
        System.out.println("✅ 测试10通过: 走法分析, 质量: " + analysis.getQuality().getDescription());
    }
    
    @Test
    @DisplayName("测试11: 局面签名")
    void testPositionSignature() {
        ChessBoard board = new ChessBoard();
        
        String sig1 = chessEngine.generatePositionSignature(board);
        assertNotNull(sig1);
        
        Move m = new Move(9, 0, 8, 0, "車");
        ChessBoard board2 = chessEngine.makeMove(board, m, "red");
        
        String sig2 = chessEngine.generatePositionSignature(board2);
        assertNotEquals(sig1, sig2);
        
        System.out.println("✅ 测试11通过: 局面签名");
    }
    
    @Test
    @DisplayName("测试12: 难度名称")
    void testDifficultyNames() {
        assertEquals("简单", ChessEngine.Difficulty.EASY.getName());
        assertEquals("中等", ChessEngine.Difficulty.MEDIUM.getName());
        assertEquals("困难", ChessEngine.Difficulty.HARD.getName());
        assertEquals("大师", ChessEngine.Difficulty.EXPERT.getName());
        
        System.out.println("✅ 测试12通过: 难度名称");
    }
    
    @Test
    @DisplayName("测试13: 实时分析")
    void testRealtimeAnalysis() {
        ChessBoard board = new ChessBoard();
        
        var analysis = analysisService.getRealtimeAnalysis(board);
        assertNotNull(analysis);
        assertTrue(analysis.containsKey("score"));
        assertTrue(analysis.containsKey("evaluation"));
        assertTrue(analysis.containsKey("suggestion"));
        
        System.out.println("✅ 测试13通过: 实时分析, 评估: " + analysis.get("evaluation"));
    }
    
    @Test
    @DisplayName("测试14: 整局分析")
    void testGameAnalysis() {
        java.util.List<Move> moves = new java.util.ArrayList<>();
        ChessBoard board = new ChessBoard();
        
        moves.add(new Move(9, 0, 8, 0, "車"));
        board = chessEngine.makeMove(board, moves.get(0), "red");
        
        moves.add(new Move(0, 0, 1, 0, "車"));
        board = chessEngine.makeMove(board, moves.get(1), "black");
        
        var gameAnalysis = analysisService.analyzeGame(moves, true);
        assertNotNull(gameAnalysis);
        assertEquals(2, gameAnalysis.getTotalMoves());
        
        System.out.println("✅ 测试14通过: 整局分析, 总步数: " + gameAnalysis.getTotalMoves());
    }
    
    @Test
    @DisplayName("测试15: 最佳走法获取")
    void testGetBestMove() {
        ChessBoard board = new ChessBoard();
        
        for (var diff : ChessEngine.Difficulty.values()) {
            chessEngine.setDifficulty(diff);
            Move move = chessEngine.getBestMove(board, "red");
            assertNotNull(move);
        }
        
        System.out.println("✅ 测试15通过: 最佳走法获取");
    }
}
