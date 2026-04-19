package compilmusique;

import java.util.ArrayList;
import java.util.List;

import compilmusique.Jeton.TypeJeton;
import compilmusique.Noeud.*;

public class AnalyseurSyntaxique {

    public static class ExceptionSyntaxique extends RuntimeException {
        public ExceptionSyntaxique(int ligne, int col, String msg) {
            super("[ERREUR SYNTAXIQUE]  ligne " + ligne + ", col " + col + " : " + msg);
        }
    }

    private final List<Jeton> jetons;
    private int pos;

    public AnalyseurSyntaxique(List<Jeton> jetons) {
        this.jetons = jetons;
        this.pos    = 0;
    }

    private Jeton courant() {
        return jetons.get(pos);
    }

    private Jeton consommer(TypeJeton attendu) {
        var j = courant();
        if (j.type() != attendu) {
            throw new ExceptionSyntaxique(j.ligne(), j.col(),
                "'" + attendu.name().toLowerCase() + "' attendu, trouvé '" + j.valeur() + "'");
        }
        pos++;
        return j;
    }

    private boolean estType(TypeJeton t) {
        return courant().type() == t;
    }

    // programme ::= bloc_partie* bloc_song
    public NoeudProgramme analyserProgramme() {
        var parties = new ArrayList<NoeudPartie>();
        while (estType(TypeJeton.PARTIE)) {
            parties.add(analyserPartie());
        }
        var chanson = analyserChanson();
        consommer(TypeJeton.FIN);
        return new NoeudProgramme(parties, chanson, 1, 1);
    }

    // bloc_partie ::= 'Part' IDENTIFIANT '{' reglage reglage instruction* '}'
    private NoeudPartie analyserPartie() {
        var tok = consommer(TypeJeton.PARTIE);
        // On accepte CHANSON comme nom ici — l'analyseur sémantique le rejettera comme réservé
        Jeton nom;
        if (estType(TypeJeton.IDENTIFIANT) || estType(TypeJeton.CHANSON)) {
            nom = courant(); pos++;
        } else {
            nom = consommer(TypeJeton.IDENTIFIANT); // déclenche le message d'erreur approprié
        }
        consommer(TypeJeton.ACCOLADE_OUV);

        var reglages = new ArrayList<Noeud>();
        // Collecte les réglages (tempo/volume) dans n'importe quel ordre
        while (estType(TypeJeton.TEMPO) || estType(TypeJeton.VOLUME)) {
            reglages.add(analyserReglage());
        }

        var instructions = new ArrayList<Noeud>();
        while (!estType(TypeJeton.ACCOLADE_FERM)) {
            instructions.add(analyserInstruction());
        }
        consommer(TypeJeton.ACCOLADE_FERM);

        return new NoeudPartie(nom.valeur(), reglages, instructions, tok.ligne(), tok.col());
    }

    // bloc_song ::= 'Song' '{' enonce_song* '}'
    private NoeudChanson analyserChanson() {
        var tok = consommer(TypeJeton.CHANSON);
        consommer(TypeJeton.ACCOLADE_OUV);

        var enonces = new ArrayList<Noeud>();
        while (!estType(TypeJeton.ACCOLADE_FERM)) {
            enonces.add(analyserEnonceChanson());
        }
        consommer(TypeJeton.ACCOLADE_FERM);

        return new NoeudChanson(enonces, tok.ligne(), tok.col());
    }

    // enonce_song ::= appel_partie | repeter_song
    private Noeud analyserEnonceChanson() {
        if (estType(TypeJeton.REPETER)) return analyserRepeter(false);
        if (estType(TypeJeton.IDENTIFIANT)) return analyserAppelPartie();
        var j = courant();
        throw new ExceptionSyntaxique(j.ligne(), j.col(),
            "énoncé de chanson attendu, trouvé '" + j.valeur() + "'");
    }

    // instruction ::= play | chord | wait | repeter_instruction | appel_partie
    private Noeud analyserInstruction() {
        return switch (courant().type()) {
            case JOUER      -> analyserJouer();
            case ACCORD     -> analyserAccord();
            case ATTENDRE   -> analyserAttendre();
            case REPETER    -> analyserRepeter(true);
            case IDENTIFIANT -> analyserAppelPartie();
            default -> {
                var j = courant();
                throw new ExceptionSyntaxique(j.ligne(), j.col(),
                    "instruction attendue, trouvé '" + j.valeur() + "'");
            }
        };
    }

    // réglage ::= énoncé_tempo | énoncé_volume
    private Noeud analyserReglage() {
        if (estType(TypeJeton.TEMPO))  return analyserTempo();
        if (estType(TypeJeton.VOLUME)) return analyserVolume();
        var j = courant();
        throw new ExceptionSyntaxique(j.ligne(), j.col(),
            "'tempo' ou 'volume' attendu, trouvé '" + j.valeur() + "'");
    }

    // énoncé_tempo ::= 'tempo' ENTIER ';'
    private NoeudTempo analyserTempo() {
        var tok = consommer(TypeJeton.TEMPO);
        var val = consommer(TypeJeton.ENTIER);
        consommer(TypeJeton.POINT_VIRGULE);
        return new NoeudTempo(Integer.parseInt(val.valeur()), tok.ligne(), tok.col());
    }

    // énoncé_volume ::= 'volume' ENTIER ';'
    private NoeudVolume analyserVolume() {
        var tok = consommer(TypeJeton.VOLUME);
        var val = consommer(TypeJeton.ENTIER);
        consommer(TypeJeton.POINT_VIRGULE);
        return new NoeudVolume(Integer.parseInt(val.valeur()), tok.ligne(), tok.col());
    }

    // play ::= 'play' NOTE DUREE '.'? ';'
    private NoeudJouer analyserJouer() {
        var tok     = consommer(TypeJeton.JOUER);
        var note    = consommer(TypeJeton.NOTE);
        var dur     = consommer(TypeJeton.DUREE);
        boolean pointee = false;
        if (estType(TypeJeton.POINT)) { consommer(TypeJeton.POINT); pointee = true; }
        consommer(TypeJeton.POINT_VIRGULE);
        return new NoeudJouer(note.valeur(), dur.valeur(), pointee, tok.ligne(), tok.col());
    }

    // chord ::= 'chord' '[' NOTE (',' NOTE)* ']' DUREE ';'
    private NoeudAccord analyserAccord() {
        var tok = consommer(TypeJeton.ACCORD);
        consommer(TypeJeton.CROCHET_OUV);
        var notes = new ArrayList<String>();
        notes.add(consommer(TypeJeton.NOTE).valeur());
        while (estType(TypeJeton.VIRGULE)) {
            consommer(TypeJeton.VIRGULE);
            notes.add(consommer(TypeJeton.NOTE).valeur());
        }
        consommer(TypeJeton.CROCHET_FERM);
        var dur = consommer(TypeJeton.DUREE);
        consommer(TypeJeton.POINT_VIRGULE);
        return new NoeudAccord(notes, dur.valeur(), tok.ligne(), tok.col());
    }

    // wait ::= 'wait' ENTIER? DUREE '.'? ';'
    private NoeudAttendre analyserAttendre() {
        var tok = consommer(TypeJeton.ATTENDRE);
        int mult = 1;
        if (estType(TypeJeton.ENTIER)) {
            mult = Integer.parseInt(consommer(TypeJeton.ENTIER).valeur());
        }
        var dur = consommer(TypeJeton.DUREE);
        boolean pointee = false;
        if (estType(TypeJeton.POINT)) { consommer(TypeJeton.POINT); pointee = true; }
        consommer(TypeJeton.POINT_VIRGULE);
        return new NoeudAttendre(mult, dur.valeur(), pointee, tok.ligne(), tok.col());
    }

    // repeat ::= 'repeat' ENTIER '{' (instruction | enonce_song)* '}'
    private NoeudRepeter analyserRepeter(boolean dansPartie) {
        var tok  = consommer(TypeJeton.REPETER);
        var fois = consommer(TypeJeton.ENTIER);
        consommer(TypeJeton.ACCOLADE_OUV);
        var corps = new ArrayList<Noeud>();
        while (!estType(TypeJeton.ACCOLADE_FERM)) {
            if (dansPartie) corps.add(analyserInstruction());
            else            corps.add(analyserEnonceChanson());
        }
        consommer(TypeJeton.ACCOLADE_FERM);
        return new NoeudRepeter(Integer.parseInt(fois.valeur()), corps, tok.ligne(), tok.col());
    }

    // appel_partie ::= IDENTIFIANT ';'  (appel d'une Part par son nom)
    private NoeudAppelPartie analyserAppelPartie() {
        var tok = consommer(TypeJeton.IDENTIFIANT);
        consommer(TypeJeton.POINT_VIRGULE);
        return new NoeudAppelPartie(tok.valeur(), tok.ligne(), tok.col());
    }
}
