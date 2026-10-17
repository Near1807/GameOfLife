# SampleDice

Plugin instrument VST3 (+ Standalone) qui tire au hasard un sample dans ta banque, le joue,
et se glisse directement dans FL Studio. JUCE 9.0.4 est téléchargé automatiquement par CMake.

## Recompiler (Windows)

Prérequis : Visual Studio 2022 (charge de travail « Développement Desktop en C++ »), CMake ≥ 3.22, Git.

```bat
cd SampleDice
cmake -B build
cmake --build build --config Release
```

Le premier `cmake -B build` télécharge JUCE (quelques minutes).

## Où est le .vst3

- Compilé : `SampleDice\build\SampleDice_artefacts\Release\VST3\SampleDice.vst3`
- Standalone : `SampleDice\build\SampleDice_artefacts\Release\Standalone\SampleDice.exe`

Après le build, le .vst3 est copié dans `C:\Program Files\Common Files\VST3`. Cette copie
demande les droits administrateur : si le build échoue à cette étape (« Permission denied » /
« Accès refusé »), au choix :

- relance le terminal **en tant qu'administrateur** et refais `cmake --build build --config Release` ;
- ou désactive la copie (`cmake -B build -DSAMPLEDICE_COPY_PLUGIN=OFF`) et copie le dossier
  toi-même depuis un terminal administrateur :

  ```bat
  xcopy /E /I /Y build\SampleDice_artefacts\Release\VST3\SampleDice.vst3 "C:\Program Files\Common Files\VST3\SampleDice.vst3"
  ```

  (ou par glisser-déposer dans l'Explorateur, qui demandera la confirmation admin).

Sans compiler : l'onglet **Actions** du dépôt GitHub (workflow « SampleDice (Windows) »)
fournit le .vst3 et l'.exe compilés dans l'artefact `SampleDice-Windows`.

## Charger dans FL Studio

1. **Options > Manage plugins**, vérifie que `C:\Program Files\Common Files\VST3` est dans les
   dossiers de recherche, puis **Find more plugins** (ou *Find installed plugins*).
2. Coche l'étoile (favori) à côté de **SampleDice** si tu veux le retrouver facilement.
3. Dans le **Channel rack**, clic sur **+** (ou clic droit > *Insert*) > **SampleDice**.

## Utilisation

- **Choisir dossier** : dossier racine de ta banque (scan récursif de .wav .aif .aiff .mp3 .flac .ogg).
  Le dossier est sauvé dans le projet FL et réutilisé par toute nouvelle instance.
- **Random** ou **Espace** / **R** (quand le plugin a le focus) : tire un sample (jamais deux fois de suite le même).
- **Play / Stop**, **Auto-play**, **Volume** : la preview sort par la sortie du plugin, donc dans le mixer de FL.
  Toute note MIDI relance la preview.
- Glisse **le nom du fichier** ou la zone **Glisse-moi dans FL** vers la playlist ou le channel rack.
- **◀ / ▶** : 20 derniers tirages. **Ouvrir le dossier** : sélectionne le fichier dans l'Explorateur.

Notes : la preview est chargée en mémoire et limitée aux 5 premières minutes du fichier.
JUCE 9 est sous double licence (AGPLv3 ou commerciale) : usage personnel sans souci, à vérifier
avant toute distribution.
