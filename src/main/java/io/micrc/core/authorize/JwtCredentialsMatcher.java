package io.micrc.core.authorize;

import com.auth0.jwt.exceptions.InvalidClaimException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import io.micrc.lib.JwtUtil;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;

import java.io.UnsupportedEncodingException;

public class JwtCredentialsMatcher extends HashedCredentialsMatcher {

    @Override
    public boolean doCredentialsMatch(AuthenticationToken authenticationToken, AuthenticationInfo authenticationInfo) {
        JwtToken jwtToken = (JwtToken) authenticationToken;
        String token = jwtToken.getCredentials().toString();
        try {
            return JwtUtil.verify(token, authenticationInfo.getPrincipals().toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("token decode error.");
        } catch (TokenExpiredException e) {
            throw new RuntimeException("token is expired.");
        } catch (InvalidClaimException e) {
            throw new RuntimeException("token doesn't match current subject.");
        }
    }
}
