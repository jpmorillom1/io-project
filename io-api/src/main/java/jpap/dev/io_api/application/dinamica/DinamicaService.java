package jpap.dev.io_api.application.dinamica;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SolucionDinamica;
import jpap.dev.io_api.domain.dinamica.asignacion.AsignacionRecursosSolver;
import jpap.dev.io_api.domain.dinamica.mochila.MochilaSolver;
import jpap.dev.io_api.domain.dinamica.produccion.PlanificacionProduccionSolver;
import jpap.dev.io_api.domain.dinamica.reemplazo.ReemplazoEquiposSolver;
import jpap.dev.io_api.domain.dinamica.ruta.RutaEtapasSolver;
import org.springframework.stereotype.Service;

/**
 * Adaptador de aplicación: despacha al solver de programación dinámica según el método del modelo.
 * Los solvers son objetos Java puros (creados con new), igual que en Inventarios y Redes.
 *
 * No hay método por defecto: los cinco submodelos son heterogéneos, así que un modelo sin
 * método es entrada malformada (400).
 */
@Service
public class DinamicaService implements DinamicaUseCase {

    private final AsignacionRecursosSolver asignacion = new AsignacionRecursosSolver();
    private final MochilaSolver mochila = new MochilaSolver();
    private final RutaEtapasSolver ruta = new RutaEtapasSolver();
    private final PlanificacionProduccionSolver produccion = new PlanificacionProduccionSolver();
    private final ReemplazoEquiposSolver reemplazo = new ReemplazoEquiposSolver();

    @Override
    public SolveResult<SolucionDinamica> resolver(ModeloDinamico modelo) {
        if (modelo == null || modelo.metodo() == null)
            throw new IllegalArgumentException(
                    "El modelo necesita el método de programación dinámica (metodo): ASIGNACION_RECURSOS, "
                    + "MOCHILA, RUTA_ETAPAS, PLANIFICACION_PRODUCCION o REEMPLAZO_EQUIPOS.");
        return switch (modelo.metodo()) {
            case ASIGNACION_RECURSOS -> asignacion.resolver(modelo);
            case MOCHILA -> mochila.resolver(modelo);
            case RUTA_ETAPAS -> ruta.resolver(modelo);
            case PLANIFICACION_PRODUCCION -> produccion.resolver(modelo);
            case REEMPLAZO_EQUIPOS -> reemplazo.resolver(modelo);
        };
    }
}
