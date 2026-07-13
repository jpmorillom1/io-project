package jpap.dev.io_api.application.redes;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.SolucionRed;
import jpap.dev.io_api.domain.redes.asignacion.AsignacionSolver;
import jpap.dev.io_api.domain.redes.dijkstra.DijkstraSolver;
import jpap.dev.io_api.domain.redes.edmondskarp.EdmondsKarpSolver;
import jpap.dev.io_api.domain.redes.flujocostominimo.FlujoCostoMinimoSolver;
import jpap.dev.io_api.domain.redes.kruskal.KruskalSolver;
import org.springframework.stereotype.Service;

/**
 * Adaptador de aplicación: despacha al solver de redes según el método del modelo.
 * Los solvers son objetos Java puros (creados con new), igual que en Transporte.
 *
 * A diferencia de Transporte no hay método por defecto: los cinco problemas son
 * heterogéneos, así que un modelo sin método es entrada malformada (400).
 */
@Service
public class RedService implements RedUseCase {

    private final DijkstraSolver dijkstra = new DijkstraSolver();
    private final KruskalSolver kruskal = new KruskalSolver();
    private final EdmondsKarpSolver edmondsKarp = new EdmondsKarpSolver();
    private final FlujoCostoMinimoSolver flujoCostoMinimo = new FlujoCostoMinimoSolver();
    private final AsignacionSolver asignacion = new AsignacionSolver();

    @Override
    public SolveResult<SolucionRed> resolver(ModeloRed modelo) {
        if (modelo.metodo() == null)
            throw new IllegalArgumentException(
                    "El modelo necesita el método de redes (metodo): DIJKSTRA, KRUSKAL, EDMONDS_KARP, "
                    + "FLUJO_COSTO_MINIMO o ASIGNACION.");
        return switch (modelo.metodo()) {
            case DIJKSTRA -> dijkstra.resolver(modelo);
            case KRUSKAL -> kruskal.resolver(modelo);
            case EDMONDS_KARP -> edmondsKarp.resolver(modelo);
            case FLUJO_COSTO_MINIMO -> flujoCostoMinimo.resolver(modelo);
            case ASIGNACION -> asignacion.resolver(modelo);
        };
    }
}
