# Compilateur MusiLang

Compilateur pour le langage **MusiLang** — lit un fichier `.musique` et génère un fichier MIDI (`.mid`) jouable par n'importe quel lecteur audio ou DAW.

---

## Lancer le compilateur

```bash
# 1. Compiler le projet (une seule fois)
mvn clean package

# 2. Générer un fichier MIDI
java --enable-preview -jar target/compilmusique.jar exemple.musique

# 3. Choisir le nom du fichier de sortie
java --enable-preview -jar target/compilmusique.jar exemple.musique --sortie ma_musique.mid

# 4. Générer et jouer immédiatement
java --enable-preview -jar target/compilmusique.jar exemple.musique --jouer
```

---

## Le langage MusiLang

Un programme MusiLang est composé de **parties** (`Part`) assemblées dans un **morceau** (`Song`).

```musique
Part intro {
    tempo 120;       // BPM (1 à 300)
    volume 80;       // vélocité (0 à 127)

    play C4 quarter;
    play F#4 half.;
    chord [C4, E4, G4] quarter;
    wait 1 quarter;

    repeat 2 {
        play G4 eighth;
        play A4 eighth;
    }
}

Song {
    intro;

    repeat 4 {
        intro;
    }
}
```

### Notes

```
HAUTEUR  [ALTERATION]  OCTAVE
   C          #            4       →  C#4
   A          b            3       →  Ab3
```

Hauteurs : `A` `B` `C` `D` `E` `F` `G`  
Altérations : `#` (dièse) ou `b` (bémol)  
Octave : `0` à `8`

### Durées

| Mot-clé | Durée |
|---|---|
| `whole` | Ronde |
| `half` | Blanche |
| `quarter` | Noire |
| `eighth` | Croche |
| `sixteenth` | Double croche |

Ajouter un `.` après la durée pour la pointer (× 1.5) : `quarter.`, `half.`

### Instructions

```musique
play C4 quarter;            // jouer une note
play F#4 half.;             // note pointée

chord [C4, E4, G4] half;    // accord

wait 1 quarter;             // silence
wait 3 eighth.;             // silence pointé × 3

repeat 4 {                  // boucle
    play G4 eighth;
}
```

---

## Pipeline du compilateur

```
.musique  →  Lexer (automate DFA)  →  Parser  →  Analyseur sémantique  →  Générateur MIDI  →  .mid
```

### Messages d'erreur

```
[ERREUR LEXICALE]    ligne 3, col 7 : unrecognised character '@'
[ERREUR SYNTAXIQUE]  ligne 5, col 1 : 'tempo' attendu
[ERREUR SÉMANTIQUE]  ligne 8, col 1 : la Partie 'intro' n'a pas de 'volume'
```

---

## Structure du projet

```
Compilateur_Musical_V1/
├── pom.xml
├── exemple.musique
├── demo.musique
└── src/main/java/compilmusique/
    ├── Principal.java              point d'entrée CLI
    ├── Jeton.java                  token + états de l'automate
    ├── AnalyseurLexical.java       lexer (automate DFA)
    ├── Noeud.java                  nœuds de l'AST
    ├── Visiteur.java               interface Visiteur
    ├── AnalyseurSyntaxique.java    parser (descente récursive)
    ├── AnalyseurSemantique.java    vérification sémantique
    └── GenerateurMidi.java         génération MIDI
```
