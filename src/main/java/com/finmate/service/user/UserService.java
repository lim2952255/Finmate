package com.finmate.service.user;

import com.finmate.domain.user.dto.LoginDTO;
import com.finmate.domain.user.dto.LoginRequest;
import com.finmate.domain.user.dto.SignupRequest;
import com.finmate.domain.user.User;
import com.finmate.exception.DuplicatedId;
import com.finmate.exception.LoginException;
import com.finmate.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    // RequiredArgsconstructor(Lombok)을 통해서 final field들을 모아 기본 생성자를 자동으로 생성.
    // 이후 생성자가 하나라면 @Autowired를 생략해도 자동으로 의존관계가 주입된다.
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Service계층에서 Repository에 접근하는 작업 단위들은 모두 @Transaction으로 묶어야 한다.
    @Transactional
    public Long save(SignupRequest signupRequest) {
        User user = findUser(signupRequest);
        if(user != null)
            throw new DuplicatedId("아이디가 중복되었습니다");

        user = new User();
        user.setUsername(signupRequest.getUsername());
        user.setTelephone(signupRequest.getTelephone());
        user.setEmail(signupRequest.getEmail());
        user.setUserId(signupRequest.getUserId());

        // 패스워드를 저장할때에는 인코딩을 한 상태로 저장해야 한다.
        String encodedPassword = passwordEncoder.encode(signupRequest.getPassword());
        user.setPassword(encodedPassword);

        User savedUser = userRepository.save(user);
        return savedUser.getId();
    }

    // 사용자를 조회만 하기 떄문에 readOnly = true로 설정하여 최적화
    @Transactional(readOnly = true)
    public User login(LoginRequest loginRequest) {
        User user = findUser(loginRequest);
        if(user == null) {
            throw new LoginException("Invalid username or password");
        }
        if(passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())){
            return user;
        }

        throw new LoginException("로그인 실패");
    }

    @Transactional(readOnly = true)
    public User findUser(LoginDTO loginDTO) {
        return userRepository.findByUserId(loginDTO.getUserId())
                .orElse(null);
    }
}
