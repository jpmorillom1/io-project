package jpap.dev.io_api.infrastructure.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class CorpusIngester {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final boolean reingestar;

    public CorpusIngester(EmbeddingStore<TextSegment> embeddingStore,
                          EmbeddingModel embeddingModel,
                          @Value("${app.rag.reingestar:true}") boolean reingestar) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.reingestar = reingestar;
    }

    @PostConstruct
    public void ingestar() throws IOException {
        if (!reingestar) {
            log.info("[RAG] app.rag.reingestar=false — ingesta omitida.");
            return;
        }

        log.info("[RAG] Iniciando ingesta del corpus...");

        // Limpiar colección para evitar duplicados al reiniciar
        embeddingStore.removeAll();
        log.info("[RAG] Colección limpiada.");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] recursos = resolver.getResources("classpath:corpus/**/*.md");

        if (recursos.length == 0) {
            log.warn("[RAG] No se encontraron archivos en classpath:corpus/. Ingesta omitida.");
            return;
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(
                350,  // chunks más pequeños → cada sección ## queda autocontenida
                30    // overlap reducido proporcionalmente
        );

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        for (Resource recurso : recursos) {
            String contenido = recurso.getContentAsString(StandardCharsets.UTF_8);
            Document documento = Document.from(contenido);
            ingestor.ingest(documento);
            log.info("[RAG] Ingestado: {}", recurso.getFilename());
        }

        log.info("[RAG] Ingesta completada — {} archivo(s) procesados.", recursos.length);
    }
}
