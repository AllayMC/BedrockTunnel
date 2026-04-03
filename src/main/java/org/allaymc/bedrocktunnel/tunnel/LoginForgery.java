package org.allaymc.bedrocktunnel.tunnel;

import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import java.util.HashMap;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

final class LoginForgery {
    private LoginForgery() {
    }

    public static LoginPacket forgeLogin(TunnelRuntime runtime) {
        ChainValidationResult.IdentityData identityData = runtime.identityData();
        if (identityData == null || runtime.skinJson() == null) {
            throw new IllegalStateException("Runtime does not contain login data");
        }

        KeyPair keyPair = runtime.proxyKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setNotBefore(NumericDate.fromMilliseconds(now.minusSeconds(1).toEpochMilli()));
        claims.setExpirationTime(NumericDate.fromMilliseconds(now.plusSeconds(86400).toEpochMilli()));
        claims.setIssuedAt(NumericDate.fromMilliseconds(now.toEpochMilli()));
        claims.setClaim("cpk", publicKey);
        claims.setClaim("xname", identityData.displayName);
        claims.setClaim("xid", identityData.xuid);
        if (identityData.minecraftId != null) {
            claims.setClaim("mid", identityData.minecraftId);
        }

        JsonWebSignature authToken = new JsonWebSignature();
        authToken.setPayload(claims.toJson());
        authToken.setKey(keyPair.getPrivate());
        authToken.setAlgorithmHeaderValue("ES384");
        authToken.setHeader(HeaderParameterNames.X509_URL, publicKey);

        JsonWebSignature skinToken = new JsonWebSignature();
        skinToken.setPayload(runtime.skinJson());
        skinToken.setKey(keyPair.getPrivate());
        skinToken.setAlgorithmHeaderValue("ES384");
        skinToken.setHeader(HeaderParameterNames.X509_URL, publicKey);

        try {
            LoginPacket loginPacket = new LoginPacket();
            if (runtime.config().codec().netEase()) {
                loginPacket.setAuthPayload(new CertificateChainPayload(List.of(forgeNetEaseChain(runtime, publicKey))));
            } else {
                loginPacket.setAuthPayload(new TokenPayload(authToken.getCompactSerialization(), AuthType.SELF_SIGNED));
            }
            loginPacket.setClientJwt(skinToken.getCompactSerialization());
            loginPacket.setProtocolVersion(runtime.config().codec().protocolVersion());
            return loginPacket;
        } catch (JoseException exception) {
            throw new IllegalStateException("Unable to forge downstream login", exception);
        }
    }

    private static String forgeNetEaseChain(TunnelRuntime runtime, String publicKey) throws JoseException {
        Map<String, Object> rawClaims = runtime.rawIdentityClaims();
        if (rawClaims == null) {
            throw new IllegalStateException("Runtime does not contain raw identity claims");
        }

        Map<String, Object> claims = new HashMap<>(rawClaims);
        claims.put("identityPublicKey", publicKey);

        JsonWebSignature authToken = new JsonWebSignature();
        authToken.setPayload(JsonUtil.toJson(claims));
        authToken.setKey(runtime.proxyKeyPair().getPrivate());
        authToken.setAlgorithmHeaderValue("ES384");
        authToken.setHeader(HeaderParameterNames.X509_URL, publicKey);
        return authToken.getCompactSerialization();
    }
}
