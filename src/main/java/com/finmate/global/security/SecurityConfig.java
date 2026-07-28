package com.finmate.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// 스프링 시큐리티에 대한 설정을 추가한다.
@Configuration
public class SecurityConfig {
	// securityFilterChain은 HTTP 요청이 들어왔을 적용할 보안필터들의 묶음이다.
	// 클라이언트 요청이 들어오 Spring Security FilterChain이 동작하면 인증 여부를 확인하고, 권한을 확인한 다음, controller로 요청을 전달한다.
	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http, // http 보안 설정을 작성하는 객체
			FinMateUserDetailsService userDetailsService, // 로그인 Id를 기반으로 DB에서 사용자를 조회하고 검증하는 서비스
			PasswordEncoder passwordEncoder // 비밀번호를 암호화해서 비교하는 객체
	) throws Exception {

		// DaoAuthenticationProvider는 DB에 저장된 사용자 정보를 기반으로 아이디와 비밀번호를 인증하는 객체이다.
		// 이는 사용자 인증정보가 들어왔을때 userDetailsService를 기반으로 DB에서 사용자 정보를 조회하고
		// 해당 사용자 정보를 기반으로 비밀번호를 검증한다.
		DaoAuthenticationProvider authenticationProvider =
				new DaoAuthenticationProvider(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);

		http
				.authenticationProvider(authenticationProvider)
				// SpringSequrity가 권한없이 접근을 허용할 경로와 권한없이 접근을 차단할 경로를 지정한다.
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/",
								"/home",
								"/login",
								"/signup",
								"/css/**",
								"/js/**",
								"/images/**",
								"/favicon.ico",
								"/error"
						).permitAll() // 로그인하지 않아도 접근가능
						.anyRequest().authenticated() // 나머지 모든 경로들은 권한이 있어야 접근 가능
						/*
						 * 만약 권한없는 사용자가 /accounts에 접근하려고 하면 다음과 같은 순서로 동작한다.
						 * 1. Spring Sequrity가 인증정보가 없다는 것을 확인한다.
						 * 2. 원래 요청 /acocunts를 RequestCache에 저장한다.
						 * 3. /login 페이지로 리다이렉트한다.
						 * 4. 로그인 성공후 RequestCache에 저장했던 /accounts로 리다이렉트한다.
						*/

				)
				// login 페이지 설정 및, 입력받는 파라미터 정보등을 설정한다.
				// 이때 로그인 처리는 사용자가 입력한 userId를 기반으로 UserDetailService에서 DB에 저장된 사용자 정보를 꺼내,
				// 요청으로 들어온 파라미터와 DB에서 조회한 사용자 정보를 비교/검증한다.
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login") // /login에 대한 POST 요청을 Spring Sequrity의 로그인 필터가 처리한다.
						.usernameParameter("userId")
						.passwordParameter("password")
						.failureUrl("/login?error") // 로그인 실패 처리
						.permitAll()
				)
				// 로그아웃요청을 처리
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/") // 로그아웃 성공시 리다이렉트되는 경로
						.invalidateHttpSession(true) // 로그아웃시 SecurityContext에 저장되어 있던 사용자 정보를 제거해야 한다.
						.deleteCookies("JSESSIONID") // 브라우저에 저장 세션 식별자 쿠키도 제거한다.
				)
				// 서버 세션기반 로그인을 처리한다.
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
						.sessionFixation(fixation -> fixation.changeSessionId())
				);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
