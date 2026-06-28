package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.application.lp.DosFasesUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.common.SolveStep;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DosFasesTool {

    private final DosFasesUseCase dosFasesUseCase;
    private final ChatContextStore contextStore;

    public DosFasesTool(DosFasesUseCase dosFasesUseCase, ChatContextStore contextStore) {
        this.dosFasesUseCase = dosFasesUseCase;
        this.contextStore = contextStore;
    }

    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las variables de decisión")
            List<Double> coeficientes,
            @Description("Tipo de restricción: LEQ (<=), GEQ (>=) o EQ (=)")
            TipoRestriccion tipo,
            @Description("Término independiente (lado derecho). Debe ser >= 0.")
            double rhs
    ) {}

    @Tool("""
            Resuelve un problema de Programación Lineal usando el método Dos Fases.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El modelo tenga restricciones >= (GEQ) y/o = (EQ), Y el estudiante NO haya
                 pedido específicamente Gran M. Este es el método predeterminado para GEQ/EQ.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            VENTAJA sobre Gran M: numéricamente más robusto, evita problemas de escala con M grande.
            La interfaz mostrará pasos de Fase 1 (factibilidad) y Fase 2 (optimización) por separado.
            Acepta restricciones LEQ (<=), GEQ (>=) y EQ (=). El término independiente debe ser >= 0.
            """)
    public String resolverDosFases(
            @P("Lista de nombres de las variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones. Cada restricción tiene 'coeficientes', 'tipo' (LEQ/GEQ/EQ) y 'rhs'")
            List<RestriccionInput> restricciones
    ) {
        log.info("[TOOL] resolverDosFases — vars={}, tipo={}, restricciones={}",
                variables, tipoObjetivo, restricciones.size());

        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), r.tipo(), r.rhs()))
                .toList();

        ModeloLP modelo = new ModeloLP(
                variables,
                new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo),
                restriccionesLP
        );

        SolveResult<SolucionLP> resultado = dosFasesUseCase.resolver(modelo);

        String zStar = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[TOOL] resolverDosFases completado — status={}, Z*={}, pasos={}",
                resultado.status(), zStar, resultado.steps().size());

        contextStore.obtener().resultado = resultado;

        return formatearParaTutor(resultado, variables);
    }

    @SuppressWarnings("unchecked")
    private String formatearParaTutor(SolveResult<SolucionLP> r, List<String> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RESULTADO DEL SOLVER (Dos Fases) ===\n");
        sb.append("Estado: ").append(r.status().name()).append("\n");

        if (r.status() == SolveStatus.NO_ACOTADO) {
            sb.append("El problema NO ESTÁ ACOTADO: la función objetivo crece sin límite.\n");
            sb.append("Pregunta al estudiante si falta alguna restricción que lo limite.\n");
            return sb.toString();
        }

        if (r.status() == SolveStatus.INFACTIBLE) {
            sb.append("El problema es INFACTIBLE: la Fase 1 terminó con w* > 0,\n");
            sb.append("lo que indica que no existe ningún punto factible.\n");
            return sb.toString();
        }

        if (r.solution() != null) {
            sb.append("Valor óptimo Z* = ").append(r.solution().valorOptimo()).append("\n");
            sb.append("Solución óptima:\n");
            r.solution().valores().forEach((v, val) ->
                    sb.append("  ").append(v).append(" = ").append(val).append("\n"));
        }

        if (r.status() == SolveStatus.MULTIPLE_OPTIMO) {
            sb.append("Nota: existen óptimos múltiples — hay otras soluciones con el mismo Z*.\n");
        }

        // Separar pasos de Fase 1 y Fase 2 para el tutor
        sb.append("\n--- RESUMEN POR FASES ---\n");
        long pasosFase1 = r.steps().stream()
                .filter(s -> s.titulo().contains("Fase 1")).count();
        long pasosFase2 = r.steps().stream()
                .filter(s -> s.titulo().contains("Fase 2")).count();
        sb.append("Fase 1 (factibilidad): ").append(pasosFase1).append(" pasos\n");
        sb.append("Fase 2 (optimización): ").append(pasosFase2).append(" pasos\n");

        sb.append("\n--- DETALLE DE ITERACIONES (Dos Fases) ---\n");
        for (SolveStep step : r.steps()) {
            sb.append("Paso ").append(step.numero()).append(": ").append(step.titulo()).append("\n");
            Object varEntra = step.datos().get("varEntra");
            Object varSale  = step.datos().get("varSale");
            if (varEntra != null) {
                sb.append("  Entra a la base: ").append(varEntra).append("\n");
                sb.append("  Sale de la base: ").append(varSale).append("\n");
            }
            Object base = step.datos().get("base");
            if (base != null) {
                sb.append("  Base resultante: ").append(base).append("\n");
            }
            Object tableau = step.datos().get("tableau");
            if (tableau instanceof List<?> filas && !((List<?>) filas).isEmpty()) {
                List<?> filaZ = (List<?>) filas.get(filas.size() - 1);
                if (!filaZ.isEmpty()) {
                    sb.append("  Valor z/w acumulado: ").append(filaZ.get(filaZ.size() - 1)).append("\n");
                }
            }
        }

        sb.append("\nLa interfaz muestra el tableau completo con los pasos de Fase 1 y Fase 2. ");
        sb.append("Explica la transición entre fases: la Fase 1 encontró la solución básica factible, ");
        sb.append("y la Fase 2 optimizó el objetivo original desde ahí. ");
        sb.append("Pregunta al estudiante qué diferencia observa entre ambas fases.");

        return sb.toString();
    }
}
