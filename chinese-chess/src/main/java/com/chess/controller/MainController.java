package com.chess.controller;

import com.chess.model.ChessBoard;
import com.chess.model.Game;
import com.chess.model.Move;
import com.chess.model.User;
import com.chess.service.ChessAnalysisService;
import com.chess.service.GameService;
import com.chess.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MainController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private ChessAnalysisService analysisService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        // 重新从数据库加载用户信息以获取最新统计数据
        if (user != null) {
            user = userService.findById(user.getId());
            session.setAttribute("user", user);
        }
        model.addAttribute("user", user);
        
        List<Game> activeGames = gameService.getActiveGames();
        model.addAttribute("activeGames", activeGames);
        
        return "index";
    }
    
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
    
    @PostMapping("/login")
    public String login(@RequestParam String username, 
                       @RequestParam String password,
                       HttpSession session,
                       Model model) {
        try {
            User user = userService.login(username, password);
            session.setAttribute("user", user);
            return "redirect:/";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }
    
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }
    
    @PostMapping("/register")
    public String register(@RequestParam String username,
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "两次密码不一致");
            return "register";
        }
        
        try {
            User user = userService.register(username, password);
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
    
    @PostMapping("/game/create")
    @ResponseBody
    public Map<String, Object> createGame(@RequestParam boolean isAi, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("success", false, "message", "请先登录");
        }
        
        Game game = gameService.createGame(user.getId(), isAi);
        return Map.of("success", true, "gameId", game.getId());
    }
    
    @GetMapping("/game/create")
    public String createGamePage() {
        return "redirect:/";
    }
    
    @PostMapping("/game/join/{id}")
    @ResponseBody
    public Map<String, Object> joinGame(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("success", false, "message", "请先登录");
        }
        
        try {
            Game game = gameService.joinGame(id, user.getId());
            return Map.of("success", true, "gameId", game.getId());
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
    
    @GetMapping("/game/{id}")
    public String gameRoom(@PathVariable Long id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        Game game = gameService.findById(id);
        
        if (game == null) {
            return "redirect:/";
        }
        
        model.addAttribute("game", game);
        model.addAttribute("user", user);
        
        return "game";
    }
    
    @GetMapping("/games")
    public String gamesList(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        model.addAttribute("user", user);
        
        if (user != null) {
            List<Game> userGames = gameService.getUserGames(user.getId());
            model.addAttribute("games", userGames);
        } else {
            List<Game> recentGames = gameService.getRecentGames(50);
            model.addAttribute("games", recentGames);
        }
        
        return "games";
    }
    
    @GetMapping("/review/{id}")
    public String review(@PathVariable Long id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        Game game = gameService.findById(id);
        
        if (game == null) {
            return "redirect:/games";
        }
        
        model.addAttribute("game", game);
        model.addAttribute("user", user);
        
        return "review";
    }
    
    @GetMapping("/api/game/{id}/pgn")
    @ResponseBody
    public Map<String, Object> getGamePgn(@PathVariable Long id) {
        Game game = gameService.findById(id);
        if (game == null) {
            return Map.of("moves", List.of());
        }
        
        try {
            ChessBoard board = objectMapper.readValue(game.getPgn(), ChessBoard.class);
            return Map.of("moves", board.getMoves());
        } catch (Exception e) {
            return Map.of("moves", List.of());
        }
    }
    
    @PostMapping("/game/restart/{id}")
    @ResponseBody
    public Map<String, Object> restartGame(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("success", false, "message", "请先登录");
        }
        
        try {
            Game game = gameService.findById(id);
            if (game == null) {
                return Map.of("success", false, "message", "游戏不存在");
            }
            
            // 只有游戏参与者才能重新开始
            if (!game.getPlayerRed().equals(user.getId()) && 
                (game.getPlayerBlack() == null || !game.getPlayerBlack().equals(user.getId()))) {
                return Map.of("success", false, "message", "无权限");
            }
            
            // 重新开始游戏
            gameService.restartGame(id);
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
    
    /**
     * 完整棋局分析
     */
    @GetMapping("/api/game/{id}/analysis")
    @ResponseBody
    public Map<String, Object> getGameAnalysis(@PathVariable Long id) {
        try {
            Game game = gameService.findById(id);
            if (game == null) {
                return Map.of("success", false, "message", "游戏不存在");
            }
            
            ChessBoard board = objectMapper.readValue(game.getPgn(), ChessBoard.class);
            List<Move> moves = board.getMoves();
            
            if (moves == null || moves.isEmpty()) {
                return Map.of("success", false, "message", "无走法记录");
            }
            
            ChessAnalysisService.GameAnalysis analysis = analysisService.analyzeGame(moves, game.getIsAi());
            
            // 转换为前端可用的格式
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("totalMoves", analysis.getTotalMoves());
            result.put("overallResult", analysis.getOverallResult());
            result.put("excellentMoves", analysis.getExcellentMoves());
            result.put("goodMoves", analysis.getGoodMoves());
            result.put("mistakes", analysis.getMistakes());
            result.put("blunders", analysis.getBlunders());
            result.put("scoreHistory", analysis.getScoreHistory());
            result.put("isAiGame", game.getIsAi());
            
            // 走法分析
            List<Map<String, Object>> moveAnalyses = new ArrayList<>();
            for (ChessAnalysisService.MoveAnalysis ma : analysis.getMoveAnalyses()) {
                Map<String, Object> maMap = new HashMap<>();
                maMap.put("step", moveAnalyses.size() + 1);
                maMap.put("fromX", ma.getMove().getFromX());
                maMap.put("fromY", ma.getMove().getFromY());
                maMap.put("toX", ma.getMove().getToX());
                maMap.put("toY", ma.getMove().getToY());
                maMap.put("piece", ma.getMove().getPiece());
                maMap.put("quality", ma.getQuality().getDescription());
                maMap.put("qualityLevel", ma.getQuality().getLevel());
                maMap.put("scoreChange", ma.getScoreChange());
                maMap.put("comment", ma.getComment());
                maMap.put("isCheck", ma.isCheck());
                maMap.put("isCaptured", ma.isCaptured());
                maMap.put("isKeyMoment", ma.isKeyMoment());
                maMap.put("keyMomentReason", ma.getKeyMomentReason());
                moveAnalyses.add(maMap);
            }
            result.put("moveAnalyses", moveAnalyses);
            
            // AI 分析
            if (game.getIsAi() && analysis.getAiAnalysis() != null) {
                Map<String, Object> aiMap = new HashMap<>();
                aiMap.put("totalMistakes", analysis.getAiAnalysis().getTotalMistakes());
                aiMap.put("totalBlunders", analysis.getAiAnalysis().getTotalBlunders());
                aiMap.put("goodMoves", analysis.getAiAnalysis().getGoodMoves());
                aiMap.put("overallAssessment", analysis.getAiAnalysis().getOverallAssessment());
                aiMap.put("observations", analysis.getAiAnalysis().getObservations());
                result.put("aiAnalysis", aiMap);
            }
            
            return result;
        } catch (Exception e) {
            return Map.of("success", false, "message", "分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前局面建议走法
     */
    @GetMapping("/api/game/{id}/suggestions")
    @ResponseBody
    public Map<String, Object> getSuggestions(@PathVariable Long id, @RequestParam(defaultValue = "3") int step) {
        try {
            Game game = gameService.findById(id);
            if (game == null) {
                return Map.of("success", false, "message", "游戏不存在");
            }
            
            ChessBoard board = objectMapper.readValue(game.getPgn(), ChessBoard.class);
            List<Move> moves = board.getMoves();
            
            // 恢复到指定步骤的局面
            ChessBoard currentBoard = new ChessBoard();
            for (int i = 0; i < step && i < moves.size(); i++) {
                String color = (i % 2 == 0) ? "red" : "black";
                currentBoard = analysisService.getChessEngine().makeMove(currentBoard, moves.get(i), color);
            }
            
            String color = (step % 2 == 0) ? "red" : "black";
            List<ChessAnalysisService.MoveSuggestion> suggestions = 
                analysisService.getSuggestedMoves(currentBoard, color, 5);
            
            List<Map<String, Object>> suggestionList = new ArrayList<>();
            for (ChessAnalysisService.MoveSuggestion ms : suggestions) {
                Map<String, Object> sm = new HashMap<>();
                sm.put("fromX", ms.getMove().getFromX());
                sm.put("fromY", ms.getMove().getFromY());
                sm.put("toX", ms.getMove().getToX());
                sm.put("toY", ms.getMove().getToY());
                sm.put("score", ms.getScore());
                sm.put("reason", ms.getReason());
                suggestionList.add(sm);
            }
            
            return Map.of("success", true, "suggestions", suggestionList, "currentTurn", color);
        } catch (Exception e) {
            return Map.of("success", false, "message", "获取建议失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出棋谱
     */
    @GetMapping("/api/game/{id}/export")
    @ResponseBody
    public Map<String, Object> exportGame(@PathVariable Long id, @RequestParam(defaultValue = "pgn") String format) {
        try {
            Game game = gameService.findById(id);
            if (game == null) {
                return Map.of("success", false, "message", "游戏不存在");
            }
            
            ChessBoard board = objectMapper.readValue(game.getPgn(), ChessBoard.class);
            List<Move> moves = board.getMoves();
            
            StringBuilder pgn = new StringBuilder();
            pgn.append("[Red \"").append(game.getRedUsername() != null ? game.getRedUsername() : "红方").append("\"]\n");
            pgn.append("[Black \"").append(game.getIsAi() ? "AI" : (game.getBlackUsername() != null ? game.getBlackUsername() : "黑方")).append("\"]\n");
            pgn.append("[Result \"").append(game.getWinner() != null ? game.getWinner() : "*").append("\"]\n");
            pgn.append("\n");
            
            // 转换走法为中文棋谱
            String[] colNames = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
            String[] rowNames = {"九", "八", "七", "六", "五", "四", "三", "二", "一"};
            
            for (int i = 0; i < moves.size(); i++) {
                Move m = moves.get(i);
                if (i % 2 == 0) {
                    pgn.append(i / 2 + 1).append(". ");
                }
                
                String piece = m.getPiece();
                // 坐标范围：X=0-9（共10行），Y=0-8（共9列）
                String from = colNames[Math.min(m.getFromY(), 8)] + rowNames[Math.min(m.getFromX(), 8)];
                String to = colNames[Math.min(m.getToY(), 8)] + rowNames[Math.min(m.getToX(), 8)];
                
                pgn.append(piece).append(from).append(to).append(" ");
                
                if ((i + 1) % 6 == 0) {
                    pgn.append("\n");
                }
            }
            
            if (game.getWinner() != null) {
                pgn.append("\n").append(game.getWinner());
            }
            
            return Map.of("success", true, "pgn", pgn.toString());
        } catch (Exception e) {
            return Map.of("success", false, "message", "导出失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户统计信息
     */
    @GetMapping("/api/user/stats")
    @ResponseBody
    public Map<String, Object> getUserStats(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("success", false, "message", "请先登录");
        }
        
        user = userService.findById(user.getId());
        if (user == null) {
            return Map.of("success", false, "message", "用户不存在");
        }
        
        int total = (user.getWinCount() == null ? 0 : user.getWinCount()) + 
                    (user.getLoseCount() == null ? 0 : user.getLoseCount()) + 
                    (user.getDrawCount() == null ? 0 : user.getDrawCount());
        double winRate = total > 0 ? (user.getWinCount() * 100.0 / total) : 0;
        
        return Map.of(
            "success", true,
            "username", user.getUsername(),
            "winCount", user.getWinCount() != null ? user.getWinCount() : 0,
            "loseCount", user.getLoseCount() != null ? user.getLoseCount() : 0,
            "drawCount", user.getDrawCount() != null ? user.getDrawCount() : 0,
            "totalGames", total,
            "winRate", String.format("%.1f", winRate)
        );
    }
}
