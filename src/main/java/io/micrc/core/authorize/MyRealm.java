package io.micrc.core.authorize;

import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.JwtUtil;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MyRealm extends AuthorizingRealm {

    /**
     * TOKEN过期时间
     */
    public static final Long TOKEN_EXPIRE_TIME = 4 * 60 * 60 * 1000L;

    /**
     * 用户权限前缀
     */
    public static final String USER_PERMISSIONS_KEY_PREFIX = "USER:PERMISSIONS:";

    @Resource(name = "memoryDbTemplate")
    RedisTemplate<Object, Object> redisTemplate;

    /**
     * 不写该方法，会报错不支持自定义的token
     * */
    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        String username = principalCollection.getPrimaryPrincipal().toString();
        // 从缓存信息中获取登录时的权限列表
        String key = USER_PERMISSIONS_KEY_PREFIX + username;
        List<String> permissions = ClassCastUtils.castArrayList(redisTemplate.opsForValue().get(key), String.class);
        info.addStringPermissions(Objects.requireNonNullElse(permissions, new ArrayList<>()));
        return info;
    }


    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        if (authenticationToken.getPrincipal()==null) {
            throw new AuthenticationException("token不合法");
        }
        // 通过token获取用户名
        String token = authenticationToken.getPrincipal().toString();
        String username = JwtUtil.getUsername(token);
        // 用户状态校验
        // 将用户名和密码发送要密码匹配器中
        return new SimpleAuthenticationInfo(username,"password", ByteSource.Util.bytes(username),getName());
    }
}
