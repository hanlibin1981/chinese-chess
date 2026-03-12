package com.chess.service;

import com.chess.dao.GameDao;
import com.chess.model.ChessBoard;
import com.chess.model.Game;
import com.chess.model.Move;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GameService {
    
    @Autowired
    private GameDao gameDao;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public Game createGame(Long playerRed, boolean isAi) {
        Game game = new Game();
        game.setPlayerRed(playerRed);
        game.setPlayerBlack(null); // null 表示 AI
        game.setIsAi(isAi);
        game.setStatus("waiting");
        
        ChessBoard board = new ChessBoard();
        try {
            game.setPgn(objectMapper.writeValueAsString(board));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        
        gameDao.insert(game);
        return game;
    }
    
    public Game joinGame(Long gameId, Long playerBlack) {
        Game game = gameDao.findById(gameId);
        if (game == null) {
            throw new RuntimeException("游戏不存在");
        }
        if (game.getPlayerBlack() != null) {
            throw new RuntimeException("游戏已满");
        }
        game.setPlayerBlack(playerBlack);
        game.setStatus("playing");
        gameDao.joinGame(gameId, playerBlack);
        return gameDao.findById(gameId);
    }
    
    public Game findById(Long id) {
        return gameDao.findById(id);
    }
    
    public List<Game> getActiveGames() {
        return gameDao.findActiveGames();
    }
    
    public List<Game> getUserGames(Long userId) {
        return gameDao.findByUserId(userId);
    }
    
    public List<Game> getRecentGames(int limit) {
        return gameDao.findRecentGames(limit);
    }
    
    public void saveGame(Game game) {
        gameDao.update(game);
    }
    
    public ChessBoard loadBoardFromPgn(String pgn) {
        try {
            return objectMapper.readValue(pgn, ChessBoard.class);
        } catch (JsonProcessingException e) {
            return new ChessBoard();
        }
    }
    
    public String boardToPgn(ChessBoard board) {
        try {
            return objectMapper.writeValueAsString(board);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    public void restartGame(Long gameId) {
        Game game = gameDao.findById(gameId);
        if (game == null) {
            throw new RuntimeException("游戏不存在");
        }
        
        // 重置棋盘
        ChessBoard board = new ChessBoard();
        try {
            game.setPgn(objectMapper.writeValueAsString(board));
            game.setStatus("playing");
            game.setWinner(null);
            game.setEndedAt(null);
            gameDao.update(game);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
