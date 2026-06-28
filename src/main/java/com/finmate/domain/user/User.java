package com.finmate.domain.user;

import com.finmate.domain.account.Account;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
@Entity
public class User {
    // PK는 보통 비즈니스 로직상 의미가 없는 대리키를 사용하는 것이 일반적이다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자정보가 사라진다고 계좌가 사라지면 안되기 때문에 Cascade + OrphanRemoval 설정 x
    // 또한 성능 + N+1 문제 방지를 위해 연관관계는 항상 지연로딩으로 설정하고, 꼭 필요한 경우에만 fetch join을 통해 즉시 로딩해야 한다.
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Account> accountList = new ArrayList<>();

    @NotBlank(message = "사용자 이름값은 필수입니다.")
    private String username;

    @Pattern(regexp = "01[0-9]-[0-9]{4}-[0-9]{4}$", message = "전화번호 형식은 010-0000-0000형식입니다.")
    private String telephone;

    @Pattern(regexp = "^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$", message = "올바른 이메일 형식을 입력해주세요.")
    private String email;

    @Pattern(regexp = "^[a-zA-Z0-9]{8,20}",message = "아이디에는 숫자,영문자만 사용 가능하며 최소 길이는 8, 최대 길이는 20이어야 합니다.")
    @Column(unique = true)
    private String userId;

    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[!@#$%^&*]).{10,}$", message = "비밀번호에는 영어,숫자,특수문자가 모두 포함되어야 하며 최소 길이는 10이상이어야 합니다.")
    private String password;

    // 양방향 연관관계 설정
    public void addAccount(Account account) {
        accountList.add(account);
        account.setUser(this);
    }
}
