plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.11.0"
}

group = "com.jglearning"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	// access JWT(HS256) 발급·검증. Boot 4에서 이름이 바뀜(옛 spring-boot-starter-oauth2-resource-server는 deprecated)
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

spotless {
	java {
		target("src/**/*.java")
		palantirJavaFormat("2.103.0")
		removeUnusedImports()
		importOrder()
		trimTrailingWhitespace()
		endWithNewline()
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// openapi.json 재생성: ./gradlew test --tests '*OpenApiSpecTest' -PwriteOpenApi=true
tasks.test {
	// -P로 준 값만 받는다. findProperty는 gradle.properties·환경 변수도 읽어 쓰기 모드가 조용히 켜질 수 있다
	systemProperty("openapi.write", gradle.startParameter.projectProperties["writeOpenApi"] ?: "false")
	// 파일이 없어도 되도록 files(최초 생성 전). 커밋본이 바뀌면 테스트를 다시 돈다
	inputs.files("docs/api/openapi.json").withPropertyName("openapiSpec")
}
