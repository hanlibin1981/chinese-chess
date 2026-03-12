package com.chess.service;

import com.chess.dao.UserDao;
import com.chess.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class UserService {
    
    @Autowired
    private UserDao userDao;
    
    public User register(String username, String password) {
        if (userDao.findByUsername(username) != null) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(hashPassword(password));
        userDao.insert(user);
        return user;
    }
    
    public User login(String username, String password) {
        User user = userDao.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!user.getPassword().equals(hashPassword(password))) {
            throw new RuntimeException("密码错误");
        }
        return user;
    }
    
    public User findById(Long id) {
        return userDao.findById(id);
    }
    
    public void addWin(Long userId) {
        userDao.addWin(userId);
    }
    
    public void addLose(Long userId) {
        userDao.addLose(userId);
    }
    
    public void addDraw(Long userId) {
        userDao.addDraw(userId);
    }
    
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
