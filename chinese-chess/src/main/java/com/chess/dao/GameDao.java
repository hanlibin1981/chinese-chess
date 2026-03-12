package com.chess.dao;

import com.chess.model.Game;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface GameDao {
    
    @Select("SELECT g.*, u1.username as red_username, u2.username as black_username " +
            "FROM games g " +
            "LEFT JOIN users u1 ON g.player_red = u1.id " +
            "LEFT JOIN users u2 ON g.player_black = u2.id " +
            "WHERE g.id = #{id}")
    Game findById(Long id);
    
    @Select("SELECT g.*, u1.username as red_username, u2.username as black_username " +
            "FROM games g " +
            "LEFT JOIN users u1 ON g.player_red = u1.id " +
            "LEFT JOIN users u2 ON g.player_black = u2.id " +
            "WHERE g.status = 'playing' ORDER BY g.created_at DESC")
    List<Game> findActiveGames();
    
    @Select("SELECT g.*, u1.username as red_username, u2.username as black_username " +
            "FROM games g " +
            "LEFT JOIN users u1 ON g.player_red = u1.id " +
            "LEFT JOIN users u2 ON g.player_black = u2.id " +
            "WHERE g.player_red = #{userId} OR g.player_black = #{userId} " +
            "ORDER BY g.created_at DESC")
    List<Game> findByUserId(Long userId);
    
    @Select("SELECT g.*, u1.username as red_username, u2.username as black_username " +
            "FROM games g " +
            "LEFT JOIN users u1 ON g.player_red = u1.id " +
            "LEFT JOIN users u2 ON g.player_black = u2.id " +
            "ORDER BY g.created_at DESC LIMIT #{limit}")
    List<Game> findRecentGames(int limit);
    
    @Insert("INSERT INTO games(player_red, player_black, pgn, is_ai, status, created_at) " +
            "VALUES(#{playerRed}, #{playerBlack}, #{pgn}, #{isAi}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Game game);
    
    @Update("UPDATE games SET pgn = #{pgn}, winner = #{winner}, status = #{status}, ended_at = #{endedAt} WHERE id = #{id}")
    int update(Game game);
    
    @Update("UPDATE games SET player_black = #{playerBlack}, status = 'playing' WHERE id = #{id}")
    int joinGame(@Param("id") Long id, @Param("playerBlack") Long playerBlack);
}
