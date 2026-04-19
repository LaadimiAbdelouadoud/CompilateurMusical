package compilmusique;

import compilmusique.Noeud.*;

public interface Visiteur<T> {
    T visiterProgramme(NoeudProgramme n);
    T visiterPartie(NoeudPartie n);
    T visiterChanson(NoeudChanson n);
    T visiterJouer(NoeudJouer n);
    T visiterAccord(NoeudAccord n);
    T visiterAttendre(NoeudAttendre n);
    T visiterRepeter(NoeudRepeter n);
    T visiterAppelPartie(NoeudAppelPartie n);
    T visiterTempo(NoeudTempo n);
    T visiterVolume(NoeudVolume n);
}
