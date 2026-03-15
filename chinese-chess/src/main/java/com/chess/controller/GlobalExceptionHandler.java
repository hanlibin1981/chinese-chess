package com.chess.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 全局异常处理，避免数据库不可用或其它异常导致直接 500 堆栈暴露，并保持服务不因单次请求异常而挂掉。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleDataAccess(DataAccessException e, Model model) {
        model.addAttribute("message", "数据库暂时不可用，请确认 MySQL 已启动且配置正确（地址、端口、用户名、密码）。");
        model.addAttribute("detail", e.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleOther(Exception e, Model model) {
        model.addAttribute("message", "服务器内部错误，请稍后重试。");
        model.addAttribute("detail", e.getMessage());
        return "error";
    }
}
