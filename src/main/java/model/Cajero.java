package model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Cajero {
    private Integer idCajero;
    private String nombre;
    private String pin;
    private Integer idUser;

    public Cajero(String pin, int idUser) {
        this.pin = pin;
        this.idUser = idUser;
    }

}
