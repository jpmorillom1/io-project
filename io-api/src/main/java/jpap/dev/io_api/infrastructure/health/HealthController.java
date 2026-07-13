package jpap.dev.io_api.infrastructure.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Chequeo de salud de las dependencias externas de la plataforma: PostgreSQL,
 * ChromaDB (vector store del RAG) y el LLM (Groq). Simple ping a cada una con
 * timeout corto — no valida lógica de negocio, solo si el servicio responde.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final int TIMEOUT_MS = 2000;

    private final DataSource dataSource;
    private final RestClient restClient;
    private final String chromaUrl;
    private final String llmBaseUrl;
    private final String llmApiKey;

    public HealthController(DataSource dataSource,
                             @Value("${app.chroma-url}") String chromaUrl,
                             @Value("${langchain4j.open-ai.chat-model.base-url}") String llmBaseUrl,
                             @Value("${langchain4j.open-ai.chat-model.api-key}") String llmApiKey) {
        this.dataSource = dataSource;
        this.chromaUrl = chromaUrl;
        this.llmBaseUrl = llmBaseUrl;
        this.llmApiKey = llmApiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        EstadoServicio baseDeDatos = revisarBaseDeDatos();
        EstadoServicio chromaDb = revisarChromaDb();
        EstadoServicio llm = revisarLlm();

        boolean todoArriba = baseDeDatos == EstadoServicio.UP
                && chromaDb == EstadoServicio.UP
                && llm == EstadoServicio.UP;
        EstadoServicio estadoGeneral = todoArriba ? EstadoServicio.UP : EstadoServicio.DOWN;

        log.info("[health] general={}, bd={}, chroma={}, llm={}", estadoGeneral, baseDeDatos, chromaDb, llm);

        HealthResponse respuesta = new HealthResponse(estadoGeneral, baseDeDatos, chromaDb, llm, Instant.now());
        return todoArriba
                ? ResponseEntity.ok(respuesta)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(respuesta);
    }

    private EstadoServicio revisarBaseDeDatos() {
        try (Connection conexion = dataSource.getConnection()) {
            return conexion.isValid(TIMEOUT_MS / 1000) ? EstadoServicio.UP : EstadoServicio.DOWN;
        } catch (SQLException e) {
            log.warn("[health] PostgreSQL no responde: {}", e.getMessage());
            return EstadoServicio.DOWN;
        }
    }

    private EstadoServicio revisarChromaDb() {
        try {
            restClient.get().uri(chromaUrl + "/api/v2/heartbeat").retrieve().toBodilessEntity();
            return EstadoServicio.UP;
        } catch (RestClientException e) {
            log.warn("[health] ChromaDB no responde: {}", e.getMessage());
            return EstadoServicio.DOWN;
        }
    }

    private EstadoServicio revisarLlm() {
        try {
            restClient.get()
                    .uri(llmBaseUrl + "/models")
                    .header("Authorization", "Bearer " + llmApiKey)
                    .retrieve()
                    .toBodilessEntity();
            return EstadoServicio.UP;
        } catch (RestClientException e) {
            log.warn("[health] LLM (Groq) no responde: {}", e.getMessage());
            return EstadoServicio.DOWN;
        }
    }
}
