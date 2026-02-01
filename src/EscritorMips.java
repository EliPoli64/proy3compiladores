import java.io.*;
import java.nio.file.*;
import java.util.*;

public class EscritorMips {
    private PrintWriter out;
    private Map<String, Integer> varMap = new HashMap<>();
    private Map<String, Integer> labelPositions = new HashMap<>();
    private Set<String> strings = new HashSet<>(); // para declarar strings arriba
    private Set<String> syscalls = new HashSet<>(); // para ver que rutinas agregamos al final
    private boolean hasMainLabel = false; // por si el programa tiene main
    private Map<String, Integer> stringMap = new HashMap<>();
    
    public void procesar(String entrada, String salida) throws IOException {
        List<String> lineas = Files.readAllLines(Paths.get(entrada));
        out = new PrintWriter(new FileWriter(salida));
        
        detectarLabels(lineas);
        extraerStrings(lineas);
        detectarSyscalls(lineas);
        calcularVariables(lineas);
        
        out.println(".data");
        out.println("    nl: .asciiz \"\\n\"");
        int strCount = 1;
        for (String str : strings) {
            out.println("    str" + strCount + ": .asciiz \"" + str + "\"");
            stringMap.put(str, strCount);
            strCount++;
        }
        
        out.println("\n.text\nmain:");
        out.println("    j inicio_codigo");

        out.println("    li $v0, 10");
        out.println("    syscall");
        
        if (!hasMainLabel) {
            out.println("\ninicio_codigo:");
        }
        
        traducirCodigo(lineas);
        
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
            
            out.println("division_entera:");
            out.println("    div $a0, $a1");
            out.println("    mflo $v0");
            out.println("    jr $ra");
            out.println(".end division_entera\n");
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
            out.println("    sw $ra, 8($sp)");
            out.println("    sw $s0, 4($sp)");
            out.println("    sw $s1, 0($sp)");
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
            out.println("    lw $ra, 8($sp)");
            out.println("    lw $s0, 4($sp)");
            out.println("    lw $s1, 0($sp)");
            out.println("    addiu $sp, $sp, 12");
            out.println("    jr $ra");
            out.println(".end potencia\n");
        }
        
        out.close();
    }
    
    private void detectarLabels(List<String> lineas) {
        for (String linea : lineas) {
            String l = linea.trim();
            if (l.isEmpty()) continue;
            
            if (l.endsWith(":")) {
                String label = l.replace(":", "");
                labelPositions.put(label, 0);
                if (label.equals("main")) {
                    hasMainLabel = true;
                }
            }
        }
    }
    
    private void extraerStrings(List<String> lineas) {
        for (String linea : lineas) {
            if (linea.contains("\"")) {
                String[] parts = linea.split("\"");
                for (int i = 1; i < parts.length; i += 2) {
                    strings.add(parts[i]);
                }
            }
        }
    }
    
    private void detectarSyscalls(List<String> lineas) {
        for (String linea : lineas) {
            if (linea.contains("printInt")) {
                syscalls.add("printInt");
                syscalls.add("printStr");
            } 
            if (linea.contains("printFloat")) {
                syscalls.add("printFloat");
                syscalls.add("printStr");
            }
            if (linea.contains("printStr")) {
                syscalls.add("printStr");
            }
            if (linea.contains("readInt")) {
                syscalls.add("readInt");
                syscalls.add("printStr");
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
            if (linea.contains("factorial") || linea.contains("!")) {
                syscalls.add("fact");
            }
        }
    }
    
    private void calcularVariables(List<String> lineas) {
        int offset = 0;
        
        for (String linea : lineas) {
            String l = linea.trim();
            if (l.isEmpty() || l.endsWith(":")) continue;
            
            String[] tokens = l.split("\\s+");
            for (String token : tokens) {
                if (token.matches("t\\d+|^[a-z][a-z0-9]*$") && !esKeyword(token) && !token.startsWith("\"") && 
                    !token.contains(":") && !labelPositions.containsKey(token)) {
                    if (!varMap.containsKey(token)) {
                        varMap.put(token, offset);
                        offset += 4;
                    }
                }
            }
        }
    }
    
    private void traducirCodigo(List<String> lineas) {
        for (String linea : lineas) {
            String l = linea.trim();
            if (l.isEmpty()) continue;
            
            out.println("\n    # " + l);
            
            if (l.endsWith(":")) {
                String label = l.replace(":", "");
                out.println(label + ":");
                continue;
            }
            
            String[] parts = l.split("\\s+");
            
            if (l.contains("printStr")) {
                int inicio = l.indexOf("\"");
                int fin = l.lastIndexOf("\"");
                if (inicio != -1 && fin != -1) {
                    String str = l.substring(inicio + 1, fin);
                    int strNum = stringMap.get(str);
                    out.println("    la $a0, str" + strNum);
                    out.println("    jal printStr");
                    out.println("    la $a0, nl");
                    out.println("    jal printStr");
                }
            } 
            else if (l.startsWith("printInt ")) {
                String valor = parts[1];
                if (valor.matches("\\d+")) {
                    out.println("    li $a0, " + valor);
                } else {
                    out.println("    lw $a0, " + varMap.get(valor) + "($sp)");
                }
                out.println("    jal printInt");
                out.println("    la $a0, nl");
                out.println("    jal printStr");
            }
            
            else if (l.startsWith("printFloat ")) {
                String valor = parts[1];
                if (valor.matches("\\d+\\.\\d+")) {
                    out.println("    li.s $f12, " + valor);
                } else {
                    out.println("    lwc1 $f12, " + varMap.get(valor) + "($sp)");
                }
                out.println("    jal printFloat");
                out.println("    la $a0, nl");
                out.println("    jal printStr");
            }
            else if (l.contains(" = ") && l.contains(" <= ")) {
                String destino = parts[0];
                String izquierda = parts[2];
                String derecha = parts[4];
                
                cargarRegistro("$t0", izquierda);
                cargarRegistro("$t1", derecha);
                out.println("    slt $t2, $t1, $t0");
                out.println("    xori $t2, $t2, 1");
                out.println("    sw $t2, " + varMap.get(destino) + "($sp)");
                
            } else if (l.startsWith("if ")) {
                String condicion = parts[1];
                String etiqueta = parts[3];
                out.println("    lw $t0, " + varMap.get(condicion) + "($sp)");
                out.println("    bnez $t0, " + etiqueta);
                
            } else if (l.contains(" = ") && l.contains(" - ")) {
                String destino = parts[0];
                String izquierda = parts[2];
                String derecha = parts[4];
                cargarRegistro("$t0", izquierda);
                cargarRegistro("$t1", derecha);
                out.println("    sub $t2, $t0, $t1");
                out.println("    sw $t2, " + varMap.get(destino) + "($sp)");
                
            } else if (l.contains(" = ") && l.contains(" * ")) {
                String destino = parts[0];
                String izquierda = parts[2];
                String derecha = parts[4];
                cargarRegistro("$a0", izquierda);
                cargarRegistro("$a1", derecha);
                out.println("    jal rutina_multiplicacion");
                out.println("    sw $v0, " + varMap.get(destino) + "($sp)");
                
            } else if (l.contains(" = ") && l.contains(" + ")) {
                String destino = parts[0];
                String izquierda = parts[2];
                String derecha = parts[4];
                cargarRegistro("$t0", izquierda);
                cargarRegistro("$t1", derecha);
                out.println("    add $t2, $t0, $t1");
                out.println("    sw $t2, " + varMap.get(destino) + "($sp)");
                
            } else if (l.contains(" = ") && l.contains(" < ")) {
                String destino = parts[0];
                String izquierda = parts[2];
                String derecha = parts[4];
                cargarRegistro("$t0", izquierda);
                cargarRegistro("$t1", derecha);
                out.println("    slt $t2, $t0, $t1");
                out.println("    sw $t2, " + varMap.get(destino) + "($sp)");
                
            } else if (l.contains(" = ") && parts.length == 3) {
                String destino = parts[0];
                String fuente = parts[2];
                cargarRegistro("$t0", fuente);
                out.println("    sw $t0, " + varMap.get(destino) + "($sp)");
            }
        }
        
        out.println("\n    # Fin del programa");
        out.println("    li $v0, 10");
        out.println("    syscall");
    }
    
    private void cargarRegistro(String registro, String valor) {
        if (valor.matches("-?\\d+")) {
            out.println("    li " + registro + ", " + valor);
        } else if (varMap.containsKey(valor)) {
            out.println("    lw " + registro + ", " + varMap.get(valor) + "($sp)");
        } else {
            out.println("    # Variable no encontrada: " + valor);
        }
    }
    
    private boolean esKeyword(String s) {
        return s.equals("if") || s.equals("goto") || s.equals("call") || 
               s.equals("param") || s.equals("return") || s.equals("print") ||
               s.equals("printString") || s.equals("printInt") || s.equals("printSpace");
    }
}