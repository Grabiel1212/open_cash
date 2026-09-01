-- =====================================================
-- INSERTS MAESTROS
-- Escenario:
-- 1 SUPER_ADMIN
-- 2 sucursales: Cieneguilla y Musa
-- Cada sucursal:
--   1 ADMIN_SUCURSAL
--   2 OPERADORES
--   3 CAJEROS
-- =====================================================

-- =====================================================
-- SUCURSALES
-- =====================================================
INSERT INTO sucursal (
    nombre,
    nombre_comercial,
    direccion,
    departamento,
    provincia,
    distrito,
    telefono,
    email,
    foto,
    descripcion,
    activo
)
VALUES
(
    'UTILMARKET CIENEGUILLA',
    'Av. San Martín 15026, Tambo Viejo - Cieneguilla',
    'Av. San Martín 15026, Tambo Viejo - Cieneguilla',
    'Lima',
    'Lima',
    'Cieneguilla',
    '954918628',
    'utilmarketcieneguillatamboviejo@gmail.com',
    '/sucursales/tambo.avif',
    'UtilMarket Tambo Viejo es tu tienda de confianza en Cieneguilla. Encuentra una amplia variedad de útiles escolares, artículos de oficina, papelería, librería, materiales para manualidades, tecnología, impresiones, fotocopias y mucho más, con productos de calidad para estudiantes, familias, emprendedores y empresas.',
    TRUE
),
(
    'UTILMARKET PARADERO 1',
    'Av. Cieneguilla 15594, Paradero 1 - Cieneguilla',
    'Av. Cieneguilla 15594, Paradero 1 - Cieneguilla',
    'Lima',
    'Lima',
    'Cieneguilla',
    '913054483',
    'utilmarketcieneguillaparadero1@gmail.com',
    '/sucursales/paradero1.avif',
    'UtilMarket Paradero 1 ofrece una gran selección de útiles escolares, papelería, artículos de oficina, librería, materiales de arte, tecnología, impresiones y productos para el día a día. Todo lo que necesitas en un solo lugar para el estudio, el trabajo y el hogar.',
    TRUE
),
(
    'UTILMARKET MUSA',
    'Calle Las Araucarias Mz. 12, Urb. Musa - La Molina',
    'Calle Las Araucarias Mz. 12, Urb. Musa - La Molina',
    'Lima',
    'Lima',
    'La Molina',
    '986827353',
    'utilmarketcieneguillamusa@gmail.com',
    '/sucursales/musa.avif',
    'UtilMarket Musa, en La Molina, pone a tu disposición una completa variedad de útiles escolares, artículos de oficina, papelería, librería, materiales creativos, tecnología, impresiones y productos esenciales para estudiantes, profesionales, negocios y familias.',
    TRUE
);



-- =====================================================
-- EMPLEADOS
-- =====================================================

INSERT INTO empleado (nombre, apellido, dni, telefono, correo)
VALUES
-- SUPER ADMIN
('RICARDO', 'VERGARA CACERES', '10610778', '996778134', 'alinaj37@gmail.com'),

('JOSE', 'ABAD VERGARA', '46934720', '969952562', 'josevergara@gmail.com'),
('JUAN GRABIEL', 'BARBOZA RIVERA', '75392895', '968124043', 'juangrabielbarbozarivera12@gmail.com'),
('ALEJANDRO', 'GONZALEZ SERGIO', '001241242', '900000008', 'alejandrosergio@gmail.com'),
('RUMI MAYURI', 'FERNANDEZ ATAUJE', '75281679', '900000008', 'rumifernnadez@gmail.com'),
('SAHORI', 'GATICA PERDOMO ', '60885704', '965834253', 'sahoriperdomo@gmail.com'),
('VALERIA', 'PISCO GUZMAN', '73983030', '900000009', 'valeriagusman@gmail.com'),
('TANI MELISSA', 'SALVADOR RAMOS', '75115837', '900000011', 'tamisalvador@gmail.com'),
('CAMILA ESTHER', 'ZAPATA VARGAS', '74010153', '900000009', 'vargaszapata@gmail.com');

-- =====================================================
-- USUARIOS
-- =====================================================
select * from empleado;
SELECT * FROM usuario;
INSERT INTO usuario (id_empleado, username, clave)
VALUES
(1, 'RICARDO', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(2, 'JOSE', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(3, 'JUAN', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(4, 'ALEJANDRO', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(5, 'RUMI', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(6, 'SAHORI', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(7, 'VALERIA', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(8, 'TANI', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),
(9, 'CAMILA', '$2a$10$EFkXI4DLl.Sb83/FSsO6R.44yrD/AR7RXbXD/10D4hZTq2FTrqkMy'),



-- =====================================================
-- ASIGNACIONES EMPLEADO_SUCURSAL
-- =====================================================

INSERT INTO empleado_sucursal (id_empleado, id_sucursal, rol, activo)
VALUES
-- SUPER ADMIN
(1, NULL, 'SUPER_ADMIN', TRUE),


(2, 1, 'SUPERVISOR', TRUE),
(2, 2, 'SUPERVISOR', TRUE),
(2, 3, 'SUPERVISOR', TRUE),


(3, 1, 'SUPERVISOR', TRUE),
(3, 2, 'SUPERVISOR', TRUE),
(3, 3, 'SUPERVISOR', TRUE),


(4, 1, 'CAJERO', TRUE),
(5, 1, 'CAJERO', TRUE),

(6, 3, 'CAJERO', TRUE),
(6, 2, 'CAJERO', TRUE),

(7, 1, 'SUPERVISOR', TRUE),
(7, 2, 'SUPERVISOR', TRUE),
(7, 3, 'SUPERVISOR', TRUE),

(8, 1, 'CAJERO', TRUE),
(9, 2, 'CAJERO', TRUE),





select * from empleado;
SELECT * FROM empleado_sucursal;
SELECT * FROM sucursal;
select * from usuario;

CALL dar_baja_empleado(10);
CALL reactivar_empleado(10);

SELECT 
    e.id_empleado,
    CONCAT(e.nombre, ' ', e.apellido) AS empleado,
    s.nombre AS sucursal,
    es.rol
FROM empleado e
JOIN empleado_sucursal es 
    ON e.id_empleado = es.id_empleado
JOIN sucursal s
    ON es.id_sucursal = s.id_sucursal
WHERE e.id_empleado = 10
  AND es.activo = TRUE;
