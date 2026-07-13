package jpap.dev.io_api.domain.entera;

/**
 * Naturaleza de una variable de decisión en un modelo de Programación Lineal Entera.
 *
 * <ul>
 *   <li>{@code ENTERA}   — debe tomar un valor entero (0, 1, 2, ...): "número de camiones", "unidades a producir".</li>
 *   <li>{@code BINARIA}  — decisión sí/no acotada a {0, 1}: "abrir la sucursal", "comprar el equipo",
 *       "seleccionar el proyecto". Branch &amp; Bound le añade implícitamente la cota {@code x <= 1}.</li>
 *   <li>{@code CONTINUA} — variable relajada, admite valores fraccionarios (caso mixto MILP).</li>
 * </ul>
 */
public enum TipoVariable {
    ENTERA, BINARIA, CONTINUA
}
