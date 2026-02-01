import java.io.*;
import java.nio.file.*;
import java.util.*;

public class EscritorMips {
    private PrintWriter out;
    private String funcionActual = ""; 
    private boolean llamadaNavidad = false;
    private List<String> argumentosPendientes = new ArrayList<>();
    private Map<String, Integer> stackOffsetMap = new HashMap<>(); // Mapa de variable -> offset en pila
    private Map<String, Integer> labelPositions = new HashMap<>();
    private Map<String, String> stringMap = new HashMap<>(); // Mapa de string -> label
    private Set<String> syscalls = new HashSet<>();
    private boolean hasMainLabel = false;
    private int stringCounter = 1;
    private int currentStackOffset = 0; // Para manejar offsets en la pila
    private List<String> todasLasStrings = new ArrayList<>();
    
    public void procesar(String entrada, String salida) throws IOException {
        List<String> lineas = Files.readAllLines(Paths.get(entrada));
        out = new PrintWriter(new FileWriter(salida));
        
        detectarLabels(lineas);
        
        extraerStringsRobusto(lineas);
        detectarSyscalls(lineas);
        calcularVariablesEnPila(lineas);
        
        // Sección .data - solo strings
        out.println(".data");
        out.println("    nl: .asciiz \"\\n\"");
        out.println("    true_str: .asciiz \"true\"");
        out.println("    false_str: .asciiz \"false\"");
        
        // Declarar todas las strings encontradas
        for (String str : todasLasStrings) {
            String label = stringMap.get(str);
            out.println("    " + label + ": .asciiz \"" + escapeString(str) + "\"");
        }
        
        out.println("\n.text\nmain:");
        
        // Reservar espacio en la pila para todas las variables
        // (incluyendo las declaradas con LOCAL)
        if (currentStackOffset > 0) {
            out.println("    addiu $sp, $sp, -" + currentStackOffset);
            out.println("    move $fp, $sp");
            
            // Inicializar variables locales a 0 (opcional)
            out.println("    # Inicializar espacio de variables locales");
            out.println("    move $t0, $fp");
            out.println("    li $t1, 0");
            for (int i = 0; i < currentStackOffset / 4; i++) {
                out.println("    sw $t1, " + (i * 4) + "($t0)");
            }
        }

        if (!hasMainLabel) {
            out.println("\ninicio_codigo:");
        }

        funcionActual = "";
        
        traducirCodigo(lineas);
        
        // Liberar pila y terminar
        out.println("\n    # Liberar pila y terminar");
        if (currentStackOffset > 0) {
            out.println("    addiu $sp, $sp, " + currentStackOffset);
        }
        out.println("    li $v0, 10");
        out.println("    syscall");
        
        generarSyscalls();
        
        out.close();
    }
    
    private void calcularVariablesEnPila(List<String> lineas) {
        currentStackOffset = 0;
        
        for (String linea : lineas) {
            String l = linea.trim();
            if (l.isEmpty() || l.endsWith(":")) continue;
            
            List<String> tokens = dividirLineaConStrings(l);
            
            // Buscar variables en la línea
            for (String token : tokens) {
                if (esVariable(token) && !stackOffsetMap.containsKey(token)) {
                    // Asignar espacio en la pila para la variable
                    stackOffsetMap.put(token, currentStackOffset);
                    currentStackOffset += 4; // 4 bytes por variable (palabra)
                }
            }
        }
        
        System.out.println("Total espacio en pila reservado: " + currentStackOffset + " bytes");
    }
    
    private boolean esVariable(String token) {
        if (token == null || token.isEmpty()) return false;
        
        // No es un número
        if (token.matches("-?\\d+")) return false;
        
        // No es una string literal
        if (token.startsWith("\"") || token.startsWith("'")) return false;
        
        // No es una keyword
        if (esKeyword(token)) return false;
        
        // No es una label
        if (labelPositions.containsKey(token)) return false;
        
        // No es un operador
        if (token.equals("=") || token.equals("+") || token.equals("-") || 
            token.equals("*") || token.equals("/") || token.equals("%") ||
            token.equals("<") || token.equals(">") || token.equals("<=") || 
            token.equals(">=") || token.equals("==") || token.equals("!=")) {
            return false;
        }
        
        // No es una instrucción especial
        if (token.equals("PRINT") || token.equals("RETURN") || token.equals("IF") || 
            token.equals("NOT") || token.equals("GOTO") || token.equals("CALL") ||
            token.equals("PRINTFLOAT") || token.equals("PARAM")) {
            return false;
        }
        
        // Es un identificador válido para variable
        return token.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }
    private void detectarLabels(List<String> lineas) {
        for (String linea : lineas) {
            String l = linea.trim();
            if (l.isEmpty()) continue;
            
            // Buscar labels (terminan con :)
            if (l.endsWith(":")) {
                String label = l.replace(":", "").trim();
                
                if (!label.isEmpty()) {
                    labelPositions.put(label, 0);
                    
                    if (label.equals("navidad")) {
                        hasMainLabel = true;
                    }
                }
            }
        }
    }
    // Método principal para cargar registros (para uso en pila)
    private void cargarRegistroPila(String registro, String valor) {
        if (esNumero(valor)) {
            // Si es un número, usar li
            out.println("    li " + registro + ", " + valor);
        } else if (esFloat(valor)) {
            if (registro.startsWith("$f")) {
                // Para registros de punto flotante ($f0-$f31)
                out.println("    li.s " + registro + ", " + valor);
            } else {
                // Para registros enteros, convertir float a int
                float floatValue = Float.parseFloat(valor);
                int intValue = (int) floatValue;
                out.println("    li " + registro + ", " + intValue);
            }
        } else if (esStringLiteral(valor)) {
            // Si es una string literal
            String str = extraerStringDeLiteral(valor);
            String label = obtenerLabelString(str);
            out.println("    la " + registro + ", " + label);
        } else if (esVariable(valor)) {
            // Si es una variable en la pila
            if (!stackOffsetMap.containsKey(valor)) {
                // Variable nueva, asignar espacio en la pila
                asignarEspacioPila(valor);
            }
            int offset = stackOffsetMap.get(valor);
            if (registro.startsWith("$f")) {
                // Cargar float desde la pila a registro de punto flotante
                out.println("    lwc1 " + registro + ", " + offset + "($fp)");
            } else {
                // Cargar entero desde la pila a registro entero
                out.println("    lw " + registro + ", " + offset + "($fp)");
            }
        } else if (esLabel(valor)) {
            // Si es una label (para saltos)
            out.println("    la " + registro + ", " + valor);
        } else if (valor.equals("true") || valor.equals("false")) {
            // Manejar booleanos
            if (valor.equals("true")) {
                out.println("    li " + registro + ", 1");
            } else {
                out.println("    li " + registro + ", 0");
            }
        } else {
            // Valor no reconocido
            out.println("    # Error: valor no reconocido '" + valor + "'");
        }
    }
    

    private void extraerStringsRobusto(List<String> lineas) {
        for (String linea : lineas) {
            List<String> stringsEnLinea = extraerStringsDeLinea(linea);
            for (String str : stringsEnLinea) {
                if (!stringMap.containsKey(str)) {
                    String label = "str_" + stringCounter;  // str_1, str_2, etc.
                    stringMap.put(str, label);
                    todasLasStrings.add(str);

                    stringCounter++;
                }
            }
        }
    }

    private boolean esNumero(String token) {
        if (token == null || token.isEmpty()) return false;
        
        // Enteros positivos o negativos
        if (token.matches("-?\\d+")) return true;
        
        // Números hexadecimales
        if (token.matches("-?0[xX][0-9a-fA-F]+")) return true;
        
        // Números binarios
        if (token.matches("-?0[bB][01]+")) return true;
        
        return false;
    }
    private void cargarRegistro(String registro, String valor) {
        if (valor == null || valor.isEmpty() || valor.equals("NAVIDAD")) {
            return;
        }
        
        // Si es una variable no declarada, declararla automáticamente
        if (esVariable(valor) && !stackOffsetMap.containsKey(valor)) {
            asignarEspacioPila(valor);
            out.println("    # Variable '" + valor + "' creada automáticamente");
        }
        
        // Determinar si es float
        boolean esFloatValor = valor.endsWith("_f") || esFloat(valor) || 
                            (stackOffsetMap.containsKey(valor) && esVariableFloat(valor));
        
        // Para registros de float
        if (registro.startsWith("$f")) {
            if (esFloatValor) {
                if (valor.endsWith("_f")) {
                    String valorSinSufijo = valor.substring(0, valor.length() - 2);
                    out.println("    li.s " + registro + ", " + valorSinSufijo);
                } else if (esFloat(valor)) {
                    out.println("    li.s " + registro + ", " + valor);
                } else if (stackOffsetMap.containsKey(valor)) {
                    int offset = stackOffsetMap.get(valor);
                    out.println("    lwc1 " + registro + ", " + offset + "($fp)");
                } else if (valor.equals("true")) {
                    out.println("    li.s " + registro + ", 1.0");
                } else if (valor.equals("false")) {
                    out.println("    li.s " + registro + ", 0.0");
                } else if (esNumero(valor)) {
                    // Convertir entero a float
                    out.println("    li $t0, " + valor);
                    out.println("    mtc1 $t0, " + registro);
                    out.println("    cvt.s.w " + registro + ", " + registro);
                }
            } else {
                // No es float, cargar como entero y convertir
                cargarRegistro(registro.replace("$f", "$t"), valor);
                out.println("    mtc1 " + registro.replace("$f", "$t") + ", " + registro);
                out.println("    cvt.s.w " + registro + ", " + registro);
            }
        } 
        // Para registros enteros
        else {
            if (esFloatValor) {
                // Convertir float a entero
                String tempFloatReg = "$f" + registro.substring(2);
                if (valor.endsWith("_f")) {
                    String valorSinSufijo = valor.substring(0, valor.length() - 2);
                    out.println("    li.s " + tempFloatReg + ", " + valorSinSufijo);
                } else if (stackOffsetMap.containsKey(valor)) {
                    int offset = stackOffsetMap.get(valor);
                    out.println("    lwc1 " + tempFloatReg + ", " + offset + "($fp)");
                }
                out.println("    cvt.w.s " + tempFloatReg + ", " + tempFloatReg);
                out.println("    mfc1 " + registro + ", " + tempFloatReg);
            } else if (valor.equals("true")) {
                out.println("    li " + registro + ", 1");
            } else if (valor.equals("false")) {
                out.println("    li " + registro + ", 0");
            } else if (esNumero(valor)) {
                out.println("    li " + registro + ", " + valor);
            } else if (esStringLiteral(valor)) {
                String str = extraerStringDeLiteral(valor);
                String label = obtenerLabelString(str);
                out.println("    la " + registro + ", " + label);
            } else if (stackOffsetMap.containsKey(valor)) {
                int offset = stackOffsetMap.get(valor);
                out.println("    lw " + registro + ", " + offset + "($fp)");
            } else {
                out.println("    # Valor no reconocido: " + valor);
            }
        }
    }

    private boolean esVariableFloat(String nombreVariable) {
        // Estrategias para identificar variables float:
        
        // 1. Por convención de nombres
        String[] floatSuffixes = {"_f", "_float", "Float", "flt"};
        String nombreLower = nombreVariable.toLowerCase();
        
        for (String suffix : floatSuffixes) {
            if (nombreLower.endsWith(suffix)) {
                return true;
            }
        }
        
        // 2. Por nombres comunes de variables float
        String[] floatNames = {"pi", "e", "epsilon", "delta", "ratio", "average", "mean"};
        for (String name : floatNames) {
            if (nombreLower.contains(name)) {
                return true;
            }
        }
                
        return false;
    }

    private boolean esFloat(String token) {
        if (token == null || token.isEmpty()) return false;
        
        // Números en formato decimal con punto
        if (token.matches("-?\\d+\\.\\d+")) return true;
        
        // Números en formato científico
        if (token.matches("-?\\d+\\.\\d+[eE][-+]?\\d+")) return true;
        
        return false;
    }
    
    private boolean esStringLiteral(String token) {
        if (token == null || token.isEmpty()) return false;
        
        // Comillas simples o dobles
        if ((token.startsWith("\"") && token.endsWith("\"")) ||
            (token.startsWith("'") && token.endsWith("'"))) {
            return true;
        }
        
        return false;
    }
    
    private String extraerStringDeLiteral(String literal) {
        if (literal.length() < 2) return "";
        
        // Eliminar las comillas del inicio y final
        return literal.substring(1, literal.length() - 1);
    }
    
    private String obtenerLabelString(String str) {
        if (!stringMap.containsKey(str)) {
            // Crear nueva label para la string
            String newLabel = "str" + (stringMap.size() + 1);
            stringMap.put(str, newLabel);
        }
        return stringMap.get(str);

    }
    
    
    private boolean esLabel(String token) {
        return labelPositions.containsKey(token);
    }
    
    private void asignarEspacioPila(String variable) {
        stackOffsetMap.put(variable, currentStackOffset);
        currentStackOffset += 4; // 4 bytes por variable
    }
    
    private List<String> extraerStringsDeLinea(String linea) {
        List<String> strings = new ArrayList<>();
        boolean dentroDeString = false;
        StringBuilder strActual = new StringBuilder();
        char comillaTipo = '\0';
        
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            
            if (!dentroDeString) {
                if (c == '"' || c == '\'') {
                    dentroDeString = true;
                    comillaTipo = c;
                }
            } else {
                if (c == comillaTipo) {
                    if (i > 0 && linea.charAt(i-1) == '\\') {
                        strActual.deleteCharAt(strActual.length() - 1);
                        strActual.append(c);
                    } else {
                        dentroDeString = false;
                        strings.add(strActual.toString());
                        strActual = new StringBuilder();
                    }
                } else {
                    strActual.append(c);
                }
            }
        }
        
        return strings;
    }
    
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\t", "\\t");
    }
    
    private void detectarSyscalls(List<String> lineas) {
        for (String linea : lineas) {
            if (linea.contains("PRINT ")) {
                syscalls.add("printStr");
                syscalls.add("printInt");

            }
            if (linea.contains("PRINTFLOAT ")) {
                syscalls.add("printStr");
                syscalls.add("printFloat");

            }
            if (linea.contains("printInt") || (linea.contains("PRINT ") && !contieneString(linea))) {
                syscalls.add("printInt");
            }
            if (linea.contains("printFloat")) {
                syscalls.add("printFloat");
            }
            if (linea.contains("readInt")) {
                syscalls.add("readInt");
            }
            if (linea.contains(" * ")) {
                syscalls.add("mult");
            }
            if (linea.contains(" / ")) {
                syscalls.add("div");
            }
            if (linea.contains(" ** ") || linea.contains("potencia")) {
                syscalls.add("pow");
            }
            if (linea.contains(" % ")) {
                syscalls.add("mod");
            }
        }
    }
    
    private boolean contieneString(String linea) {
        return extraerStringsDeLinea(linea).size() > 0;
    }

    

    private void procesarCallInstruccion(String linea) {
        // CALL nombreFuncion
        String nombreFuncion = linea.replace("CALL", "").trim();

        out.println("    # CALL " + nombreFuncion);

        for (int i = 0; i < argumentosPendientes.size(); i++) {
            String arg = argumentosPendientes.get(i);
            String reg = "$a" + i;
            cargarRegistro(reg, arg);   // mueve literal/temp/var a $ai
        }

        argumentosPendientes.clear();

        out.println("    sw $ra, -4($fp)");
        out.println("    jal " + nombreFuncion);
        out.println("    lw $ra, -4($fp)");
    }



    
    private void procesarPrintFloat(List<String> tokens) {
        String valor = tokens.get(1);
        
        out.println("    # PRINTFLOAT - syscall directo");
        
        if (valor.startsWith("\"")) {
            // Es una string, no un float
            String str = valor.substring(1, valor.length() - 1);
            String label = "str_" + stringMap.get(str);
            out.println("    la $a0, " + label);
            out.println("    li $v0, 4");  // Syscall para imprimir string
            out.println("    syscall");
        } else if (valor.matches("-?\\d+\\.\\d+")) {
            // Es un literal float
            out.println("    li.s $f12, " + valor);
            out.println("    li $v0, 2");  // Syscall para imprimir float
            out.println("    syscall");
        } else {
            // Es una variable en la pila
            cargarRegistroPila("$f12", valor);
            out.println("    li $v0, 2");  // Syscall para imprimir float
            out.println("    syscall");
        }
        
        // Nueva línea después de imprimir
        out.println("    la $a0, nl");
        out.println("    li $v0, 4");
        out.println("    syscall");
    }

    private void procesarReturn(List<String> tokens) {
        if (tokens.size() > 1 && tokens.get(1).equals("NAVIDAD")) {
            return;
        }

        if (!funcionActual.equals("navidad")) { 
            // Para funciones normales
            if (tokens.size() > 1) {
                String valor = tokens.get(1);
                
                // Determinar el tipo de retorno basado en el mapa de variables
                String tipoRetorno = determinarTipoRetorno(valor);
                
                // Cargar en el registro apropiado según el tipo
                if (tipoRetorno != null) {
                    switch (tipoRetorno.toLowerCase()) {
                        case "float":
                            // Para floats, usar $f0
                            cargarRegistro("$f0", valor);
                            break;
                        case "int":
                        case "bool":
                        case "char":
                            // Para enteros y booleanos, usar $v0
                            cargarRegistro("$v0", valor);
                            break;
                        case "string":
                            // Para strings, usar $v0 para la dirección
                            cargarRegistro("$v0", valor);
                            break;
                        default:
                            // Por defecto, usar $v0
                            cargarRegistro("$v0", valor);
                            break;
                    }
                } else {
                    // Si no sabemos el tipo, usar $v0 por defecto
                    cargarRegistro("$v0", valor);
                }
            } else {
                // Return sin valor
                out.println("    # Return sin valor");
            }
            
            out.println("    jr $ra");  // Retornar al llamante
        } else {
            // En navidad, no hacemos jr $ra (termina el programa)
            if (tokens.size() > 1) {
                String valor = tokens.get(1);
                if (!valor.equals("NAVIDAD")) {
                    cargarRegistro("$v0", valor);
                }
            }
        }
    }

    private String determinarTipoRetorno(String valor) {
        if (valor == null || valor.isEmpty()) {
            return null;
        }
        
        // 1. Verificar si es un número float
        if (valor.matches("-?\\d+\\.\\d+")) {
            return "float";
        }
        
        // 2. Verificar si es un número entero
        if (valor.matches("-?\\d+")) {
            return "int";
        }
        
        // 3. Verificar si es un booleano
        if (valor.equals("true") || valor.equals("false")) {
            return "bool";
        }
        
        // 4. Verificar si es un carácter
        if (valor.startsWith("'") && valor.endsWith("'") && valor.length() == 3) {
            return "char";
        }
        
        // 5. Verificar si es una string literal
        if ((valor.startsWith("\"") && valor.endsWith("\"")) ||
            (valor.startsWith("'") && valor.endsWith("'"))) {
            return "string";
        }
        
        // 6. Buscar en el mapa de variables
        if (stackOffsetMap.containsKey(valor)) {
            // El tipo podría estar en un mapa de tipos si lo tienes
            // Por ahora, usar heurísticas basadas en el nombre
            return determinarTipoPorNombreVariable(valor);
        }
        
        // 7. Verificar si es un temporal (empieza con t)
        if (valor.startsWith("t") && valor.length() > 1 && 
            Character.isDigit(valor.charAt(1))) {
            // Podríamos rastrear tipos de temporales si los guardamos
            return "int"; // Por defecto asumir int
        }
        
        return null; // Tipo desconocido
    }

    private String determinarTipoPorNombreVariable(String nombreVariable) {
        // Heurísticas para determinar tipo por nombre
        String nombreLower = nombreVariable.toLowerCase();
        
        // Variables float
        if (nombreLower.contains("float") || nombreLower.contains("_f") || 
            nombreLower.contains("flt") || nombreLower.contains("pi") ||
            nombreLower.contains("ratio") || nombreLower.contains("average") ||
            nombreLower.contains("mean") || nombreLower.contains("temp")) {
            return "float";
        }
        
        // Variables bool
        if (nombreLower.startsWith("is") || nombreLower.startsWith("has") ||
            nombreLower.startsWith("can") || nombreLower.contains("flag") ||
            nombreLower.contains("bool") || nombreLower.contains("valid") ||
            nombreLower.contains("found") || nombreLower.contains("done")) {
            return "bool";
        }
        
        // Variables char
        if (nombreLower.contains("char") || nombreLower.contains("ch") ||
            nombreLower.length() == 1 || nombreLower.equals("c")) {
            return "char";
        }
        
        // Variables string
        if (nombreLower.contains("str") || nombreLower.contains("msg") ||
            nombreLower.contains("text") || nombreLower.contains("name") ||
            nombreLower.contains("label") || nombreLower.startsWith("s_")) {
            return "string";
        }
        
        // Por defecto, asumir int
        return "int";
    }
    private void procesarDeclaracionLocal(String linea) {
        out.println("    # " + linea);
        
        // Formato: LOCAL nombre_variable : tipo
        // Ejemplo: LOCAL x : INT
        //          LOCAL temp1 : FLOAT
        
        String[] partes = linea.split(":");
        if (partes.length < 2) {
            out.println("    # Error: Formato LOCAL incorrecto");
            return;
        }
        
        String parteVariable = partes[0].trim();  // "LOCAL x"
        String tipo = partes[1].trim().toUpperCase();  // "INT", "FLOAT", etc.
        
        // Extraer el nombre de la variable
        String nombreVariable = parteVariable.replace("LOCAL", "").trim();
        
        if (nombreVariable.isEmpty()) {
            out.println("    # Error: Nombre de variable vacío");
            return;
        }
        
        // Asignar espacio en la pila para esta variable local
        if (!stackOffsetMap.containsKey(nombreVariable)) {
            int offset = currentStackOffset;
            stackOffsetMap.put(nombreVariable, offset);
            
            // Incrementar el offset según el tipo
            if (tipo.equals("INT") || tipo.equals("BOOLEAN") || tipo.equals("CHAR")) {
                currentStackOffset += 4;  // 4 bytes para enteros, booleanos, caracteres
                out.println("    # Variable local '" + nombreVariable + "' (tipo: " + tipo + 
                        ") asignada en offset " + offset + "($fp)");
            } else if (tipo.equals("FLOAT") || tipo.equals("DOUBLE")) {
                currentStackOffset += 4;  // 4 bytes para floats (MIPS single precision)
                out.println("    # Variable local '" + nombreVariable + "' (tipo: " + tipo + 
                        ") asignada en offset " + offset + "($fp)");
            } else if (tipo.equals("STRING")) {
                currentStackOffset += 4;  // 4 bytes para puntero a string
                out.println("    # Variable local string '" + nombreVariable + 
                        "' (puntero) asignada en offset " + offset + "($fp)");
            } else {
                // Por defecto, 4 bytes
                currentStackOffset += 4;
                out.println("    # Variable local '" + nombreVariable + "' (tipo desconocido: " + 
                        tipo + ") asignada en offset " + offset + "($fp)");
            }
        } else {
            out.println("    # Variable local '" + nombreVariable + "' ya está definida");
        }
    }
    
    
    private void traducirCodigo(List<String> lineas) {
        for (String linea : lineas) {
            String l = linea.trim();
            
            if (l.isEmpty()) continue;
            if (l.startsWith("GLOBAL ") || l.startsWith("ARRAY ")) {
                continue;
            }
            if (l.startsWith("LOCAL ")) {
                procesarDeclaracionLocal(l);
                continue;
            }

            out.println("\n    # " + l);

            if (l.endsWith(":")) {
                String label = l.replace(":", "");
                out.println(label + ":");
                
                funcionActual = label;
                continue;
            }
            
            
            if (l.startsWith("CALL ")) {
                procesarCallInstruccion(l);
                continue;
            }
            if (l.startsWith("READ ")) {
                procesarRead(l);
                continue;
            }
            
            if (l.startsWith("GOTO ")) {
                List<String> tokens = dividirLineaConStrings(l);
                procesarSaltoFuncion(l, tokens);
                continue;
            }
            
            if (l.equals("GOTO navidad")) {
                out.println("    jal navidad");
                continue;
            }
            
            // Procesar strings en la línea
            List<String> stringsEnLinea = extraerStringsDeLinea(l);
            for (String str : stringsEnLinea) {
                if (!stringMap.containsKey(str)) {
                    String label = "str" + stringCounter++;
                    stringMap.put(str, label);
                    todasLasStrings.add(str);
                }
            }
            
            // Dividir la línea
            List<String> tokens = dividirLineaConStrings(l);
            if (tokens.size() == 3 && tokens.get(2).equals("RET")) {
                procesarAsignacionRetorno(l, tokens);
                continue;
            }
            
            if (l.startsWith("PRINT ")) {
                procesarPrint(tokens);
            } else if (l.startsWith("PARAM ") && tokens.size() < 3) {
                System.out.println("Procesando PARAM: " + l);
                String arg = tokens.get(1);
                argumentosPendientes.add(arg);
                continue;
            } else if (l.startsWith("PRINTFLOAT ")) {
                procesarPrintFloat(tokens);
            } else if (l.startsWith("PRINTSTRING ")) {
                procesarPrintString(tokens);
            } else if (l.startsWith("RETURN ")) {
                // Manejar RETURN de funciones
                procesarReturn(tokens);
            } else if (l.startsWith("IF NOT ")) {
                String[] partes = linea.trim().split("\\s+");
                String var = partes[2];
                String label = partes[4];

                int offsetVar = stackOffsetMap.get(var);

                out.println("    # IF NOT " + var + " GOTO " + label);
                out.println("    lw $t0, " + offsetVar + "($fp)");
                out.println("    beq $t0, $zero, " + label);
            } else if (l.startsWith("IF ")) {
                String[] partes = linea.trim().split("\\s+");
                String var = partes[1];
                String label = partes[3];

                int offsetVar = stackOffsetMap.get(var);
                out.println("    # IF " + var + " GOTO " + label);
                out.println("    lw $t0, " + offsetVar + "($fp)");
                out.println("    bnez $t0, " + label);
            } else if (l.startsWith("if ")) {
                String[] partes = linea.trim().split("\\s+");
                String var = partes[1];
                String label = partes[3];

                int offsetVar = stackOffsetMap.get(var);
                out.println("    # IF " + var + " GOTO " + label);
                out.println("    lw $t0, " + offsetVar + "($fp)");
                out.println("    bnez $t0, " + label);
            } else if (l.contains(" = ")) {
                procesarAsignacion(l, tokens);
            }
        }
    }
    private void procesarAsignacionRetorno(String linea, List<String> tokens) {
        String destino = tokens.get(0);
        
        out.println("    # Asignando valor de retorno a " + destino);
        
        // Asegurar que la variable destino tiene espacio en la pila
        if (!stackOffsetMap.containsKey(destino)) {
            asignarEspacioPila(destino);
        }
        
        int offset = stackOffsetMap.get(destino);
        
        // Determinar el tipo de retorno basado en el contexto
        String tipoRetorno = determinarTipoRetorno(funcionActual);
        
        if (tipoRetorno != null && tipoRetorno.equals("float")) {
            // Para floats, el retorno está en $f0
            out.println("    swc1 $f0, " + offset + "($fp)");
        } else {
            // Para otros tipos, el retorno está en $v0
            out.println("    sw $v0, " + offset + "($fp)");
        }
    }
    private void procesarRead(String linea) {
        List<String> tokens = dividirLineaConStrings(linea);
        if (tokens.size() < 2) {
            out.println("    # Error: READ sin argumento");
            return;
        }
        
        String comando = tokens.get(0); // READ, READINT, READFLOAT, etc.
        String variable = tokens.get(1);
        
        out.println("    # " + comando + " " + variable);
        
        // Determinar el tipo de lectura
        if (comando.equals("READINT") || (comando.equals("READ") && esVariableInt(variable))) {
            procesarReadInt(variable);
        } else if (comando.equals("READFLOAT") || (comando.equals("READ") && esVariableFloat(variable))) {
            procesarReadFloat(variable);
        } else if (comando.equals("READSTRING") || (comando.equals("READ") && esVariableString(variable))) {
            procesarReadString(variable);
        } else if (comando.equals("READCHAR")) {
            procesarReadChar(variable);
        } else if (comando.equals("READBOOL")) {
            procesarReadBool(variable);
        } else {
            // Por defecto, leer como int
            procesarReadInt(variable);
        }
    }
    private void procesarReadInt(String variable) {
        out.println("    # Leer entero");
        out.println("    li $v0, 5");          // Syscall para leer entero
        out.println("    syscall");
        
        // Guardar en la variable (en la pila)
        guardarEnPila(variable, "$v0", false);        
        // Nueva línea
        out.println("    la $a0, nl");
        out.println("    li $v0, 4");
        out.println("    syscall");
    }

    private void procesarReadFloat(String variable) {
        out.println("    # Leer float");
        out.println("    li $v0, 6");          // Syscall para leer float
        out.println("    syscall");
        
        // Guardar en la variable (float se guarda en $f0)
        guardarEnPila(variable, "$f0", true);
        
        
        // Nueva línea
        out.println("    la $a0, nl");
        out.println("    li $v0, 4");
        out.println("    syscall");
    }

    private void procesarReadString(String variable) {
        out.println("    # Leer string");
        
        // Primero necesitamos espacio para el string
        // Asumimos que la variable ya tiene espacio reservado
        int offset = stackOffsetMap.getOrDefault(variable, -1);
        
        if (offset != -1) {
            // Cargar dirección del buffer en $a0
            out.println("    addiu $a0, $sp, " + offset);
            out.println("    li $a1, 100");        // Tamaño máximo del buffer
            out.println("    li $v0, 8");          // Syscall para leer string
            out.println("    syscall");
            
            // Eliminar el newline al final si existe
            out.println("    # Eliminar newline del final");
            out.println("    la $t0, ($a0)");
            out.println("remove_newline:");
            out.println("    lb $t1, ($t0)");
            out.println("    addiu $t0, $t0, 1");
            out.println("    bnez $t1, remove_newline");
            out.println("    beq $t1, 10, found_newline");
            out.println("    j end_remove");
            out.println("found_newline:");
            out.println("    sb $zero, -1($t0)");
            out.println("end_remove:");
        } else {
            out.println("    # Error: variable no encontrada en la pila");
        }
    }

    private void procesarReadChar(String variable) {
        out.println("    # Leer carácter");
        out.println("    li $v0, 12");         // Syscall para leer carácter
        out.println("    syscall");
        
        // Guardar en la variable
        guardarEnPila(variable, "$v0", false);
        
        // Nueva línea
        out.println("    la $a0, nl");
        out.println("    li $v0, 4");
        out.println("    syscall");
    }

    private void procesarReadBool(String variable) {
        out.println("    # Leer booleano (1/0)");
        out.println("    li $v0, 5");          // Leer como entero
        out.println("    syscall");
        
        // Convertir a booleano (0 = false, cualquier otro = true)
        out.println("    # Convertir a booleano");
        out.println("    seq $t0, $v0, $zero"); // $t0 = 1 si $v0 == 0, else 0
        out.println("    xori $t1, $t0, 1");    // Invertir: 0→1, 1→0
        
        // Guardar en la variable
        guardarEnPila(variable, "$t1", false);
        
        // Nueva línea
        out.println("    la $a0, nl");
        out.println("    li $v0, 4");
        out.println("    syscall");
    }

    private boolean esVariableInt(String variable) {
        // Verificar si la variable es de tipo int
        // Podrías tener un mapa de tipos de variables
        return variable.matches("^[ijklmn]_?\\d*$") || 
            variable.toLowerCase().contains("int") ||
            variable.toLowerCase().contains("contador") ||
            variable.toLowerCase().contains("indice");
    }

    private boolean esVariableString(String variable) {
        // Verificar si la variable es de tipo string
        return variable.matches("^[s]_?\\d*$") || 
            variable.toLowerCase().contains("str") ||
            variable.toLowerCase().contains("nombre") ||
            variable.toLowerCase().contains("mensaje") ||
            variable.toLowerCase().contains("texto");
    }

    private void procesarPrintString(List<String> tokens) {
        if (tokens.size() < 2) {
            out.println("    # Error: PRINTSTRING sin argumento");
            return;
        }

        String valor = tokens.get(1);
        cargarDireccionStringEnA0(valor);
        
        // Hacer syscall para imprimir string
        out.println("    li $v0, 4");
        out.println("    syscall");
        
        // Imprimir nueva línea
        imprimirNuevaLinea();
    }

    private void cargarDireccionStringEnA0(String valor) {
        if (valor.startsWith("\"") && valor.endsWith("\"")) {
            String str = valor.substring(1, valor.length() - 1);
            String label = obtenerLabelString(str);
            out.println("    la $a0, " + label);
        } else if (valor.startsWith("'") && valor.endsWith("'")) {
            String str = valor.substring(1, valor.length() - 1);
            if (str.length() == 1) {
                String charStr = String.valueOf(str.charAt(0));
                String label = obtenerLabelString(charStr);
                out.println("    la $a0, " + label);
            } else {
                out.println("    # Error: char inválido - usando primer carácter");
                String charStr = String.valueOf(str.charAt(0));
                String label = obtenerLabelString(charStr);
                out.println("    la $a0, " + label);
            }
        } else if (valor.startsWith("_str_") || valor.startsWith("str_")) {
            out.println("    la $a0, " + valor);
        } else {
            // Variable
            cargarRegistro("$a0", valor);
        }
    }

    private void imprimirNuevaLinea() {
        out.println("    la $a0, nl");
        out.println("    li $v0, 4");
        out.println("    syscall");
    }
    private void procesarSaltoFuncion(String linea, List<String> tokens) {
        if (tokens.size() >= 2 && tokens.get(0).equals("GOTO")) {
            String destino = tokens.get(1);
            currentStackOffset += 4; // Reservar espacio para $ra
            int stackOffset = currentStackOffset;
            
            if (destino.equals("navidad") && !llamadaNavidad) {
                llamadaNavidad = true;
                // Llamada a función principal
                out.println("    # Llamada a navidad");
                out.println("    addiu $sp, $sp, -4");
                out.println("    sw $ra, "+String.valueOf(stackOffset)+"($fp)");
                out.println("    jal navidad");
                out.println("    lw $ra, "+String.valueOf(stackOffset)+"($fp)");
                out.println("    addiu $sp, $sp, 4");
            } else if (esFuncion(destino)) {
                // Llamada a función normal
                out.println("    # Llamada a función: " + destino);
                out.println("    addiu $sp, $sp, -4");
                out.println("    sw $ra, 0($fp)");
                out.println("    jal " + destino);
                out.println("    lw $ra, 0($fp)");
                out.println("    addiu $sp, $sp, 4");
            } else if (destino.startsWith("L")) {
                // Es una etiqueta de salto interno (no función)
                out.println("    j " + destino);
            } else {
                // Por defecto, tratar como función
                out.println("    # Salto a: " + destino);
                out.println("    addiu $sp, $sp, -4");
                out.println("    sw $ra, 0($fp)");
                out.println("    jal " + destino);
                out.println("    lw $ra, 0($fp)");
                out.println("    addiu $sp, $sp, 4");
            }
        }
    }

    private boolean esFuncion(String nombre) {
        // Verificar si es una función conocida
        if (nombre.equals("navidad") || 
            nombre.equals("_mi_") || 
            nombre.equals("_miOtraFun_") ||
            nombre.startsWith("func_")) {
            return true;
        }
        
        // También verificar en las labels detectadas
        return labelPositions.containsKey(nombre) && 
            !nombre.startsWith("L") &&  // No son etiquetas de control
            !nombre.equals("main") &&
            !nombre.equals("inicio_codigo");
    }
    
    private void procesarPrint(List<String> tokens) {
        String valor = tokens.get(1);
        
        if (valor.equals("true")) {
            out.println("    la $a0, true_str");
            out.println("    li $v0, 4");
            out.println("    syscall");
        } else if (valor.equals("false")) {
            out.println("    la $a0, false_str");
            out.println("    li $v0, 4");
            out.println("    syscall");
        } else if (valor.startsWith("\"")) {
            String str = valor.substring(1, valor.length() - 1);
            String label = "str_" + stringMap.get(str);
            out.println("    la $a0, " + label);
            out.println("    li $v0, 4");
            out.println("    syscall");
        } else if (valor.matches("\\d+\\.\\d+")) {
            out.println("    li.s $f12, " + valor);
            out.println("    li $v0, 2");
            out.println("    syscall");
        } else if (valor.matches("-?\\d+")) {
            out.println("    li $a0, " + valor);
            out.println("    li $v0, 1");
            out.println("    syscall");
        } else {
            // Es una variable en la pila
            cargarRegistroPila("$a0", valor);
            out.println("    li $v0, 1");
            out.println("    syscall");
        }
        
        // Nueva línea después de imprimir
        out.println("    la $a0, nl");
        out.println("    li $v0, 4");
        out.println("    syscall");
    }
    
    private List<String> dividirLineaConStrings(String linea) {
        List<String> tokens = new ArrayList<>();
        boolean dentroDeString = false;
        StringBuilder tokenActual = new StringBuilder();
        char comillaTipo = '\0';
        
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            
            if (!dentroDeString) {
                if (Character.isWhitespace(c)) {
                    if (tokenActual.length() > 0) {
                        tokens.add(tokenActual.toString());
                        tokenActual = new StringBuilder();
                    }
                } else if (c == '"' || c == '\'') {
                    dentroDeString = true;
                    comillaTipo = c;
                    tokenActual.append(c);
                } else {
                    tokenActual.append(c);
                }
            } else {
                tokenActual.append(c);
                if (c == comillaTipo) {
                    if (i > 0 && linea.charAt(i-1) != '\\') {
                        dentroDeString = false;
                    }
                }
            }
        }
        
        if (tokenActual.length() > 0) {
            tokens.add(tokenActual.toString());
        }
        
        return tokens;
    }
    
    private void procesarAsignacion(String linea, List<String> tokens) {
        String destino = tokens.get(0);
        
        // Asegurar que la variable destino tiene espacio en la pila
        if (!stackOffsetMap.containsKey(destino)) {
            stackOffsetMap.put(destino, currentStackOffset);
            currentStackOffset += 4;
        }
        
        if (linea.contains(" <= ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    slt $t2, $t1, $t0");
            out.println("    xori $t2, $t2, 1");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" = NOT ")) {
            String[] partes = linea.trim().split("\\s+");
            String destino2 = partes[0];
            String origen = partes[3];

            int offsetOrigen = stackOffsetMap.get(origen);
            int offsetDestino = stackOffsetMap.get(destino2);

            out.println("    # " + destino2 + " = NOT " + origen);
            out.println("    lw $t0, " + offsetOrigen + "($fp)");
            out.println("    seq $t1, $t0, $zero"); // Esta es la clave: 0->1, 1->0
            out.println("    sw $t1, " + offsetDestino + "($fp)");
        }else if (linea.contains(" >= ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    slt $t2, $t0, $t1");
            out.println("    xori $t2, $t2, 1");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" == ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    seq $t2, $t0, $t1");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" % ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$a0", izquierda);
            cargarRegistroPila("$a1", derecha);
            out.println("    jal modulo");
            guardarEnPila(destino, "$v0", false);
            
        } else if (linea.contains(" / ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$a0", izquierda);
            cargarRegistroPila("$a1", derecha);
            out.println("    jal division");
            guardarEnPila(destino, "$v0", false);
            
        } else if (linea.contains(" - ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    sub $t2, $t0, $t1");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" * ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$a0", izquierda);
            cargarRegistroPila("$a1", derecha);
            out.println("    mult $a0, $a1\n" + //
                                "    mflo $v0");
            guardarEnPila(destino, "$v0", false);
            
        } else if (linea.contains(" + ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    add $t2, $t0, $t1");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" < ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    slt $t2, $t1, $t0");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" > ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    sgt $t2, $t1, $t0");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" != ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    seq $t2, $t0, $t1");
            out.println("    xori $t2, $t2, 1");
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" == ")) {
            String izquierda = tokens.get(2);
            String derecha = tokens.get(4);
            cargarRegistroPila("$t0", izquierda);
            cargarRegistroPila("$t1", derecha);
            out.println("    seq $t2, $t0, $t1");
            guardarEnPila(destino, "$t2", false);
            guardarEnPila(destino, "$t2", false);
            
        } else if (linea.contains(" NOT ")) {
            String fuente = tokens.get(3);
            cargarRegistroPila("$t0", fuente);
            out.println("    seq $t1, $t0, $zero");
            guardarEnPila(destino, "$t1", false);
            
        } else if (tokens.size() == 3) {
            // Asignación simple
            String fuente = tokens.get(2);
            
            // Verificar si es una string literal
            if (fuente.startsWith("\"") || fuente.startsWith("'")) {
                String str = fuente.substring(1, fuente.length() - 1);
                String label = stringMap.get(str);
                out.println("    la $t0, " + label);
                guardarEnPila(destino, "$t0", false);
            } else if (esFloat(fuente)){
                cargarRegistroPila("$f0", fuente);
                guardarEnPila(destino, "$f0", true);
            } else {
                cargarRegistroPila("$t0", fuente); // BUSCAR MENSAJE BIENVENIDA
                guardarEnPila(destino, "$t0", false);
            }
        }
    }
    
    
    
    private void guardarEnPila(String variable, String registro, boolean esFloat) {
        int offset = stackOffsetMap.get(variable);
        if (esFloat) {
            out.println("    swc1 " + registro + ", " + offset + "($fp)");
        } else {
            out.println("    sw " + registro + ", " + offset + "($fp)");
        }
    }
    
    private void generarSyscalls() {
        out.println("\n#*******************************************SYSCALL*****************************************************");
        if (syscalls.contains("printInt")) {
            out.println("printInt:");
            out.println("    li   $v0, 1");
            out.println("    syscall");
            out.println("    jr $ra");
            out.println(".end printInt\n");
        }
        
        if (syscalls.contains("printStr")) {
            out.println("printStr:");
            out.println("    li   $v0, 4");
            out.println("    syscall");
            out.println("    jr $ra");
            out.println(".end printStr\n");
        }
        
        if (syscalls.contains("printFloat")) {
            out.println("printFloat:");
            out.println("    li   $v0, 2");
            out.println("    syscall");
            out.println("    jr $ra");
            out.println(".end printFloat\n");
        }
        
        if (syscalls.contains("mod")) {
            out.println("modulo:");
            out.println("    div $a0, $a1");
            out.println("    mfhi $v0");
            out.println("    jr $ra");
            out.println(".end modulo\n");
        }
        
        if (syscalls.contains("div")) {
            out.println("division:");
            out.println("    div $a0, $a1");
            out.println("    mflo $v0");
            out.println("    jr $ra");
            out.println(".end division\n");
        }
        
        if (syscalls.contains("readInt")) {
            out.println("readInt:");
            out.println("    li   $v0, 5");
            out.println("    syscall");
            out.println("    jr $ra");
            out.println(".end readInt\n");
        }
        
        if (syscalls.contains("mult")) {
            out.println("rutina_multiplicacion:");
            out.println("    mult $a0, $a1");
            out.println("    mflo $v0");
            out.println("    jr $ra");
            out.println(".end rutina_multiplicacion\n");
        }

        if (syscalls.contains("pow")) {
            out.println("potencia:");
            out.println("    addiu $sp, $sp, -12");
            out.println("    sw $ra, 8($fp)");
            out.println("    sw $s0, 4($fp)");
            out.println("    sw $s1, 0($fp)");
            out.println("    move $s0, $a0");
            out.println("    move $s1, $a1");
            out.println("    li $v0, 1");
            out.println("potencia_loop:");
            out.println("    blez $s1, potencia_fin");
            out.println("    mult $v0, $s0");
            out.println("    mflo $v0");
            out.println("    addiu $s1, $s1, -1");
            out.println("    j potencia_loop");
            out.println("potencia_fin:");
            out.println("    lw $ra, 8($fp)");
            out.println("    lw $s0, 4($fp)");
            out.println("    lw $s1, 0($fp)");
            out.println("    addiu $sp, $sp, 12");
            out.println("    jr $ra");
            out.println(".end potencia\n");
        }
    }
    
    private boolean esKeyword(String s) {
        return s.equals("IF") || s.equals("GOTO") || s.equals("CALL") || 
               s.equals("PARAM") || s.equals("RETURN") || s.equals("PRINT") ||
               s.equals("printString") || s.equals("printInt") || s.equals("printSpace") ||
               s.equals("main") || s.equals("true") || s.equals("false");
    }
}