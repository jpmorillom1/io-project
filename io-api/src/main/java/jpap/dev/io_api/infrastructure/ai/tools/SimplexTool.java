package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.application.lp.SimplexUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador @Tool que expone el SimplexSolver a la IA.
 * Esta anotación @Tool vive aquí (infrastructure), nunca en el dominio.
 */
@Slf4j
@Component
public class SimplexTool {

    private final SimplexUseCase simplexUseCase;

    public SimplexTool(SimplexUseCase simplexUseCase) {
        this.simplexUseCase = simplexUseCase;
    }

    /** Tipo auxiliar para describir cada restricción al LLM (siempre LEQ para Simplex estándar). */
    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las variables de decisión")
            List<Double> coeficientes,
            @Description("Término independiente (lado derecho). Debe ser >= 0 para Simplex estándar")
            double rhs
    ) {}

    @Tool("""
            Resuelve un problema de Programación Lineal usando el método Simplex estándar.
            INVOCA ESTA HERRAMIENTA SOLO cuando se cumplan AMBAS condiciones:
              1) El modelo esté completamente validado por el estudiante (variables, función objetivo y restricciones confirmadas).
              2) El estudiante haya pedido explícitamente resolver o calcular.
            RESTRICCIONES TÉCNICAS: solo funciona con restricciones de tipo <= y término independiente >= 0.
            Si el modelo tiene >= o =, informa al estudiante que necesita Dos Fases o Gran M.
            """)
    public String resolverSimplex(
            @P("Lista de nombres de las variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones del modelo (todas de tipo <=). Cada restricción tiene 'coeficientes' y 'rhs'")
            List<RestriccionInput> restricciones
    ) {
        log.info("[TOOL] resolverSimplex invocado — vars={}, tipo={}, restricciones={}",
                variables, tipoObjetivo, restricciones.size());

        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), TipoRestriccion.LEQ, r.rhs()))
                .toList();

        ModeloLP modelo = new ModeloLP(
                variables,
                new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo),
                restriccionesLP
        );

        SolveResult<SolucionLP> resultado = simplexUseCase.resolver(modelo);

        String zStar = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[TOOL] resolverSimplex completado — status={}, Z*={}, pasos={}",
                resultado.status(), zStar, resultado.steps().size());

        return formatearParaTutor(resultado, variables);
    }

    private String formatearParaTutor(SolveResult<SolucionLP> r, List<String> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RESULTADO DEL SOLVER ===\n");
        sb.append("Estado: ").append(r.status().name()).append("\n");

        if (r.status() == SolveStatus.NO_ACOTADO) {
            sb.append("El problema no está acotado: la función objetivo puede crecer indefinidamente.\n");
            sb.append("Revisa con el estudiante si falta alguna restricción.\n");
            return sb.toString();
        }

        if (r.status() == SolveStatus.INFACTIBLE) {
            sb.append("El problema es infactible: no existe solución que satisfaga todas las restricciones.\n");
            return sb.toString();
        }

        if (r.solution() != null) {
            sb.append("Valor óptimo Z* = ").append(r.solution().valorOptimo()).append("\n");
            sb.append("Solución:\n");
            r.solution().valores().forEach((v, val) ->
                    sb.append("  ").append(v).append(" = ").append(val).append("\n"));
        }

        if (r.status() == SolveStatus.MULTIPLE_OPTIMO) {
            sb.append("Nota: El problema tiene óptimos múltiples (hay otras soluciones con el mismo Z*).\n");
        }

        int iteraciones = Math.max(0, r.steps().size() - 2);
        sb.append("Iteraciones del Simplex: ").append(iteraciones).append("\n");
        sb.append("Pasos del tableau disponibles para revisar: ").append(r.steps().size()).append("\n");
        sb.append("\nAhora pregunta al estudiante cómo interpreta este resultado.");

        return sb.toString();
    }
}
