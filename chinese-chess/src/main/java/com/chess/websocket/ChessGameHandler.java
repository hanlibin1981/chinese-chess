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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
    
    // 局面历史（用于三次重复检测）
    private final Map<Long, List<String>> positionHistory = new ConcurrentHashMap<>();
    
    public static class GameState {
        public ChessBoard board;
        public Long redPlayer;
        public Long blackPlayer;
        public String status;
        public boolean undoRequested;
        public Long undoRequestedBy;
        public Stack<ChessBoard> history;
        public List<Move> moveHistory;
        public Long resignedBy;
        public String drawOfferedBy;
        public Map<String, Integer> playerTime; // 玩家剩余时间(秒)
        public Integer initialTime; // 初始时间(秒)
        public Integer incrementTime; // 读秒增量(秒)
        public boolean timeControlEnabled;
        
        public GameState(ChessBoard board, Long redPlayer, Long blackPlayer) {
            this.board = board;
            this.redPlayer = redPlayer;
            this.blackPlayer = blackPlayer;
            this.status = "playing";
            this.history = new Stack<>();
            this.moveHistory = new CopyOnWriteArrayList<>();
            this.playerTime = new HashMap<>();
            this.timeControlEnabled = false;
            this.initialTime = 600; // 默认10分钟
            this.incrementTime = 0;
            playerTime.put("red", 600);
            playerTime.put("black", 600);
        }
        
        public GameState(ChessBoard board, Long redPlayer, Long blackPlayer, Integer initialTime, Integer incrementTime) {
            this(board, redPlayer, blackPlayer);
            this.initialTime = initialTime;
            this.incrementTime = incrementTime;
            this.timeControlEnabled = true;
            playerTime.put("red", initialTime);
            playerTime.put("black", initialTime);
        }
    }
    
    @MessageMapping("/game/join")
    public void joinGame(Map<String, Object> data) {
        Object gameIdObj = data.get("gameId");
        Object userIdObj = data.get("userId");

        if (gameIdObj == null) return;

        Long gameId = Long.parseLong(gameIdObj.toString());
        Long userId = null;
        if (userIdObj != null) {
            try {
                userId = Long.parseLong(userIdObj.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        Game game = gameService.findById(gameId);
        if (game == null) return;
        
        GameState state = gameStates.get(gameId);
        if (state == null) {
            ChessBoard board = gameService.loadBoardFromPgn(game.getPgn());
            Long blackPlayer = game.getIsAi() ? null : game.getPlayerBlack();
            state = new GameState(board, game.getPlayerRed(), blackPlayer);
            gameStates.put(gameId, state);
            positionHistory.put(gameId, new ArrayList<>());
        } else {
            // 如果已经有游戏状态，更新玩家信息（处理玩家重新加入的情况）
            if (game.getPlayerBlack() != null && state.blackPlayer == null) {
                state.blackPlayer = game.getPlayerBlack();
            }
            if (state.status.equals("waiting")) {
                state.status = "playing";
            }
        }
        
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "state");
        msg.put("board", state.board);
        msg.put("currentTurn", state.board.getCurrentTurn());
        msg.put("redPlayer", state.redPlayer);
        msg.put("blackPlayer", state.blackPlayer);
        msg.put("status", state.status);
        if (game.getRedUsername() != null) msg.put("redUsername", game.getRedUsername());
        if (game.getBlackUsername() != null) msg.put("blackUsername", game.getBlackUsername());
        else if (game.getIsAi() != null && game.getIsAi()) msg.put("blackUsername", "AI");
        messagingTemplate.convertAndSend("/topic/game/" + gameId, msg);
    }
    
    @MessageMapping("/game/move")
    public void makeMove(Map<String, Object> data) {
        if (data.get("gameId") == null || data.get("userId") == null ||
            data.get("fromX") == null || data.get("fromY") == null ||
            data.get("toX") == null || data.get("toY") == null) {
            return;
        }
        
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        int fromX = Integer.parseInt(data.get("fromX").toString());
        int fromY = Integer.parseInt(data.get("fromY").toString());
        int toX = Integer.parseInt(data.get("toX").toString());
        int toY = Integer.parseInt(data.get("toY").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null || !state.status.equals("playing")) return;
        
        String color = null;
        if (state.redPlayer != null && state.redPlayer.equals(userId)) {
            color = "red";
        } else if (state.blackPlayer != null && state.blackPlayer.equals(userId)) {
            color = "black";
        } else if (userId == -1) {
            color = "black";
        }
        
        if (color == null || !color.equals(state.board.getCurrentTurn())) return;
        
        String piece = state.board.getBoard()[fromX][fromY];
        Move move = new Move(fromX, fromY, toX, toY, piece);
        
        if (!isValidMove(state.board, move, color)) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "error", "message", "无效的走法"
            ));
            return;
        }
        
        // 保存历史
        state.history.push(state.board.copy());
        state.moveHistory.add(move);
        
        // 记录局面
        List<String> history = positionHistory.get(gameId);
        if (history != null) {
            history.add(chessEngine.generatePositionSignature(state.board));
        }
        
        // 执行走法
        state.board = chessEngine.makeMove(state.board, move, color);
        
        Game game = gameService.findById(gameId);
        game.setPgn(gameService.boardToPgn(state.board));
        
        // 检查三次重复
        if (history != null && history.size() >= 6) {
            String currentPos = chessEngine.generatePositionSignature(state.board);
            if (chessEngine.isDrawByRepetition(history, currentPos)) {
                state.status = "finished";
                game.setStatus("finished");
                game.setWinner("draw");
                game.setEndedAt(java.time.LocalDateTime.now());
                gameService.saveGame(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "draw",
                    "reason", "三次重复局面和棋"
                ));
                return;
            }
        }
        
        // 检查将死
        String opponentKing = color.equals("red") ? "將" : "帥";
        boolean kingCaptured = !containsPiece(state.board, opponentKing);
        
        if (kingCaptured) {
            state.status = "finished";
            game.setStatus("finished");
            game.setWinner(color);
            game.setEndedAt(java.time.LocalDateTime.now());
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "checkmate", "winner", color
            ));
            gameService.saveGame(game);
            updateUserStats(game);
            return;
        }
        
        // 检查将军
        String opponentColor = color.equals("red") ? "black" : "red";
        if (chessEngine.isKingInCheck(state.board, opponentColor)) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "check", "color", opponentColor
            ));
        }
        
        // 检查将死
        boolean isCheckmate = isGameOver(state.board, opponentColor);
        
        if (isCheckmate) {
            state.status = "finished";
            game.setStatus("finished");
            game.setWinner(color);
            game.setEndedAt(java.time.LocalDateTime.now());
            gameService.saveGame(game);
            updateUserStats(game);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "checkmate",
                "winner", color,
                "message", color.equals("red") ? "红方获胜！" : "黑方获胜！"
            ));
            return;
        }
        
        // 检查困毙
        if (chessEngine.isStalemate(state.board, opponentColor)) {
            state.status = "finished";
            game.setStatus("finished");
            game.setWinner("draw");
            game.setEndedAt(java.time.LocalDateTime.now());
            gameService.saveGame(game);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "draw",
                "reason", "困毙和棋"
            ));
            return;
        }
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "move",
            "move", move,
            "board", state.board,
            "currentTurn", state.board.getCurrentTurn()
        ));
        
        // 人机对战
        if (game.getIsAi() && state.board.getCurrentTurn().equals("black")) {
            doAiMove(gameId, state, game);
        } else if (state.timeControlEnabled && state.incrementTime != null && state.incrementTime > 0) {
            // 走棋后增加时间
            String prevColor = color.equals("red") ? "black" : "red";
            Integer prevTime = state.playerTime.get(prevColor);
            if (prevTime != null) {
                state.playerTime.put(prevColor, Math.min(prevTime + state.incrementTime, state.initialTime));
            }
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "timeUpdate",
                "currentTurn", state.board.getCurrentTurn(),
                "redTime", state.playerTime.get("red"),
                "blackTime", state.playerTime.get("black")
            ));
        }
    }
    
    private void doAiMove(Long gameId, GameState state, Game game) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {}
        
        Move aiMove = chessEngine.getBestMove(state.board, "black");
        if (aiMove != null) {
            state.history.push(state.board.copy());
            state.moveHistory.add(aiMove);
            
            List<String> history = positionHistory.get(gameId);
            if (history != null) {
                history.add(chessEngine.generatePositionSignature(state.board));
            }
            
            state.board = chessEngine.makeMove(state.board, aiMove, "black");
            game.setPgn(gameService.boardToPgn(state.board));
            
            if (chessEngine.isKingInCheck(state.board, "red")) {
                messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "check", "color", "red"
                ));
            }
            
            if (isGameOver(state.board, "red")) {
                state.status = "finished";
                game.setStatus("finished");
                game.setWinner("black");
                game.setEndedAt(java.time.LocalDateTime.now());
                gameService.saveGame(game);
                updateUserStats(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "checkmate",
                    "winner", "black",
                    "message", "将死！你输了"
                ));
                return;
            }
            
            gameService.saveGame(game);
            
            // 更新计时
            if (state.timeControlEnabled && state.initialTime != null) {
                if (state.incrementTime != null && state.incrementTime > 0) {
                    Integer redTime = state.playerTime.get("red");
                    if (redTime != null) {
                        state.playerTime.put("red", Math.min(redTime + state.incrementTime, state.initialTime));
                    }
                }
                
                messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "timeUpdate",
                    "currentTurn", state.board.getCurrentTurn(),
                    "redTime", state.playerTime.get("red"),
                    "blackTime", state.playerTime.get("black")
                ));
            }
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "move",
                "move", aiMove,
                "board", state.board,
                "currentTurn", state.board.getCurrentTurn()
            ));
        }
    }
    
    /**
     * 认输
     */
    @MessageMapping("/game/resign")
    public void resign(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null || !state.status.equals("playing")) return;
        
        String color = null;
        if (state.redPlayer != null && state.redPlayer.equals(userId)) {
            color = "red";
        } else if (state.blackPlayer != null && state.blackPlayer.equals(userId)) {
            color = "black";
        }
        
        if (color == null) return;
        
        state.status = "finished";
        state.resignedBy = userId;
        
        String winner = color.equals("red") ? "black" : "red";
        
        Game game = gameService.findById(gameId);
        game.setStatus("finished");
        game.setWinner(winner);
        game.setEndedAt(java.time.LocalDateTime.now());
        gameService.saveGame(game);
        updateUserStats(game);
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "resign",
            "resignedBy", color,
            "winner", winner,
            "message", color.equals("red") ? "红方认输，黑方获胜！" : "黑方认输，红方获胜！"
        ));
    }
    
    /**
     * 提和
     */
    @MessageMapping("/game/offerDraw")
    public void offerDraw(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null || !state.status.equals("playing")) return;
        
        String color = null;
        if (state.redPlayer != null && state.redPlayer.equals(userId)) {
            color = "red";
        } else if (state.blackPlayer != null && state.blackPlayer.equals(userId)) {
            color = "black";
        }
        
        if (color == null) return;
        
        // 如果对方已经提和，同意和棋
        if (state.drawOfferedBy != null && !state.drawOfferedBy.equals(color)) {
            state.status = "finished";
            
            Game game = gameService.findById(gameId);
            game.setStatus("finished");
            game.setWinner("draw");
            game.setEndedAt(java.time.LocalDateTime.now());
            gameService.saveGame(game);
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "draw",
                "reason", "双方同意和棋",
                "message", "和棋！"
            ));
        } else {
            // 提和
            state.drawOfferedBy = color;
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "drawOffered",
                "by", color
            ));
        }
    }
    
    /**
     * 拒绝和棋
     */
    @MessageMapping("/game/declineDraw")
    public void declineDraw(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        state.drawOfferedBy = null;
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "drawDeclined"
        ));
    }
    
    @MessageMapping("/game/undo")
    public void requestUndo(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        if (state.history == null || state.history.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "error", "message", "没有可悔的棋"
            ));
            return;
        }
        
        Game game = gameService.findById(gameId);
        
        // 人机模式
        if (game != null && game.getIsAi()) {
            // 撤销AI的走法
            if (!state.history.isEmpty()) {
                state.board = state.history.pop();
            }
            // 撤销玩家的走法
            if (!state.history.isEmpty()) {
                state.board = state.history.pop();
            }
            
            // 撤销走法历史
            if (!state.moveHistory.isEmpty()) {
                state.moveHistory.remove(state.moveHistory.size() - 1);
            }
            if (!state.moveHistory.isEmpty()) {
                state.moveHistory.remove(state.moveHistory.size() - 1);
            }
            
            game.setPgn(gameService.boardToPgn(state.board));
            gameService.saveGame(game);
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "undoAccepted", "board", state.board
            ));
            return;
        }
        
        // 双人模式
        if (state.undoRequested && !state.undoRequestedBy.equals(userId)) {
            if (!state.history.isEmpty()) state.board = state.history.pop();
            if (!state.history.isEmpty()) state.board = state.history.pop();
            
            if (!state.moveHistory.isEmpty()) {
                state.moveHistory.remove(state.moveHistory.size() - 1);
            }
            if (!state.moveHistory.isEmpty()) {
                state.moveHistory.remove(state.moveHistory.size() - 1);
            }
            
            state.undoRequested = false;
            state.undoRequestedBy = null;
            
            game.setPgn(gameService.boardToPgn(state.board));
            gameService.saveGame(game);
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "undoAccepted", "board", state.board
            ));
        } else {
            state.undoRequested = true;
            state.undoRequestedBy = userId;
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "undoRequested", "by", userId
            ));
        }
    }
    
    /**
     * 开始人机对战（AI开始走棋）
     */
    @MessageMapping("/game/start")
    public void startGame(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        handleStartGame(gameId);
    }
    
    /**
     * REST API 版本的游戏开始
     */
    @PostMapping("/game/start/{id}")
    @ResponseBody
    public Map<String, Object> startGameRest(@PathVariable Long id) {
        try {
            handleStartGame(id);
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
    
    private void handleStartGame(Long gameId) {
        Game game = gameService.findById(gameId);
        if (game == null || !game.getIsAi()) return;
        
        // 获取当前局面
        ChessBoard board;
        try {
            board = objectMapper.readValue(game.getPgn(), ChessBoard.class);
        } catch (Exception e) {
            board = new ChessBoard();
        }
        
        // 确保游戏状态存在
        GameState state = gameStates.get(gameId);
        if (state == null) {
            state = new GameState(board, game.getPlayerRed(), null);
            gameStates.put(gameId, state);
        }
        
        // 更新游戏状态为进行中
        if ("waiting".equals(game.getStatus())) {
            game.setStatus("playing");
            gameService.saveGame(game);
        }
        
        // 通知前端游戏已开始
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "gameStarted", "board", board
        ));
        
        // 如果是AI先手（黑方），让AI走棋
        if ("black".equals(board.getCurrentTurn())) {
            // AI走棋
            Move aiMove = chessEngine.getBestMove(board, "black");
            if (aiMove != null) {
                board = chessEngine.makeMove(board, aiMove, "black");
                
                // 保存棋谱
                game.setPgn(gameService.boardToPgn(board));
                gameService.saveGame(game);
                
                // 更新内存状态
                state.board = board;
                state.moveHistory.add(aiMove);
                
                // 广播AI的走法
                messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "move",
                    "move", aiMove,
                    "board", board,
                    "currentTurn", board.getCurrentTurn()
                ));
            }
        }
    }
    
    @MessageMapping("/game/restart")
    public void restartGame(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        Game game = gameService.findById(gameId);
        if (game == null) return;
        
        ChessBoard board = new ChessBoard();
        
        game.setPgn(gameService.boardToPgn(board));
        game.setStatus("playing");
        game.setWinner(null);
        game.setEndedAt(null);
        gameService.saveGame(game);
        
        Long blackPlayer = game.getIsAi() ? null : game.getPlayerBlack();
        GameState newState = new GameState(board, game.getPlayerRed(), blackPlayer);
        gameStates.put(gameId, newState);
        positionHistory.put(gameId, new ArrayList<>());
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "restarted", "board", board
        ));
    }
    
    /** 清除内存中的对局状态缓存（配合管理员清理未完成对弈时调用） */
    public void clearAllGameState() {
        gameStates.clear();
        positionHistory.clear();
    }
    
    /**
     * 复盘请求
     */
    @MessageMapping("/game/review")
    public void requestReview(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        Game game = gameService.findById(gameId);
        if (game == null) return;
        
        // 完整分析
        List<Move> allMoves = new ArrayList<>(state.moveHistory);
        var analysis = analysisService.analyzeGame(allMoves, game.getIsAi());
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "review",
            "analysis", Map.of(
                "totalMoves", analysis.getTotalMoves(),
                "overallResult", analysis.getOverallResult(),
                "excellentMoves", analysis.getExcellentMoves(),
                "goodMoves", analysis.getGoodMoves(),
                "mistakes", analysis.getMistakes(),
                "blunders", analysis.getBlunders(),
                "scoreHistory", analysis.getScoreHistory()
            ),
            "moveHistory", state.moveHistory
        ));
    }
    
    /**
     * 获取历史走法（用于复盘）
     */
    @MessageMapping("/game/getHistory")
    public void getMoveHistory(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "history",
            "moves", state.moveHistory,
            "currentMoveIndex", state.moveHistory.size()
        ));
    }
    
    /**
     * 跳转到指定步数
     */
    @MessageMapping("/game/gotoMove")
    public void gotoMove(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        int moveIndex = Integer.parseInt(data.get("moveIndex").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null || moveIndex < 0 || moveIndex > state.moveHistory.size()) return;
        
        // 重新生成棋盘到指定步数
        ChessBoard board = new ChessBoard();
        for (int i = 0; i < moveIndex; i++) {
            Move m = state.moveHistory.get(i);
            String color = (i % 2 == 0) ? "red" : "black";
            board = chessEngine.makeMove(board, m, color);
        }
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "position",
            "board", board,
            "moveIndex", moveIndex,
            "currentTurn", board.getCurrentTurn()
        ));
    }
    
    @MessageMapping("/game/hint")
    public void requestHint(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Long userId = Long.parseLong(data.get("userId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        String color = null;
        if (state.redPlayer != null && state.redPlayer.equals(userId)) color = "red";
        else if (state.blackPlayer != null && state.blackPlayer.equals(userId)) color = "black";
        
        if (color == null) return;
        
        Move hintMove = chessEngine.getHint(state.board, color);
        
        if (hintMove != null) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "hint",
                "fromX", hintMove.getFromX(),
                "fromY", hintMove.getFromY(),
                "toX", hintMove.getToX(),
                "toY", hintMove.getToY()
            ));
        }
    }
    
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
                "type", "error", "message", "无效的难度等级"
            ));
        }
    }
    
    @MessageMapping("/game/analyze")
    public void analyzePosition(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        Map<String, Object> analysis = analysisService.getRealtimeAnalysis(state.board);
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "analysis",
            "analysis", analysis
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
        boolean isCheck = chessEngine.isKingInCheck(board, color);
        var moves = chessEngine.getAllValidMoves(board, color);
        return isCheck && moves.isEmpty();
    }
    
    private boolean containsPiece(ChessBoard board, String piece) {
        String[][] b = board.getBoard();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (piece.equals(b[i][j])) return true;
            }
        }
        return false;
    }
    
    private void updateUserStats(Game game) {
        if (game.getStatus() == null || !game.getStatus().equals("finished")) return;
        
        if (game.getIsAi()) {
            Long playerRed = game.getPlayerRed();
            if (playerRed != null) {
                if ("red".equals(game.getWinner())) userService.addWin(playerRed);
                else if ("black".equals(game.getWinner())) userService.addLose(playerRed);
                else if ("draw".equals(game.getWinner())) userService.addDraw(playerRed);
            }
        } else {
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
    
    /**
     * 设置计时规则
     */
    @MessageMapping("/game/setTimeControl")
    public void setTimeControl(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        Integer initialTime = Integer.parseInt(data.get("initialTime").toString());
        Integer incrementTime = data.containsKey("incrementTime") ? 
            Integer.parseInt(data.get("incrementTime").toString()) : 0;
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        state.initialTime = initialTime;
        state.incrementTime = incrementTime;
        state.timeControlEnabled = true;
        state.playerTime.put("red", initialTime);
        state.playerTime.put("black", initialTime);
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "timeControlSet",
            "initialTime", initialTime,
            "incrementTime", incrementTime,
            "redTime", initialTime,
            "blackTime", initialTime
        ));
    }
    
    /**
     * 减少当前玩家时间（每分钟调用）
     */
    @MessageMapping("/game/tick")
    public void tick(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null || !state.status.equals("playing") || !state.timeControlEnabled) return;
        
        String currentColor = state.board.getCurrentTurn();
        Integer currentTime = state.playerTime.get(currentColor);
        
        if (currentTime != null && currentTime > 0) {
            state.playerTime.put(currentColor, currentTime - 1);
            
            // 检查是否超时
            if (state.playerTime.get(currentColor) <= 0) {
                state.status = "finished";
                String winner = currentColor.equals("red") ? "black" : "red";
                
                Game game = gameService.findById(gameId);
                if (game != null) {
                    game.setStatus("finished");
                    game.setWinner(winner);
                    game.setEndedAt(java.time.LocalDateTime.now());
                    gameService.saveGame(game);
                }
                
                messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                    "type", "timeOut",
                    "loser", currentColor,
                    "winner", winner,
                    "message", currentColor.equals("red") ? "红方超时，黑方获胜！" : "黑方超时，红方获胜！"
                ));
                return;
            }
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                "type", "timeUpdate",
                "currentTurn", currentColor,
                "redTime", state.playerTime.get("red"),
                "blackTime", state.playerTime.get("black")
            ));
        }
    }
    
    /**
     * 获取当前时间
     */
    @MessageMapping("/game/getTime")
    public void getTime(Map<String, Object> data) {
        Long gameId = Long.parseLong(data.get("gameId").toString());
        
        GameState state = gameStates.get(gameId);
        if (state == null) return;
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
            "type", "timeState",
            "redTime", state.playerTime.get("red"),
            "blackTime", state.playerTime.get("black"),
            "currentTurn", state.board.getCurrentTurn(),
            "timeControlEnabled", state.timeControlEnabled
        ));
    }
}
