import { useState } from "react";
import { formatMoney } from "../../utils/moneyFormatting.js";

// 여러 통화 금액 중 하나를 크게 보여주고 버튼으로 표시 통화를 바꾸는 컴포넌트
export default function CurrencySwitcher({ balances, label, compact = false }) {
  const safeBalances = Array.isArray(balances) && balances.length > 0
    ? balances
    : [{ currencyCode: "KRW", fractionDigits: 0, amount: "0" }];
  const [selectedCurrency, setSelectedCurrency] = useState(safeBalances[0].currencyCode);

  // 선택 중인 통화가 새 응답에 없다면 state를 추가 변경하지 않고 첫 통화를 사용한다.
  const activeCurrency = safeBalances.some((balance) => balance.currencyCode === selectedCurrency)
    ? selectedCurrency
    : safeBalances[0].currencyCode;
  const selectedBalance = safeBalances.find(
    (balance) => balance.currencyCode === activeCurrency
  ) || safeBalances[0];
  const containerClassName = compact ? "primary-currency-switcher" : "currency-switcher";
  const valuesClassName = compact ? "primary-currency-values" : "currency-primary-values";
  const valueClassName = compact ? "primary-currency-value" : "currency-primary-value";
  const optionsClassName = compact ? "primary-currency-options" : "currency-options";
  const optionClassName = compact ? "primary-currency-option" : "currency-option";

  return (
    <div className={containerClassName}>
      {compact ? <small>{label}</small> : <span className="currency-switcher-label">{label}</span>}

      <div className={valuesClassName}>
        <div className={`${valueClassName} is-active`}>
          <strong>{formatMoney(selectedBalance.amount, selectedBalance.fractionDigits)}</strong>
          <span>{selectedBalance.currencyCode}</span>
        </div>
      </div>

      {safeBalances.length > 1 && (
        <div className={optionsClassName}>
          {safeBalances.map((balance) => (
            <button
              key={balance.currencyCode}
              className={optionClassName}
              type="button"
              aria-pressed={balance.currencyCode === activeCurrency}
              onClick={() => setSelectedCurrency(balance.currencyCode)}
            >
              <strong>{formatMoney(balance.amount, balance.fractionDigits)}</strong>
              <span>{balance.currencyCode}</span>
            </button>
          ))}
        </div>
      )}

      {!compact && (
        <p className="currency-switcher-note">보조 통화를 선택하면 두 금액의 표시 위치가 바뀝니다.</p>
      )}
    </div>
  );
}
