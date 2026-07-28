package com.finmate.service.user;

import com.finmate.domain.user.User;
import com.finmate.domain.user.oauth.OAuthAccount;
import com.finmate.domain.user.oauth.OAuthProvider;
import com.finmate.repository.user.OAuthAccountRepository;
import com.finmate.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 소셜 로그인(OAuth2 기반 로그인)시에 기존에 로그인된 사용자와 연결하거나, 기존에 로그인한 적이 없으면 새로운 FinMate 사용자를 생성해서 연결한다.
@Service
@RequiredArgsConstructor
public class OAuthAccountService {

    private static final String DEFAULT_DISPLAY_NAME = "소셜 사용자";

    private final OAuthAccountRepository oauthAccountRepository; // OAuth 사용자 정보를 저장하는 레파지터리
    private final UserRepository userRepository; // FinMate User 정보를 저장하는 레파지터리

    @Transactional
    public ResolvedOAuthUser resolveOrCreate(OAuthProvider provider,
                                             String providerSubject,
                                             String providerEmail,
                                             String displayName) {
        // oauthAccountrepository 에서 OAuth 사용자 정보를 조회하고, 사용자 정보가 없으면 사용자를 생성하고 리턴한다.
        return oauthAccountRepository
                .findByProviderAndProviderSubject(provider, providerSubject)
                .map(OAuthAccount::getUser)
                .map(this::resolvedUser)
                .orElseGet(() -> createOAuthUser(
                        provider,
                        providerSubject,
                        providerEmail,
                        displayName
                ));
    }

    // 사용자 정보가 없으면 사용자를 생성한다.
    private ResolvedOAuthUser createOAuthUser(OAuthProvider provider,
                                               String providerSubject,
                                               String providerEmail,
                                               String displayName) {
        User user = new User();
        user.setUsername(normalizeDisplayName(displayName, providerEmail));

        // 새로운 FinMate 사용자를 생성해서 저장한다.
        User savedUser = userRepository.save(user);
        // 새로운 OAuth 사용자를 생성해서 저장하며, 새로운 FinMate 사용자와 연결한다.
        oauthAccountRepository.save(new OAuthAccount(
                savedUser,
                provider,
                providerSubject,
                providerEmail
        ));

        return resolvedUser(savedUser);
    }

    // display name 생성
    private String normalizeDisplayName(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return DEFAULT_DISPLAY_NAME;
    }

    // User 엔티티에서 ResolvedOAuthUser에 필요한 정보만 담아서 리턴한다(DRO 생성)
    private ResolvedOAuthUser resolvedUser(User user) {
        return new ResolvedOAuthUser(
                user.getId(),
                user.getUserId(),
                user.getUsername()
        );
    }

    // Finmate에서 사용할 사용자 정보
    // ResolvedOAuthUser는 실제 DB에서 조회한 사용자의 정보이다.
    public record ResolvedOAuthUser(
            Long id,
            String userId,
            String displayName
    ) {
    }
}
