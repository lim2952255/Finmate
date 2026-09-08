package com.finmate.repository.user;

import com.finmate.domain.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    // 계좌가 없는 사용자도 잠글 수 있도록 사용자 행을 잠근다.
    // 같은 사용자의 동시 개설 요청이 계좌 수와 지급 여부를 동시에 통과하는 것을 방지한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    // 스프링 데이터 JPA를 사용하기 위해서는 JPARepository 인터페이스를 상속받으면 된다.
    // 스프링 데이터 JPA를 사용할 때에는 기본적으로 제공되는 CRUD + 쿼리 메서드 기능을 사용하면 된다.
    // 쿼리 메서드는 JPQL기반이기 때문에 즉시 로딩시에 N+1문제가 발생하는 것을 조심해야 한다.(기본적으로 모두 LAZY Loading으로 설정하기)
    Optional<User> findByUserId(String userId);

	// OAuth 사용자인지, 아니면 로컬 계정 사용자인지 식별하기 위해 전화번호와 이메일정보가 존재하는지 검사한다.(OAuth 사용자는 전화번호와 이메일번호가 없다)
    boolean existsByTelephone(String telephone);

    boolean existsByEmail(String email);

	// 사용자이름, 전화번호, 이메일을 기반으로 사용자 아이디를 식별하기 위한 메서드
    Optional<User> findByUsernameAndTelephoneAndEmailAndUserIdIsNotNull(
            String username,
            String telephone,
            String email
    );
}
