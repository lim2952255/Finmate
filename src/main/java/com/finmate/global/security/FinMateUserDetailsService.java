package com.finmate.global.security;

import com.finmate.domain.user.User;
import com.finmate.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// UserDetailService에서는 User정보가 저장된 Repository에서 입력된 UserId를 기반으로 사용자 정보를 찾아서 리턴하는 역할을 수행한다.
// 이때 DB에서 사용자 정보를 조회한 다음, Spring Security가 사용자 객체를 관리하는 FinMatePrincipal 객체를 생성해서 리턴한다.
@Service
@RequiredArgsConstructor
public class FinMateUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // DB에서 사용자 정보를 조회해서 FinMatePrincipal을 생성한다.
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (user.getPassword() == null) {
            throw new UsernameNotFoundException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        // DB에서 조회한 사용자 정보를 기반으로 FinMatePrincipal을 생성한다. (사용자 검증용)
        return new FinMatePrincipal(
                user.getId(),
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
