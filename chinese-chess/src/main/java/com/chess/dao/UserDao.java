package com.chess.dao;

import com.chess.model.User;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface UserDao {
    
    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(String username);
    
    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(Long id);
    
    @Insert("INSERT INTO users(username, password, created_at) VALUES(#{username}, #{password}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
    
    @Update("UPDATE users SET win_count = win_count + 1 WHERE id = #{id}")
    void addWin(Long id);
    
    @Update("UPDATE users SET lose_count = lose_count + 1 WHERE id = #{id}")
    void addLose(Long id);
    
    @Update("UPDATE users SET draw_count = draw_count + 1 WHERE id = #{id}")
    void addDraw(Long id);
}
