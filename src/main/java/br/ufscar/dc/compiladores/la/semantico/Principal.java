package br.ufscar.dc.compiladores.la.semantico;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

public class Principal {

    public static void main(String[] args) {
        try {
            String arquivoSaida = args[1];
            CharStream cs = CharStreams.fromFileName(args[0]);

            System.out.println(arquivoSaida);
            try (PrintWriter pw = new PrintWriter(arquivoSaida)) {
                System.out.println("Tentanod gerar arquivo");
                
                try {
                    LaSemanticLexer lex = new LaSemanticLexer(cs);
                    Token t = null;
                    while ((t = lex.nextToken()).getType() != Token.EOF) {
                        String nomeToken = LaSemanticLexer.VOCABULARY.getDisplayName(t.getType());
                        switch (nomeToken) {
                            case "ERRO":
                                throw new ParseCancellationException(
                                        "Linha " + t.getLine() + ": " + t.getText() + " - simbolo nao identificado");
                            case "CADEIA_NAO_FECHADA":
                                throw new ParseCancellationException(
                                        "Linha " + t.getLine() + ": cadeia literal nao fechada");
                            case "COMENTARIO_NAO_FECHADO":
                                throw new ParseCancellationException(
                                        "Linha " + t.getLine() + ": comentario nao fechado");
                        }
                    }
                    lex.reset();
                    CommonTokenStream tokens = new CommonTokenStream(lex);
                    LaSemanticParser parser = new LaSemanticParser(tokens);
                    LaSemanticParser.ProgramaContext programa = parser.programa();

                    // Realizar análise semântica
                    LaSemantico semantic = new LaSemantico();
                    semantic.visitPrograma(programa);

                    // Gerar código em C
                    LaToC generator = new LaToC();
                    generator.visitPrograma(programa);

                    // Verificar se o código foi gerado
                    if (generator.finalCode.length() > 0) {
                        pw.write(generator.finalCode.toString());
                    } else {
                        System.err.println("Nenhum código foi gerado.");
                    }

                } catch (ParseCancellationException e) {
                    pw.println(e.getMessage());
                    pw.println("Fim da compilacao");
                }
            } catch (FileNotFoundException fnfe) {
                System.err.println("O arquivo/diretório não existe: " + args[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
