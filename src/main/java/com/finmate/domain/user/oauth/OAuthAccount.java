package com.finmate.domain.user.oauth;

import com.finmate.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// FinMate 사용자 정보와 외부 OAuth 계정의 연결정보를 저장하는 엔티티
@Getter
@Entity
@Table(
        name = "oauth_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oauth_account_provider_subject",
                // provier + provider_subject에 유니크 제약을 설정함으로서 같은 OAuth 계정이 DB에 중복 저장되는것을 방지한다.
                columnNames = {"provider", "provider_subject"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러개의 OAuth 계정이 하나의 Finmate User에 연결될 수 있다. 즉 User 한명 계정에 Google계정, Kakao 계정등이 연결될 수 있다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // OAuth 계정과 연결된 Finmate User

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OAuthProvider provider; // OAuth 제공자 (Google)

    // OAuth 제공자가 발급한 사용자 고유 식별자 번호. 즉, 사용자마다 고유한 번호를 발급받는다.
    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    // 사용자 이메일 주소
    @Column(name = "provider_email")
    private String providerEmail;

    public OAuthAccount(User user,
                        OAuthProvider provider,
                        String providerSubject,
                        String providerEmail) {
        this.user = user; // 연관관계 설정
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.providerEmail = providerEmail;
    }
}
