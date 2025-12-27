-- V4: Crear tablas de reembolsos
CREATE TABLE IF NOT EXISTS reembolsos (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empacador_id UUID NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notas VARCHAR(500),
    estado VARCHAR(50) NOT NULL DEFAULT 'CONFIRMADO',
    CONSTRAINT fk_reembolso_empacador
    FOREIGN KEY (empacador_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT reembolsos_estado_check
    CHECK (estado IN ('CONFIRMADO', 'CANCELADO'))
    );

CREATE TABLE IF NOT EXISTS reembolso_items (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reembolso_id UUID NOT NULL,
    producto_id UUID NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    CONSTRAINT fk_reembolso_item_reembolso
    FOREIGN KEY (reembolso_id) REFERENCES reembolsos(id) ON DELETE CASCADE,
    CONSTRAINT fk_reembolso_item_producto
    FOREIGN KEY (producto_id) REFERENCES products(id) ON DELETE RESTRICT
    );

CREATE INDEX IF NOT EXISTS idx_reembolsos_empacador_fecha
    ON reembolsos(empacador_id, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_reembolso_items_reembolso
    ON reembolso_items(reembolso_id);

CREATE INDEX IF NOT EXISTS idx_reembolso_items_producto
    ON reembolso_items(producto_id);
