package jpap.dev.io_api.domain.common;

/**
 * Interfaz marcador para cualquier modelo que la capa de IA pueda resolver bajo
 * aprobación humana (Human-in-the-Loop). Permite que la cadena HITL transporte
 * modelos de distintos módulos (LP, Transporte, ...) sin acoplarse a un tipo concreto.
 *
 * La dirección de dependencia es la correcta: los módulos (lp, transporte) dependen
 * de common; common NO conoce a los módulos (por eso no es sealed).
 */
public interface ModeloResoluble {}
