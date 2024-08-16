package br.ufscar.dc.compiladores.la.semantico;

import java.util.List;
import org.antlr.v4.runtime.tree.TerminalNode;

public class LaToC extends LaSemanticBaseVisitor<Void> {
    Escopos escoposAninhados = new Escopos();
    TabelaDeSimbolos tabelaDeSimbolos;

    public StringBuilder finalCode;

    public LaToC() {
        finalCode = new StringBuilder();
        this.tabelaDeSimbolos = new TabelaDeSimbolos();
    }

    @Override
    public Void visitPrograma (LaSemanticParser.ProgramaContext ctx) {
        finalCode.append("#include <stdio.h>\n");
        finalCode.append("#include <stdlib.h>\n");
        finalCode.append("\n");
        finalCode.append("int main() {\n");
        visitDeclaracoes_locais(ctx.corpo().declaracao_local());
        // Add any additional code or visit other parts of the program here
        finalCode.append("return 0;\n");
        finalCode.append("}\n");
        return null;
    }

    private Void visitDeclaracoes_locais(List<LaSemanticParser.Declaracao_localContext> listaDeclaracoes){
        listaDeclaracoes.forEach(this::visitDeclaracao_local);
        return null;
    }

    @Override
    public Void visitDeclaracao_local(LaSemanticParser.Declaracao_localContext ctx) {
        if (ctx.IDENT() != null) {
            String nomeConst = ctx.IDENT().getText();
            // Handle constant declaration here
        } else {
            if (ctx.variavel().tipo().registro() != null) {
                // Handle record declaration here
            } else {
                for (LaSemanticParser.IdentificadorContext identificadorCtx : ctx.variavel().identificador()) {
                    StringBuilder nomesVar = new StringBuilder();
                    for (TerminalNode nome : identificadorCtx.IDENT()) {
                        nomesVar.append(nome.getText());
                    }

                    String tipo = getTipoC(ctx.variavel().tipo()); // Method to convert LA type to C type
                    finalCode.append(tipo).append(" ").append(identificadorCtx.getText()).append(";\n");
                }
            }
        }
        return null;
    }

    private String getTipoC(LaSemanticParser.TipoContext tipoCtx) {
        // Implement the logic to convert from LA type to C type
        // Example: return "int" for integers, "float" for floats, etc.
        return "int"; // Placeholder return
    }
}
