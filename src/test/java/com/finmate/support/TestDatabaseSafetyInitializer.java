package com.finmate.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

// 테스트를 수행하기 전에, 통합 테스트가 실수로 Test DB가 아니라, 개발 DB에 연결되어 있지 않은지 확인하고, 위험하면 테스트 실행을 막는다.
public final class TestDatabaseSafetyInitializer
	// ApplicationContextInitializer는 Spring 애플리케이션 컨텍스트가 생성되기 전에 실행되는 초기화 지점이다.
	implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	/*
	* Spring 컨테이너 = ApplicationContext: Spring Bean을 실제로 생성하고 관리하는 실제 컨테이너
	*
	* Spring TestContext Framework: 테스트에 ApplicationContext를 생성·캐싱·관리하고 테스트 클래스와 연결하는 Spring 테스트 지원 체계
	*
	* 운영프로세스에서는 실제 애플리케이션이 실행되면 Spring Container, 즉 ApplicationContext가 생성되고 빈을 관리한다.
	*
	* 반면 테스트에서는 Spring TestContext가 추가로 개입하여 테스트용 ApplicationContext를 생성하여 빈을 관리하고, @Test를 실행하는 전체 테스트환경을 관리한다.
	* */
	// 스프링 테스트 컨텍스트가 만들어지는 시점에 호출된다. (아직 스프링 테스트 컨텍스트가 다 생성되지 않았을 수 있다.)
	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		Environment environment = applicationContext.getEnvironment(); // Environment는 Spring 설정값들을 조회할 수 있는 객체이다.
		// DataSource 설정정보를 조회
		String jdbcUrl = requiredProperty(environment, "spring.datasource.url");
		String username = requiredProperty(environment, "spring.datasource.username");
		String password = requiredProperty(environment, "spring.datasource.password");

		// 현재 연결되어 있는 DB가 Test DB인지, 개발 DB인지를 검증한다.
		assertContainerUrl(jdbcUrl);

		// 실제 Test DB에 연결한 다음, catalog를 확인한다.
		try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			String catalog = connection.getCatalog();
			// Catalog에서 데이터베이스 이름을 검증한다.
			if (!MySqlIntegrationTestSupport.DATABASE_NAME.equals(catalog)) {
				throw new IllegalStateException(
					"Refusing to run integration tests against catalog '" + catalog
						+ "'; expected '" + MySqlIntegrationTestSupport.DATABASE_NAME + "'.");
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Could not verify the isolated integration-test database.", exception);
		}
	}

	// 현재 연결된 JDBCUrl이 테스트DB의 JDBCUrl과 일치하는지 확인하고, 일치하지 않다면 예외를 발생시킨다.
	private static void assertContainerUrl(String jdbcUrl) {
		String expectedJdbcUrl = MySqlIntegrationTestSupport.expectedJdbcUrl();
		if (!expectedJdbcUrl.equals(jdbcUrl)) {
			throw new IllegalStateException(
				"Refusing to run integration tests with a datasource other than the dedicated Testcontainers database."
					+ " Configured URL: " + jdbcUrl);
		}

		String urlWithoutQuery = jdbcUrl.split("\\?", 2)[0];
		if (urlWithoutQuery.endsWith("/finmate") || !urlWithoutQuery.endsWith("/" + MySqlIntegrationTestSupport.DATABASE_NAME)) {
			throw new IllegalStateException("Refusing to run integration tests against the development database: " + jdbcUrl);
		}
	}
	// Environment에서 설정정보를 읽고, 설정정보가 없다면 예외를 발생시킨다.
	private static String requiredProperty(Environment environment, String propertyName) {
		String value = environment.getProperty(propertyName);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Required integration-test property is missing: " + propertyName);
		}
		return value;
	}
}
