# Documentación interna del Proyecto: Analizador Sintáctico

Este proyecto implementa un analizador sintáctico utilizando **JFlex** y **Java CUP**. El sistema lee un archivo fuente, identifica los tokens definidos en la especificación léxica, y genera un árbol de sintaxis y las tablas de símbolos por scope.

## Estructura del Proyecto

*   **`src/`**: Código fuente (`.java`, `.flex`, `.cup`).
*   **`lib/`**: Librerías y herramientas necesarias (`jflex`, `cup`).
*   **`bin/`**: Archivos de clase compilados (`.class`).
*   **`base.c`**: Archivo de prueba con código ejemplo.
*   **`arbol.txt`**: Archivo generado con el árbol de sintaxis.
*   **`tablaSimbolos.txt`**: Archivo generado con las tablas de símbolos.

## Requisitos

*   **JDK**: Debe estar instalado y configurado en el PATH del sistema.

> **Nota**: No es necesario instalar JFlex o CUP en el sistema, ya que el proyecto utiliza las versiones incluidas en la carpeta `lib/`.

## Instrucciones de Compilación

### 1. Generar Código Fuente (Lexer y Parser)

Genera las clases `LexerProyUno.java`, `parser.java` y `sym.java` a partir de las especificaciones:

```powershell
java -jar lib/jflex-full-1.9.1.jar src/jflex.flex

java -jar lib/java-cup-11b.jar -destdir src -parser parser -symbols sym src/cup.cup
```

### 2. Compilar JAR

Compila el código fuente y empaqueta todo en un .jar:

```powershell
mkdir bin

javac -d bin -cp "lib/*" src/*.java
# Si jar está en el path del sistema:
jar cvfm proy2compiladores.jar manifest.txt -C bin .
# Si jar no está en el path del sistema, se puede usar esto en Windows:
"C:\Program Files\Java\jdk-21\bin\jar.exe" cfm proy2compiladores.jar manifest.txt -C bin .
```

## Instrucciones de Ejecución

Para ejecutar el analizador, ejecute este comando:

### Sintaxis
```powershell
java -jar proy1compiladores.jar <nombre del archivo a tokenizar>  
```

### Ejemplo de Uso
Para analizar el archivo de prueba `base.c`:

```powershell
java -jar proy1compiladores.jar base.c
```

## Salida y Resultados

El analizador sintáctico genera tres archivos de salida:

### 1. **`salida.txt`** - Secuencia de Tokens

Contiene todos los tokens identificados por el analizador léxico. Formato:
```text
Token - Lexema (si aplica) - Linea - Columna   
```

**Ejemplo:**
```text
WORLD                                         1          1         
STRING                                       1          7         
IDENTIFIER       _s1_                        1          14        
ENDL                                         1          18        
COMMENT_SINGLE   comentario linea            1          23        
COMMENT_MULTI    є !@#$$%^& multilinea э    2          1         
GIFT                                         4          1         
FLOAT                                        4          6         
IDENTIFIER       _mi_                        4          12        
```

### 2. **`arbol.txt`** - Árbol de Sintaxis

Representa la estructura jerárquica del programa analizado. Formato:
```
└── [program]
    ├── [globales]
    │   ├── [WORLD]
    │   ├── [STRING]
    │   └── IDENTIFIER [_s1_]
    └── [funciones]
        └── [funcion]
            ├── [GIFT]
            ├── [float]
            └── IDENTIFIER [_mi_]
```

Muestra:
- Declaraciones globales y funciones
- Estructura de parámetros
- Bloques de código y sentencias
- Expresiones y operadores anidados

### 3. **`tablaSimbolos.txt`** - Tablas de Símbolos por Scope

Documenta todos los identificadores encontrados, organizados por ámbito (scope).

**Formato:**
```
=== TABLAS DE SIMBOLOS ===

Ambito: <nombre_scope>
NOMBRE          TIPO            ROL             AMBITO         
----------------------------------------------------------------
<identificador> <tipo>          <rol>           <scope>
```

**Roles posibles:**
- Variable Global
- Variable Local
- Parametro
- Array Local
- Array Global

**Ejemplo:**
```
=== TABLAS DE SIMBOLOS ===

Ambito: _mi_
NOMBRE          TIPO            ROL             AMBITO         
----------------------------------------------------------------
_dif_           int             Parametro       _mi_           
_otra_          char            Parametro       _mi_           
miArr           int             Array Local     _mi_           

Ambito: Global
NOMBRE          TIPO            ROL             AMBITO         
----------------------------------------------------------------
_s1_            string          Variable Global Global         
==========================
```

## Ejemplo Completo

Para analizar el archivo `base.c`:

```bash
java -jar proy2compiladores.jar base.c
```

Esto generará:
- **`salida.txt`**: Lista completa de tokens
- **`arbol.txt`**: Árbol sintáctico del programa
- **`tablaSimbolos.txt`**: Tablas de símbolos organizadas por scope

