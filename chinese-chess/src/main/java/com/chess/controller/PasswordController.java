package com.chess.controller;

import com.chess.model.User;
import com.chess.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class PasswordController {
    
    @Autowired
    private UserService userService;
    
    // 存储重置令牌（生产环境应该用数据库）
    private final Map<String, ResetToken> resetTokens = new ConcurrentHashMap<>();
    
    // 简单的令牌存储类
    static class ResetToken {
        String username;
        String token;
        long expiresAt;
        
        ResetToken(String username, String token, long expiresAt) {
            this.username = username;
            this.token = token;
            this.expiresAt = expiresAt;
        }
        
        boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }
    
    /**
     * 修改密码页面
     */
    @GetMapping("/password/change")
    public String changePasswordPage() {
        return "change-password";
    }
    
    /**
     * 修改密码
     */
    @PostMapping("/password/change")
    @ResponseBody
    public Map<String, Object> changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            result.put("success", false);
            result.put("message", "两次输入的密码不一致");
            return result;
        }
        
        if (newPassword.length() < 6) {
            result.put("success", false);
            result.put("message", "密码长度不能少于6位");
            return result;
        }
        
        try {
            userService.changePassword(user.getUsername(), oldPassword, newPassword);
            result.put("success", true);
            result.put("message", "密码修改成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 忘记密码页面
     */
    @GetMapping("/password/forgot")
    public String forgotPasswordPage() {
        return "forgot-password";
    }
    
    /**
     * 发送重置密码邮件/验证码
     */
    @PostMapping("/password/forgot/send")
    @ResponseBody
    public Map<String, Object> sendResetCode(@RequestParam String username) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userService.findByUsername(username);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        
        // 生成6位数字验证码
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        
        // 生成令牌
        String token = UUID.randomUUID().toString();
        
        // 存储令牌（有效期30分钟）
        resetTokens.put(username, new ResetToken(username, code, System.currentTimeMillis() + 30 * 60 * 1000));
        
        // TODO: 实际应该发送邮件，这里暂时返回验证码（仅用于测试）
        System.out.println("验证码: " + code);
        
        result.put("success", true);
        result.put("message", "验证码已发送");
        result.put("debugCode", code); // 测试用，生产环境应删除
        
        return result;
    }
    
    /**
     * 重置密码页面
     */
    @GetMapping("/password/reset")
    public String resetPasswordPage(@RequestParam String username, @RequestParam String token, Model model) {
        model.addAttribute("username", username);
        model.addAttribute("token", token);
        return "reset-password";
    }
    
    /**
     * 重置密码
     */
    @PostMapping("/password/reset")
    @ResponseBody
    public Map<String, Object> resetPassword(
            @RequestParam String username,
            @RequestParam String token,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword) {
        
        Map<String, Object> result = new HashMap<>();
        
        if (!newPassword.equals(confirmPassword)) {
            result.put("success", false);
            result.put("message", "两次输入的密码不一致");
            return result;
        }
        
        if (newPassword.length() < 6) {
            result.put("success", false);
            result.put("message", "密码长度不能少于6位");
            return result;
        }
        
        // 验证令牌
        ResetToken storedToken = resetTokens.get(username);
        if (storedToken == null) {
            result.put("success", false);
            result.put("message", "请先获取验证码");
            return result;
        }
        
        if (!storedToken.isValid()) {
            result.put("success", false);
            result.put("message", "验证码已过期，请重新获取");
            resetTokens.remove(username);
            return result;
        }
        
        if (!storedToken.token.equals(token)) {
            result.put("success", false);
            result.put("message", "验证码错误");
            return result;
        }
        
        try {
            userService.resetPassword(username, newPassword);
            resetTokens.remove(username); // 清除令牌
            result.put("success", true);
            result.put("message", "密码重置成功，请使用新密码登录");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }
}
