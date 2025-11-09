package schemas;

public class Consultas {

    // PARA EL USUARIO
    public static final String VALIDAR_USUARIO = "SELECT * FROM Usuarios WHERE Nombre = ? AND Password = ?";

    // PARA EL CAJERO
    public static final String VALIDAR_CAJERO = "SELECT * FROM Cajero WHERE Pin = ?";
    public static final String ACTUALIZAR_PIN = "UPDATE Cajero SET Pin = ? WHERE IdCajero = ?";
    public static final String OBTENER_CAJERO_ID = "SELECT * FROM Cajero WHERE IdCajero = ?";
    public static final String OBTENER_CAJERO_USUARIO = "SELECT * FROM Cajero WHERE IdUser = ?";

}
