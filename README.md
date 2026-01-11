# Sistema de Gestión Vitalexa – Backend

Backend empresarial desarrollado en **Java 17 + Spring Boot**, orientado a la gestión de **órdenes, inventario, clientes y métricas**, con control de acceso por roles, autenticación JWT y arquitectura modular preparada para entornos productivos.

---

## 🎯 Objetivo del proyecto

Diseñar e implementar un backend **escalable, mantenible y seguro**, aplicando buenas prácticas de arquitectura, separación de responsabilidades y control de acceso, simulando un entorno real de PyME.

---

## 🧱 Arquitectura y principios

- Arquitectura por capas:
  - Controller
  - Service
  - Repository
- Separación clara entre:
  - Entidades
  - DTOs
  - Lógica de negocio
- Principios aplicados:
  - Clean Code
  - SOLID
  - RESTful APIs
- Diseño orientado a roles y flujos reales de negocio

---

## ⚙️ Stack tecnológico

### Backend
- Java 17
- Spring Boot
- Spring Security + JWT
- Spring Data JPA (Hibernate)
- MapStruct (mapeo Entity ⇆ DTO)
- Lombok
- PostgreSQL
- Flyway / Liquibase (migraciones)

### Infraestructura
- Docker
- Railway (deploy backend)
- Configuración por perfiles (`dev`, `prod`)
- Cloudinary (almacenamiento de recursos)

---

## 🔐 Seguridad

- Autenticación basada en JWT
- Autorización por roles:
  - `ADMIN`
  - `OWNER`
  - `VENDEDOR`
  - `EMPACADOR`
- Protección de endpoints mediante `@PreAuthorize`
- Manejo centralizado de errores y validaciones

---

## 🗂️ Estructura del proyecto
```text
src/main/java/org/example/sistema_gestion_vitalexa
├── config
├── controller
│ ├── admin
│ ├── owner
│ ├── vendedor
│ ├── AuthController
│ ├── EmpacadorController
│ ├── ImageController
│ ├── ReportController
│ └── ReportExportController
├── dto
├── entity
├── enums
├── exceptions
├── mapper
├── repository
├── security
└── service
```


---

## 🔄 Flujo principal de negocio

1. El **Vendedor** crea una orden.
2. El **Administrador** valida y confirma.
3. El sistema:
   - Actualiza inventario
   - Genera factura PDF
   - Actualiza métricas y metas
4. El **Owner** analiza reportes y rendimiento.
5. El **Empacador** gestiona devoluciones controladas.

---

## 📡 API REST (resumen)

- Autenticación JWT (`/api/auth`)
- Gestión de órdenes (admin, owner, vendedor)
- Gestión de productos e inventario
- CRM básico
- Metas de ventas por usuario
- Reportes analíticos
- Exportación de datos (PDF / Excel / CSV)
- Servicio público de imágenes

📄 **Documentación completa de endpoints** incluida en este repositorio.

---

## 📊 Reportes y exportaciones

- Reportes por rango de fechas (ISO `yyyy-MM-dd`)
- Reportes:
  - Ventas
  - Productos
  - Clientes
  - Vendedores
- Exportación en:
  - PDF
  - Excel
  - CSV
- Manejo de headers HTTP (`Content-Disposition`)

---

## 🚀 Despliegue

- Contenerizado con Docker
- Desplegado en Railway
- Preparado para CI/CD
- Configuración externa mediante variables de entorno

---

## 👨‍💻 Autor

**José Alberto Méndez Domínguez**  
Estudiante de Ingeniería de Software (8° semestre)  
Backend Developer – Java & Spring Boot  
Santa Marta, Colombia


