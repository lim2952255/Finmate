package com.finmate.service.normal.account;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.account.AccountNumberRegistry;
import com.finmate.domain.normal.account.AccountType;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.OpenAccount;
import com.finmate.domain.user.User;
import com.finmate.repository.normal.account.AccountNumberRegistryRepository;
import com.finmate.repository.normal.account.AccountRepository;
import com.finmate.support.FinancialIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

// 이때 FinancialIntegrationTestSupport를 상속받기 떄문에, 스프링 컨텍스트를 로드하고, 테스트용 DB에 연결해서 테스트용 사용자와 계좌를 등록한다.
class AccountOpeningIntegrationTest extends FinancialIntegrationTestSupport {

    @Autowired
    private AccountService accountService;

    @MockitoSpyBean
    private AccountRepository accountRepository;

    @Autowired
    private AccountNumberRegistryRepository registryRepository;

    @Test
    @DisplayName("ACC-001: 일반계좌는 사용자당 열 번째까지 개설할 수 있다")
    void acc001_tenthAccountIsAllowed() {
        User user = persistUser("account-owner");
        // 일반 계좌 9개 개설
        for (int i = 0; i < 9; i++) {
            persistAccount(user, accountNumber(i), BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        }

        // 10번째 계좌 개설 (사용자당 총 10개의 일반계좌만 개설할 수 있다)
        accountService.openAccount(openAccount(), user);

        assertThat(accountRepository.countByUser_Id(user.getId())).isEqualTo(10);
        // persistAccount는 RegistryRepository에 게좌번호를 등록하지 않기 때문에 마지막 계좌 1개의 계좌번호만 등록된다.
        assertThat(registryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("ACC-001: 열한 번째 일반계좌는 계좌번호를 발급하지 않고 거부한다")
    void acc001_eleventhAccountIsRejectedWithoutIssuingNumber() {
        User user = persistUser("account-owner");
        // 총 10개의 일반 계좌 개설
        for (int i = 0; i < 10; i++) {
            persistAccount(user, accountNumber(i), BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        }
        // 각 사용자는 총 10개의 일반계좌만 개설할 수 있기때문에, 11번째 계좌를 개설하려고 하면 예외가 발생한다.
        assertThatThrownBy(() -> accountService.openAccount(openAccount(), user))
                .hasMessage("계좌는 최대 10개까지만 개설할 수 있습니다.");

        assertThat(accountRepository.countByUser_Id(user.getId())).isEqualTo(10);
        assertThat(registryRepository.count()).isZero();
    }

    @Test
    @DisplayName("ACC-008: 일반·투자 계좌번호는 유형과 관계없이 전역 유일하다")
    void acc008_registryAccountNumberIsGloballyUniqueAcrossAccountTypes() {
        // 일반 계좌번호와 투자 계좌번호
        String duplicateNumber = "999999-99-999999";
        // registryRepository에 계좌번호를 등록하고 Flush한다.
        registryRepository.saveAndFlush(AccountNumberRegistry.create(duplicateNumber, AccountType.NORMAL));

        // 동일한 계좌번호를 registryRepository에 또 등록허려고 하면 예외가 발생한다.
        assertThatThrownBy(() -> registryRepository.saveAndFlush(
                AccountNumberRegistry.create(duplicateNumber, AccountType.INVESTMENT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ACC-009: 일반계좌 저장 실패 시 발급한 계좌번호 등록도 롤백한다")
    void acc009_accountSaveFailureRollsBackIssuedRegistryNumber() {
        User user = persistUser("account-owner");
        long registryCountBefore = registryRepository.count();
        // accountRepository의 save() 메서드를 호출하면 예외가 발생한다(일반계좌 저장 실패)
        doThrow(new RuntimeException("injected account save failure"))
                .when(accountRepository).save(any());

        // 일반계좌를 개설하려고 할때, Repository에 저장하는데 실패하기 때문에 예외가 발생한다.
        assertThatThrownBy(() -> accountService.openAccount(openAccount(), user))
                .hasMessage("injected account save failure");

        reset(accountRepository);
        assertThat(accountRepository.countByUser_Id(user.getId())).isZero();
        // 일반계좌를 저장저장하는데 실패하면 계좌번호 저장도 롤백되어야 한다.
        assertThat(registryRepository.count()).isEqualTo(registryCountBefore);
    }

    private OpenAccount openAccount() {
        OpenAccount request = new OpenAccount();
        request.setBankCode(BankCode.KB_KOOKMIN);
        request.setCurrencyCode(CurrencyCode.KRW);
        return request;
    }

    private String accountNumber(int index) {
        return String.format("%06d-00-000000", index);
    }
}
