package jpap.dev.io_api.domain.transporte.modi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CicloSteppingStoneTest {

    @Test
    void encuentra_el_ciclo_rectangular_simple() {
        // Celdas básicas que rodean a la entrante (0,0) formando el rectángulo
        // (0,0)-(0,1)-(1,1)-(1,0).
        int[] entrada = {0, 0};
        List<int[]> basicas = List.of(
                new int[]{0, 1},
                new int[]{1, 1},
                new int[]{1, 0});

        List<int[]> ciclo = CicloSteppingStone.encontrar(entrada, basicas);

        assertNotNull(ciclo);
        assertEquals(4, ciclo.size());
        assertArrayEquals(new int[]{0, 0}, ciclo.get(0), "el ciclo debe empezar en la celda entrante");
        assertTrue(contiene(ciclo, 0, 1));
        assertTrue(contiene(ciclo, 1, 1));
        assertTrue(contiene(ciclo, 1, 0));
    }

    @Test
    void encuentra_un_ciclo_en_forma_de_ele() {
        // Entrante (0,0); el ciclo debe rodear pasando por (0,2),(2,2),(2,0).
        int[] entrada = {0, 0};
        List<int[]> basicas = List.of(
                new int[]{0, 2},
                new int[]{2, 2},
                new int[]{2, 0},
                new int[]{1, 1});   // celda básica ajena al ciclo, no debe usarse

        List<int[]> ciclo = CicloSteppingStone.encontrar(entrada, basicas);

        assertNotNull(ciclo);
        assertEquals(4, ciclo.size());
        assertArrayEquals(new int[]{0, 0}, ciclo.get(0));
        assertTrue(contiene(ciclo, 0, 2));
        assertTrue(contiene(ciclo, 2, 2));
        assertTrue(contiene(ciclo, 2, 0));
        assertFalse(contiene(ciclo, 1, 1), "la celda ajena no debe formar parte del ciclo");
    }

    private static boolean contiene(List<int[]> ciclo, int i, int j) {
        return ciclo.stream().anyMatch(c -> c[0] == i && c[1] == j);
    }
}
