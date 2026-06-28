package com.finmate.repository.user;

import com.finmate.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    // 스프링 데이터 JPA를 사용하기 위해서는 JPARepository 인터페이스를 상속받으면 된다.
    // 스프링 데이터 JPA를 사용할 때에는 기본적으로 제공되는 CRUD + 쿼리 메서드 기능을 사용하면 된다.
    // 쿼리 메서드는 JPQL기반이기 때문에 즉시 로딩시에 N+1문제가 발생하는 것을 조심해야 한다.(기본적으로 모두 LAZY Loading으로 설정하기)
    Optional<User> findByUserId(String userId);
}
