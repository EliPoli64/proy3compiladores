# Script de Automatización para el Proyecto de Compiladores

Write-Host "--- Iniciando Generación de Código ---" -ForegroundColor Cyan

# 1. Generar Lexer con JFlex
Write-Host "1. Ejecutando JFlex..."
java -jar lib/jflex-full-1.9.1.jar src/jflex.flex

# 2. Generar Parser con CUP
Write-Host "2. Ejecutando Java CUP..."
java -jar lib/java-cup-11b.jar -destdir src -parser parser -symbols sym -expect 10 src/cup.cup

Write-Host "--- Iniciando Compilación ---" -ForegroundColor Cyan

# 3. Crear carpeta bin si no existe
if (!(Test-Path bin)) {
    New-Item -ItemType Directory bin
}

# 4. Compilar todo
Write-Host "3. Compilando archivos .java..."
javac -d bin -cp "lib/*;src" src/*.java

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n¡ÉXITO! Todo se ha generado y compilado correctamente." -ForegroundColor Green
    Write-Host "Puedes ejecutarlo usando: java -cp `"bin;lib/*`" Main test_input.txt"
} else {
    Write-Host "`nERROR en la compilación." -ForegroundColor Red
}
