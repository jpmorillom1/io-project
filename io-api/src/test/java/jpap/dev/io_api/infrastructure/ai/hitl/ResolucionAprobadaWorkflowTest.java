package jpap.dev.io_api.infrastructure.ai.hitl;

import jpap.dev.io_api.application.lp.DosFasesService;
import jpap.dev.io_api.application.lp.GraficoService;
import jpap.dev.io_api.application.lp.GranMService;
import jpap.dev.io_api.application.lp.SimplexService;
import jpap.dev.io_api.application.transporte.TransporteService;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.infrastructure.ai.dto.SolicitudAprobacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba end-to-end del Human-in-the-Loop SIN LLM ni Spring: el workflow agéntico
 * (compuerta HumanInTheLoop + acción resolutora) se construye igual que en producción
 * y la decisión humana se simula completando el PendingResponse vía el servicio.
 *
 * Garantía verificada: el solver NO se ejecuta hasta que llega la aprobación,
 * y NUNCA se ejecuta si el humano rechaza.
 */
class ResolucionAprobadaWorkflowTest {

    private SolicitudAprobacionRegistry registry;
    private AprobacionHumanaService service;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        registry = new SolicitudAprobacionRegistry();
        ResolucionEjecutor ejecutor = new ResolucionEjecutor(
                new SimplexService(), new GranMService(), new DosFasesService(),
                new GraficoService(), new TransporteService());
        ResolucionAprobadaWorkflow workflow = new HitlConfig().resolucionAprobadaWorkflow(ejecutor, registry);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        service = new AprobacionHumanaService(workflow, registry, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /** MAX Z = 3x1 + 5x2, s.a. x1 ≤ 4, 2x2 ≤ 12, 3x1 + 2x2 ≤ 18 → Z* = 36. */
    private ModeloLP modeloClasico() {
        return new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(3.0, 5.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 0.0), TipoRestriccion.LEQ, 4),
                        new Restriccion(List.of(0.0, 2.0), TipoRestriccion.LEQ, 12),
                        new Restriccion(List.of(3.0, 2.0), TipoRestriccion.LEQ, 18)
                )
        );
    }

    @Test
    void elSolverNoCorreHastaQueElHumanoApruebe() throws InterruptedException {
        SolicitudAprobacion solicitud = service.solicitar("sesion-1", modeloClasico(), MetodoResolucion.SIMPLEX);

        // El workflow está bloqueado en la compuerta: no hay resultado todavía
        Thread.sleep(300);
        var enVuelo = registry.obtener(solicitud.solicitudId()).orElseThrow();
        assertNull(enVuelo.resultado(), "el solver no debe ejecutarse antes de la aprobación");
        assertFalse(enVuelo.ejecucion().isDone(), "el workflow debe seguir esperando la decisión humana");
    }

    @Test
    void aprobarEjecutaElSolverYDevuelveElResultado() {
        SolicitudAprobacion solicitud = service.solicitar("sesion-2", modeloClasico(), MetodoResolucion.SIMPLEX);

        var desenlace = service.decidir(solicitud.solicitudId(), true, null);

        assertTrue(desenlace.aprobado());
        assertNotNull(desenlace.ejecucion(), "una aprobación debe traer la ejecución del solver");
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultado().status());
        assertEquals(36.0, desenlace.ejecucion().resultado().solution().valorOptimo(), 1e-6);
        assertTrue(desenlace.resumenParaTutor().contains("RESULTADO DEL SOLVER"));

        // La solicitud se limpió del registro al decidirse
        assertTrue(registry.obtener(solicitud.solicitudId()).isEmpty());
    }

    @Test
    void rechazarNoEjecutaNingunSolver() {
        SolicitudAprobacion solicitud = service.solicitar("sesion-3", modeloClasico(), MetodoResolucion.SIMPLEX);

        var desenlace = service.decidir(solicitud.solicitudId(), false, "la ganancia de x1 está mal");

        assertFalse(desenlace.aprobado());
        assertNull(desenlace.ejecucion(), "un rechazo jamás debe ejecutar el solver");
        assertTrue(desenlace.resumenParaTutor().contains("RECHAZÓ"));
        assertTrue(desenlace.resumenParaTutor().contains("la ganancia de x1 está mal"));
        assertTrue(registry.obtener(solicitud.solicitudId()).isEmpty());
    }

    @Test
    void aprobarConMetodoGraficoDevuelveResultadoGrafico() {
        SolicitudAprobacion solicitud = service.solicitar("sesion-4", modeloClasico(), MetodoResolucion.GRAFICO);

        var desenlace = service.decidir(solicitud.solicitudId(), true, null);

        assertNull(desenlace.ejecucion().resultado(), "el método gráfico no produce resultado tabular");
        assertNotNull(desenlace.ejecucion().resultadoGrafico());
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultadoGrafico().status());
        assertEquals(36.0, desenlace.ejecucion().resultadoGrafico().solution().valorOptimo(), 1e-6);
    }

    @Test
    void aprobarConMetodoTransporteDevuelveResultadoTransporte() {
        ModeloTransporte modelo = new ModeloTransporte(
                List.of("O1", "O2", "O3"),
                List.of("D1", "D2", "D3"),
                List.of(20.0, 30.0, 25.0),
                List.of(30.0, 25.0, 20.0),
                List.of(
                        List.of(4.0, 6.0, 8.0),
                        List.of(6.0, 4.0, 2.0),
                        List.of(2.0, 8.0, 6.0)),
                MetodoTransporte.MODI);

        SolicitudAprobacion solicitud = service.solicitar("sesion-t", modelo, MetodoResolucion.TRANSPORTE);
        var desenlace = service.decidir(solicitud.solicitudId(), true, null);

        assertNull(desenlace.ejecucion().resultado(), "transporte no produce resultado tabular LP");
        assertNull(desenlace.ejecucion().resultadoGrafico());
        assertNotNull(desenlace.ejecucion().resultadoTransporte());
        assertEquals(240.0, desenlace.ejecucion().resultadoTransporte().solution().costoTotal(), 1e-6);
        assertTrue(desenlace.resumenParaTutor().contains("Transporte"));
    }

    @Test
    void unaNuevaSolicitudDeLaMismaSesionReemplazaLaAnterior() {
        SolicitudAprobacion primera = service.solicitar("sesion-5", modeloClasico(), MetodoResolucion.SIMPLEX);
        SolicitudAprobacion segunda = service.solicitar("sesion-5", modeloClasico(), MetodoResolucion.DOS_FASES);

        assertTrue(registry.obtener(primera.solicitudId()).isEmpty(), "la solicitud previa debe abortarse");
        assertTrue(registry.obtener(segunda.solicitudId()).isPresent());

        // Decidir sobre la solicitud abortada debe fallar con 400 (IllegalArgumentException)
        assertThrows(IllegalArgumentException.class,
                () -> service.decidir(primera.solicitudId(), true, null));

        // La nueva sigue siendo decidible
        var desenlace = service.decidir(segunda.solicitudId(), true, null);
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultado().status());
    }

    @Test
    void decidirSobreUnaSolicitudInexistenteFalla() {
        assertThrows(IllegalArgumentException.class,
                () -> service.decidir("no-existe", true, null));
    }
}
