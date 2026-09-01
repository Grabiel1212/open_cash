package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Data
public class Empleado {
    private Long idEmpleado;
    private String nombre;
    private String apellido;
    private String dni;
    private String foto;
    private String estado;

}
