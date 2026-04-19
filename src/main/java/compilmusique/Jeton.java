package compilmusique;

public record Jeton(TypeJeton type, String valeur, int ligne, int col) {

    public enum TypeJeton {
        PARTIE, CHANSON, TEMPO, VOLUME, JOUER, ACCORD, ATTENDRE, REPETER,
        NOTE, DUREE, ENTIER, IDENTIFIANT,
        ACCOLADE_OUV, ACCOLADE_FERM, CROCHET_OUV, CROCHET_FERM,
        VIRGULE, POINT_VIRGULE, POINT, FIN
    }

    public enum EtatLexeur {
        DEBUT, EN_IDENT, EN_ENTIER, EN_NOTE, EN_COMMENTAIRE, ERREUR
    }

    public static class ExceptionLexicale extends RuntimeException {
        public ExceptionLexicale(int ligne, int col, String msg) {
            super("[ERREUR LEXICALE]    ligne " + ligne + ", col " + col + " : " + msg);
        }
    }
}
