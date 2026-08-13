// 서버가 문자열로 전달한 금액을 Number로 바꾸지 않고 천 단위 쉼표를 붙인다.
export function formatMoney(amount, fractionDigits = 0) {
  const normalizedAmount = String(amount ?? "0");
  const negative = normalizedAmount.startsWith("-");
  const unsignedAmount = negative ? normalizedAmount.slice(1) : normalizedAmount;
  const [integerPart = "0", decimalPart = ""] = unsignedAmount.split(".");
  const groupedInteger = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  const fraction = decimalPart.padEnd(fractionDigits, "0").slice(0, fractionDigits);

  return `${negative ? "-" : ""}${groupedInteger}${fractionDigits > 0 ? `.${fraction}` : ""}`;
}
