package test;

import java.sql.Connection;

import helpers.MensajeHelper;
import util.ConeccionPosgre;

public class ProbarConecion {

    public static void main(String[] args) {
        Connection con = ConeccionPosgre.conectar();

        MensajeHelper.info("" + con);
    }

}
