package compilmusique;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import compilmusique.Noeud.*;

public class AnalyseurSemantique implements Visiteur<Void> {

    public static class ExceptionSemantique extends RuntimeException {
        public ExceptionSemantique(int ligne, int col, String msg) {
            super("[ERREUR SÉMANTIQUE]  ligne " + ligne + ", col " + col + " : " + msg);
        }
    }

    private Map<String, NoeudPartie> partiesDefinies;
    private final Deque<String> pileAppels = new ArrayDeque<>();

    @Override
    public Void visiterProgramme(NoeudProgramme n) {
        // Pass 1 — collect all Partie names
        partiesDefinies = new HashMap<>();
        Set<String> doublons = new HashSet<>();
        for (var partie : n.parties) {
            if ("Song".equals(partie.nom)) {
                throw new ExceptionSemantique(partie.ligne, partie.col,
                    "the name 'Song' is reserved and cannot be used as a Part name");
            }
            if (partiesDefinies.containsKey(partie.nom)) {
                throw new ExceptionSemantique(partie.ligne, partie.col,
                    "la Partie '" + partie.nom + "' est définie en double");
            }
            partiesDefinies.put(partie.nom, partie);
        }

        // Pass 2 — walk and validate
        for (var partie : n.parties) {
            partie.accepter(this);
        }
        n.chanson.accepter(this);
        return null;
    }

    @Override
    public Void visiterPartie(NoeudPartie n) {
        boolean aTempo = false, aVolume = false;
        for (var r : n.reglages) {
            if (r instanceof NoeudTempo t) {
                if (aTempo) throw new ExceptionSemantique(t.ligne, t.col,
                    "la Partie '" + n.nom + "' a un 'tempo' en double");
                aTempo = true;
                t.accepter(this);
            } else if (r instanceof NoeudVolume v) {
                if (aVolume) throw new ExceptionSemantique(v.ligne, v.col,
                    "la Partie '" + n.nom + "' a un 'volume' en double");
                aVolume = true;
                v.accepter(this);
            }
        }
        if (!aTempo)  throw new ExceptionSemantique(n.ligne, n.col,
            "la Partie '" + n.nom + "' n'a pas de 'tempo'");
        if (!aVolume) throw new ExceptionSemantique(n.ligne, n.col,
            "la Partie '" + n.nom + "' n'a pas de 'volume'");

        for (var instr : n.instructions) instr.accepter(this);
        return null;
    }

    @Override
    public Void visiterChanson(NoeudChanson n) {
        for (var e : n.enonces) e.accepter(this);
        return null;
    }

    @Override
    public Void visiterJouer(NoeudJouer n) { return null; }

    @Override
    public Void visiterAccord(NoeudAccord n) { return null; }

    @Override
    public Void visiterAttendre(NoeudAttendre n) {
        if (n.multiplicateur <= 0) {
            throw new ExceptionSemantique(n.ligne, n.col,
                "le multiplicateur de 'attendre' doit être > 0, trouvé " + n.multiplicateur);
        }
        return null;
    }

    @Override
    public Void visiterRepeter(NoeudRepeter n) {
        for (var c : n.corps) c.accepter(this);
        return null;
    }

    @Override
    public Void visiterAppelPartie(NoeudAppelPartie n) {
        if (!partiesDefinies.containsKey(n.nom)) {
            throw new ExceptionSemantique(n.ligne, n.col,
                "la Partie '" + n.nom + "' n'est pas définie");
        }
        if (pileAppels.contains(n.nom)) {
            throw new ExceptionSemantique(n.ligne, n.col,
                "appel récursif détecté vers la Partie '" + n.nom + "'");
        }
        pileAppels.push(n.nom);
        partiesDefinies.get(n.nom).accepter(this);
        pileAppels.pop();
        return null;
    }

    @Override
    public Void visiterTempo(NoeudTempo n) {
        if (n.valeur < 1 || n.valeur > 300) {
            throw new ExceptionSemantique(n.ligne, n.col,
                "tempo doit être entre 1 et 300, trouvé " + n.valeur);
        }
        return null;
    }

    @Override
    public Void visiterVolume(NoeudVolume n) {
        if (n.valeur < 0 || n.valeur > 127) {
            throw new ExceptionSemantique(n.ligne, n.col,
                "volume doit être entre 0 et 127, trouvé " + n.valeur);
        }
        return null;
    }
}
