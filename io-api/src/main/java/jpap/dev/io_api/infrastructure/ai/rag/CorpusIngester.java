package jpap.dev.io_api.infrastructure.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class CorpusIngester {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final boolean reingestar;
    private final boolean incluirPdf;

    public CorpusIngester(EmbeddingStore<TextSegment> embeddingStore,
                          EmbeddingModel embeddingModel,
                          @Value("${app.rag.reingestar:true}") boolean reingestar,
                          @Value("${app.rag.incluir-pdf:true}") boolean incluirPdf) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.reingestar = reingestar;
        this.incluirPdf = incluirPdf;
    }

    @PostConstruct
    public void ingestar() throws IOException {
        if (!reingestar) {
            log.info("[RAG] app.rag.reingestar=false — ingesta omitida.");
            return;
        }

        log.info("[RAG] Iniciando ingesta del corpus...");
        embeddingStore.removeAll();
        log.info("[RAG] Colección limpiada.");

        ingestarMarkdown();

        if (incluirPdf) {
            ingestarPdf();
        } else {
            log.warn("[RAG] app.rag.incluir-pdf=false — PDF omitido. La colección contiene solo Markdown.");
        }
    }

    private void ingestarMarkdown() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] recursos = resolver.getResources("classpath:corpus/**/*.md");

        if (recursos.length == 0) {
            log.warn("[RAG] No se encontraron archivos .md en classpath:corpus/**/ — Markdown omitido.");
            return;
        }

        EmbeddingStoreIngestor ingestor = buildIngestor(DocumentSplitters.recursive(350, 30));

        for (Resource recurso : recursos) {
            String contenido = recurso.getContentAsString(StandardCharsets.UTF_8);
            Document documento = Document.from(contenido, Metadata.from("fuente", recurso.getFilename()));
            ingestor.ingest(documento);
            log.info("[RAG] MD ingestado: {}", recurso.getFilename());
        }

        log.info("[RAG] Markdown completado — {} archivo(s) procesados.", recursos.length);
    }

    private void ingestarPdf() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] recursos = resolver.getResources("classpath:corpus/**/*.pdf");

        if (recursos.length == 0) {
            log.warn("[RAG] No se encontraron archivos .pdf en classpath:corpus/**/ — PDF omitido.");
            return;
        }

        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        // Chunks más grandes para prosa académica densa (~140 tokens, dentro del límite 256 de AllMiniLM)
        EmbeddingStoreIngestor ingestor = buildIngestor(DocumentSplitters.recursive(700, 70));

        for (Resource recurso : recursos) {
            log.info("[RAG] Procesando PDF: {} ({} bytes)", recurso.getFilename(), recurso.contentLength());

            Document documento;
            try (InputStream is = recurso.getInputStream()) {
                documento = parser.parse(is);
            }

            String texto = documento.text();
            if (texto == null || texto.isBlank()) {
                log.error("[RAG] PDF '{}' no produjo texto extraíble — posible escaneo sin capa de texto. Omitido.",
                        recurso.getFilename());
                continue;
            }

            log.info("[RAG] PDF extraído: {} caracteres — {}", texto.length(), recurso.getFilename());
            log.debug("[RAG] Muestra: {}", texto.substring(0, Math.min(300, texto.length())).replace('\n', ' '));

            Document documentoConMeta = Document.from(texto, Metadata.from("fuente", recurso.getFilename()));
            ingestor.ingest(documentoConMeta);
            log.info("[RAG] PDF ingestado: {}", recurso.getFilename());
        }

        log.info("[RAG] PDF completado — {} archivo(s) procesados.", recursos.length);
    }

    private EmbeddingStoreIngestor buildIngestor(DocumentSplitter splitter) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }
}
