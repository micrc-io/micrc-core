package io.micrc.lib;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {
    public static final String KEY = "1qaz@WSX";

    /**
     * 创建token
     *
     * @param username      username
     * @param permissions   permissions
     * @param time          time
     * @return              token
     */
    public static String createToken(String username, String[] permissions, Long time) {
        //设置token头部
        Map<String, Object> map = new HashMap<>();
        map.put("alg","HS256");
        map.put("typ","JWT");
        //创建token
        JWTCreator.Builder builder = JWT.create()
                .withClaim("username", username)
                .withArrayClaim("permissions", permissions)
                .withHeader(map);
        if (time != null) {
            //设置过期时间
            long expiration = System.currentTimeMillis() + time;
            Date expireDate = new Date(expiration);
            builder.withExpiresAt(expireDate);
        }
        return builder.sign(Algorithm.HMAC256(KEY));
    }

    /**
     * 校验token是否正确
     *
     * @param token     token
     * @param username  username
     * @return          verify result
     */
    public static boolean verify(String token, String username) {
        //通过KEY利用HMAC256解码
        Algorithm algorithm = Algorithm.HMAC256(KEY);
        /*
        JWTVerifier是一个用于验证JSON Web Token（JWT）的工具。JWT是一种用于在网络上安全传输信息的标准，它由三个部分组成：头部、载荷和签名。
        JWTVerifier可以验证JWT的签名是否正确，以确保JWT的完整性和真实性。
        JWTVerifier可以通过使用公钥来验证签名，也可以使用对称密钥来验证签名。
        对称密钥是一种加密和解密使用相同密钥的加密方式，而公钥加密则使用公钥和私钥进行加密和解密。
        * */
        //通过设置好的算法解密
        JWTVerifier verifier = JWT.require(algorithm)
                //添加自定义申明
                .withClaim("username", username)
                .build();
        DecodedJWT verify = verifier.verify(token);
        return true;
    }

    /**
     * 解析token，获取用户名
     *
     * @param token token
     * @return      username
     */
    public static String getUsername(String token) {
        DecodedJWT decode = JWT.decode(token);
        return decode.getClaim("username").asString();
    }
}
