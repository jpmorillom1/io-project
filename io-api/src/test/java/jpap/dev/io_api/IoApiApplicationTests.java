package jpap.dev.io_api;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class IoApiApplicationTests {

	@MockitoBean
	private EmbeddingStore<TextSegment> embeddingStore;

	@Test
	void contextLoads() {
	}

}
