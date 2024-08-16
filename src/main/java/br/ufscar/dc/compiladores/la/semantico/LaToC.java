package br.ufscar.dc.compiladores.la.semantico;

import java.util.List;
import org.antlr.v4.runtime.tree.TerminalNode;

import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.IdentificadorContext;

public class LaToC extends LaSemanticBaseVisitor<Void> {
    Escopos escoposAninhados = new Escopos();
    TabelaDeSimbolos tabelaDeSimbolos;

    public StringBuilder finalCode;

    private String getCSymbol(TabelaDeSimbolos.TipoLa tipo) {
        switch (tipo) {
            case INT:
                return "%d";
            case REAL:
                return "%f";
            default:
                return "%s";
        }
    }

    public LaToC() {
        finalCode = new StringBuilder();
        this.tabelaDeSimbolos = new TabelaDeSimbolos();
    }

    @Override
    public Void visitPrograma(LaSemanticParser.ProgramaContext ctx) {
        finalCode.append("#include <stdio.h>\n");
        finalCode.append("#include <stdlib.h>\n");
        finalCode.append("\n");
        finalCode.append("int main() {\n");
        visitDeclaracoes_locais(ctx.corpo().declaracao_local());
        visitCmds(ctx.corpo().cmd());
        finalCode.append("return 0;\n");
        finalCode.append("}\n");
        return null;
    }

    private void visitDeclaracoes_locais(List<LaSemanticParser.Declaracao_localContext> listaDeclaracoes) {
        for (LaSemanticParser.Declaracao_localContext declaracao : listaDeclaracoes) {
            visitDeclaracao_local(declaracao);
        }
    }

    private void visitCmds(List<LaSemanticParser.CmdContext> listaCmds) {
        for (LaSemanticParser.CmdContext cmd : listaCmds) {
            visitCmd(cmd);
        }
    }

    @Override
    public Void visitDeclaracao_local(LaSemanticParser.Declaracao_localContext ctx) {
        if (ctx.IDENT() != null) {

        } else {
            if (ctx.variavel().tipo().registro() == null) {
                String tipo = ctx.variavel().tipo().getText();
                // Checar pois o nome numeral de tipo é diferente do nome comum
                switch (tipo) {
                    case "inteiro":
                        tipo = "int";
                        break;
                    case "literal":
                        tipo = "lit";
                        break;
                    default:
                        break;
                }
                TabelaDeSimbolos.TipoLa tipoTable = TabelaDeSimbolos.TipoLa.valueOf(tipo.toUpperCase());
                for (IdentificadorContext ctxVariable : ctx.variavel().identificador()) {
                    tabelaDeSimbolos.adicionar(ctxVariable.getText(), tipoTable);
                    switch (tipoTable) {
                        case INT:
                            tipo = "int " + ctxVariable.getText() + ";\n";
                            break;
                        case REAL:
                            tipo = "float " + ctxVariable.getText() + ";\n";
                            break;
                        case LIT:
                            tipo = "char " + ctxVariable.getText() + "[80];\n";
                            break;
                    }
                    finalCode.append(tipo);
                }
            }

        }
        return null;
    }

    @Override
    public Void visitCmd(LaSemanticParser.CmdContext ctx) {
        if (ctx.cmdLeia() != null) {
            visitCmdLeia(ctx.cmdLeia());
        } else if (ctx.cmdEscreva() != null) {
            visitCmdEscreva(ctx.cmdEscreva());
        }
        return null;
    }

    @Override
    public Void visitCmdLeia(LaSemanticParser.CmdLeiaContext ctx) {
        for (LaSemanticParser.IdentificadorContext identificadorCtx : ctx.identificador()) {
            String nomeVar = identificadorCtx.getText();
            TabelaDeSimbolos.TipoLa tipo = tabelaDeSimbolos.verificar(nomeVar); // Verifica o tipo na tabela de símbolos
            String tipoCSimbolo = getCSymbol(tipo);

            finalCode.append("scanf(\"" + tipoCSimbolo + "\", &" + nomeVar + ");\n");
        }
        return null;
    }

    @Override
    public Void visitCmdEscreva(LaSemanticParser.CmdEscrevaContext ctx) {
        finalCode.append("printf(");

        // Adicionando o formato inicial para o printf
        StringBuilder formatString = new StringBuilder("\"");

        // Percorre todas as expressões que fazem parte do comando escreva
        for (LaSemanticParser.ExpressaoContext expressaoCtx : ctx.expressao()) {
            if (expressaoCtx.getText().startsWith("\"") && expressaoCtx.getText().endsWith("\"")) {
                // Se for um literal de string, adicione-o diretamente ao formatString
                formatString.append(expressaoCtx.getText().replace("\"", ""));
            } else {
                // Se for uma variável, pegue o tipo dela da tabela de símbolos
                String nomeVar = expressaoCtx.getText();
                TabelaDeSimbolos.TipoLa tipo = tabelaDeSimbolos.verificar(nomeVar);
                String tipoCSimbolo = getCSymbol(tipo);

                // Adicione o formato correto ao formatString
                formatString.append(tipoCSimbolo);
            }
        }

        formatString.append("\"");

        // Adiciona a string formatada no printf
        finalCode.append(formatString.toString());

        // Adiciona as variáveis no printf
        for (LaSemanticParser.ExpressaoContext expressaoCtx : ctx.expressao()) {
            if (!expressaoCtx.getText().startsWith("\"") || !expressaoCtx.getText().endsWith("\"")) {
                String nomeVar = expressaoCtx.getText();
                finalCode.append(", ").append(nomeVar);
            }
        }

        finalCode.append(");\n");

        return null;
    }

}
