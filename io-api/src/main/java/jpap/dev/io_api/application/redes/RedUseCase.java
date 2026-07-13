package jpap.dev.io_api.application.redes;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.SolucionRed;

/**
 * Puerto de entrada del módulo de redes: resuelve el modelo con el método
 * que este indique (Dijkstra, Kruskal, Edmonds-Karp, MCF o Asignación).
 */
public interface RedUseCase {

    SolveResult<SolucionRed> resolver(ModeloRed modelo);
}
