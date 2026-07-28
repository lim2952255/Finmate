package com.finmate.global.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;

// 환경 설정 파일
@Configuration
@ConditionalOnExpression(
        // google, kakao, naver 등이 enabled 되어있을 때에만 해당 설정파일이 활성화된다
        "${finmate.oauth.google.enabled:false}"
                + " or ${finmate.oauth.kakao.enabled:false}"
                + " or ${finmate.oauth.naver.enabled:false}"
)
public class SocialOAuthClientConfig {

    // ClientRegistrationRepository는 Spring Security가 OAuth 공급자 설정을 조회하는 저장소이다.
    // 해당 저장소의 registration id를 기반으로 적절한 공급자 설정을 찾아서 조회한다.
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            Environment environment
    ) {
        // ClientRegistration을 관리하는 리스트
        List<ClientRegistration> registrations = new ArrayList<>();

        // google OAuth가 활성화되어 있다면, client-id와 client-secret을 읽어서 ClientRegistration을 생성한다.
        if (enabled(environment, "google")) {
            registrations.add(google(
                    required(environment, "google", "client-id"),
                    required(environment, "google", "client-secret")
            ));
        }
        // Kakao OAuth가 활성화되어 있다면, client-id와 client-secret을 읽어서 ClientRegistration을 생성한다.
        if (enabled(environment, "kakao")) {
            registrations.add(kakao(
                    required(environment, "kakao", "client-id"),
                    required(environment, "kakao", "client-secret")
            ));
        }
        // Naver OAuth가 활성화되어 있다면, client-id와 client-secret을 읽어서 ClientRegistration을 생성한다.
        if (enabled(environment, "naver")) {
            registrations.add(naver(
                    required(environment, "naver", "client-id"),
                    required(environment, "naver", "client-secret")
            ));
        }

        // 생성한 공급자 저장소를 서버메모리에서 관리한다.
        return new InMemoryClientRegistrationRepository(registrations);
    }

    // 환경설정정보에서 해당 공급자가 true로 설정되어있는지를 검사한다.
    private boolean enabled(Environment environment, String provider) {
        return environment.getProperty(
                "finmate.oauth." + provider + ".enabled",
                Boolean.class,
                false
        );
    }

    // OAuth를 활성화하기 필요한 설정정보가 존재하는지를 검사한다.
    private String required(Environment environment,
                            String provider,
                            String property) {
        String key = "finmate.oauth." + provider + "." + property;
        String value = environment.getProperty(key, "");
        if (value.isBlank()) {
            throw new IllegalStateException(
                    provider + " OAuth를 활성화하려면 " + key
                            + " 설정이 필요합니다."
            );
        }
        return value;
    }

    // Google OAuth/OIDC 로그인에 필요한 정보를 저장해서 ClientRegistration으로 생성한다.
    // ClientRegistration은 Spring Security가 특정 OAuth 공급자와 어떻게 통신해야 하는지 알려주는 설정묶음이다.
    /*
    * 1. 사용자를 공급자 로그인 화면으로 내보낸다.
    * 2. 로그인 성공 후 Authentication Code를 받는다.
    * 3. Authentication Code를 다시 공급자에게 요청해서 Access Token을 받는다.
    * 4. Access Token을 기반으로 사용자 정보를 조회한다.
    * 5. 사용자 고유 식별자를 기반으로 로그인 처리한다.
    * */
    private ClientRegistration google(String clientId,
                                      String clientSecret) {
        return ClientRegistration
                .withRegistrationId("google") // Spring Security에서 해당 Registration을 찾기 위한 Key 값 설정
                // 외부 공급자에게 로그인 권한을 위임하기 위해 필요한 clientId와 clientSecret을 등록한다.
                .clientId(clientId)
                .clientSecret(clientSecret)

                // Authorization Code를 Access Token으로 교환할때 cliendId와 clientSecret을 어떻게 전달할지를 결정한다.
                // 해당 설정은 HTTP Authentication 헤더에 정보를 담아서 전달하도록 설정한다.
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                )
                // 공급자가 Authorization Code를 받고, 이를 다시 공급자에게 요청해서 사용자정보를 조회할 수 있는 Access token으로 변환한다.
                .authorizationGrantType(
                        AuthorizationGrantType.AUTHORIZATION_CODE
                )
                // OAuth 인증 완료 후 공급자가 클라이언트를 다시 돌려보낼 리다이렉트 주소
                .redirectUri(
                        "{baseUrl}/login/oauth2/code/{registrationId}"
                )
                // 각 공급자에게 요청할 권한 범위
                .scope("openid", "profile", "email")
                // 사용자를 구글 로그인화면으로 보내는 주소
                .authorizationUri(
                        "https://accounts.google.com/o/oauth2/v2/auth"
                )
                // 공급자로부터 받은 Authorization Code를 Access token으로 교환하기 위한 요청 주소
                .tokenUri("https://oauth2.googleapis.com/token")
                // Access Token을 기반으로 사용자 정보를 조회할 때 사용하는 요청 주소
                .userInfoUri(
                        "https://openidconnect.googleapis.com/v1/userinfo"
                )
                // Google 사용자 객체에서 고유 사용자 이름으로 사용할 Claim을 sub로 사용한다.
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientName("Google")
                .build();
    }

    private ClientRegistration kakao(String clientId,
                                     String clientSecret) {
        return ClientRegistration
                .withRegistrationId("kakao")
                .clientId(clientId)
                .clientSecret(clientSecret)
                // 해당 방식은 clientId와 clientSecret을 토큰 요청 본분에 넣어서 전송한다.
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_POST
                )
                .authorizationGrantType(
                        AuthorizationGrantType.AUTHORIZATION_CODE
                )
                .redirectUri(
                        "{baseUrl}/login/oauth2/code/{registrationId}"
                )
                .scope("openid", "profile_nickname")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v1/oidc/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .jwkSetUri(
                        "https://kauth.kakao.com/.well-known/jwks.json"
                )
                .clientName("Kakao")
                .build();
    }

    private ClientRegistration naver(String clientId,
                                     String clientSecret) {
        return ClientRegistration
                .withRegistrationId("naver")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_POST
                )
                .authorizationGrantType(
                        AuthorizationGrantType.AUTHORIZATION_CODE
                )
                .redirectUri(
                        "{baseUrl}/login/oauth2/code/{registrationId}"
                )
                .authorizationUri(
                        "https://nid.naver.com/oauth2.0/authorize"
                )
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
    }
}
