package org.allaymc.bedrocktunnel.tunnel;

import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

final class LoginForgery {
    private static final int TOKEN_AUTH_MIN_PROTOCOL = Bedrock_v818.CODEC.getProtocolVersion();

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
                loginPacket.setAuthPayload(createCertificateChainAuthPayload(runtime, publicKey));
            } else {
                loginPacket.setAuthPayload(createStandardAuthPayload(
                        runtime,
                        runtime.config().codec().protocolVersion(),
                        authToken.getCompactSerialization(),
                        publicKey
                ));
            }
            loginPacket.setClientJwt(skinToken.getCompactSerialization());
            loginPacket.setProtocolVersion(runtime.config().codec().protocolVersion());
            return loginPacket;
        } catch (JoseException exception) {
            throw new IllegalStateException("Unable to forge downstream login", exception);
        }
    }

    private static CertificateChainPayload createCertificateChainAuthPayload(TunnelRuntime runtime, String publicKey) throws JoseException {
        return createCertificateChainAuthPayload(runtime.rawIdentityClaims(), runtime.identityData(), runtime.proxyKeyPair(), publicKey);
    }

    static CertificateChainPayload createCertificateChainAuthPayload(
            Map<String, Object> rawClaims,
            ChainValidationResult.IdentityData identityData,
            KeyPair keyPair,
            String publicKey
    ) throws JoseException {
        return new CertificateChainPayload(List.of(forgeIdentityChain(rawClaims, identityData, keyPair, publicKey)), AuthType.SELF_SIGNED);
    }

    private static String forgeIdentityChain(
            Map<String, Object> rawClaims,
            ChainValidationResult.IdentityData identityData,
            KeyPair keyPair,
            String publicKey
    ) throws JoseException {
        if (rawClaims == null) {
            throw new IllegalStateException("Runtime does not contain raw identity claims");
        }
        if (identityData == null) {
            throw new IllegalStateException("Runtime does not contain identity data");
        }

        Map<String, Object> claims = new HashMap<>(rawClaims);
        claims.put("identityPublicKey", publicKey);
        claims.put("extraData", createLegacyExtraData(identityData));

        JsonWebSignature authToken = new JsonWebSignature();
        authToken.setPayload(JsonUtil.toJson(claims));
        authToken.setKey(keyPair.getPrivate());
        authToken.setAlgorithmHeaderValue("ES384");
        authToken.setHeader(HeaderParameterNames.X509_URL, publicKey);
        return authToken.getCompactSerialization();
    }

    static AuthPayload createStandardAuthPayload(TunnelRuntime runtime, int protocolVersion, String authToken, String publicKey) throws JoseException {
        if (supportsTokenAuthPayload(protocolVersion)) {
            return new TokenPayload(authToken, AuthType.SELF_SIGNED);
        }
        return createCertificateChainAuthPayload(runtime, publicKey);
    }

    static boolean supportsTokenAuthPayload(int protocolVersion) {
        return protocolVersion >= TOKEN_AUTH_MIN_PROTOCOL;
    }

    static String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT");
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> createLegacyExtraData(ChainValidationResult.IdentityData identityData) {
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("displayName", identityData.displayName);
        extraData.put("identity", identityData.identity.toString());
        extraData.put("XUID", identityData.xuid == null ? "" : identityData.xuid);
        if (identityData.titleId != null) {
            extraData.put("titleId", identityData.titleId);
        }
        return extraData;
    }
}
