package com.finmate.service.news;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * multilingual-e5-small 모델을 Java 애플리케이션 안에서 실행해 문장을 임베딩 벡터로 변환한다.
 *
 * ONNX는 학습이 끝난 모델의 연산 그래프와 가중치를 저장하는 공통 파일 형식이고,
 * ONNX Runtime은 그 파일을 Python 추론 서버 없이 현재 JVM 프로세스에서 실행하는 엔진이다.
 * 이 클래스의 변환 흐름은 다음과 같다
 *
 * 생성 비용이 큰 모델과 토크나이저는 lazy loading를 통해 최초 사용 시 한 번만 로딩하고,
 * 이후 뉴스 캐시 갱신에서는 같은 ONNX 세션을 재사용한다.
 *
 * 기본적으로 파이썬에서는 파이토치로 구현된 모델을 바로 사용할 수 있다.(파이토치가 파이썬 라이브러리이기 때문에)
 * 하지만 자바에서는 파이토치로 구현된 모델을 바로 사용할 수 없다.
 * 따라서 파이토치모델을 다른 언어/프레임워크에서 사용할 수 있도록 Onnx 모델이라는 공통 포멧으로 변환한다.
 * 따라서 자바/스프링에서도 Onnx모델을 통해 모델을 실행할 수 있게 된다.
 *
 * 전체 실행 흐름:
 * 1. download-e5-small-model.sh가 model.onnx와 tokenizer.json을 models 디렉터리에 준비한다.
 * 2. Spring이 이 컴포넌트를 처음 필요로 할 때 생성자가 파일 경로를 읽는다.
 * 3. tokenizer가 문자열을 토큰 ID와 마스크로 바꾼다.
 * 4. Java 배열을 ONNX 텐서로 바꾸고 session.run(...)으로 모델을 실행한다.
 * 5. 토큰별 출력 벡터를 평균 내고 정규화하여 문장당 float[] 하나를 반환한다.
 */
// @Lazy가 없으면 애플리케이션 시작 중 모델 파일을 읽는다. @Lazy를 사용하면 실제 임베딩 전략이
// 이 객체를 요구할 때 생성하므로, 다른 랭킹 전략을 사용하는 실행에서는 E5 모델을 로딩하지 않는다.
@Lazy
@Component
// AutoCloseable은 이 객체가 close()로 정리할 자원을 가진다는 Java 표준 계약이다.
// Spring은 @PreDestroy가 붙은 close()를 애플리케이션 종료 시 호출한다.
public class E5EmbeddingModel implements AutoCloseable {
    // 모델 입력이 지나치게 길어지는 것을 막고 E5 계열의 최대 입력 길이에 맞춘다.
    private static final int MAX_TOKEN_LENGTH = 512;

    // ONNX Runtime 실행 환경이다. 네이티브 ONNX 엔진과 Java 코드를 연결하고 세션 및 텐서를
    // 생성할 기반을 제공한다. getEnvironment()가 반환하는 프로세스 공용 singleton을 참조한다.
    private final OrtEnvironment environment;

    // 특정 model.onnx를 읽어 추론 가능한 상태로 준비한 객체다. 모델 그래프와 가중치가 연결되어
    // 있으며 생성 비용이 크므로 embed()를 호출할 때마다 만들지 않고 같은 세션을 재사용한다.
    private final OrtSession session;

    // 자연어를 모델이 이해하는 숫자로 바꾸는 전처리기다. tokenizer.json에 저장된 어휘와
    // 분리 규칙을 사용해 input_ids, attention_mask, token_type_ids를 만든다.
    // 모델 학습 때와 같은 토크나이저를 써야 토큰 ID의 의미가 일치한다.
    private final HuggingFaceTokenizer tokenizer;

    public E5EmbeddingModel(
            // @Value는 application.properties의 설정을 주입한다. 환경변수가 없을 때는 콜론 뒤의
            // models/... 기본 경로를 사용하며, Docker/EC2에서는 컨테이너 내부 경로로 바꿀 수 있다.
            @Value("${finmate.news.embedding.model-path:models/multilingual-e5-small/model.onnx}") String modelPath,
            @Value("${finmate.news.embedding.tokenizer-path:models/multilingual-e5-small/tokenizer.json}") String tokenizerPath) {
        // 상대 경로와 Docker 마운트 경로를 정규화하고 실제 파일인지 먼저 검증한다.
        Path resolvedModelPath = requireRegularFile(modelPath, "E5 ONNX 모델");
        Path resolvedTokenizerPath = requireRegularFile(tokenizerPath, "E5 토크나이저");

        try {
            // 환경은 ONNX Runtime을 사용할 수 있게 하는 공용 기반이고,
			// 세션은 그 환경 위에 특정 model.onnx를 올린 실제 추론 단위다.
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession( // environment를 기반으로 ONNX 세션을 생성한다.
                    resolvedModelPath.toString(), // model.onnx를 ONNX Runtime이 읽고, 모델 구조를 파악하고, 가중치를 로딩해서 모델을 추론할 준비를 마친다.
                    // 기본 SessionOptions를 사용하므로 현재 ONNX Runtime이 선택한 기본 CPU 실행 및
                    // 그래프 최적화 설정을 따른다. 향후 실행 provider나 스레드 옵션도 여기서 설정한다.
                    new OrtSession.SessionOptions());

            // 토크나이저는 모델 자체가 아니며 추론도 하지 않는다. 문자열을 학습 당시와 같은
            // 규칙으로 토큰화하고 특수 토큰 추가 및 최대 512개 토큰 절단을 담당한다.
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(resolvedTokenizerPath) // tokenizer.json 파일을 읽는다.
                    .optAddSpecialTokens(true) // 토크나이저가 필요로 하는 특수 토큰을 추가한다.
                    .optTruncation(true) //최대 512토큰을 넘어가면 초과하는 토큰은 자른다.
                    .optMaxLength(MAX_TOKEN_LENGTH) // 최대 토큰 길이를 설정한다.
                    .build();
        } catch (OrtException | IOException e) {
            throw new IllegalStateException("E5-small 모델을 초기화할 수 없습니다.", e);
        }
    }

    // 실제 텍스트 리스트를 받아, 임베딩을 하여, 임베딩 벡터 리스트를 리턴한다.
	// 이때 여러 스레드가 임베딩모델을 호출하면 자원사용량이 증가하기 때문에 여러 스레드들이 모델을 동시에 호출하는 것을 제한하기 위해 synchronized를 붙인다.
    public synchronized List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        // batchEncode는 여러 문자열을 한 번에 토큰으로 변환하지만, 아직 아직 임베딩 모델을 실행하지는 않아 임베딩된 상태는 아니다.
        // 반환된 Encoding마다 토큰 ID, attention mask, type ID가 들어 있다. 단 아직은 패딩이 추가되지 않았기 때문에 attention mask값이 큰 의미가 없다.
        Encoding[] encodings = tokenizer.batchEncode(texts);
        // 여러 문장을 한 번의 session.run()으로 처리하려면 [문장 수][토큰 수] 형태의 행렬이 필요하므로, 가장 긴 문장 길이에 맞춘 배치 입력을 만든다.
		// 배치 입력을 만드는 과정에서 패딩토큰이 추가되면서 attention mask가 효과가 있기 시작한다.
        BatchInputs batchInputs = createBatchInputs(encodings);

        // OnnxTensor는 Java 배열을 ONNX Runtime이 읽을 수 있는 텐서로 감싼다. 즉 long[][] 배열을 ONNX Runtime이 읽을 수 있는 텐서로 감싼다.
        // GC만 기다리면 네이티브 메모리 반환 시점이 늦어질 수 있으므로 요청 단위 텐서와 추론 결과는 try-with-resources로 사용 직후 닫는다.
        try (OnnxTensor inputIds = OnnxTensor.createTensor(environment, batchInputs.inputIds());
             OnnxTensor attentionMask = OnnxTensor.createTensor(environment, batchInputs.attentionMask());
             OnnxTensor tokenTypeIds = OnnxTensor.createTensor(environment, batchInputs.tokenTypeIds())) {
			// 실제 ONNX 모델이 받을 입력 Map을 만든다.
			// 즉 token ids, attention mask, type id를 각각 Map으로 매핑해서 모델에 입력으로 넣어줄 input map을 생성한다.
            Map<String, OnnxTensor> inputs = createSessionInputs(inputIds, attentionMask, tokenTypeIds);

            // session.run(...)부터가 실제 신경망 추론이다. 입력 텐서를 모델 그래프에 통과시켜 출력 텐서를 만들고, 이를 정규화한다.
            try (OrtSession.Result result = session.run(inputs)) {
                return extractNormalizedEmbeddings(result, batchInputs.attentionMask());
            }
        } catch (OrtException e) {
            throw new IllegalStateException("E5-small 임베딩 추론에 실패했습니다.", e);
        }
    }

    // ONNX 모델은 한 배치를 직사각형 행렬로 받으므로 가장 긴 문장에 길이를 맞춘다.
    // Java long 배열의 기본값 0이 PAD 토큰과 attention 대상에서 제외될 빈 자리로 남는다.
    private BatchInputs createBatchInputs(Encoding[] encodings) {
        // input 토큰 리스트들중, 최대 길이 토큰 리스트를 찾는다.
		int maxLength = 0;
        for (Encoding encoding : encodings) {
            maxLength = Math.max(maxLength, encoding.getIds().length);
        }

		// 모든 배열의 크기를 문장 개수 x 최대길이로 맞춘다. 이때 자바 배열은 처음 생성시 모든 값이 0으로 채워진다.
        long[][] inputIds = new long[encodings.length][maxLength]; // 각 토큰을 숫자로 표현한 실제 모델 입력
        long[][] attentionMask = new long[encodings.length][maxLength]; // 실제 토큰과 padding 토큰을 구분하는 attention mask
        long[][] tokenTypeIds = new long[encodings.length][maxLength]; // 문장 segment를 구분한다. 문장 A: 0, 문장 B: 1

        for (int index = 0; index < encodings.length; index++) { // 모든 문장을 하나씩 순회한다.
            Encoding encoding = encodings[index]; // 인코딩된 문장을 하나 꺼낸다.
			// 각 문장이 인코딩된 Encoding 객체에는 토큰 ID, attention mask, type ID가 들어있다.

			// 토크나이저가 만든 토큰 ID 배열을 batchInput 배열에 복사한다.
			// 이렇게 배열에 복사할때 남는길이 만큼은 기존의 0이 채워지면서 자연스럽게 패딩이 추가되는 효과가 나타난다.
            System.arraycopy(encoding.getIds(), 0, inputIds[index], 0, encoding.getIds().length);

			// 토크나이저가 만든 attention mask 배열을 batchInput 배열에 복사한다.
            System.arraycopy(encoding.getAttentionMask(), 0, attentionMask[index], 0,
                    encoding.getAttentionMask().length);

			// 토크나이저가 만든 type ID 배열을 batchInput 배열에 복사한다.
            System.arraycopy(encoding.getTypeIds(), 0, tokenTypeIds[index], 0,
                    encoding.getTypeIds().length);
        }
        return new BatchInputs(inputIds, attentionMask, tokenTypeIds);
    }

    // input_ids는 토큰 번호, attention_mask는 실제 토큰(1)과 패딩(0)의 구분,
    // token_type_ids는 문장 구간 번호다. 모델마다 세 번째 입력 유무가 다르므로
    // 세션이 model.onnx에서 읽은 실제 입력 이름에 맞는 텐서만 전달한다.
    private Map<String, OnnxTensor> createSessionInputs(OnnxTensor inputIds,
                                                        OnnxTensor attentionMask,
                                                        OnnxTensor tokenTypeIds) {
        // 세션이 로딩한 ONNX 그래프의 입력 이름을 직접 조회한다. 모델 export 방식에 따라
        // token_type_ids가 생략될 수 있으므로 이름을 하드코딩해 무조건 전달하지 않는다.
        Set<String> inputNames = session.getInputNames();
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", inputIds);
        inputs.put("attention_mask", attentionMask);
        if (inputNames.contains("token_type_ids")) { // 특정 ONNX 그래프는 token_type_ids가 필요할 수도/ 필요하지 않을 수도 있기 떄문에 정책에 맞게 추가한다.
            inputs.put("token_type_ids", tokenTypeIds);
        }
        return inputs;
    }

    // ONNX 임베딩 모델이 출력된 임베딩 벡터를 정규화한다.
    private List<float[]> extractNormalizedEmbeddings(OrtSession.Result result,
                                                       long[][] attentionMask) throws OrtException {
        // 이름이 보존된 모델은 last_hidden_state로 찾고, 이름이 다르거나 사라진 export 결과는
        // 첫 번째 출력으로 보완한다. 값은 배치 크기와 출력 방식에 따라 2차원 또는 3차원이다.
        OnnxValue output = result.get("last_hidden_state")
                .orElseGet(() -> result.get(0));
        Object value = output.getValue();

		// ONNX 모델이 출력한 임베딩벡터가 3차원일수도 있고, 2차원일수도 있다.
        // [문장][토큰][벡터 차원]: 각 토큰마다 벡터가 나온 경우 평균 pooling이 필요하다.
		// 즉 문장 하나당 임베딩벡터 하나가 아니라, 문장 내 토큰 하나하나 마다 임베딩 벡터가 생성된 경우에는 평균 pooling을 통해 문장 하나당 하나의 임베딩 벡터를 생성한다.
        if (value instanceof float[][][] tokenEmbeddings) {
            return averagePoolAndNormalize(tokenEmbeddings, attentionMask);
        }
        // [문장][벡터 차원]: ONNX 그래프가 문장 pooling까지 수행해 반환한 경우다.
		// 이 경우에는 문장 하나당 임베딩벡터가 하나이므로, 각 문장베겉만 정규화해서 리턴한다.
        if (value instanceof float[][] sentenceEmbeddings) {
            ArrayList<float[]> normalized = new ArrayList<>(sentenceEmbeddings.length);
            for (float[] sentenceEmbedding : sentenceEmbeddings) {
                normalized.add(normalize(sentenceEmbedding));
            }
            return List.copyOf(normalized);
        }
        throw new IllegalStateException("지원하지 않는 E5-small ONNX 출력 형식입니다: " + value.getClass());
    }

    // 패딩 토큰을 제외한 토큰 벡터의 평균을 문장 벡터로 사용하고 cosine 비교를 위해 정규화한다.
	// 즉 토큰별 임베딩벡터를 평균(average Pooling)내고, 그 결과를 정규화한다.
    private List<float[]> averagePoolAndNormalize(float[][][] tokenEmbeddings,
                                                  long[][] attentionMask) {
		// 모든 문장별 임베딩벡터를 저장할 list를 생성한다.
        ArrayList<float[]> embeddings = new ArrayList<>(tokenEmbeddings.length);
		// 문장을 하나씩 순회한다.
        for (int batchIndex = 0; batchIndex < tokenEmbeddings.length; batchIndex++) {
            int dimension = tokenEmbeddings[batchIndex][0].length; // 임베딩벡터의 차원을 계산한다.
            float[] pooled = new float[dimension]; // 각 문장별 pooling된 결과를 저장할 임베딩벡터를 생성한다.
            int validTokenCount = 0;
            for (int tokenIndex = 0; tokenIndex < tokenEmbeddings[batchIndex].length; tokenIndex++) {
                // attention_mask가 0인 위치는 길이를 맞추기 위해 추가한 PAD 자리이므로 문장의 의미 벡터 평균에 포함하지 않는다.
                if (attentionMask[batchIndex][tokenIndex] == 0L) {
                    continue;
                }

				// 토큰 벡터를 dimension 인덱스별로 더한다.
                validTokenCount++;
                for (int dimensionIndex = 0; dimensionIndex < dimension; dimensionIndex++) {
                    pooled[dimensionIndex] += tokenEmbeddings[batchIndex][tokenIndex][dimensionIndex];
                }
            }
			// 각 dimension 인덱스별로 validTokenCount수로 나눠 평균 벡터를 생성한다.
            if (validTokenCount > 0) {
                for (int dimensionIndex = 0; dimensionIndex < dimension; dimensionIndex++) {
                    pooled[dimensionIndex] /= validTokenCount;
                }
            }
			// average pooling을 수행한 벡터를 정규화해서 리스트에 추가한다.
            embeddings.add(normalize(pooled));
        }
        return List.copyOf(embeddings);
    }

    // 단위 벡터로 L2 정규화해 이후 내적만으로 cosine similarity를 계산할 수 있게 한다.
	// 임베딩벡터는 크기보다는 방향이 중요하기 때문에 벡터의 크기를 정규화한다.
    private float[] normalize(float[] embedding) {
        double squaredNorm = 0.0;
		// 임베딩벡터의 L2 norm을 계산한다.
        for (float value : embedding) {
            squaredNorm += value * value;
        }
        // L2 norm = sqrt(x1^2 + x2^2 + ... + xn^2)이다.
        double norm = Math.sqrt(squaredNorm);
        if (norm == 0.0) {
            return embedding;
        }

        // 각 원소를 벡터 길이로 나누면 길이가 1인 단위 벡터가 된다. 이후 두 단위 벡터의
        // 내적은 cosine similarity와 같아져 뉴스 유사도 계산이 단순해진다.
        for (int index = 0; index < embedding.length; index++) {
            embedding[index] /= (float) norm;
        }
        return embedding;
    }

    // 잘못된 로컬 경로나 누락된 Docker 볼륨을 초기화 시점에 명확한 오류로 알린다.
    private Path requireRegularFile(String path, String description) {
        Path resolvedPath = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isRegularFile(resolvedPath)) {
            throw new IllegalStateException(description + " 파일을 찾을 수 없습니다: " + resolvedPath);
        }
        return resolvedPath;
    }

    // 장기간 살아 있던 네이티브 자원을 Spring 빈의 생명주기 종료와 함께 반환한다.
    // 세션은 반드시 닫아 모델 관련 자원을 해제한다. getEnvironment()가 반환한 환경은
    // 프로세스 전역 singleton이고 현재 ONNX Runtime에서는 close()가 no-op이므로 따로 닫지 않는다.
    @Override
    @PreDestroy
    public void close() {
        tokenizer.close();
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("E5-small ONNX 세션을 종료할 수 없습니다.", e);
        }
    }

    // ONNX 세션에 전달할 세 입력 행렬을 하나의 불변 값으로 묶는다.
    // record는 필드와 접근자(inputIds() 등)를 자동 생성하며 생성 후 참조를 바꿀 수 없다.
    private record BatchInputs(long[][] inputIds, long[][] attentionMask, long[][] tokenTypeIds) {
    }
}
