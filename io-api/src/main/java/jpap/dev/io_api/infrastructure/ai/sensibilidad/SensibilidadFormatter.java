package jpap.dev.io_api.infrastructure.ai.sensibilidad;

import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.RangoCoeficiente;
import jpap.dev.io_api.domain.lp.RangoRHS;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;

import java.util.List;
import java.util.Map;

/**
 * Convierte el análisis post-óptimo de un LP en el texto que lee el tutor.
 *
 * Lo consumen dos caminos: el resumen que se inyecta como [SISTEMA] justo tras
 * resolver (ResolucionEjecutor) y la relectura del último resultado guardado en
 * BD (SensibilidadService), para cuando el estudiante pregunta turnos después.
 *
 * ⚠ La indexación de holguras NO coincide con la de restricciones: el solver solo
 * crea columna de holgura cuando el tipo no es EQ (slackCol[i] = -1 para las EQ),
 * y SensibilidadCalculator numera "s1, s2, …" avanzando solo en esos casos. Con un
 * modelo [≤, =, ≥], la holgura "s2" es la de R3. Unir s_i con R_i por número daría
 * explicaciones falsas, así que aquí se reconstruye el mapeo con el mismo contador.
 */
public final class SensibilidadFormatter {

    private static final String SIN_LIMITE = "sin límite";

    private SensibilidadFormatter() {}

    /** Vacío si el resultado no trae análisis post-óptimo (gráfico, infactible, no acotado). */
    public static String formatear(SolucionLP sol, ModeloLP modelo) {
        if (sol == null || modelo == null || sol.rangosSensibilidad() == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("--- ANÁLISIS DE SENSIBILIDAD (datos reales del solver) ---\n");
        sb.append("ESTO ES MATERIA PRIMA INTERNA, NO UN BORRADOR DE TU RESPUESTA. Está escrito en jerga\n");
        sb.append("técnica solo para que TÚ identifiques cada dato. TRADÚCELO ENTERO antes de responder.\n");
        sb.append("PROHIBIDO en tu respuesta: x1, x2, Z, R1, s1, el álgebra de las restricciones, y las\n");
        sb.append("palabras holgura, precio sombra, coeficiente, lado derecho, RHS, base o rango.\n");
        sb.append("Usa el sustantivo del enunciado y su unidad: \"las horas de carpintería\", \"las mesas\".\n");
        sb.append("VE DIRECTO a la explicación: sin preámbulo, sin repetir el modelo, sin tablas.\n");
        sb.append("El objetivo es ").append(modelo.objetivo().tipo() == TipoObjetivo.MAXIMIZAR
                ? "MAXIMIZAR: un valor marginal positivo significa MÁS ganancia.\n"
                : "MINIMIZAR: un valor marginal se lee como cambio en el COSTO, no en la ganancia.\n");

        sb.append("\nRESTRICCIONES / RECURSOS (el álgebra es solo para que reconozcas de qué recurso\n");
        sb.append("habla cada línea — NO la copies: nómbralo por su nombre en el enunciado)\n");
        List<Restriccion> restricciones = modelo.restricciones();
        List<RangoRHS> rangosRhs = sol.rangosSensibilidad().rhs();
        int sIdx = 0;

        for (int i = 0; i < restricciones.size(); i++) {
            Restriccion r = restricciones.get(i);

            // Mismo avance que el solver: las EQ no consumen número de holgura.
            Double sobrante = null;
            if (r.tipo() != TipoRestriccion.EQ) {
                sIdx++;
                sobrante = valor(sol.holguras(), "s" + sIdx);
            }
            Double precio = valor(sol.preciosSombra(), "R" + (i + 1));
            RangoRHS rango = (rangosRhs != null && i < rangosRhs.size()) ? rangosRhs.get(i) : null;

            sb.append("  R").append(i + 1).append(": ")
              .append(algebra(r.coeficientes(), modelo.variables()))
              .append(signo(r.tipo())).append(fmt(r.rhs())).append("\n");

            sb.append("      ").append(descripcionSobrante(r.tipo(), sobrante)).append("\n");

            if (precio != null) {
                sb.append("      1 unidad más del lado derecho cambia el óptimo en ")
                  .append(precio >= 0 ? "+" : "").append(fmt(precio)).append("\n");
            }
            if (rango != null) {
                sb.append("      ese valor marginal se mantiene mientras el lado derecho esté entre ")
                  .append(fmt(rango.min())).append(" y ").append(fmt(rango.max())).append("\n");
            }
        }

        sb.append("\nVARIABLES DE DECISIÓN\n");
        List<RangoCoeficiente> rangosCoef = sol.rangosSensibilidad().coeficientesObjetivo();
        if (rangosCoef != null) {
            for (RangoCoeficiente c : rangosCoef) {
                Double valorVariable = valor(sol.valores(), c.variable());
                double v = valorVariable != null ? valorVariable : 0.0;
                sb.append("  ").append(c.variable()).append(" = ").append(fmt(v))
                  .append(v > 1e-9 ? "  (en uso)" : "  (fuera del plan)").append("\n");
                sb.append("      coeficiente actual ").append(fmt(c.valorActual()))
                  .append("; el plan óptimo no cambia mientras esté entre ")
                  .append(fmt(c.min())).append(" y ").append(fmt(c.max())).append("\n");
            }
        }

        sb.append("\nCOHERENCIA: una restricción con sobrante siempre tiene valor marginal 0, y una sin ")
          .append("sobrante suele tener valor marginal distinto de 0. Úsalo para verificar tu explicación.\n");

        return sb.toString();
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private static String descripcionSobrante(TipoRestriccion tipo, Double sobrante) {
        if (tipo == TipoRestriccion.EQ) {
            return "restricción de igualdad: se cumple exacta, no tiene sobrante";
        }
        if (sobrante == null) {
            return "sobrante no disponible";
        }
        boolean agotado = Math.abs(sobrante) < 1e-9;
        if (tipo == TipoRestriccion.LEQ) {
            return agotado
                    ? "sin sobrante (recurso agotado: es un cuello de botella y limita la solución)"
                    : "sobran " + fmt(sobrante) + " unidades (no limita la solución)";
        }
        return agotado
                ? "el mínimo exigido se cumple justo en el límite (sin excedente)"
                : "se supera el mínimo exigido por " + fmt(sobrante) + " unidades";
    }

    private static String signo(TipoRestriccion tipo) {
        return switch (tipo) {
            case LEQ -> " <= ";
            case GEQ -> " >= ";
            case EQ -> " = ";
        };
    }

    private static String algebra(List<Double> coefs, List<String> vars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coefs.size() && i < vars.size(); i++) {
            double c = coefs.get(i);
            if (i > 0) sb.append(c >= 0 ? " + " : " - ");
            sb.append(fmt(i > 0 ? Math.abs(c) : c)).append("·").append(vars.get(i));
        }
        return sb.toString();
    }

    private static Double valor(Map<String, Double> mapa, String clave) {
        return mapa != null ? mapa.get(clave) : null;
    }

    /**
     * null = rango no acotado. Un infinito jamás debe salir de aquí: además del gotcha
     * conocido de Jackson, el token "Infinity" no le dice nada al estudiante.
     */
    private static String fmt(Double v) {
        if (v == null || v.isInfinite() || v.isNaN()) return SIN_LIMITE;
        return String.valueOf(redondear(v));
    }

    private static String fmt(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v)) return SIN_LIMITE;
        return String.valueOf(redondear(v));
    }

    private static double redondear(double v) {
        if (Math.abs(v) < 1e-9) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
