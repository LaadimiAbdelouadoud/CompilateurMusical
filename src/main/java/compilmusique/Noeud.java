package compilmusique;

import java.util.List;

public abstract sealed class Noeud
    permits Noeud.NoeudProgramme, Noeud.NoeudPartie, Noeud.NoeudChanson,
            Noeud.NoeudJouer, Noeud.NoeudAccord, Noeud.NoeudAttendre,
            Noeud.NoeudRepeter, Noeud.NoeudAppelPartie,
            Noeud.NoeudTempo, Noeud.NoeudVolume {

    public final int ligne;
    public final int col;

    protected Noeud(int ligne, int col) {
        this.ligne = ligne;
        this.col   = col;
    }

    public abstract <T> T accepter(Visiteur<T> v);

    // -------------------------------------------------------------------------

    public static final class NoeudProgramme extends Noeud {
        public final List<NoeudPartie> parties;
        public final NoeudChanson chanson;

        public NoeudProgramme(List<NoeudPartie> parties, NoeudChanson chanson, int ligne, int col) {
            super(ligne, col);
            this.parties = parties;
            this.chanson = chanson;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterProgramme(this); }
    }

    public static final class NoeudPartie extends Noeud {
        public final String nom;
        public final List<Noeud> reglages;
        public final List<Noeud> instructions;

        public NoeudPartie(String nom, List<Noeud> reglages, List<Noeud> instructions, int ligne, int col) {
            super(ligne, col);
            this.nom          = nom;
            this.reglages     = reglages;
            this.instructions = instructions;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterPartie(this); }
    }

    public static final class NoeudChanson extends Noeud {
        public final List<Noeud> enonces;

        public NoeudChanson(List<Noeud> enonces, int ligne, int col) {
            super(ligne, col);
            this.enonces = enonces;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterChanson(this); }
    }

    public static final class NoeudJouer extends Noeud {
        public final String note;
        public final String duree;
        public final boolean pointee;

        public NoeudJouer(String note, String duree, boolean pointee, int ligne, int col) {
            super(ligne, col);
            this.note    = note;
            this.duree   = duree;
            this.pointee = pointee;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterJouer(this); }
    }

    public static final class NoeudAccord extends Noeud {
        public final List<String> notes;
        public final String duree;

        public NoeudAccord(List<String> notes, String duree, int ligne, int col) {
            super(ligne, col);
            this.notes = notes;
            this.duree = duree;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterAccord(this); }
    }

    public static final class NoeudAttendre extends Noeud {
        public final int multiplicateur;
        public final String duree;
        public final boolean pointee;

        public NoeudAttendre(int multiplicateur, String duree, boolean pointee, int ligne, int col) {
            super(ligne, col);
            this.multiplicateur = multiplicateur;
            this.duree          = duree;
            this.pointee        = pointee;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterAttendre(this); }
    }

    public static final class NoeudRepeter extends Noeud {
        public final int fois;
        public final List<Noeud> corps;

        public NoeudRepeter(int fois, List<Noeud> corps, int ligne, int col) {
            super(ligne, col);
            this.fois = fois;
            this.corps = corps;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterRepeter(this); }
    }

    public static final class NoeudAppelPartie extends Noeud {
        public final String nom;

        public NoeudAppelPartie(String nom, int ligne, int col) {
            super(ligne, col);
            this.nom = nom;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterAppelPartie(this); }
    }

    public static final class NoeudTempo extends Noeud {
        public final int valeur;

        public NoeudTempo(int valeur, int ligne, int col) {
            super(ligne, col);
            this.valeur = valeur;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterTempo(this); }
    }

    public static final class NoeudVolume extends Noeud {
        public final int valeur;

        public NoeudVolume(int valeur, int ligne, int col) {
            super(ligne, col);
            this.valeur = valeur;
        }

        @Override public <T> T accepter(Visiteur<T> v) { return v.visiterVolume(this); }
    }
}
