package com.finmate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"finmate.stock-ranking.initial-delay-millis=600000"
})
class FinmateApplicationTests {

	@Test
	void contextLoads() {
	}

}
