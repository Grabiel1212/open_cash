-- =====================================================
-- BASE CENTRAL DEL SISTEMA (VERSION OPTIMIZADA FINAL)
-- =====================================================

-- =====================================================
-- ENUMS
-- =====================================================

CREATE TYPE rol_enum AS ENUM (
    'SUPER_ADMIN',
    'SUPERVISOR',
    'CONTADOR',
    'CAJERO'
);

CREATE TYPE estado_empleado_enum AS ENUM (
    'ACTIVO',
    'SUSPENDIDO'
);

-- =====================================================
-- SECUENCIA GLOBAL
-- =====================================================

CREATE SEQUENCE seq_codigo_global START 1;

-- =====================================================
-- TABLA SUCURSAL
-- =====================================================

CREATE TABLE sucursal (
    id_sucursal BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) UNIQUE NOT NULL,

    nombre VARCHAR(150) NOT NULL
        CHECK (TRIM(nombre) <> ''),

	nombre_comercial VARCHAR(150) NOT NULL
        CHECK (TRIM(nombre) <> ''),

    direccion VARCHAR(255),

    departamento VARCHAR(100) NOT NULL
        CHECK (TRIM(departamento) <> ''),

    provincia VARCHAR(100) NOT NULL
        CHECK (TRIM(provincia) <> ''),

    distrito VARCHAR(100) NOT NULL
        CHECK (TRIM(distrito) <> ''),

    telefono VARCHAR(20)
        CHECK (
            telefono IS NULL
            OR telefono ~ '^[0-9]{9,15}$'
        ),

    email VARCHAR(150) UNIQUE
        CHECK (
            email IS NULL
            OR email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
        ),
	
	foto VARCHAR(255),
	descripcion TEXT  ,

    activo BOOLEAN NOT NULL DEFAULT TRUE,

    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- TABLA EMPLEADO
-- =====================================================

CREATE TABLE empleado (
    id_empleado BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) UNIQUE NOT NULL,

    nombre VARCHAR(150) NOT NULL CHECK (TRIM(nombre) <> ''),
    apellido VARCHAR(150) NOT NULL CHECK (TRIM(apellido) <> ''),

    dni VARCHAR(9) UNIQUE NOT NULL
        CHECK (dni ~ '^[0-9]{8,9}$'),

    telefono VARCHAR(20)
        CHECK (telefono IS NULL OR telefono ~ '^[0-9]{9,15}$'),

    correo VARCHAR(150) UNIQUE
        CHECK (
            correo IS NULL OR
            correo ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
        ),

    foto VARCHAR(255),

    fecha_ingreso_empresa DATE NOT NULL DEFAULT CURRENT_DATE,

    anio_ingreso_empresa INT GENERATED ALWAYS AS (
        EXTRACT(YEAR FROM fecha_ingreso_empresa)
    ) STORED,

    fecha_baja DATE,

    estado estado_empleado_enum NOT NULL DEFAULT 'ACTIVO',

    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_fechas_empleado
    CHECK (
        fecha_baja IS NULL
        OR fecha_baja >= fecha_ingreso_empresa
    ),

    CONSTRAINT chk_fecha_ingreso
    CHECK (
        fecha_ingreso_empresa <= CURRENT_DATE
    )
);

-- =====================================================
-- TABLA USUARIO
-- =====================================================

CREATE TABLE usuario (
    id_usuario BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) UNIQUE NOT NULL,

    id_empleado BIGINT UNIQUE NOT NULL,

    username VARCHAR(100) UNIQUE NOT NULL
        CHECK (TRIM(username) <> ''),

    clave VARCHAR(255) NOT NULL
        CHECK (LENGTH(clave) >= 8),

    ultimo_login TIMESTAMP,

    activo BOOLEAN NOT NULL DEFAULT TRUE,

    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_empleado)
        REFERENCES empleado(id_empleado)
        ON DELETE CASCADE
);

-- =====================================================
-- TABLA EMPLEADO_SUCURSAL
-- =====================================================

CREATE TABLE empleado_sucursal (
    id_empleado_sucursal BIGSERIAL PRIMARY KEY,

    id_empleado BIGINT NOT NULL,
    id_sucursal BIGINT,

    rol rol_enum NOT NULL,

    fecha_inicio DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_fin DATE,

    activo BOOLEAN NOT NULL DEFAULT TRUE,

    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (id_empleado)
        REFERENCES empleado(id_empleado)
        ON DELETE CASCADE,

    FOREIGN KEY (id_sucursal)
        REFERENCES sucursal(id_sucursal)
        ON DELETE RESTRICT,

    CONSTRAINT chk_fechas_asignacion
    CHECK (
        fecha_fin IS NULL OR fecha_fin >= fecha_inicio
    ),

    CONSTRAINT chk_super_admin_sucursal
    CHECK (
        (rol = 'SUPER_ADMIN' AND id_sucursal IS NULL)
        OR
        (rol <> 'SUPER_ADMIN' AND id_sucursal IS NOT NULL)
    ),

    CONSTRAINT chk_activo_fecha_fin
    CHECK (
        (activo = TRUE AND fecha_fin IS NULL)
        OR
        (activo = FALSE AND fecha_fin IS NOT NULL)
    )
);

CREATE TABLE caja_cash (
    id_caja_cash BIGSERIAL PRIMARY KEY,
    id_empleado BIGINT NOT NULL UNIQUE,
    pin VARCHAR(20) NOT NULL,

    FOREIGN KEY (id_empleado)
        REFERENCES empleado(id_empleado)
        ON DELETE RESTRICT,

    CHECK (TRIM(pin) <> '')
);
-- =====================================================
-- INDICES
-- =====================================================

CREATE UNIQUE INDEX uq_admin_sucursal
ON empleado_sucursal(id_sucursal)
WHERE rol = 'SUPER_ADMIN' AND activo = TRUE;

CREATE UNIQUE INDEX uq_super_admin
ON empleado_sucursal(rol)
WHERE rol = 'SUPER_ADMIN' AND activo = TRUE;

CREATE UNIQUE INDEX uq_empleado_sucursal_activo
ON empleado_sucursal(id_empleado, id_sucursal)
WHERE activo = TRUE;

CREATE UNIQUE INDEX uq_empleado_sucursal_rol_activo
ON empleado_sucursal(id_empleado, id_sucursal, rol)
WHERE activo = TRUE;

CREATE UNIQUE INDEX uq_usuario_username_lower
ON usuario(LOWER(username));

CREATE INDEX idx_empleado_estado ON empleado(estado);
CREATE INDEX idx_usuario_activo ON usuario(activo);

-- =====================================================
-- FUNCIONES
-- =====================================================

CREATE OR REPLACE FUNCTION generar_codigo(prefijo TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN prefijo || LPAD(nextval('seq_codigo_global')::TEXT, 5, '0');
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION actualizar_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;



-- =====================================================
-- TRIGGERS CODIGO (CORREGIDO)
-- =====================================================

CREATE OR REPLACE FUNCTION trg_set_codigo_sucursal()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.codigo IS NULL OR TRIM(NEW.codigo) = '' THEN
        NEW.codigo := generar_codigo('SUC_');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_set_codigo_empleado()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.codigo IS NULL OR TRIM(NEW.codigo) = '' THEN
        NEW.codigo := generar_codigo('EMP_');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_set_codigo_usuario()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.codigo IS NULL OR TRIM(NEW.codigo) = '' THEN
        NEW.codigo := generar_codigo('USR_');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- ASIGNACION TRIGGERS
-- =====================================================

CREATE TRIGGER trg_sucursal_codigo
BEFORE INSERT ON sucursal
FOR EACH ROW EXECUTE FUNCTION trg_set_codigo_sucursal();

CREATE TRIGGER trg_empleado_codigo
BEFORE INSERT ON empleado
FOR EACH ROW EXECUTE FUNCTION trg_set_codigo_empleado();

CREATE TRIGGER trg_usuario_codigo
BEFORE INSERT ON usuario
FOR EACH ROW EXECUTE FUNCTION trg_set_codigo_usuario();

CREATE TRIGGER trg_sucursal_update
BEFORE UPDATE ON sucursal
FOR EACH ROW EXECUTE FUNCTION actualizar_timestamp();

CREATE TRIGGER trg_empleado_update
BEFORE UPDATE ON empleado
FOR EACH ROW EXECUTE FUNCTION actualizar_timestamp();

CREATE TRIGGER trg_usuario_update
BEFORE UPDATE ON usuario
FOR EACH ROW EXECUTE FUNCTION actualizar_timestamp();

CREATE TRIGGER trg_empleado_sucursal_update
BEFORE UPDATE ON empleado_sucursal
FOR EACH ROW EXECUTE FUNCTION actualizar_timestamp();


CREATE OR REPLACE PROCEDURE dar_baja_empleado(p_id BIGINT)
LANGUAGE plpgsql
AS $$
BEGIN
    -- Suspender empleado
    UPDATE empleado
    SET estado = 'SUSPENDIDO',
        fecha_baja = CURRENT_DATE
    WHERE id_empleado = p_id;

    -- Desactivar usuario
    UPDATE usuario
    SET activo = FALSE
    WHERE id_empleado = p_id;

    -- Cerrar asignaciones activas
    UPDATE empleado_sucursal
    SET activo = FALSE,
        fecha_fin = CURRENT_DATE
    WHERE id_empleado = p_id
      AND activo = TRUE;
END;
$$;

CREATE OR REPLACE PROCEDURE reactivar_empleado(p_id BIGINT)
LANGUAGE plpgsql
AS $$
BEGIN
    -- Reactivar empleado
    UPDATE empleado
    SET estado = 'ACTIVO',
        fecha_baja = NULL
    WHERE id_empleado = p_id;

    -- Reactivar usuario
    UPDATE usuario
    SET activo = TRUE
    WHERE id_empleado = p_id;

    -- Reactivar asignaciones (sin fecha_fin)
    UPDATE empleado_sucursal
    SET activo = TRUE,
        fecha_fin = NULL
    WHERE id_empleado = p_id;
END;
$$;