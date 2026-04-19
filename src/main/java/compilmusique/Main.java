package compilmusique;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar compilmusique.jar <fichier.musique> [--sortie <fichier>] [--jouer]");
            System.exit(1);
        }

        String fichierSource = args[0];
        String fichierSortie = "sortie.mid";
        boolean jouer = false;

        for (int i = 1; i < args.length; i++) {
            if ("--sortie".equals(args[i]) && i + 1 < args.length) {
                fichierSortie = args[++i];
            } else if ("--jouer".equals(args[i])) {
                jouer = true;
            }
        }

        try {
            var source = Files.readString(Path.of(fichierSource));

            var lexer  = new AnalyseurLexical(source);
            var jetons = lexer.tokeniser();

            var parser = new AnalyseurSyntaxique(jetons);
            var ast    = parser.analyserProgramme();

            var semantique = new AnalyseurSemantique();
            semantique.visiterProgramme(ast);

            var generateur = new GenerateurMidi();
            var sequence   = generateur.generer(ast);

            GenerateurMidi.ecrire(sequence, fichierSortie);
            System.out.println("MIDI généré : " + fichierSortie);

            if (jouer) GenerateurMidi.jouerSequence(sequence);

        } catch (Jeton.ExceptionLexicale |
                 AnalyseurSyntaxique.ExceptionSyntaxique |
                 AnalyseurSemantique.ExceptionSemantique e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Erreur inattendue : " + e.getMessage());
            System.exit(1);
        }
    }
}
