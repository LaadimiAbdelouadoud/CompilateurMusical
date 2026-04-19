package compilmusique;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import compilmusique.Jeton.EtatLexeur;
import compilmusique.Jeton.ExceptionLexicale;
import compilmusique.Jeton.TypeJeton;

public class AnalyseurLexical {

    private final String source;
    private int pos;
    private int ligne;
    private int col;
    private EtatLexeur etat;

    private static final Map<String, TypeJeton> MOTS_CLES = Map.ofEntries(
        Map.entry("Part",       TypeJeton.PARTIE),
        Map.entry("Song",       TypeJeton.CHANSON),
        Map.entry("tempo",      TypeJeton.TEMPO),
        Map.entry("volume",     TypeJeton.VOLUME),
        Map.entry("play",       TypeJeton.JOUER),
        Map.entry("chord",      TypeJeton.ACCORD),
        Map.entry("wait",       TypeJeton.ATTENDRE),
        Map.entry("repeat",     TypeJeton.REPETER),
        Map.entry("whole",      TypeJeton.DUREE),
        Map.entry("half",       TypeJeton.DUREE),
        Map.entry("quarter",    TypeJeton.DUREE),
        Map.entry("eighth",     TypeJeton.DUREE),
        Map.entry("sixteenth",  TypeJeton.DUREE)
    );

    public AnalyseurLexical(String source) {
        this.source = source;
        this.pos    = 0;
        this.ligne  = 1;
        this.col    = 1;
        this.etat   = EtatLexeur.DEBUT;
    }

    private char courant() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    private char avancer() {
        char c = source.charAt(pos++);
        if (c == '\n') { ligne++; col = 1; } else { col++; }
        return c;
    }

    /**
     * DFA transition function — returns the next token from the source.
     *
     * States:
     *   DEBUT          — initial / between tokens
     *   EN_IDENT       — reading an identifier or keyword
     *   EN_ENTIER      — reading an integer literal
     *   EN_NOTE        — reading a note (letter A-G + optional accidental + octave digit)
     *   EN_COMMENTAIRE — inside a // line comment
     */
    public Jeton jetonSuivant() {
        etat = EtatLexeur.DEBUT;
        var buf = new StringBuilder();
        int tokLigne = ligne, tokCol = col;

        while (true) {
            char c = courant();

            switch (etat) {
                case DEBUT -> {
                    tokLigne = ligne;
                    tokCol   = col;

                    if (c == '\0') {
                        return new Jeton(TypeJeton.FIN, "", tokLigne, tokCol);
                    }
                    if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                        avancer();
                        // stay DEBUT
                    } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                        avancer(); avancer();
                        etat = EtatLexeur.EN_COMMENTAIRE;
                    } else if (c >= 'A' && c <= 'G') {
                        buf.setLength(0);
                        buf.append(avancer());
                        etat = EtatLexeur.EN_NOTE;
                    } else if (Character.isLetter(c)) {
                        buf.setLength(0);
                        buf.append(avancer());
                        etat = EtatLexeur.EN_IDENT;
                    } else if (Character.isDigit(c)) {
                        buf.setLength(0);
                        buf.append(avancer());
                        etat = EtatLexeur.EN_ENTIER;
                    } else {
                        char sym = avancer();
                        return switch (sym) {
                            case '{' -> new Jeton(TypeJeton.ACCOLADE_OUV,  "{", tokLigne, tokCol);
                            case '}' -> new Jeton(TypeJeton.ACCOLADE_FERM, "}", tokLigne, tokCol);
                            case '[' -> new Jeton(TypeJeton.CROCHET_OUV,   "[", tokLigne, tokCol);
                            case ']' -> new Jeton(TypeJeton.CROCHET_FERM,  "]", tokLigne, tokCol);
                            case ',' -> new Jeton(TypeJeton.VIRGULE,       ",", tokLigne, tokCol);
                            case ';' -> new Jeton(TypeJeton.POINT_VIRGULE, ";", tokLigne, tokCol);
                            case '.' -> new Jeton(TypeJeton.POINT,         ".", tokLigne, tokCol);
                            default  -> throw new ExceptionLexicale(tokLigne, tokCol,
                                            "unrecognised character '" + sym + "'");
                        };
                    }
                }

                case EN_NOTE -> {
                    // Optional accidental
                    if ((c == '#' || c == 'b') && buf.length() == 1) {
                        buf.append(avancer());
                    // Octave digit → complete note token
                    } else if (c >= '0' && c <= '8') {
                        buf.append(avancer());
                        etat = EtatLexeur.DEBUT;
                        return new Jeton(TypeJeton.NOTE, buf.toString(), tokLigne, tokCol);
                    } else {
                        // No octave — emit as IDENTIFIANT (unget: do NOT consume c)
                        etat = EtatLexeur.DEBUT;
                        var val = buf.toString();
                        return new Jeton(
                            MOTS_CLES.getOrDefault(val, TypeJeton.IDENTIFIANT),
                            val, tokLigne, tokCol);
                    }
                }

                case EN_IDENT -> {
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        buf.append(avancer());
                    } else {
                        // Unget: do NOT consume c
                        etat = EtatLexeur.DEBUT;
                        var val = buf.toString();
                        return new Jeton(
                            MOTS_CLES.getOrDefault(val, TypeJeton.IDENTIFIANT),
                            val, tokLigne, tokCol);
                    }
                }

                case EN_ENTIER -> {
                    if (Character.isDigit(c)) {
                        buf.append(avancer());
                    } else {
                        // Unget: do NOT consume c
                        etat = EtatLexeur.DEBUT;
                        return new Jeton(TypeJeton.ENTIER, buf.toString(), tokLigne, tokCol);
                    }
                }

                case EN_COMMENTAIRE -> {
                    if (c == '\n' || c == '\0') {
                        etat = EtatLexeur.DEBUT;
                    } else {
                        avancer(); // discard comment char
                    }
                }

                case ERREUR -> throw new ExceptionLexicale(tokLigne, tokCol, "lexer error state");
            }
        }
    }

    public List<Jeton> tokeniser() {
        var jetons = new ArrayList<Jeton>();
        Jeton j;
        do {
            j = jetonSuivant();
            jetons.add(j);
        } while (j.type() != TypeJeton.FIN);
        return jetons;
    }
}
