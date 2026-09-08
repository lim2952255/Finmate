package com.finmate.service.user;

import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.ChangePasswordRequest;
import com.finmate.domain.user.dto.FindUserIdRequest;
import com.finmate.domain.user.dto.LoginDTO;
import com.finmate.domain.user.dto.SignupRequest;
import com.finmate.exception.BusinessRuleException;
import com.finmate.exception.DuplicatedId;
import com.finmate.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

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
        // 화면 검증을 우회한 요청도 가입되지 않도록 서버에서 비밀번호 확인을 다시 비교한다.
		// 입력한 비밀번호와 비밀번호 확인이 일치하는지를 검사한다.
        if (!signupRequest.getPassword().equals(signupRequest.getPasswordConfirmation())) {
            throw new BusinessRuleException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }

        User user = findUser(signupRequest);
        if(user != null)
            throw new DuplicatedId("아이디가 중복되었습니다");

        String telephone = normalizeTelephone(signupRequest.getTelephone());
        if (userRepository.existsByTelephone(telephone)) {
            throw new DuplicatedId("전화번호가 중복되었습니다");
        }

        String email = normalizeEmail(signupRequest.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicatedId("이메일이 중복되었습니다");
        }

        user = new User();
        user.setUsername(signupRequest.getUsername().trim());
        user.setTelephone(telephone);
        user.setEmail(email);
        user.setUserId(signupRequest.getUserId());

        // 패스워드를 저장할때에는 인코딩을 한 상태로 저장해야 한다.
        String encodedPassword = passwordEncoder.encode(signupRequest.getPassword());
        user.setPassword(encodedPassword);

        User savedUser = userRepository.save(user);
        return savedUser.getId();
    }

    @Transactional(readOnly = true)
    public User findUser(LoginDTO loginDTO) {
        return userRepository.findByUserId(loginDTO.getUserId())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElse(null);
    }

	// 사용자가 입력한 사용자이름, 전화번호, 이메일을 기반으로 사용자 아이디를 찾는 메서드
    @Transactional(readOnly = true)
    public String findUserId(FindUserIdRequest request) {
        // 가입 당시 입력한 세 정보가 모두 일치하고 로그인 아이디가 있는 로컬 계정만 찾는다.
        User user = userRepository
                .findByUsernameAndTelephoneAndEmailAndUserIdIsNotNull(
                        request.username().trim(),
                        normalizeTelephone(request.telephone()),
                        normalizeEmail(request.email())
                )
                .orElseThrow(() -> new BusinessRuleException(
                        "입력한 정보와 일치하는 로컬 계정을 찾을 수 없습니다."
                ));

        return user.getUserId();
    }

	// 로그인한 사용자가 자신의 비밀번호를 수정하는 메서드
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("사용자 정보를 찾을 수 없습니다."));

        // OAuth 전용 계정은 검증할 기존 로컬 비밀번호가 없으므로 변경 대상에서 제외한다.
        if (user.getUserId() == null || user.getPassword() == null) {
            throw new BusinessRuleException("소셜 로그인 전용 계정은 변경할 로컬 비밀번호가 없습니다.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!request.newPassword().equals(request.passwordConfirmation())) {
            throw new BusinessRuleException("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessRuleException("새 비밀번호는 현재 비밀번호와 다르게 입력해주세요.");
        }

        // 평문 비밀번호는 저장하지 않고 새 BCrypt 해시만 영속화한다.
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    private String normalizeTelephone(String telephone) {
        String digits = telephone.replace("-", "");
        return digits.substring(0, 3)
                + "-" + digits.substring(3, 7)
                + "-" + digits.substring(7);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
