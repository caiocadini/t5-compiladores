package br.ufscar.dc.compiladores.la.semantico;

import java.util.List;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.ibm.icu.text.SymbolTable;

import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.IdentificadorContext;
import br.ufscar.dc.compiladores.la.semantico.TabelaDeSimbolos.TipoLa;

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
        String tipo = null;
        if (ctx.IDENT() != null) {
            // Lidando com constantes
            if (ctx.valor_constante() != null) {
                finalCode.append("#define ").append(ctx.IDENT().getText()).append(" ")
                        .append(ctx.valor_constante().getText()).append("\n");

            }
        } else {
            if (ctx.variavel().tipo().registro() == null) {
                tipo = ctx.variavel().tipo().getText();
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
            } else {
                finalCode.append("struct {\n");
                for (LaSemanticParser.IdentificadorContext ic : ctx.variavel().identificador()) {
                    tabelaDeSimbolos.adicionar(ic.getText(), TipoLa.REG);

                    LaSemanticParser.RegistroContext registroCtx = ctx.variavel().tipo().registro();
                    for (LaSemanticParser.VariavelContext vc : registroCtx.variavel()) {
                        String tipoVariavelStr = vc.tipo().getText();
                        TipoLa tipoVariavelLa = LaSemantico.obterTipo(tipoVariavelStr);

                        if (tipoVariavelLa != TipoLa.INVALIDO) {
                            for (LaSemanticParser.IdentificadorContext icr : vc.identificador()) {
                                String nomeCampo = icr.getText();
                                tabelaDeSimbolos.adicionar(ic.getText() + "." + nomeCampo, tipoVariavelLa);
                                switch (tipoVariavelLa) {
                                    case INT:
                                        tipo = "int " + icr.getText() + ";\n";
                                        break;
                                    case REAL:
                                        tipo = "float " + icr.getText() + ";\n";
                                        break;
                                    case LIT:
                                        tipo = "char " + icr.getText() + "[80];\n";
                                        break;
                                }

                                finalCode.append(tipo);
                            }
                        }
                    }
                }
                finalCode.append("} ");
                for (LaSemanticParser.IdentificadorContext ic : ctx.variavel().identificador()) {
                    finalCode.append(ic.getText() + ";\n");
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
        } else if (ctx.cmdSe() != null) {
            visitCmdSe(ctx.cmdSe());
        } else if (ctx.cmdCaso() != null) {
            visitCmdCaso(ctx.cmdCaso());
        } else if (ctx.cmdAtribuicao() != null) {
            visitCmdAtribuicao(ctx.cmdAtribuicao());
        } else if (ctx.cmdPara() != null){
            visitCmdPara(ctx.cmdPara());
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

    @Override
    public Void visitCmdSe(LaSemanticParser.CmdSeContext ctx) {
        // Visitar a expressão de condição
        finalCode.append("if (");
        finalCode.append(ctx.expressao().getText());
        finalCode.append(") {\n");

        // Visitar comandos dentro do bloco "então"
        for (LaSemanticParser.CmdContext cmdCtx : ctx.cmd()) {
            visitCmd(cmdCtx);
        }

        // Verificar se há o bloco "senao"
        if (ctx.getChild(ctx.getChildCount() - 2).getText().equals("senao")) {
            finalCode.append("} else {\n");

            // Visitar comandos dentro do bloco "senao"
            for (int i = ctx.cmd().size() / 2; i < ctx.cmd().size(); i++) {
                visitCmd(ctx.cmd(i));
            }
        }

        finalCode.append("}\n");

        return null;
    }

    @Override
    public Void visitCmdCaso(LaSemanticParser.CmdCasoContext ctx) {
        // Iniciar o switch com a expressão aritmética
        finalCode.append("switch (");
        finalCode.append(ctx.exp_aritmetica().getText());
        finalCode.append(") {\n");

        // Visitar cada item de seleção
        for (LaSemanticParser.Item_selecaoContext itemCtx : ctx.selecao().item_selecao()) {
            for (LaSemanticParser.Numero_intervaloContext intervaloCtx : itemCtx.constantes().numero_intervalo()) {
                // Se o intervalo for um único número
                if (intervaloCtx.NUM_INT().size() == 1) {
                    finalCode.append("case ");
                    finalCode.append(intervaloCtx.NUM_INT(0).getText());
                    finalCode.append(":\n");
                }
                // Se o intervalo for um intervalo de números
                else {
                    int inicio = Integer.parseInt(intervaloCtx.NUM_INT(0).getText());
                    int fim = Integer.parseInt(intervaloCtx.NUM_INT(1).getText());
                    for (int i = inicio; i <= fim; i++) {
                        finalCode.append("case ");
                        finalCode.append(i);
                        finalCode.append(":\n");
                    }
                }
            }

            // Adicionar os comandos dentro do case
            for (LaSemanticParser.CmdContext cmdCtx : itemCtx.cmd()) {
                visitCmd(cmdCtx);
            }

            // Adicionar o break
            finalCode.append("break;\n");
        }

        // Verificar se há o bloco "senao" (default)
        if (ctx.getChild(ctx.getChildCount() - 2).getText().equals("senao")) {
            finalCode.append("default:\n");
            for (int i = ctx.cmd().size() / 2; i < ctx.cmd().size(); i++) {
                visitCmd(ctx.cmd(i));
            }
            finalCode.append("break;\n");
        }

        finalCode.append("}\n");

        return null;
    }

    @Override
    public Void visitCmdAtribuicao(LaSemanticParser.CmdAtribuicaoContext ctx) {
        String[] var_valor = ctx.getText().split("<-");

        TabelaDeSimbolos.TipoLa tipoVar = tabelaDeSimbolos.verificar(var_valor[0]);

        if (tipoVar == TabelaDeSimbolos.TipoLa.LIT) {
            finalCode.append("strcpy(");

            finalCode.append(ctx.identificador().getText()+"," + ctx.expressao().getText() + ");\n");
        } else {
            finalCode.append(ctx.identificador().getText());
            finalCode.append(" = ");
            finalCode.append(ctx.expressao().getText());
            finalCode.append(";\n");
        }
        return null;
    };

    @Override
    public Void visitCmdPara(LaSemanticParser.CmdParaContext ctx){
        finalCode.append("for(" + ctx.IDENT().getText()+"="+ctx.exp_aritmetica(0).getText()+"; ");
        finalCode.append(ctx.IDENT().getText()+"<="+ctx.exp_aritmetica(1).getText()+"; ");
        finalCode.append(ctx.IDENT().getText()+"++){\n");
        for(LaSemanticParser.CmdContext cmdCtx : ctx.cmd()){
            visitCmd(cmdCtx);
        }
        finalCode.append("}\n");
        return null;

    }

}
