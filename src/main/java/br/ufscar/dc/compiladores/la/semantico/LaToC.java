package br.ufscar.dc.compiladores.la.semantico;

import java.util.List;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.ibm.icu.text.SymbolTable;

import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.CmdCasoContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.CmdEnquantoContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.CmdFacaContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.Exp_aritmeticaContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.Exp_relacionalContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.ExpressaoContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.FatorContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.Fator_logicoContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.IdentificadorContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.ParcelaContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.Parcela_logicaContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.Parcela_nao_unarioContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.Parcela_unarioContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.TermoContext;
import br.ufscar.dc.compiladores.la.semantico.LaSemanticParser.Termo_logicoContext;
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
        visitDeclaracoesGlobais(ctx.declaracoes().decl_local_global().declaracao_global());
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

    private void visitDeclaracoesGlobais(List<LaSemanticParser.Declaracao_globalContext> listaDeclaracoes) {
        for (LaSemanticParser.Declaracao_globalContext declaracao : listaDeclaracoes) {
            visitDeclaracao_global(declaracao);
        }
    }

    private void visitCmds(List<LaSemanticParser.CmdContext> listaCmds) {
        for (LaSemanticParser.CmdContext cmd : listaCmds) {
            visitCmd(cmd);
        }
    }


    //Função e procedimento
    @Override
    public Void visitDeclaracao_global(LaSemanticParser.Declaracao_globalContext ctx) {
        String identificador = ctx.IDENT().getText();
        if (ctx.getText().contains("procedimento")) {
            finalCode.append("void ").append(identificador).append("(");
        } else if (ctx.getText().contains("funcao")) {
            //Salvar tipo de retorno da função na tabela para recuperar em comandos de print
            TabelaDeSimbolos.TipoLa tipoTabela = null;
            String tipo = ctx.tipo_estendido().getText();
            if (tipo.equals("inteiro")) {
                tipo = "int";
                tipoTabela = TabelaDeSimbolos.TipoLa.INT;
            } else if (tipo.equals("literal")) {
                tipo = "char*";
            }
            tabelaDeSimbolos.adicionar(identificador, tipoTabela);
            finalCode.append(tipo).append(" ").append(identificador).append("(");
        }

        // Adicionar parâmetros se existirem
        if (ctx.parametros() != null) {
            visitParametros(ctx.parametros());
        }

        finalCode.append(") {\n");

        // Visitar declarações locais e comandos
        for(LaSemanticParser.Declaracao_localContext decLocal: ctx.declaracao_local()){
            visitDeclaracao_local(decLocal);
        }
        for (LaSemanticParser.CmdContext cmd: ctx.cmd()){
            visitCmd(cmd);
        }

        finalCode.append("}\n");
        return null;
    }

    //Analisar parâmetros das funções
    @Override
    public Void visitParametros(LaSemanticParser.ParametrosContext ctx) {
        for (int i = 0; i < ctx.parametro().size(); i++) {
            visitParametro(ctx.parametro(i));
            if (i < ctx.parametro().size() - 1) {
                finalCode.append(", ");
            }
        }
        return null;
    }

    //Add códgio do parâmmetro em C
    @Override
    public Void visitParametro(LaSemanticParser.ParametroContext ctx) {
        String tipo = ctx.tipo_estendido().getText();
        if (tipo.equals("inteiro")) {
            tipo = "int";
        } else if (tipo.equals("literal")) {
            tipo = "char*";
        }

        if (ctx.getChild(0).getText().equals("var")) {
            tipo += "*";
        }

        for (LaSemanticParser.IdentificadorContext identCtx : ctx.identificador()) {
            finalCode.append(tipo).append(" ").append(identCtx.getText());
        }

        return null;
    }  

    //Chamada de função
    @Override
    public Void visitCmdChamada(LaSemanticParser.CmdChamadaContext ctx) {
        finalCode.append(ctx.IDENT().getText()).append("(");
        for (int i = 0; i < ctx.expressao().size(); i++) {
            visitExpressao(ctx.expressao(i));
            if (i < ctx.expressao().size() - 1) {
                finalCode.append(", ");
            }
        }
        finalCode.append(");\n");
        return null;
    }

    //Declaração de variáveis, constantes, structs e typedef
    @Override
    public Void visitDeclaracao_local(LaSemanticParser.Declaracao_localContext ctx) {
        String tipo = null;
        if (ctx.IDENT() != null) {
            // Lidando com constantes
            if (ctx.valor_constante() != null) {
                finalCode.append("#define ").append(ctx.IDENT().getText()).append(" ")
                        .append(ctx.valor_constante().getText()).append("\n");
                // Lidando com typedef
            } else if (ctx.tipo() != null) {
                finalCode.append("typedef ");
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
                    case "^inteiro":
                        tipo = "ponteiroint";
                        break;
                    case "^real":
                        tipo = "ponteiroreal";
                        break;
                    default:
                        break;
                }
                TabelaDeSimbolos.TipoLa tipoTable = TabelaDeSimbolos.TipoLa.valueOf(tipo.toUpperCase());
                for (IdentificadorContext ctxVariable : ctx.variavel().identificador()) {
                    if (ctxVariable.dimensao().getText().length() > 0) {
                        String varLoopNome = ctxVariable.dimensao().exp_aritmetica(0).getText();
                        int varLoop = Integer.parseInt(varLoopNome);
                        for (int i = 0; i < varLoop; i++) {
                            String indice = Integer.toString(i);
                            String nomeTabela = ctxVariable.IDENT(0).getText() + "[" + indice + "]";
                            tabelaDeSimbolos.adicionar(nomeTabela, tipoTable);
                        }
                    } else {
                        tabelaDeSimbolos.adicionar(ctxVariable.getText(), tipoTable);
                    }

                    String nomeVar = ctxVariable.getText();

                    switch (tipoTable) {
                        case INT:
                            tipo = "int " + nomeVar + ";\n";
                            break;
                        case PONTEIROINT:
                            tipo = "int* " + nomeVar + ";\n";
                            break;
                        case REAL:
                            tipo = "float " + nomeVar + ";\n";
                            break;
                        case PONTEIROREAL:
                            tipo = "float* " + nomeVar + ";\n";
                            break;
                        case LIT:
                            tipo = "char " + nomeVar + "[80];\n";
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

    //Analisar comandos
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
        } else if (ctx.cmdPara() != null) {
            visitCmdPara(ctx.cmdPara());
        } else if (ctx.cmdEnquanto() != null) {
            visitCmdEnquanto(ctx.cmdEnquanto());
        } else if (ctx.cmdFaca() != null) {
            visitCmdFaca(ctx.cmdFaca());
        }else if(ctx.cmdRetorne() != null){
            visitCmdRetorne(ctx.cmdRetorne());
        }
        return null;
    }

    //Add comando retorne
    @Override
    public Void visitCmdRetorne(LaSemanticParser.CmdRetorneContext ctx){
        finalCode.append("return " + ctx.expressao().getText() + ";");
        return null;
    }
    //Análise scanf
    @Override
    public Void visitCmdLeia(LaSemanticParser.CmdLeiaContext ctx) {
        for (LaSemanticParser.IdentificadorContext identificadorCtx : ctx.identificador()) {
            String nomeVar = identificadorCtx.getText();
            TabelaDeSimbolos.TipoLa tipo = tabelaDeSimbolos.verificar(nomeVar); // Verifica o tipo na tabela de símbolos
            String tipoCSimbolo = getCSymbol(tipo); //Gera o tipo com "%" de acordo

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
                String nomeVar = expressaoCtx.getText();
                if (nomeVar.contains("(")) {
                    //Pega apenas o nome da função
                    int index = nomeVar.indexOf("(");
                    nomeVar = nomeVar.substring(0, index);
                }
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
            if (!expressaoCtx.getText().contains("\"")) {
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
        //Pegando a variável atribuída e o valor
        String[] var_valor = ctx.getText().split("<-");

        if (var_valor[0].contains("^")) {
            finalCode.append("*");
        }
        TabelaDeSimbolos.TipoLa tipoVar = tabelaDeSimbolos.verificar(var_valor[0]);
        //Se for string usa strcpy
        if (tipoVar == TabelaDeSimbolos.TipoLa.LIT) {
            finalCode.append("strcpy(");

            finalCode.append(ctx.identificador().getText() + "," + ctx.expressao().getText() + ");\n");
        } else {
            finalCode.append(ctx.identificador().getText());
            finalCode.append(" = ");
            finalCode.append(ctx.expressao().getText());
            finalCode.append(";\n");
        }
        return null;
    };

    //Geração do for
    @Override
    public Void visitCmdPara(LaSemanticParser.CmdParaContext ctx) {
        finalCode.append("for(" + ctx.IDENT().getText() + "=" + ctx.exp_aritmetica(0).getText() + "; ");
        finalCode.append(ctx.IDENT().getText() + "<=" + ctx.exp_aritmetica(1).getText() + "; ");
        finalCode.append(ctx.IDENT().getText() + "++){\n");
        for (LaSemanticParser.CmdContext cmdCtx : ctx.cmd()) {
            visitCmd(cmdCtx);
        }
        finalCode.append("}\n");
        return null;

    }

    //Geração do while
    @Override
    public Void visitCmdEnquanto(CmdEnquantoContext ctx) {
        finalCode.append("while(" + ctx.expressao().getText());
        finalCode.append("){\n");
        for (LaSemanticParser.CmdContext cmdCtx : ctx.cmd()) {
            visitCmd(cmdCtx);
        }
        finalCode.append("}\n");
        return null;
    }
    //Geração do do-while
    @Override
    public Void visitCmdFaca(CmdFacaContext ctx) {
        finalCode.append("do{\n");
        for (LaSemanticParser.CmdContext cmdCtx : ctx.cmd()) {
            visitCmd(cmdCtx);
        }
        finalCode.append("} while(");
        visitExpressao(ctx.expressao());
        finalCode.append(");\n");
        return null;
    }

        /*    Análise a partir das Expressões até  Parcela não unário (final na hierarquia)*/
    
    @Override
    public Void visitExpressao(ExpressaoContext ctx) {
        if (ctx.termo_logico() != null) {
            visitTermo_logico(ctx.termo_logico(0));
            //Adicionado OR
            for (int i = 1; i < ctx.termo_logico().size(); i++) {
                finalCode.append(" || ");
                visitTermo_logico(ctx.termo_logico(i));
            }
        }

        return null;
    }

    @Override
    public Void visitTermo_logico(Termo_logicoContext ctx) {
        visitFator_logico(ctx.fator_logico(0));
        //Adicionando AND
        for (int i = 1; i < ctx.fator_logico().size(); i++) {
            finalCode.append(" && ");
            visitFator_logico(ctx.fator_logico(i));
        }

        return null;
    }

    @Override
    public Void visitFator_logico(Fator_logicoContext ctx) {
        //Adicionando NOT
        if (ctx.getText().startsWith("nao")) {
            finalCode.append("!");
        }

        visitParcela_logica(ctx.parcela_logica());

        return null;
    }

    //Analisando True e False
    @Override
    public Void visitParcela_logica(Parcela_logicaContext ctx) {
        if (ctx.exp_relacional() != null) {
            visitExp_relacional(ctx.exp_relacional());
        } else {
            if (ctx.getText().equals("verdadeiro")) {
                finalCode.append("true");
            } else {
                finalCode.append("false");
            }
        }

        return null;
    }
    

    @Override
    public Void visitExp_relacional(Exp_relacionalContext ctx) {
        visitExp_aritmetica(ctx.exp_aritmetica(0));
        //Transformando operador de igual
        for (int i = 1; i < ctx.exp_aritmetica().size(); i++) {
            finalCode.append(" ");
            if (ctx.op_relacional().getText().equals("=")) {
                finalCode.append("==");
            } else {
                finalCode.append(ctx.op_relacional().getText());
            }
            finalCode.append(" ");
            visitExp_aritmetica(ctx.exp_aritmetica(i));
        }

        return null;
    }

    @Override
    public Void visitExp_aritmetica(Exp_aritmeticaContext ctx) {
        visitTermo(ctx.termo(0));
        //definindo Exp_aritmetica, termo a termo
        for (int i = 1; i < ctx.termo().size(); i++) {
            finalCode.append(" ");
            finalCode.append(ctx.op1(i - 1).getText());
            finalCode.append(" ");
            visitTermo(ctx.termo(i));
        }

        return null;
    }

    @Override
    public Void visitTermo(TermoContext ctx) {
        visitFator(ctx.fator(0));

        for (int i = 1; i < ctx.fator().size(); i++) {
            finalCode.append(" ");
            finalCode.append(ctx.op2(i - 1).getText());
            finalCode.append(" ");
            //Analisa o fator
            visitFator(ctx.fator(i));
        }

        return null;
    }

    @Override
    public Void visitFator(FatorContext ctx) {
        visitParcela(ctx.parcela(0));

        for (int i = 1; i < ctx.parcela().size(); i++) {
            finalCode.append(" ");
            finalCode.append(ctx.op3(i - 1).getText());
            finalCode.append(" ");
            visitParcela(ctx.parcela(i));
        }

        return null;
    }

    @Override
    public Void visitParcela(ParcelaContext ctx) {
        if (ctx.parcela_unario() != null) {
            if (ctx.op_unario() != null) {
                finalCode.append(ctx.op_unario().getText());
            }
            //Analise de unario ou não unário
            visitParcela_unario(ctx.parcela_unario());
        } else {
            visitParcela_nao_unario(ctx.parcela_nao_unario());
        }

        return null;
    }

    @Override
    public Void visitParcela_unario(Parcela_unarioContext ctx) {
        if (ctx.IDENT() != null) {
            finalCode.append(ctx.IDENT().getText());
            finalCode.append("(");
            for (int i = 0; i < ctx.expressao().size(); i++) {
                visitExpressao(ctx.expressao(i));
                if (i < ctx.expressao().size() - 1) {
                    finalCode.append(", ");
                }
            }
            finalCode.append(")");
        } else {
            finalCode.append(ctx.getText());
        }

        return null;
    }

    @Override
    public Void visitParcela_nao_unario(Parcela_nao_unarioContext ctx) {
        finalCode.append(ctx.getText());
        return null;
    }

}
