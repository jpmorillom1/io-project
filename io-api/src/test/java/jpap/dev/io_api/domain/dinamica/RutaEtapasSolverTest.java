package jpap.dev.io_api.domain.dinamica;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.dinamica.ruta.RutaEtapasSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RutaEtapasSolverTest {

    private final RutaEtapasSolver solver = new RutaEtapasSolver();

    /** Las 5 etapas del problema clasico de la diligencia (stagecoach). */
    private static List<EtapaRuta> etapasDiligencia() {
        return List.of(
                new EtapaRuta(1, List.of("A")),
                new EtapaRuta(2, List.of("B", "C", "D")),
                new EtapaRuta(3, List.of("E", "F", "G")),
                new EtapaRuta(4, List.of("H", "I")),
                new EtapaRuta(5, List.of("J")));
    }

    private static List<ArcoRuta> arcosDiligencia() {
        return List.of(
                new ArcoRuta("A", "B", 2), new ArcoRuta("A", "C", 4), new ArcoRuta("A", "D", 3),
                new ArcoRuta("B", "E", 7), new ArcoRuta("B", "F", 4), new ArcoRuta("B", "G", 6),
                new ArcoRuta("C", "E", 3), new ArcoRuta("C", "F", 2), new ArcoRuta("C", "G", 4),
                new ArcoRuta("D", "E", 4), new ArcoRuta("D", "F", 1), new ArcoRuta("D", "G", 5),
                new ArcoRuta("E", "H", 1), new ArcoRuta("E", "I", 4),
                new ArcoRuta("F", "H", 6), new ArcoRuta("F", "I", 3),
                new ArcoRuta("G", "H", 3), new ArcoRuta("G", "I", 3),
                new ArcoRuta("H", "J", 3), new ArcoRuta("I", "J", 4));
    }

    private ModeloDinamico modelo(List<EtapaRuta> etapas, List<ArcoRuta> arcos) {
        return ModeloDinamico.builder()
                .metodo(MetodoDinamico.RUTA_ETAPAS)
                .etapasRuta(etapas)
                .arcos(arcos)
                .build();
    }

    /**
     * Problema clasico de la diligencia: f_4(H)=3, f_4(I)=4; f_3(E)=4, f_3(F)=7, f_3(G)=6;
     * f_2(B)=11, f_2(C)=7, f_2(D)=8; f_1(A)=min(2+11, 4+7, 3+8)=11.
     * La ruta A-C-E-H-J cuesta 4+3+1+3 = 11.
     */
    @Test
    void diligencia_encuentra_la_ruta_de_costo_11() {
        SolveResult<SolucionDinamica> res = solver.resolver(modelo(etapasDiligencia(), arcosDiligencia()));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(11.0, res.solution().valorOptimo(), 1e-9);
        assertEquals(List.of("A", "C", "E", "H", "J"), res.solution().rutaOptima());
        assertEquals(4, res.solution().politicaOptima().size());
        // 4 tablas (etapas 4..1), en orden de la recursion hacia atras.
        assertEquals(4, res.solution().tablas().size());
        assertEquals(4, res.solution().tablas().get(0).etapa());
        assertEquals(1, res.solution().tablas().get(3).etapa());
    }

    /** Sin arcos que salgan del origen no hay ruta: INFACTIBLE, no excepcion. */
    @Test
    void sin_camino_al_destino_devuelve_infactible() {
        List<ArcoRuta> sinSalidaDeA = arcosDiligencia().stream()
                .filter(a -> !a.origen().equals("A"))
                .toList();

        SolveResult<SolucionDinamica> res = solver.resolver(modelo(etapasDiligencia(), sinSalidaDeA));

        assertEquals(SolveStatus.INFACTIBLE, res.status());
        assertNull(res.solution());
        assertTrue(res.steps().get(res.steps().size() - 1).titulo().contains("Sin ruta"));
    }

    /** Un nodo intermedio sin salida no genera fila y deja de ser una decision admisible. */
    @Test
    void nodo_intermedio_sin_salida_no_rompe_la_recursion() {
        List<ArcoRuta> sinSalidaDeG = arcosDiligencia().stream()
                .filter(a -> !a.origen().equals("G"))
                .toList();

        SolveResult<SolucionDinamica> res = solver.resolver(modelo(etapasDiligencia(), sinSalidaDeG));

        assertEquals(SolveStatus.OPTIMO, res.status());
        assertEquals(11.0, res.solution().valorOptimo(), 1e-9);
        TablaEtapa etapa3 = res.solution().tablas().stream()
                .filter(t -> t.etapa() == 3).findFirst().orElseThrow();
        assertEquals(2, etapa3.filas().size());   // E y F; G quedo fuera
    }

    @Test
    void entrada_malformada_lanza() {
        // Un arco que salta dos etapas.
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(
                etapasDiligencia(), List.of(new ArcoRuta("A", "E", 1)))));
        // La etapa 1 debe tener un unico nodo: el origen.
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(modelo(
                List.of(new EtapaRuta(1, List.of("A", "X")), new EtapaRuta(2, List.of("J"))),
                List.of(new ArcoRuta("A", "J", 1)))));
    }
}
