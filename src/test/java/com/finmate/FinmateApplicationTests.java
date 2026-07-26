package com.finmate;

import com.finmate.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// @SpringBootTest는 실제 Spring Boot 애플리케이션을 실행하는 것과 비슷하게 Spring 컨테이너를 생성해서 테스트한다.
@SpringBootTest(properties = {
		// 테스트중에 랭킹 스케줄러가 실행되지 않도록 시간을 지연시킨다.
		"finmate.stock-ranking.initial-delay-millis=600000"
})
class FinmateApplicationTests extends MySqlIntegrationTestSupport {

	// @Test는 junit 애노테이션으로, Gradle에서 ./gradlew test를 실행하면, junit이 @test 애노테이션이 붙은 메서드들을 실행해서 테스트를 수행한다.
	// contextLoads 메서드는 스프링 컨테이너가 생성되면 실행되며, 스프링컨테이너가 성공적으로 생성되었는지를 검증한다.
	@Test
	@DisplayName("애플리케이션 컨텍스트는 격리된 테스트 MySQL로 정상 실행된다")
	void contextLoads() {
	}

}
