package io.micrc.core.authorize;

import io.micrc.lib.JwtUtil;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;

public class JwtCredentialsMatcher extends HashedCredentialsMatcher {

    @Override
    public boolean doCredentialsMatch(AuthenticationToken authenticationToken, AuthenticationInfo authenticationInfo) {
        JwtToken jwtToken = (JwtToken) authenticationToken;
        String token = jwtToken.getCredentials().toString();
        return JwtUtil.verify(token, authenticationInfo.getPrincipals().toString());
    }
}
