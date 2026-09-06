package com.finmate.evaluation.news;

import java.util.Arrays;
import java.util.List;

/**
 * 평가 실행에 필요한 환경변수를 읽는다.
 *
 * 평가 프로그램은 Spring Boot를 실행하지 않고 순수 자바로 실행되기 때문에 @Value를 통해 환경변수를 바로 읽어오는게 불가능하다.
 * 대신 gradle task가 .env에 설정된 환경변수들을 평가용 JVM에 환경변수로 전달하면, System.getenv()와 같은 방식으로 직접 환경변수를 읽어야 한다.
 *
 * gradle task는 평가용 JVM을 생성할 때 .env파일을 읽고, 해당 파일안에 작성된 환경변수값들을 JVM 프로세스에 전달한다.
 *
 * .env -> Docker compose, IDE등이 읽어 환경변수로 등록 -> 자바 프로그램 실행용 JVM
 * 이때 .env에 설정한 환경변수는 로컬에 저장되는 것은 아니며, JVM 프로세스를 실행할때 함께 전달할 환경변수를 저장하는 파일이다.
 */
final class EvaluationEnvironment {

    // 모든 기능이 static인 유틸리티 클래스이므로 객체를 만들지 못하게 생성자를 private으로 생성한다. 이렇게되면 해당 클래스는 객체 생성이 불가능하다.
    private EvaluationEnvironment() {
    }

    // API key처럼 반드시 필요한 값에 사용한다. null뿐 아니라 공백 문자열도 누락으로 처리한다.
    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("평가 환경변수가 필요합니다: " + name);
        }
        return value.trim();
    }

    // 선택 설정에 사용한다. 환경변수가 없으면 코드에 정의된 안전한 기본값을 반환한다.
    static String optional(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    // 문자열 환경변수를 double로 변환한다. 잘못된 숫자를 조용히 기본값으로 바꾸지 않고 즉시 알린다.
    static double optionalDouble(String name, double defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("숫자 형식의 평가 환경변수가 필요합니다: " + name, e);
        }
    }

    /**
     * 쉼표로 구분한 임계값 목록을 읽어 evaluation threshold sweep에 사용한다.
     * 환경 변수에 0.20, 0.30과 같으 쉼표 단위로 임계값이 나눠져 있기 때문에, 쉼표단위로 나눠 임계값을 추출한다.
     */
    static List<Double> optionalDoubleList(String name, List<Double> defaultValues) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return List.copyOf(defaultValues);
        }
        try {
            List<Double> values = Arrays.stream(value.split(",")) // 쉼표 단위로 문자열을 나눈다.
                    .map(String::trim)
                    .filter(token -> !token.isBlank())
                    .map(Double::parseDouble) // 문자열로 된 임계값을 double형으로 변환한다.
                    .distinct() // 같은 임계값을 중복 실행하지 않는다. 따라서 중복을 제거한다.
                    .toList();
            if (values.isEmpty()) {
                throw new IllegalArgumentException("임계값 목록이 비어 있습니다.");
            }
            return values;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "쉼표로 구분한 숫자 목록 형식의 평가 환경변수가 필요합니다: " + name,
                    e);
        }
    }
}
