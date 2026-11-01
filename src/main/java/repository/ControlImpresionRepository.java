
package repository;

import java.util.List;

import model.ControlImpresion;
import model.ControlImpresionInsumo;

public interface ControlImpresionRepository {

        // Crear apertura
        // Crear apertura y devolver el ID generado
        Long CrearConteoInicial(
                        Long idSucursal,
                        Long inicialRicoh,
                        Long inicialEpson);

        // Cerrar el control por su ID
        Boolean CrearConteoFinal(
                        Long idControl,
                        Long finalRicoh,
                        Long finalEpson,
                        List<ControlImpresionInsumo> insumos);

        // Listar historial de controles de la sucursal
        List<ControlImpresion> ListarControles(
                        Long idSucursal);

        // Buscar control de impresión por ID
        ControlImpresion ListarPorId(Long idControl);

        boolean ExisteAbiertoPorSucursal(Long idSucursal);

        // Obtener el control ABIERTO actual de una sucursal (para el cierre)
        ControlImpresion ObtenerControlAbiertoPorSucursal(Long idSucursal);
}
