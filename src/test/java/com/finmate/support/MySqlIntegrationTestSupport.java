package com.finmate.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// 해당 테스트를 실행할때에는 application-testcontainers.yml 설정파이릉ㄹ 읽는다.
@ActiveProfiles("testcontainers")
// Spring 테스트 애플리케이션 컨텍스트를 초기화할 때 TestDatabaseSafetyInitializer가 먼저 실행되도록 설정한다.
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
public abstract class MySqlIntegrationTestSupport {

	// 테스트용 데이터베이스와 User를 설정한다. (테스트 DB 접속 정보)
	static final String DATABASE_NAME = "finmate_test";
	static final String USERNAME = "finmate_test_user";
	static final String PASSWORD = "finmate_test_password";

	// MySQLContainer는 TestContainer가 제공하는 MYSQL 전용 컨테이너로, mysql:8.4라는 도커이미지를 기반으로 컨테이너관련 설정
	// 이때 MYSQL 컨테이너는 static final로 만듦으로서 테스트내부에서 공유하도록 구성한다.
	/*
	* Docker 이미지: mysql:8.4
	* DB 이름: finmate_test
	* 사용자명: finmate_test_user
	* 비밀번호: finmate_test_password
	* 재사용 설정: 비활성화
	* 와 같은 컨테이너 설정정보를 등록한다.
	* */
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
		// 컨테이너 설정
		.withDatabaseName(DATABASE_NAME)
		.withUsername(USERNAME)
		.withPassword(PASSWORD)
		.withReuse(false); // 테스트를 새로 실행할때마다 컨테이너를 재사용하지 않고, 매 테스트마다 컨테이너를 새로 생성한다.

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// MySqlIntegrationTestSupport가 JVM에 처음 로딩될 MYSQL 컨테이너를 실행한다.
	static {
		// MYSQL.start()를 호출하면 TestContainers가 로컬 또는 Runners의 도커에 연결한다.
		// 해당 도커를 기반으로 컨테이너 설정정보를 기반으로 이미지를 빌드하고, 컨테이너를 생성 및 실행한다.
		// 임의의 호스트 포트 컨테이너의 3306 포트에 연결한다.
		// 이때 Ryuk이라는 리소스 정리용 컨테이너를 함께 실행한다.
		MYSQL.start();
		// test코드가 종료되어 테스트 JVM이 종료될때 Ryuk 컨테이너가 실행되고 있든 TestContainers의 실행중이던 컨테이너들을 정리한다.
	}

	// 컨테이너 접속 정보를 Spring Environment에 동적으로 등록하며, 이는 application-testcontainers.properties보다 우선적으로 적용된다.
	// Spring Environment에 등적으로 등록한 설정정보는 Test가 종료되면 초기화된다.
	@DynamicPropertySource
	static void configureDatasource(DynamicPropertyRegistry registry) {
		// 테스트를 매번 실행할때마다 localhost의 port번호,  url이 달라지기 때문에 매 컨테이너를 실행할때마다 설정정보를 동적으로 넣어줘야한다.
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName); // MySQL JDBC driver 정보를 설정한다.
	}

	// 현재 TestContrainers MYSQL JDBCUrl을 반환한다.
	static String expectedJdbcUrl() {
		return MYSQL.getJdbcUrl();
	}

	@BeforeEach
	void clearTestDatabase() {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			List<String> tableNames = new ArrayList<>();
			try (PreparedStatement statement = connection.prepareStatement("""
					select table_name
					from information_schema.tables
					where table_schema = ?
					  and table_type = 'BASE TABLE'
					""")) {
				statement.setString(1, DATABASE_NAME);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						tableNames.add(resultSet.getString(1));
					}
				}
			}

			try (Statement statement = connection.createStatement()) {
				statement.execute("set foreign_key_checks = 0");
				try {
					for (String tableName : tableNames) {
						if (!tableName.matches("[A-Za-z0-9_]+")) {
							throw new IllegalStateException("Unsafe test table name: " + tableName);
						}
						statement.executeUpdate("truncate table `" + tableName + "`");
					}
				} finally {
					statement.execute("set foreign_key_checks = 1");
				}
			}
			return null;
		});
	}
}
