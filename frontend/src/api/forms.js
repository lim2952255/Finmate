import { getSession } from "./session.js";
import { fetchJson, readJson } from "./http.js";

export async function getJson(url, options = {}) {
  return fetchJson(url, { ...options, fallbackMessage: "화면 정보를 불러오지 못했습니다." });
}

export async function postJson(url, body, { csrf = true } = {}) {
  const session = csrf ? await getSession() : null;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(session ? { [session.csrf.headerName]: session.csrf.token } : {})
    },
    body: JSON.stringify(body)
  });
  return readJson(response, "요청을 처리하지 못했습니다.");
}
