package jpap.dev.io_api.infrastructure.ai.hitl;

import jpap.dev.io_api.application.dinamica.DinamicaService;
import jpap.dev.io_api.application.entera.EnteraService;
import jpap.dev.io_api.application.inventario.InventarioService;
import jpap.dev.io_api.application.lp.DosFasesService;
import jpap.dev.io_api.application.lp.GraficoService;
import jpap.dev.io_api.application.lp.GranMService;
import jpap.dev.io_api.application.lp.SimplexService;
import jpap.dev.io_api.application.redes.RedService;
import jpap.dev.io_api.application.transporte.TransporteService;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.dinamica.ArticuloMochila;
import jpap.dev.io_api.domain.dinamica.MetodoDinamico;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.entera.TipoVariable;
import jpap.dev.io_api.domain.inventario.MetodoInventario;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.infrastructure.ai.actividad.ActividadRegistry;
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
                new GraficoService(), new TransporteService(), new RedService(),
                new EnteraService(), new InventarioService(), new DinamicaService());
        ResolucionAprobadaWorkflow workflow = new HitlConfig().resolucionAprobadaWorkflow(ejecutor, registry);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        // El registro de actividad es un indicador de progreso: aquí solo tiene que existir.
        service = new AprobacionHumanaService(workflow, registry, executor, new ActividadRegistry());
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
        assertNull(desenlace.ejecucion().resultadoRed());
        assertNotNull(desenlace.ejecucion().resultadoTransporte());
        assertEquals(240.0, desenlace.ejecucion().resultadoTransporte().solution().costoTotal(), 1e-6);
        assertTrue(desenlace.resumenParaTutor().contains("Transporte"));
    }

    /** Ruta más corta A→D en A→B(4), A→C(2), C→B(1), B→D(5): A → C → B → D con distancia 8. */
    @Test
    void aprobarConMetodoRedesDevuelveResultadoRed() {
        ModeloRed modelo = new ModeloRed(
                List.of("A", "B", "C", "D"),
                List.of(
                        new Arista("A", "B", 4.0, null, null),
                        new Arista("A", "C", 2.0, null, null),
                        new Arista("C", "B", 1.0, null, null),
                        new Arista("B", "D", 5.0, null, null)),
                true, MetodoRed.DIJKSTRA, "A", "D", null, null, null);

        SolicitudAprobacion solicitud = service.solicitar("sesion-r", modelo, MetodoResolucion.REDES);
        var desenlace = service.decidir(solicitud.solicitudId(), true, null);

        assertNull(desenlace.ejecucion().resultado(), "redes no produce resultado tabular LP");
        assertNull(desenlace.ejecucion().resultadoGrafico());
        assertNull(desenlace.ejecucion().resultadoTransporte());
        assertNotNull(desenlace.ejecucion().resultadoRed());
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultadoRed().status());
        assertEquals(8.0, desenlace.ejecucion().resultadoRed().solution().valorObjetivo(), 1e-6);
        assertEquals(List.of("A", "C", "B", "D"),
                desenlace.ejecucion().resultadoRed().solution().rutaOptima());
        assertTrue(desenlace.resumenParaTutor().contains("Redes"));
    }

    /** MAX 5x1 + 4x2, s.a. 6x1+4x2 ≤ 24, x1+2x2 ≤ 6, x1,x2 enteras → óptimo entero Z*=20 en (4,0). */
    @Test
    void aprobarConMetodoBranchAndBoundDevuelveResultadoEntero() {
        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(
                        List.of("x1", "x2"),
                        new FuncionObjetivo(List.of(5.0, 4.0), TipoObjetivo.MAXIMIZAR),
                        List.of(
                                new Restriccion(List.of(6.0, 4.0), TipoRestriccion.LEQ, 24),
                                new Restriccion(List.of(1.0, 2.0), TipoRestriccion.LEQ, 6))),
                List.of(TipoVariable.ENTERA, TipoVariable.ENTERA));

        SolicitudAprobacion solicitud = service.solicitar("sesion-e", modelo, MetodoResolucion.BRANCH_AND_BOUND);
        var desenlace = service.decidir(solicitud.solicitudId(), true, null);

        assertNull(desenlace.ejecucion().resultado(), "PL entera no produce resultado tabular LP");
        assertNull(desenlace.ejecucion().resultadoGrafico());
        assertNull(desenlace.ejecucion().resultadoTransporte());
        assertNull(desenlace.ejecucion().resultadoRed());
        assertNotNull(desenlace.ejecucion().resultadoEntero());
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultadoEntero().status());
        assertEquals(20.0, desenlace.ejecucion().resultadoEntero().solution().valorOptimo(), 1e-6);
        assertTrue(desenlace.resumenParaTutor().contains("PL Entera"));
    }

    /** EOQ básico D=1000, K=50, H=4 → Q*≈158.11, costo total≈632.46. */
    @Test
    void aprobarConMetodoInventarioDevuelveResultadoInventario() {
        ModeloInventario modelo = new ModeloInventario(
                MetodoInventario.EOQ_BASICO, 1000.0, 50.0, 4.0,
                null, null, null, null, null, null);

        SolicitudAprobacion solicitud = service.solicitar("sesion-i", modelo, MetodoResolucion.INVENTARIO);
        var desenlace = service.decidir(solicitud.solicitudId(), true, null);

        assertNull(desenlace.ejecucion().resultado(), "inventario no produce resultado tabular LP");
        assertNull(desenlace.ejecucion().resultadoGrafico());
        assertNull(desenlace.ejecucion().resultadoTransporte());
        assertNull(desenlace.ejecucion().resultadoRed());
        assertNull(desenlace.ejecucion().resultadoEntero());
        assertNull(desenlace.ejecucion().resultadoDinamica());
        assertNotNull(desenlace.ejecucion().resultadoInventario());
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultadoInventario().status());
        assertEquals(158.113883, desenlace.ejecucion().resultadoInventario().solution().cantidadOptima(), 1e-4);
        assertTrue(desenlace.resumenParaTutor().contains("Inventario"));
    }

    /** Mochila 0/1 con capacidad 5: A(2,3), B(3,4), C(4,5) → optimo 7 llevando A y B. */
    @Test
    void aprobarConMetodoProgramacionDinamicaDevuelveResultadoDinamica() {
        ModeloDinamico modelo = ModeloDinamico.builder()
                .metodo(MetodoDinamico.MOCHILA)
                .capacidad(5)
                .articulos(List.of(
                        new ArticuloMochila("A", 2, 3.0, null),
                        new ArticuloMochila("B", 3, 4.0, null),
                        new ArticuloMochila("C", 4, 5.0, null)))
                .build();

        SolicitudAprobacion solicitud = service.solicitar("sesion-pd", modelo,
                MetodoResolucion.PROGRAMACION_DINAMICA);
        var desenlace = service.decidir(solicitud.solicitudId(), true, null);

        assertNull(desenlace.ejecucion().resultado(), "PD no produce resultado tabular LP");
        assertNull(desenlace.ejecucion().resultadoGrafico());
        assertNull(desenlace.ejecucion().resultadoTransporte());
        assertNull(desenlace.ejecucion().resultadoRed());
        assertNull(desenlace.ejecucion().resultadoEntero());
        assertNull(desenlace.ejecucion().resultadoInventario());
        assertNotNull(desenlace.ejecucion().resultadoDinamica());
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultadoDinamica().status());
        assertEquals(7.0, desenlace.ejecucion().resultadoDinamica().solution().valorOptimo(), 1e-6);
        assertTrue(desenlace.resumenParaTutor().contains("Programación Dinámica"));
        assertTrue(desenlace.resumenParaTutor().contains("PRINCIPIO DE OPTIMALIDAD"));
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

    @Test
    void aprobacionConModeloModificadoSobrescribeYResuelveNuevoModelo() {
        // Modelo original: max 3x1 + 2x2 s.a. x1 <= 4, x2 <= 6 (Z opt = 24)
        ModeloLP original = modeloClasico();
        SolicitudAprobacion solicitud = service.solicitar("sesion-mod", original, MetodoResolucion.SIMPLEX);

        // Modelo modificado en la UI: max 10x1 + 10x2 s.a. x1 <= 1, x2 <= 1 (Z opt = 20)
        ModeloLP modificado = new ModeloLP(
                List.of("x1", "x2"),
                new FuncionObjetivo(List.of(10.0, 10.0), TipoObjetivo.MAXIMIZAR),
                List.of(
                        new Restriccion(List.of(1.0, 0.0), TipoRestriccion.LEQ, 1.0),
                        new Restriccion(List.of(0.0, 1.0), TipoRestriccion.LEQ, 1.0)
                )
        );
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        tools.jackson.databind.JsonNode modificadoNode = mapper.valueToTree(modificado);

        var desenlace = service.decidir(solicitud.solicitudId(), true, "Aprobado con modelo modificado en UI", modificadoNode);

        assertNotNull(desenlace.ejecucion());
        assertEquals(SolveStatus.OPTIMO, desenlace.ejecucion().resultado().status());
        assertEquals(20.0, desenlace.ejecucion().resultado().solution().valorOptimo(), 1e-6);
    }
}
