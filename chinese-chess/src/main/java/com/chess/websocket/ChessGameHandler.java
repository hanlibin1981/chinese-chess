package com.chess.websocket;

import com.chess.model.ChessBoard;
import com.chess.model.Game;
import com.chess.model.Move;
import com.chess.service.ChessAnalysisService;
import com.chess.service.ChessEngine;
import com.chess.service.GameService;
import com.chess.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Stack;

@Controller
public class ChessGameHandler {
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ChessEngine chessEngine;
    
    @Autowired
    private ChessAnalysisService analysisService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 游戏状态缓存
    private final Map<Long, GameState> gameStates = new ConcurrentHashMap<>();
    
    public static class GameState {
        public ChessBoard board;
        public Long redPlayer;
        public Long blackPlayer;
        public String status;
        public boolean undoRequested;
        public Long undoRequestedBy;
        public Stack<ChessBoard> history; // 用于悔棋
        
        public GameState(ChessBoard board, Long redPlayer, Long blackPlayer) {
            this.board = board;
            this.redPlayer = redPlayer;
            this.blackPlayer = blackPlayer;
            this.status = "playing";
            this.history = new Stack<>();
        }
    }
    
    @MessageMapping("/game/join")
    public void joinGame(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        
        Game game = gameService.findById(gameId);
        if (game == null) return;
        
        GameState state = gameStates.get(gameId);
        if (state == null) {
            ChessBoard board = gameService.loadBoardFromPgn(game.getPgn());
            Long blackPlayer = game.getIsAi() ? null : game.getPlayerBlack();
            state = new GameState(board, game.getPlayerRed(), blackPlayer);
            gameStates.put(gameId, state);
        }
        
        // 广播游戏状态 - 使用 HashMap 避免 null 值问题
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "state");
        msg.put("board", state.board);
        msg.put("currentTurn", state.board.getCurrentTurn());
        msg.put("redPlayer", state.redPlayer);
        msg.put("blackPlayer", state.blackPlayer);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, msg);
    }
    
    @MessageMapping("/game/move")
    public void makeMove(Map<String, Object> data) throws Exception {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        int fromX = Integer.parseInt(data.get("fromX").toString());
        int fromY = Integer.parseInt(data.get("fromY").toString());
        int toX = Integer.parseInt(data.get("toX").toString());
        int toY = Integer.parseInt(data.get("toY").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null || !state.status.equals("playing")) return;
        
        // 验证是否是合法的玩家
        String color = null;
        if (state.redPlayer.equals(userId)) {
            color = "red";
        } else if (state.blackPlayer != null && state.blackPlayer.equals(userId)) {
            color = "black";
        } else if (userId == -1) {
            color = "black"; // AI
        }
        
        if (color == null || !color.equals(state.board.getCurrentTurn())) {
            return;
        }
        
        // 获取走法
        String piece = state.board.getBoard()[fromX][fromY];
        Move move = new Move(fromX, fromY, toX, toY, piece);
        
        // 验证走法合法性
        if (!isValidMove(state.board, move, color)) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "error",
                    "message", "无效的走法"
            ));
            return;
        }
        
        // 保存当前棋盘到历史记录（用于悔棋）
        state.history.push(state.board.copy());
        
        // 执行走法
        state.board = chessEngine.makeMove(state.board, move, color);
        
        // 保存棋谱
        Game game = gameService.findById(gameId);
        game.setPgn(gameService.boardToPgn(state.board));
        
        // 检查是否吃掉了对方的王（将/帥）
        String opponentKing = color.equals("red") ? "將" : "帥";
        boolean kingCaptured = !containsPiece(state.board, opponentKing);
        
        if (kingCaptured) {
            // 王被吃了，游戏结束
            state.status = "finished";
            game.setStatus("finished");
            game.setWinner(color);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "checkmate",
                    "winner", game.getWinner()
            ));
            gameService.saveGame(game);
            updateUserStats(game);
            return;
        }
        
        // 检查是否结束 - 检查对方是否被将死
        String opponentColor = color.equals("red") ? "black" : "red";
        boolean isCheckmate = isGameOver(state.board, opponentColor);
        
        if (isCheckmate) {
            state.status = "finished";
            game.setStatus("finished");
            game.setWinner(color);  // 当前走棋方获胜
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "checkmate",
                    "winner", game.getWinner()
            ));
        } else if (chessEngine.isKingInCheck(state.board, opponentColor)) {
            // 如果被将军但没有被将死，发送将军消息
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "check",
                    "color", opponentColor
            ));
        }
        
        gameService.saveGame(game);
        
        // 广播走法
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "move",
                "move", move,
                "board", state.board,
                "currentTurn", state.board.getCurrentTurn()
        ));
        
        // 如果是人机对战，AI走棋
        if (game.getIsAi() && state.board.getCurrentTurn().equals("black")) {
            doAiMove(gameId, state);
        }
    }
    
    private void doAiMove(Long gameId, GameState state) {
        try {
            Thread.sleep(500); // 稍微延迟，让玩家看到局面
        } catch (InterruptedException e) {}
        
        // 使用带难度的AI
        Move aiMove = chessEngine.getBestMove(state.board, "black");
        if (aiMove != null) {
            // 保存AI走棋前的棋盘
            state.history.push(state.board.copy());
            
            state.board = chessEngine.makeMove(state.board, aiMove, "black");
            
            Game game = gameService.findById(gameId);
            game.setPgn(gameService.boardToPgn(state.board));
            
            if (isGameOver(state.board, "black")) {
                state.status = "finished";
                game.setStatus("finished");
                game.setWinner("black");
                messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                        "type", "gameOver",
                        "winner", "black"
                ));
            }
            
            gameService.saveGame(game);
            updateUserStats(game);
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "move",
                    "move", aiMove,
                    "board", state.board,
                    "currentTurn", state.board.getCurrentTurn()
            ));
        }
    }
    
    @MessageMapping("/game/undo")
    public void requestUndo(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        // 检查是否有历史记录可回退
        if (state.history == null || state.history.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "error",
                    "message", "没有可悔的棋"
            ));
            return;
        }
        
        Game game = gameService.findById(gameId);
        
        // 人机模式下，自动同意悔棋（只有一个人在玩）
        if (game != null && game.getIsAi()) {
            // 撤销AI的走法（如果有）
            if (!state.history.isEmpty()) {
                state.board = state.history.pop();
            }
            // 撤销玩家的走法
            if (!state.history.isEmpty()) {
                state.board = state.history.pop();
            }
            
            // 保存到数据库
            game.setPgn(gameService.boardToPgn(state.board));
            gameService.saveGame(game);
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "undoAccepted",
                    "board", state.board
            ));
            return;
        }
        
        // 双人模式下的悔棋逻辑
        if (state.undoRequested && !state.undoRequestedBy.equals(userId)) {
            // 对方同意悔棋
            if (!state.history.isEmpty()) {
                state.board = state.history.pop();
            }
            
            state.undoRequested = false;
            state.undoRequestedBy = null;
            
            // 保存到数据库
            game.setPgn(gameService.boardToPgn(state.board));
            gameService.saveGame(game);
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "undoAccepted",
                    "board", state.board
            ));
        } else {
            // 请求悔棋
            state.undoRequested = true;
            state.undoRequestedBy = userId;
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "undoRequested",
                    "by", userId
            ));
        }
    }
    
    @MessageMapping("/game/restart")
    public void restartGame(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        Game game = gameService.findById(gameId);
        if (game == null) return;
        
        // 创建新的棋盘
        ChessBoard board = new ChessBoard();
        
        // 保存到数据库
        game.setPgn(gameService.boardToPgn(board));
        game.setStatus("playing");
        game.setWinner(null);
        gameService.saveGame(game);
        
        // 重建内存中的游戏状态
        Long blackPlayer = game.getIsAi() ? null : game.getPlayerBlack();
        GameState newState = new GameState(board, game.getPlayerRed(), blackPlayer);
        gameStates.put(gameId, newState);
        
        // 广播重新开始
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "restarted",
                "board", board
        ));
    }
    
    /**
     * 请求走法提示
     */
    @MessageMapping("/game/hint")
    public void requestHint(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        // 获取当前玩家的颜色
        String color = null;
        if (state.redPlayer.equals(userId)) {
            color = "red";
        } else if (state.blackPlayer != null && state.blackPlayer.equals(userId)) {
            color = "black";
        }
        
        if (color == null) return;
        
        // 获取提示走法
        Move hintMove = chessEngine.getHint(state.board, color);
        
        if (hintMove != null) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "hint",
                    "hintMove", hintMove,
                    "fromX", hintMove.getFromX(),
                    "fromY", hintMove.getFromY(),
                    "toX", hintMove.getToX(),
                    "toY", hintMove.getToY()
            ));
        }
    }
    
    /**
     * 设置AI难度
     */
    @MessageMapping("/game/setDifficulty")
    public void setDifficulty(Map<String, Object> data) {
        String difficulty = data.get("difficulty").toString();
        
        try {
            ChessEngine.Difficulty diff = ChessEngine.Difficulty.valueOf(difficulty.toUpperCase());
            chessEngine.setDifficulty(diff);
            
            messagingTemplate.convertAndSend("/topic/game/global", Map.of(
                    "type", "difficultyChanged",
                    "difficulty", diff.getName()
            ));
        } catch (IllegalArgumentException e) {
            messagingTemplate.convertAndSend("/topic/game/global", Map.of(
                    "type", "error",
                    "message", "无效的难度等级"
            ));
        }
    }
    
    /**
     * 请求实时局面分析
     */
    @MessageMapping("/game/analyze")
    public void analyzePosition(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        // 获取实时分析
        Map<String, Object> analysis = analysisService.getRealtimeAnalysis(state.board);
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "analysis",
                "analysis", analysis,
                "score", analysis.get("score"),
                "evaluation", analysis.get("evaluation"),
                "suggestion", analysis.get("suggestion")
        ));
    }
    
    private boolean isValidMove(ChessBoard board, Move move, String color) {
        var validMoves = chessEngine.getAllValidMoves(board, color);
        
        return validMoves.stream().anyMatch(m -> 
                m.getFromX() == move.getFromX() && 
                m.getFromY() == move.getFromY() && 
                m.getToX() == move.getToX() && 
                m.getToY() == move.getToY()
        );
    }
    
    private boolean isGameOver(ChessBoard board, String color) {
        var moves = chessEngine.getAllValidMoves(board, color);
        return moves.isEmpty();
    }
    
    private boolean containsPiece(ChessBoard board, String piece) {
        String[][] b = board.getBoard();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (piece.equals(b[i][j])) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 更新用户战绩统计
     */
    private void updateUserStats(Game game) {
        if (game.getStatus() == null || !game.getStatus().equals("finished")) {
            return;
        }
        if (game.getIsAi()) {
            // 人机对战：只更新玩家战绩
            Long playerRed = game.getPlayerRed();
            if (playerRed != null) {
                if ("red".equals(game.getWinner())) {
                    userService.addWin(playerRed);
                } else if ("black".equals(game.getWinner())) {
                    userService.addLose(playerRed);
                } else if ("draw".equals(game.getWinner())) {
                    userService.addDraw(playerRed);
                }
            }
        } else {
            // 人人对战：更新双方战绩
            Long playerRed = game.getPlayerRed();
            Long playerBlack = game.getPlayerBlack();
            
            if (playerRed != null) {
                if ("red".equals(game.getWinner())) {
                    userService.addWin(playerRed);
                    if (playerBlack != null) userService.addLose(playerBlack);
                } else if ("black".equals(game.getWinner())) {
                    userService.addLose(playerRed);
                    if (playerBlack != null) userService.addWin(playerBlack);
                } else if ("draw".equals(game.getWinner())) {
                    userService.addDraw(playerRed);
                    if (playerBlack != null) userService.addDraw(playerBlack);
                }
            }
        }
    }
}
