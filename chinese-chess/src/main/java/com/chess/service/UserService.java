package com.chess.service;

import com.chess.dao.UserDao;
import com.chess.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;

@Service
public class UserService {
    
    @Autowired
    private UserDao userDao;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
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
        
        String storedPassword = user.getPassword();
        
        // 检查是否是旧的MD5密码
        if (isMd5Hash(storedPassword)) {
            // 验证MD5密码
            if (storedPassword.equals(hashPasswordMD5(password))) {
                // 密码正确，升级为BCrypt
                String newHash = hashPassword(password);
                userDao.updatePassword(user.getId(), newHash);
                user.setPassword(newHash);
                return user;
            } else {
                throw new RuntimeException("密码错误");
            }
        } else {
            // 使用BCrypt验证
            if (!checkPassword(password, storedPassword)) {
                throw new RuntimeException("密码错误");
            }
            return user;
        }
    }
    
    public User findById(Long id) {
        return userDao.findById(id);
    }
    
    public User findByUsername(String username) {
        return userDao.findByUsername(username);
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
    
    /**
     * 使用BCrypt加密密码
     */
    private String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }
    
    /**
     * 验证密码
     */
    private boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    /**
     * 判断是否是MD5哈希（32位十六进制字符串）
     */
    private boolean isMd5Hash(String hash) {
        return hash != null && hash.length() == 32 && hash.matches("^[a-f0-9]+$");
    }
    
    /**
     * MD5加密（用于兼容旧密码）
     */
    private String hashPasswordMD5(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 修改密码
     */
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userDao.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        String storedPassword = user.getPassword();
        
        // 验证旧密码
        if (isMd5Hash(storedPassword)) {
            if (!storedPassword.equals(hashPasswordMD5(oldPassword))) {
                throw new RuntimeException("原密码错误");
            }
        } else {
            if (!checkPassword(oldPassword, storedPassword)) {
                throw new RuntimeException("原密码错误");
            }
        }
        
        // 更新密码
        userDao.updatePassword(user.getId(), hashPassword(newPassword));
    }
    
    /**
     * 重置密码（通过验证码）
     */
    public void resetPassword(String username, String newPassword) {
        User user = userDao.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        userDao.updatePassword(user.getId(), hashPassword(newPassword));
    }
}
