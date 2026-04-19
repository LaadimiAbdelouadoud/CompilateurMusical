package compilmusique;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.*;

import compilmusique.Noeud.*;

public class GenerateurMidi implements Visiteur<Void> {

    private static final int RESOLUTION = 480; // PPQ ticks per quarter note
    private static final int CANAL      = 0;
    private static final int PROGRAMME  = 0;   // Acoustic Grand Piano

    private Sequence sequence;
    private Track track;
    private long ticActuel;

    private int tempoActuel;
    private int volumeActuel;

    private Map<String, NoeudPartie> parties;

    // -------------------------------------------------------------------------
    // Public API

    public Sequence generer(NoeudProgramme programme) throws Exception {
        sequence = new Sequence(Sequence.PPQ, RESOLUTION);
        track    = sequence.createTrack();
        ticActuel = 0;

        // Build part lookup
        parties = new HashMap<>();
        for (var p : programme.parties) parties.put(p.nom, p);

        // Set instrument
        var msg = new ShortMessage();
        msg.setMessage(ShortMessage.PROGRAM_CHANGE, CANAL, PROGRAMME, 0);
        track.add(new MidiEvent(msg, 0));

        programme.accepter(this);
        return sequence;
    }

    public static void ecrire(Sequence sequence, String chemin) throws Exception {
        MidiSystem.write(sequence, 1, new File(chemin));
    }

    public static void jouerSequence(Sequence sequence) throws Exception {
        var synth    = MidiSystem.getSynthesizer();
        var sequencer = MidiSystem.getSequencer(false);
        synth.open();
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synth.getReceiver());
        sequencer.setSequence(sequence);
        sequencer.start();
        while (sequencer.isRunning()) Thread.sleep(100);
        sequencer.stop();
        sequencer.close();
        synth.close();
    }

    // -------------------------------------------------------------------------
    // Note / duration utilities

    public static int noteVersMidi(String note) {
        char hauteur   = note.charAt(0);
        int  i         = 1;
        int  alteration = 0;
        if (i < note.length() && (note.charAt(i) == '#' || note.charAt(i) == 'b')) {
            alteration = note.charAt(i) == '#' ? 1 : -1;
            i++;
        }
        int octave = Integer.parseInt(note.substring(i));
        int demi = switch (hauteur) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default  -> throw new IllegalArgumentException("Hauteur inconnue : " + hauteur);
        };
        return (octave + 1) * 12 + demi + alteration;
    }

    public static long dureeVersTics(String duree, boolean pointee) {
        long base = switch (duree) {
            case "whole"      -> 1920L;
            case "half"       -> 960L;
            case "quarter"    -> 480L;
            case "eighth"     -> 240L;
            case "sixteenth"  -> 120L;
            default -> throw new IllegalArgumentException("Unknown duration: " + duree);
        };
        return pointee ? (base * 3 / 2) : base;
    }

    // -------------------------------------------------------------------------
    // Visitor implementation

    @Override
    public Void visiterProgramme(NoeudProgramme n) {
        // Parts are visited via appel_partie from chanson
        n.chanson.accepter(this);
        return null;
    }

    @Override
    public Void visiterPartie(NoeudPartie n) {
        // Read settings first
        for (var r : n.reglages) r.accepter(this);
        // Apply tempo change event
        setTempoEvent(tempoActuel);
        for (var instr : n.instructions) instr.accepter(this);
        return null;
    }

    @Override
    public Void visiterChanson(NoeudChanson n) {
        for (var e : n.enonces) e.accepter(this);
        return null;
    }

    @Override
    public Void visiterJouer(NoeudJouer n) {
        long duree = dureeVersTics(n.duree, n.pointee);
        int  midi  = noteVersMidi(n.note);
        noteOn(midi, volumeActuel, ticActuel);
        noteOff(midi, ticActuel + duree);
        ticActuel += duree;
        return null;
    }

    @Override
    public Void visiterAccord(NoeudAccord n) {
        long duree = dureeVersTics(n.duree, false);
        for (var note : n.notes) {
            int midi = noteVersMidi(note);
            noteOn(midi, volumeActuel, ticActuel);
            noteOff(midi, ticActuel + duree);
        }
        ticActuel += duree;
        return null;
    }

    @Override
    public Void visiterAttendre(NoeudAttendre n) {
        ticActuel += (long) n.multiplicateur * dureeVersTics(n.duree, n.pointee);
        return null;
    }

    @Override
    public Void visiterRepeter(NoeudRepeter n) {
        for (int i = 0; i < n.fois; i++) {
            for (var c : n.corps) c.accepter(this);
        }
        return null;
    }

    @Override
    public Void visiterAppelPartie(NoeudAppelPartie n) {
        parties.get(n.nom).accepter(this);
        return null;
    }

    @Override
    public Void visiterTempo(NoeudTempo n) {
        tempoActuel = n.valeur;
        return null;
    }

    @Override
    public Void visiterVolume(NoeudVolume n) {
        volumeActuel = n.valeur;
        return null;
    }

    // -------------------------------------------------------------------------
    // MIDI helpers

    private void noteOn(int pitch, int velocity, long tick) {
        try {
            var msg = new ShortMessage();
            msg.setMessage(ShortMessage.NOTE_ON, CANAL, pitch, velocity);
            track.add(new MidiEvent(msg, tick));
        } catch (InvalidMidiDataException e) {
            throw new RuntimeException(e);
        }
    }

    private void noteOff(int pitch, long tick) {
        try {
            var msg = new ShortMessage();
            msg.setMessage(ShortMessage.NOTE_OFF, CANAL, pitch, 0);
            track.add(new MidiEvent(msg, tick));
        } catch (InvalidMidiDataException e) {
            throw new RuntimeException(e);
        }
    }

    private void setTempoEvent(int bpm) {
        // MIDI tempo is in microseconds per quarter note
        int microsPQ = 60_000_000 / bpm;
        byte[] data = {
            (byte) ((microsPQ >> 16) & 0xFF),
            (byte) ((microsPQ >>  8) & 0xFF),
            (byte) ( microsPQ        & 0xFF)
        };
        try {
            var msg = new MetaMessage();
            msg.setMessage(0x51, data, 3);
            track.add(new MidiEvent(msg, ticActuel));
        } catch (InvalidMidiDataException e) {
            throw new RuntimeException(e);
        }
    }
}
