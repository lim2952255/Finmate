import { deleteJson, getJson, postJson } from "./forms.js";

const endpoint = (stockId) => `/api/stocks/${encodeURIComponent(stockId)}/price-lines`;

export function getStockPriceLines(stockId, options = {}) {
  return getJson(endpoint(stockId), options);
}

export function createStockPriceLine(stockId, price) {
  return postJson(endpoint(stockId), { price: String(price) });
}

export function deleteStockPriceLine(stockId, lineId) {
  return deleteJson(`${endpoint(stockId)}/${encodeURIComponent(lineId)}`);
}

export function deleteAllStockPriceLines(stockId) {
  return deleteJson(endpoint(stockId));
}
