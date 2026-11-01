
package services.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import model.Sucursal;
import repository.SucursalRepository;
import util.ConeccionPosgre;

public class SucursalService implements SucursalRepository {

    @Override
    public List<Sucursal> ListSucursales() {

        List<Sucursal> lista = new ArrayList<>();

        String sql = """
                SELECT id_sucursal, nombre
                FROM sucursal
                WHERE activo = TRUE
                ORDER BY nombre ASC
                """;

        try (Connection conn = ConeccionPosgre.conectar();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                Sucursal sucursal = new Sucursal();

                sucursal.setIdSucursal(rs.getLong("id_sucursal"));
                sucursal.setNombre(rs.getString("nombre"));

                lista.add(sucursal);
            }

        } catch (SQLException e) {
            System.err.println(
                    "Error al listar sucursales: " + e.getMessage());
        }

        return lista;
    }
}
