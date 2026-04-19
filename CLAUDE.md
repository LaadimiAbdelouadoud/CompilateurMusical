# CompilateurMusical — CLAUDE.md

## Project Overview

Compiler for a custom music language **MusiLang** written in Java 26.
Reads `.musique` source files and outputs a real MIDI file (`.mid`)
playable by any media player or DAW.

Java 26 features used where appropriate:
- `record` for `Jeton`
- `sealed classes` for the AST node hierarchy
- Pattern matching `switch` in Visitor dispatch
- `var` for local type inference

### Pipeline

```
Source file → Lexer → Token list → Parser → AST → Semantic Analyser → Code Generator → MIDI file

Fichier source → AnalyseurLexical → List<Jeton> → AnalyseurSyntaxique → NoeudProgramme → AnalyseurSemantique → GenerateurMidi → Sequence → .mid
```

---

## Grammar (source of truth)

```
programme            ::= bloc_partie* bloc_chanson

bloc_partie          ::= 'Partie' IDENTIFIANT '{' reglage reglage instruction* '}'

bloc_chanson         ::= 'Chanson' '{' enonce_chanson* '}'

enonce_chanson       ::= appel_partie
                       | repeter_chanson

instruction          ::= jouer
                       | accord
                       | attendre
                       | repeter_instruction
                       | appel_partie

repeter_chanson      ::= 'repeter' ENTIER '{' enonce_chanson* '}'
repeter_instruction  ::= 'repeter' ENTIER '{' instruction* '}'

reglage              ::= enonce_tempo | enonce_volume

enonce_tempo         ::= 'tempo' ENTIER ';'
enonce_volume        ::= 'volume' ENTIER ';'

jouer                ::= 'jouer' NOTE DUREE '.'? ';'
accord               ::= 'accord' '[' NOTE (',' NOTE)* ']' DUREE ';'
attendre             ::= 'attendre' ENTIER? DUREE '.'? ';'
appel_partie         ::= IDENTIFIANT ';'

NOTE                 ::= HAUTEUR ALTERATION? OCTAVE
HAUTEUR              ::= 'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'G'
ALTERATION           ::= '#' | 'b'
OCTAVE               ::= '0'..'8'

DUREE                ::= 'ronde' | 'blanche' | 'noire' | 'croche' | 'doublecroche'

ENTIER               ::= CHIFFRE+
CHIFFRE              ::= '0'..'9'
IDENTIFIANT          ::= LETTRE (LETTRE | CHIFFRE | '_')*
LETTRE               ::= 'a'..'z' | 'A'..'Z'
```

---

## Semantic Rules

1. Exactly one `Chanson` block must exist in the programme.
2. `Chanson` is a reserved name — no `Partie` may be named `Chanson`.
3. Every `Partie` block must contain exactly one `tempo` and one `volume`, in any order. Missing or duplicate settings → semantic error.
4. `tempo` must be in range 1..300. `volume` must be in range 0..127.
5. Every `appel_partie` must reference a defined `Partie` name.
6. No duplicate `Partie` names.
7. No recursive `Partie` calls (direct or indirect).
8. The multiplier of `attendre`, when provided, must be > 0.

---

## Project Structure (Maven, Java 26)

```
compilmusique/
  pom.xml
  exemple.musique
  src/
    main/java/compilmusique/
      Principal.java              — CLI entry point
      Jeton.java                  — Token record + TokenType enum + LexerState enum + LexerException
      AnalyseurLexical.java       — Deterministic Finite Automaton (DFA) tokeniser
      Noeud.java                  — Sealed AST node base class + all node subclasses
      AnalyseurSyntaxique.java    — Recursive descent Parser + ParseException
      Visiteur.java               — Generic Visitor interface
      AnalyseurSemantique.java    — Semantic Analyser (implements Visitor) + SemanticException
      GenerateurMidi.java         — Code Generator (implements Visitor) + note/duration utils + MIDI writer
    test/java/compilmusique/
      TestLexeur.java
      TestAnalyseurSyntaxique.java
      TestSemantique.java
      TestMidi.java
```

---

## Implementation Details

### Jeton.java — Token

The Token is the atomic unit produced by the Lexer.

```java
public record Jeton(TypeJeton type, String valeur, int ligne, int col) {

    // Token types — vocabulary of the language
    public enum TypeJeton {
        PARTIE, CHANSON, TEMPO, VOLUME, JOUER, ACCORD, ATTENDRE, REPETER,
        NOTE, DUREE, ENTIER, IDENTIFIANT,
        ACCOLADE_OUV, ACCOLADE_FERM, CROCHET_OUV, CROCHET_FERM,
        VIRGULE, POINT_VIRGULE, FIN
    }

    // Lexer FSA states
    public enum EtatLexeur {
        DEBUT, EN_IDENT, EN_ENTIER, EN_NOTE, EN_COMMENTAIRE, ERREUR
    }

    // Lexical error
    public static class ExceptionLexicale extends RuntimeException {
        public ExceptionLexicale(int ligne, int col, String msg) { ... }
    }
}
```

---

### AnalyseurLexical.java — Lexer (DFA)

Implements a **Deterministic Finite Automaton (DFA)**.
**No regex, no string splitting, no libraries** — pure state transitions only.

Fields:
```java
private final String source;
private int pos, ligne, col;
private EtatLexeur etat;
```

Transition function (`jetonSuivant()`):

```
FROM DEBUT:
  whitespace / newline     → stay DEBUT
  '/' followed by '/'      → EN_COMMENTAIRE
  letter 'A'-'G'           → EN_NOTE, start buffer
  letter a-z / A-Z         → EN_IDENT, start buffer
  digit 0-9                → EN_ENTIER, start buffer
  '{'  → emit ACCOLADE_OUV      '}'  → emit ACCOLADE_FERM
  '['  → emit CROCHET_OUV       ']'  → emit CROCHET_FERM
  ','  → emit VIRGULE            ';'  → emit POINT_VIRGULE
  EOF  → emit FIN
  other → ERREUR → throw ExceptionLexicale

FROM EN_IDENT:
  letter / digit / '_'     → stay EN_IDENT, append to buffer
  other                    → emit token via keyword map or IDENTIFIANT
                             unget char → DEBUT

FROM EN_ENTIER:
  digit                    → stay EN_ENTIER, append to buffer
  other                    → emit ENTIER, unget char → DEBUT

FROM EN_NOTE:
  '#' or 'b'               → stay EN_NOTE (accidental), append to buffer
  digit '0'-'8'            → append, emit NOTE → DEBUT
  other                    → unget char, emit IDENTIFIANT (e.g. 'A' alone) → DEBUT

FROM EN_COMMENTAIRE:
  newline                  → DEBUT
  other                    → stay EN_COMMENTAIRE (discard)
```

Keyword map:
```java
Map.of(
  "Partie",       PARTIE,
  "Chanson",      CHANSON,
  "tempo",        TEMPO,
  "volume",       VOLUME,
  "jouer",        JOUER,
  "accord",       ACCORD,
  "attendre",     ATTENDRE,
  "repeter",      REPETER,
  "ronde",        DUREE,
  "blanche",      DUREE,
  "noire",        DUREE,
  "croche",       DUREE,
  "doublecroche", DUREE
)
```

Public methods:
- `Jeton jetonSuivant()` — advance the automaton by one token
- `List<Jeton> tokeniser()` — run until FIN, return full token list

---

### Noeud.java — AST Nodes

All AST node classes live in a single file.
The base class is a **sealed abstract class**, enforcing a closed hierarchy.

```java
public abstract sealed class Noeud
    permits NoeudProgramme, NoeudPartie, NoeudChanson,
            NoeudJouer, NoeudAccord, NoeudAttendre,
            NoeudRepeter, NoeudAppelPartie,
            NoeudTempo, NoeudVolume {

    public final int ligne, col;
    public Noeud(int ligne, int col) { ... }
    public abstract <T> T accepter(Visiteur<T> v);
}
```

Node subtypes (all in `Noeud.java`):

| Node | Fields |
|---|---|
| `NoeudProgramme`   | `List<NoeudPartie> parties`, `NoeudChanson chanson` |
| `NoeudPartie`      | `String nom`, `List<Noeud> reglages`, `List<Noeud> instructions` |
| `NoeudChanson`     | `List<Noeud> enonces` |
| `NoeudJouer`       | `String note`, `String duree`, `boolean pointee` |
| `NoeudAccord`      | `List<String> notes`, `String duree` |
| `NoeudAttendre`    | `int multiplicateur`, `String duree`, `boolean pointee` |
| `NoeudRepeter`     | `int fois`, `List<Noeud> corps` |
| `NoeudAppelPartie` | `String nom` |
| `NoeudTempo`       | `int valeur` |
| `NoeudVolume`      | `int valeur` |

Each node implements `accepter()` by calling the matching `Visitor` method:
```java
// Example
public final class NoeudJouer extends Noeud {
    ...
    @Override public <T> T accepter(Visiteur<T> v) { return v.visiterJouer(this); }
}
```

---

### Visiteur.java — Visitor Interface

Generic Visitor interface parameterised by return type `T`.
Each `Visitor` implementation traverses the AST for a different purpose.

```java
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
```

---

### AnalyseurSyntaxique.java — Parser

**Recursive descent Parser** — one method per grammar rule.
Consumes the `List<Jeton>` produced by the Lexer and builds the AST.

```java
public static class ExceptionSyntaxique extends RuntimeException {
    public ExceptionSyntaxique(int ligne, int col, String msg) { ... }
}
```

Key methods (one per grammar rule):
- `NoeudProgramme analyserProgramme()`
- `NoeudPartie analyserPartie()`
- `NoeudChanson analyserChanson()`
- `Noeud analyserInstruction()`
- `NoeudJouer analyserJouer()`
- `NoeudAccord analyserAccord()`
- `NoeudAttendre analyserAttendre()`
- `NoeudRepeter analyserRepeter()`
- `Noeud analyserReglage()`

Helper:
- `Jeton consommer(TypeJeton attendu)` — consume the next token or throw `ExceptionSyntaxique`

---

### AnalyseurSemantique.java — Semantic Analyser

`implements Visiteur<Void>`

Two-pass `visiterProgramme()`:
- **Pass 1** — collect all `Partie` names into `partiesDefinies` (symbol table)
- **Pass 2** — walk the AST and enforce all semantic rules

Call stack `Deque<String> pileAppels` for recursion detection.

```java
public static class ExceptionSemantique extends RuntimeException {
    public ExceptionSemantique(int ligne, int col, String msg) { ... }
}
```

Checks performed:

| Rule | Check |
|---|---|
| Unique `Chanson` | Exactly one `NoeudChanson` in programme |
| Reserved name    | No `Partie` named `Chanson` |
| Required settings| Each `Partie` has exactly one `tempo` and one `volume` |
| Tempo range      | `1 ≤ tempo ≤ 300` |
| Volume range     | `0 ≤ volume ≤ 127` |
| Defined parts    | Every `NoeudAppelPartie.nom` exists in `partiesDefinies` |
| No duplicates    | No two `Partie` blocks share the same name |
| No recursion     | `pileAppels` must not contain the callee name |
| Wait multiplier  | `multiplicateur > 0` when provided |

---

### GenerateurMidi.java — Code Generator

`implements Visiteur<Void>`

The Code Generator traverses the validated AST and emits MIDI events.

- Piano only: channel 0, MIDI program 0 (Acoustic Grand Piano)
- `javax.sound.midi` only — no external libraries
- `Sequence` type `PPQ`, resolution 480 ticks/quarter note
- `long ticActuel` — current time cursor in ticks

Visitor methods:
- `visiterJouer()` — emit `NOTE_ON` at `ticActuel`, `NOTE_OFF` at `ticActuel + durée`, advance `ticActuel`
- `visiterAccord()` — emit `NOTE_ON` + `NOTE_OFF` for every note at the same tick, advance `ticActuel` by one duration
- `visiterAttendre()` — advance `ticActuel` by `multiplicateur × durée` ticks, no notes
- `visiterRepeter()` — iterate `fois` times, calling `accepter()` on each instruction in `corps`
- `visiterAppelPartie()` — look up `NoeudPartie` by name, walk its instructions
- `visiterPartie()` — set `tempoActuel` and `volumeActuel` from settings, walk instructions

Static utilities (inside `GenerateurMidi.java`):

```java
// Note name → MIDI number. C4 = 60 (middle C), A4 = 69
// Formula: (octave + 1) * 12 + semitones + accidental
// Semitones: C=0 D=2 E=4 F=5 G=7 A=9 B=11 / # = +1, b = -1
public static int noteVersMidi(String note)

// Duration → ticks at resolution 480 PPQ
// ronde=1920, blanche=960, noire=480, croche=240, doublecroche=120
// Dotted duration = base × 1.5
public static long dureeVersTics(String duree, boolean pointee)

// Write Sequence to .mid file on disk
public static void ecrire(Sequence sequence, String chemin) throws Exception

// Play Sequence immediately using the built-in JVM synthesiser
public static void jouerSequence(Sequence sequence) throws Exception
```

---

### Principal.java — CLI Entry Point

```
Usage: java -jar compilmusique.jar <fichier.musique> [options]
  --sortie <fichier>   output MIDI file path (default: sortie.mid)
  --jouer              play MIDI immediately after compilation
```

Pipeline in order:
1. `AnalyseurLexical.tokeniser()` → `List<Jeton>`  *(Lexer)*
2. `AnalyseurSyntaxique.analyserProgramme()` → `NoeudProgramme`  *(Parser → AST)*
3. `AnalyseurSemantique.visiterProgramme()` — halt on error  *(Semantic Analyser)*
4. `GenerateurMidi.visiterProgramme()` → `Sequence`  *(Code Generator)*
5. `GenerateurMidi.ecrire(sequence, chemin)`  *(serialisation)*
6. Optionally `GenerateurMidi.jouerSequence(sequence)`  *(playback)*

Error messages:
```
[ERREUR LEXICALE]    ligne 3, col 7 : caractère non reconnu '@'
[ERREUR SYNTAXIQUE]  ligne 5, col 1 : 'tempo' attendu
[ERREUR SÉMANTIQUE]  ligne 8, col 1 : la Partie 'couplet' n'a pas de 'volume'
```
Exit with code 1 on any error.

---

## exemple.musique

```
Partie couplet {
    tempo 120;
    volume 80;

    jouer C4 noire;
    jouer D4 croche;
    jouer E4 blanche;
    jouer F#4 ronde;
    jouer Bb3 doublecroche;

    attendre 1 noire;

    repeter 2 {
        jouer G4 noire;
        jouer A4 croche;
        attendre 1 blanche;
    }
}

Partie refrain {
    volume 90;
    tempo 140;

    jouer D3 noire;
    jouer F3 noire;

    repeter 4 {
        accord [D3, F3, A3] noire;
        attendre 1 croche;
    }
}

Partie pont {
    tempo 100;
    volume 60;

    jouer C5 ronde;
    attendre 3 blanche;

    repeter 2 {
        jouer G4 blanche;
        attendre 1 noire;
    }
}

Chanson {
    couplet;

    repeter 4 {
        refrain;
    }

    pont;

    repeter 2 {
        couplet;
        refrain;
    }
}
```

---

## Tests

### TestLexeur.java — Lexer Tests
- Tokenises `jouer C4 noire;` correctly (5 tokens)
- Recognises all keywords
- Recognises NOTE with accidental: `F#4`, `Bb3`
- Throws `ExceptionLexicale` on invalid character (`@`, `$`, etc.)
- Ignores `//` line comments

### TestAnalyseurSyntaxique.java — Parser Tests
- Parses a minimal valid programme (one `Partie` + `Chanson`)
- Parses `repeter 3 { jouer C4 noire; }`
- Parses `accord [D3, F3, A3] noire;`
- Throws `ExceptionSyntaxique` on missing `Chanson`
- Throws `ExceptionSyntaxique` on unclosed `{`

### TestSemantique.java — Semantic Analyser Tests
- Error if `volume` missing from a `Partie` block
- Error if `tempo` appears twice in a block
- Error on undefined part call
- Error on `tempo 0` and `tempo 301`
- Error on `volume 128`
- Error on `attendre 0 blanche`
- Error on direct recursion (`Partie a` calls `a`)
- Passes when `tempo` and `volume` are in any order

### TestMidi.java — Code Generator Tests
- `noteVersMidi("C4")` returns 60
- `noteVersMidi("A4")` returns 69
- `noteVersMidi("F#4")` returns 66
- `noteVersMidi("Bb3")` returns 58
- `dureeVersTics("noire", false)` returns 480
- `dureeVersTics("ronde", false)` returns 1920
- `dureeVersTics("noire", true)` returns 720
- Full compile of `exemple.musique` produces a non-empty `Sequence`

---

## pom.xml Requirements

- Java 26 (`<maven.compiler.source>26</maven.compiler.source>`)
- JUnit 5 for tests
- Maven Shade plugin → fat jar `compilmusique.jar`
- No external music libraries — `javax.sound.midi` (built into JDK) only
