package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.application.lp.GranMUseCase;
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
public class GranMTool {

    private final GranMUseCase granMUseCase;
    private final ChatContextStore contextStore;

    public GranMTool(GranMUseCase granMUseCase, ChatContextStore contextStore) {
        this.granMUseCase = granMUseCase;
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
            Resuelve un problema de Programación Lineal usando el método Gran M (penalidad).
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El modelo tenga restricciones >= (GEQ) y/o = (EQ), Y el estudiante haya pedido
                 específicamente el método Gran M, O esté aprendiendo el método de penalidad.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            Si el modelo solo tiene <= y el estudiante no pidió Gran M, usa resolverSimplex.
            Si el modelo tiene >= o = pero el estudiante no pidió Gran M específicamente, usa resolverDosFases.
            Acepta restricciones LEQ (<=), GEQ (>=) y EQ (=). El término independiente debe ser >= 0.
            """)
    public String resolverGranM(
            @P("Lista de nombres de las variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones. Cada restricción tiene 'coeficientes', 'tipo' (LEQ/GEQ/EQ) y 'rhs'")
            List<RestriccionInput> restricciones
    ) {
        log.info("[TOOL] resolverGranM — vars={}, tipo={}, restricciones={}",
                variables, tipoObjetivo, restricciones.size());

        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), r.tipo(), r.rhs()))
                .toList();

        ModeloLP modelo = new ModeloLP(
                variables,
                new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo),
                restriccionesLP
        );

        SolveResult<SolucionLP> resultado = granMUseCase.resolver(modelo);

        String zStar = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[TOOL] resolverGranM completado — status={}, Z*={}, pasos={}",
                resultado.status(), zStar, resultado.steps().size());

        contextStore.obtener().resultado = resultado;

        return formatearParaTutor(resultado, variables);
    }

    @SuppressWarnings("unchecked")
    private String formatearParaTutor(SolveResult<SolucionLP> r, List<String> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RESULTADO DEL SOLVER (Gran M) ===\n");
        sb.append("Estado: ").append(r.status().name()).append("\n");

        if (r.status() == SolveStatus.NO_ACOTADO) {
            sb.append("El problema NO ESTÁ ACOTADO: la función objetivo crece sin límite.\n");
            sb.append("Pregunta al estudiante si falta alguna restricción que lo limite.\n");
            return sb.toString();
        }

        if (r.status() == SolveStatus.INFACTIBLE) {
            sb.append("El problema es INFACTIBLE: ningún punto satisface todas las restricciones.\n");
            sb.append("Al menos una variable artificial permaneció en la base con valor positivo.\n");
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

        sb.append("\n--- DETALLE DE ITERACIONES (Gran M) ---\n");
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
                    sb.append("  Valor z acumulado: ").append(filaZ.get(filaZ.size() - 1)).append("\n");
                }
            }
        }

        sb.append("\nLa interfaz muestra el tableau completo con variables artificiales y penalidad M. ");
        sb.append("Explica al estudiante por qué las artificiales deben salir de la base y ");
        sb.append("pregunta qué observa en la solución óptima.");

        return sb.toString();
    }
}
