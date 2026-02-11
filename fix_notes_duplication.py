import re

# Leer el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\OrderServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Buscar y reemplazar la sección problemática
old_section = '''        notes.append("Pagado: $").append(request.amountPaid())
                .append(" | Debe: $").append(amountDue);

        if (request.notes() != null && !request.notes().isBlank()) {
            notes.append(" - ").append(request.notes());
        }
        notes.append(" ").append(request.invoiceType().getSuffix());'''

new_section = '''        notes.append("Pagado: $").append(request.amountPaid())
                .append(" | Debe: $").append(amountDue);

        // NO agregamos request.notes() aquí para evitar duplicación
        // El usuario maneja la nota completa en el frontend
        notes.append(" ").append(request.invoiceType().getSuffix());'''

if old_section in content:
    content = content.replace(old_section, new_section)
    print("✓ Note duplication fix applied!")
else:
    print("✗ Section not found, checking variations...")
    # Intentar sin espacios exactos
    old_alt = '''notes.append("Pagado: $").append(request.amountPaid()).append(" | Debe: $").append(amountDue);if(request.notes()!=null&&!request.notes().isBlank()){notes.append(" - ").append(request.notes());}notes.append(" ").append(request.invoiceType().getSuffix());'''
    content_normalized = re.sub(r'\s+', '', content)
    if old_alt in content_normalized:
        print("Found alternative format")

# Guardar el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\OrderServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("File processed!")
