package com.finmate.support;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.user.User;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

// 스프링 컨텍스트 로딩
@SpringBootTest(properties = {
        "finmate.stock-ranking.initial-delay-millis=600000"
})
// 해당 클래스에서 다른 테스트 클래스에서 필요한 기본 정보들을 설정한다.
public abstract class FinancialIntegrationTestSupport extends MySqlIntegrationTestSupport {

    // 테스트 사용자를 만들 때, 이메일과 아이디가 중복되지 않도 번호를 증가시키는 용도
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    // JPA 엔티티를 직접 저장하거나 조회하기 위한 객체
    // 각 엔티티별 Repository를 모두 주입하지 않고 EntityManager 하나로 테스트 데이터를 직접 영속화한다.
    // 또한 persist, flush, getReference 등 JPA 영속성 컨텍스트의 동작을 명시적으로 제어하기 위해 사용한다.
    @Autowired
    protected EntityManager entityManager;

    // 코드 블록을 명시적으로 트랜잭션 내에서 실행하기 위한 도구
    // @Trnasactional이 아니라 TransactionTemplate을 사용하는 이유는 @Transactional은 객체를 프록시 객체로 감싸서 해당 객체의 메서드를 호출시
    // 프록시 객체가 트랜잭션을 열고 닫음으로서 트랜잭션을 적용할 수 있지만, 테스트 코드에서는 this.method()와 같이 프록시 객체를 거치지 않고 자기 호출 방식으로
    // 메서드를 호출하기 떄문에 트랜잭션이 걸리지 않을 수 있다. 따라서 TransactionTemplate을 통해서 명시적으로 트랜잭션을 걸어준다.
    @Autowired
    protected TransactionTemplate transactionTemplate;

    // 테스트용 사용자를 생성하고, DB에 저장하는 메서드
    protected User persistUser(String name) {
        return transactionTemplate.execute(status -> {
            int sequence = SEQUENCE.incrementAndGet();
            User user = new User();
            user.setUsername(name);
            user.setTelephone("010-0000-0000");
            user.setEmail("integration" + sequence + "@finmate.test");
            user.setUserId(String.format("testuser%04d", sequence));
            user.setPassword("password1!");
            entityManager.persist(user); // User 엔티티를 EntityManager에 등록한다.
            entityManager.flush(); // 이후에 EntityManager를 flush함으로서 영속성 컨텍스트의 변경사항을 DB에 반영한다.
            return user; // 콜백이 정상 종료되면 TransactionTemplate이 트랜잭션을 커밋한다.
        });
    }

    // 테스트용 일반 계좌를 생성하고 저장하는 메서드
    protected Account persistAccount(User user, String accountNumber, BankCode bankCode, CurrencyCode currencyCode) {
        return transactionTemplate.execute(status -> {
            // user 객체를 프록시 객체로 받아온다. (find: 해당 객체를 DB에서 즉시 읽는다. getReference: 해당 객체의 프록시 객체를 받고, 실제 객체 데이터 접근시에 DB에서 데이터를 조회한다.)
            // 전달받은 user는 이전 트랜잭션 종료 후 준영속 상태일 수 있으므로, 현재 영속성 컨텍스트가 관리하는 User 참조를 얻는다.
            // getReference()는 일반적으로 즉시 SELECT하지 않고 프록시를 반환하며, ID 외의 실제 데이터가 필요할 때 DB를 조회할 수 있다.
            User managedUser = entityManager.getReference(User.class, user.getId());
            Account account = Account.create(accountNumber, bankCode, currencyCode); // 계좌 생성
            managedUser.addAccount(account); // 연관관계 설정
            entityManager.persist(account); // Account 엔티티를 EntityManager에 등록한다.
            entityManager.flush(); // 이후에 EntityManager를 flush함으로서 영속성 컨텍스트의 변경사항을 DB에 반영한다.
            return account; // 콜백이 정상 종료되면 TransactionTemplate이 트랜잭션을 커밋한다.
        });
    }

    // 테스트용 증권 계좌를 생성하고 저장하는 메서드
    protected Investment persistInvestment(User user,
                                             String accountNumber,
                                             SecuritiesCompanyCode securitiesCompanyCode) {
        return transactionTemplate.execute(status -> {
            User managedUser = entityManager.getReference(User.class, user.getId()); // user객체가 준영속상태일 수 있기 때문에 DB에서 다시 조회함으로서 영속상태로 만든다.
            Investment investment = Investment.create(managedUser, accountNumber, securitiesCompanyCode); // 증권 계좌 생성
            entityManager.persist(investment); // investment 엔티티를 EntityManager에 등록한다.
            entityManager.flush(); // 이후에 EntityManager를 flush함으로서 영속성 컨텍스트의 변경사항을 DB에 반영한다.
            return investment; // 콜백이 정상 종료되면 TransactionTemplate이 트랜잭션을 커밋한다.
        });
    }
}
