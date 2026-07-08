package jpap.dev.io_api.infrastructure.ai.tools;

import jpap.dev.io_api.application.entera.EnteraService;
import jpap.dev.io_api.application.lp.DosFasesService;
import jpap.dev.io_api.application.lp.GraficoService;
import jpap.dev.io_api.application.lp.GranMService;
import jpap.dev.io_api.application.lp.SimplexService;
import jpap.dev.io_api.application.redes.RedService;
import jpap.dev.io_api.application.transporte.TransporteService;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.hitl.HitlConfig;
import jpap.dev.io_api.infrastructure.ai.hitl.ResolucionAprobadaWorkflow;
import jpap.dev.io_api.infrastructure.ai.hitl.ResolucionEjecutor;
import jpap.dev.io_api.infrastructure.ai.hitl.SolicitudAprobacionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Candado anti-bucle: llama-3.3 a temperatura 0 tiende a repetir la misma tool call
 * tras recibir el resultado. La segunda invocación de una tool de resolución en el
 * MISMO turno no debe crear otra solicitud HITL — debe devolver un mensaje de stop.
 */
class SolicitudAprobacionHelperTest {

    private ChatContextStore contextStore;
    private RedTool redTool;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        SolicitudAprobacionRegistry registry = new SolicitudAprobacionRegistry();
        ResolucionEjecutor ejecutor = new ResolucionEjecutor(
                new SimplexService(), new GranMService(), new DosFasesService(),
                new GraficoService(), new TransporteService(), new RedService(),
                new EnteraService(), new jpap.dev.io_api.application.inventario.InventarioService());
        ResolucionAprobadaWorkflow workflow = new HitlConfig().resolucionAprobadaWorkflow(ejecutor, registry);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        AprobacionHumanaService service = new AprobacionHumanaService(workflow, registry, executor);
        contextStore = new ChatContextStore();
        redTool = new RedTool(service, contextStore);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        contextStore.limpiar();
    }

    private String invocarResolverRed() {
        return redTool.resolverRed(
                List.of("S", "A", "T"),
                List.of(new RedTool.AristaInput("S", "A", null, 10.0, null),
                        new RedTool.AristaInput("A", "T", null, 5.0, null)),
                true, MetodoRed.EDMONDS_KARP, "S", "T");
    }

    @Test
    void la_segunda_invocacion_en_el_mismo_turno_no_crea_otra_solicitud() {
        contextStore.iniciar("sesion-test");

        String primera = invocarResolverRed();
        assertTrue(primera.contains("SOLICITUD DE APROBACIÓN ENVIADA"),
                "la primera invocación crea la solicitud normalmente");
        var solicitudOriginal = contextStore.obtener().solicitudAprobacion;
        assertNotNull(solicitudOriginal);

        String segunda = invocarResolverRed();
        assertTrue(segunda.contains("YA FUE ENVIADA"),
                "la repetición debe devolver el mensaje de stop, no crear otra solicitud");
        assertEquals(solicitudOriginal.solicitudId(),
                contextStore.obtener().solicitudAprobacion.solicitudId(),
                "la solicitud del turno debe seguir siendo la primera");
    }
}
