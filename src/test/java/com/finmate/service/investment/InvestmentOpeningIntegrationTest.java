package com.finmate.service.investment;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.OpenInvestment;
import com.finmate.domain.user.User;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.investment.InvestmentRepository;
import com.finmate.repository.normal.account.AccountNumberRegistryRepository;
import com.finmate.support.FinancialIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

class InvestmentOpeningIntegrationTest extends FinancialIntegrationTestSupport {

    @Autowired
    private InvestmentService investmentService;

    // MockitoSpyBean은 실제 스프링 컨테이너에서 해당 빈을 의존관계 주입을 받지만, 해당 빈의 일부 기능을 수정하고 싶을때 사용한다.
    @MockitoSpyBean
    private InvestmentRepository investmentRepository;

    @Autowired
    private InvestmentCashBalanceRepository cashBalanceRepository;

    @Autowired
    private AccountNumberRegistryRepository registryRepository;

    @Test
    @DisplayName("INV-001: 열 번째 투자계좌까지 개설하고 KRW·USD 예수금을 함께 생성한다")
    void inv001_tenthInvestmentIsAllowedAndCreatesBothCurrencyBalances() {
        User user = persistUser("investment-owner");
        // 총 9개의 증권계좌를 개설
        for (int i = 0; i < 9; i++) {
            persistInvestment(user, investmentNumber(i), SecuritiesCompanyCode.KIWOOM);
        }
        // 한개의 증권계좌를 추가로 개설 -> 해당 사용자 명의 증권계좌는 총 10개이며, 더이상 증권계좌를 개설할 수 없다.
        Long investmentId = investmentService.openInvestment(openInvestment(), user);

        assertThat(investmentRepository.countByUser_Id(user.getId())).isEqualTo(10);
        // CashBalanceRepository에 KRW와 USD 통화 예수금만 생성되었는지를 검사한다.
        assertThat(cashBalanceRepository.findByInvestmentAccount_Id(investmentId))
                .extracting(InvestmentCashBalance::getCurrencyCode)
                .containsExactlyInAnyOrder(CurrencyCode.KRW, CurrencyCode.USD);

        // 테스트용 증권계좌를 개설하는 PersistInvestment는 registryRepository에 계좌번호를 저장하지 않기 떄문에,
        // Long investmentId = investmentService.openInvestment(openInvestment(), user);를 통해 개설한 계좌 1건에 대해서만 RegistryRepository에 저장된다.
        assertThat(registryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("INV-001: 열한 번째 투자계좌는 계좌번호를 발급하지 않고 거부한다")
    void inv001_eleventhInvestmentIsRejectedWithoutIssuingNumber() {
        User user = persistUser("investment-owner");
        for (int i = 0; i < 10; i++) {
            persistInvestment(user, investmentNumber(i), SecuritiesCompanyCode.KIWOOM);
        }

        // 사용자가 개설할 수 있는 증권 계좌는 총 10개까지이기 때문에 열한번째 증권계좌 개설시에는 예외가 발생한다.
        assertThatThrownBy(() -> investmentService.openInvestment(openInvestment(), user))
                .hasMessage("증권 계좌는 최대 10개까지만 개설할 수 있습니다.");

        assertThat(investmentRepository.countByUser_Id(user.getId())).isEqualTo(10);
        assertThat(registryRepository.count()).isZero();
    }

    @Test
    @DisplayName("INV-005: 투자계좌와 통화 조합은 데이터베이스에서 유일하다")
    void inv005_cashBalanceCurrencyPairHasDatabaseUniqueConstraint() {
        // 하나의 투자계좌 같은 통화의 예수금 행을 두개이상 만들 수 없도록 DB Unique 제약조건이 걸려있는지를 테스트한다.
        User user = persistUser("investment-owner");
        Investment investment = persistInvestment(
                user, "800000-00-000001", SecuritiesCompanyCode.KIWOOM);

        // 증권계좌에 KRW 예수금을 하나 더 추가적으로 삽입을 시도하면 DB Unique 제약조건을 위반하여 에외가 발생한다.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Investment managed = entityManager.find(Investment.class, investment.getId());
            entityManager.persist(InvestmentCashBalance.create(managed, CurrencyCode.KRW));
            entityManager.flush();
        })).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);

        // 특정 증권계좌의 CashBalance가 KRW와 USD통화만 저장되어 있는지 검사한다. 다른 토오하는 저장되어 있으면 안된다.
        assertThat(cashBalanceRepository.findByInvestmentAccount_Id(investment.getId()))
                .extracting(InvestmentCashBalance::getCurrencyCode)
                .containsExactlyInAnyOrder(CurrencyCode.KRW, CurrencyCode.USD);
    }

    @Test
    @DisplayName("ACC-009: 투자계좌 저장 실패 시 발급한 계좌번호 등록도 롤백한다")
    void acc009_investmentSaveFailureRollsBackIssuedRegistryNumber() {
        User user = persistUser("investment-owner");
        long registryCountBefore = registryRepository.count();
        // investmentRepositry의 save() 메서드 호출시 예외가 발생하도록 MockitoSpyBean을 수정한다.
        doThrow(new RuntimeException("injected investment save failure"))
                .when(investmentRepository).save(any());

        // 증권 계좌 개설시에 투자계좌를 저장하는데 실패했기 때문에 예외가 발생하낟.
        assertThatThrownBy(() -> investmentService.openInvestment(openInvestment(), user))
                .hasMessage("injected investment save failure");

        reset(investmentRepository);
        assertThat(investmentRepository.countByUser_Id(user.getId())).isZero();
        // 투자계좌를 저장하는데 실패한 경우에, 발급한 계좌번호도 함께 롤백되어야 한다.
        // 그렇지 않으면 실제 투자계좌는 개설되지 않았는데, 계좌번호만 의미없이 등록되어 사용할수 없는 상태가 된다.
        assertThat(registryRepository.count()).isEqualTo(registryCountBefore);
    }

    private OpenInvestment openInvestment() {
        OpenInvestment request = new OpenInvestment();
        request.setSecuritiesCompanyCode(SecuritiesCompanyCode.KIWOOM);
        return request;
    }

    private String investmentNumber(int index) {
        return String.format("%06d-11-000000", index);
    }
}
