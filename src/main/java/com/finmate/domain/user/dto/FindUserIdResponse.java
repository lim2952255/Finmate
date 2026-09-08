package com.finmate.domain.user.dto;

// 사용자 ID를 찾은 뒤, 찾은 사용자 ID 정보를 DTO에 담아서 리턴한다.
public record FindUserIdResponse(String userId) {
}
