package test;

import java.sql.Connection;

import helpers.MensajeHelper;
import util.ConeccionSql;

public class ProbarConecion {

    public static void main(String[] args) {
        Connection con = ConeccionSql.getConnection();

        MensajeHelper.info("" + con);
    }

}
