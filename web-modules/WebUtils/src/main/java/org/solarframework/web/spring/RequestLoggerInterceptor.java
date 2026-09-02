package org.solarframework.web.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class RequestLoggerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        System.out.println("[" + req.getMethod() + "] " + req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "") + " from " + req.getRemoteAddr());
        return true;
    }
}
