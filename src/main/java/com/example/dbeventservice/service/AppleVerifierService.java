package com.example.dbeventservice.service;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.*;

import com.nimbusds.jose.util.DefaultResourceRetriever;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Map;

@Service
public class AppleVerifierService {

    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";

    public Map<String, Object> verify(String idToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(idToken);

            JWKSource<com.nimbusds.jose.proc.SecurityContext> keySource = new RemoteJWKSet<>(
                    new URL(APPLE_KEYS_URL),
                    new DefaultResourceRetriever(3000, 3000) // timeouts
            );

            ConfigurableJWTProcessor<com.nimbusds.jose.proc.SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            JWSKeySelector<com.nimbusds.jose.proc.SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(signedJWT.getHeader().getAlgorithm(), keySource);
            jwtProcessor.setJWSKeySelector(keySelector);

            SecurityContext context = null;
            return jwtProcessor.process(idToken, context).getClaims();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
