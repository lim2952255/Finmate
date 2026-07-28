package com.finmate.repository.user;

import com.finmate.domain.user.oauth.OAuthAccount;
import com.finmate.domain.user.oauth.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    // provider와 providerSubject를 기반으로 OAuth 사용자 정보를 조회한다.
    Optional<OAuthAccount> findByProviderAndProviderSubject(
            OAuthProvider provider,
            String providerSubject
    );
}
