package io.micrc.core.authorize;

import io.micrc.core.rpc.Result;
import io.micrc.lib.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        String token = wrapper.getHeader("Authorization");
        if (StringUtils.isNotBlank(token)) {
            JwtToken jwtToken = new JwtToken(token);
            try {
                subject.login(jwtToken);
                return true;
            } catch (Exception e) {
                writeUnauthorizedResponse((HttpServletResponse) servletResponse);
                return false;
            }
        } else {
            writeUnauthorizedResponse((HttpServletResponse) servletResponse);
            return false;
        }
    }

    private static void writeUnauthorizedResponse(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.setStatus(200);
        httpServletResponse.setHeader("Content-Type", "application/json");
        Result<?> result = new Result<>();
        result.setCode("401");
        result.setMessage("Unauthorized");
        httpServletResponse.getWriter().write(JsonUtil.writeValueAsString(result));
    }
}
