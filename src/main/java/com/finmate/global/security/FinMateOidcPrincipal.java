package com.finmate.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Google과 같은 OIDC 기반으로 처리하는 공급자
// 외부 공급자를 통해 로그인을 하게 되면 OAuth2User라는 객체에 로그인 정보를 담아준다.
// 하지만 해당 객체 내에 속한 정보가 충분하지 않거나, 불필요할 수 있기 때문, 외부 인증 정보와 FinMate 내부 사용자 정보를 합쳐서 FinMateAuthenticatedPrincipal로 생성한다.
public final class FinMateOidcPrincipal
        // OidcUser가 Principal 인터페이스를 상속받기 떄문에, FinMateOidcPrincipal을 Principal로 사용할 수 있다.
        implements OidcUser, FinMateAuthenticatedPrincipal {

    private final Long id;
    private final String userId;
    private final String displayName;
    private final OidcUser delegate; // Spring Security가 만들어준 OidcUser 객체 정보
    private final Set<GrantedAuthority> authorities; // 사용자 권한

    public FinMateOidcPrincipal(Long id,
                                String userId,
                                String displayName,
                                OidcUser delegate) {
        // 외부 인증정보와 FinMate 내부 사용자 정보를 기반으로 필요한 정보를 저장한다.
        this.id = id;
        this.userId = userId;
        this.displayName = displayName;
        this.delegate = delegate;

        // 권한 정보를 설정한다.
        LinkedHashSet<GrantedAuthority> mappedAuthorities =
                new LinkedHashSet<>(delegate.getAuthorities());
        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        this.authorities = Set.copyOf(mappedAuthorities);
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    // delegate에서 관련 정보들을 꺼내서 조회한다.
    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
