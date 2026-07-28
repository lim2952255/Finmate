package com.finmate.service.user;

import com.finmate.domain.user.User;
import com.finmate.domain.user.oauth.OAuthAccount;
import com.finmate.domain.user.oauth.OAuthProvider;
import com.finmate.repository.user.OAuthAccountRepository;
import com.finmate.repository.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 테스트 클래스 설정
@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

    @Mock
    private OAuthAccountRepository oauthAccountRepository;

    @Mock
    private UserRepository userRepository;

    // @InjectMock은 실제 객체를 생성하면서 Mock객체를 연관관계를 주입한다.
    // 즉 OAuthAccountService는 실제 기능을 가지고 있는 객체이다.
    @InjectMocks
    private OAuthAccountService oauthAccountService;

    @Test
    @DisplayName("기존 Google 계정 연결은 같은 FinMate 사용자를 반환한다")
    void resolvesExistingOAuthAccount() {
        User user = user(1L, "기존 사용자"); // 테스트용 사용자 생성
        // 테스트용 구글계정 생성
        OAuthAccount account = new OAuthAccount(
                user,
                OAuthProvider.GOOGLE,
                "google-subject",
                "user@example.com"
        );
        // 구글계정 조회
        when(oauthAccountRepository.findByProviderAndProviderSubject(
                OAuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.of(account));

        // 구글계정을 조회하고 존재하면 해당 계정정보를 ResolvedOAuthUser에 담는다.
        OAuthAccountService.ResolvedOAuthUser resolved =
                oauthAccountService.resolveOrCreate(
                        OAuthProvider.GOOGLE,
                        "google-subject",
                        "user@example.com",
                        "Google Name"
                );

        assertThat(resolved.id()).isEqualTo(1L);
        assertThat(resolved.displayName()).isEqualTo("기존 사용자");
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("최초 Google 로그인은 비밀번호 없는 User와 OAuthAccount를 함께 만든다")
    void createsUserAndOAuthAccountForFirstLogin() {
        // 구글 계정이 아직 없다고 가정
        when(oauthAccountRepository.findByProviderAndProviderSubject(
                OAuthProvider.GOOGLE,
                "new-google-subject"
        )).thenReturn(Optional.empty());
        // 사용자 생성
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // 신규 사용자 생성시 UserRepository에 새로운 사용자를 저장한다.
        OAuthAccountService.ResolvedOAuthUser resolved =
                oauthAccountService.resolveOrCreate(
                        OAuthProvider.GOOGLE,
                        "new-google-subject",
                        "new@example.com",
                        "신규 사용자"
                );

        // 사용자가 새로생성되서 등록되었는지, 구글 사용자가 제대로 생성되었는지를 검사한다.
        assertThat(resolved.id()).isEqualTo(2L);
        assertThat(resolved.userId()).isNull();
        assertThat(resolved.displayName()).isEqualTo("신규 사용자");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isNull();
        assertThat(userCaptor.getValue().getUserId()).isNull();

        ArgumentCaptor<OAuthAccount> accountCaptor =
                ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oauthAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider())
                .isEqualTo(OAuthProvider.GOOGLE);
        assertThat(accountCaptor.getValue().getProviderSubject())
                .isEqualTo("new-google-subject");
        assertThat(accountCaptor.getValue().getProviderEmail())
                .isEqualTo("new@example.com");
        assertThat(accountCaptor.getValue().getUser().getId()).isEqualTo(2L);
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
