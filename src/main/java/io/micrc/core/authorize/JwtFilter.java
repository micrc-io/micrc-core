package io.micrc.core.authorize;

import com.auth0.jwt.exceptions.TokenExpiredException;
import io.micrc.core.rpc.Result;
import io.micrc.lib.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

@Slf4j
public class JwtFilter extends AccessControlFilter {

    @Override
    protected boolean preHandle(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        httpServletResponse.setHeader("Access-control-Allow-Origin", httpServletRequest.getHeader("Origin"));
        httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS,PUT,DELETE");
        httpServletResponse.setHeader("Access-Control-Allow-Headers", httpServletRequest.getHeader("Access-Control-Request-Headers"));
        // 跨域时会首先发送一个option请求，这里我们给option请求直接返回正常状态
        if (httpServletRequest.getMethod().equals(RequestMethod.OPTIONS.name())) {
            httpServletResponse.setStatus(HttpStatus.OK.value());
            return false;
        }
        return super.preHandle(request, response);
    }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) throws Exception {
        return false;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest servletRequest, ServletResponse servletResponse) throws Exception {
        Subject subject = SecurityUtils.getSubject();
        HttpServletRequestWrapper wrapper = (HttpServletRequestWrapper) servletRequest;
        Enumeration<String> headerNames = wrapper.getHeaderNames();
        String token = null;
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key)) {
                token = wrapper.getHeader(key);
                break;
            }
        }
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;
        if (StringUtils.isNotBlank(token)) {
            try {
                if (token.contains(" ")) {
                    token = token.split(" ")[1];
                }
                JwtToken jwtToken = new JwtToken(token);
                subject.login(jwtToken);
                return true;
            } catch (Exception e) {
                if (e.getCause() instanceof TokenExpiredException) {
                    httpStatus = HttpStatus.FORBIDDEN;
                } else {
                    log.error("subject login error: {}", e.getLocalizedMessage());
                }
            }
        }
        writeResponse((HttpServletResponse) servletResponse, httpStatus);
        return false;
    }

    private static void writeResponse(HttpServletResponse httpServletResponse, HttpStatus httpStatus) throws IOException {
        httpServletResponse.setStatus(HttpStatus.OK.value());
        httpServletResponse.setHeader("Content-Type", "application/json");
        Result<?> result = new Result<>();
        result.setCode(String.valueOf(httpStatus.value()));
        result.setMessage(httpStatus.getReasonPhrase());
        httpServletResponse.getWriter().write(JsonUtil.writeValueAsString(result));
    }
}
