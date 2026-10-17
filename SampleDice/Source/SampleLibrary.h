#pragma once

#include <juce_core/juce_core.h>
#include <atomic>

/**
    Liste des samples d'un dossier racine.

    Le scan récursif tourne sur son propre thread ; les fichiers trouvés sont ajoutés
    par paquets à la liste partagée, donc on peut déjà tirer au sort pendant un long scan.
    Toutes les méthodes publiques s'appellent depuis le thread UI (jamais le thread audio).
*/
class SampleLibrary : private juce::Thread
{
public:
    SampleLibrary();
    ~SampleLibrary() override;

    /** Change le dossier racine et lance un scan (ne fait rien si c'est déjà ce dossier). */
    void setRootFolder (const juce::File& folder);
    juce::File getRootFolder() const;

    /** Relance un scan complet du dossier racine. */
    void rescan();

    bool isScanning() const noexcept   { return scanning.load(); }
    int getNumFiles() const noexcept   { return numFiles.load(); }

    /** Tire un fichier au hasard, différent de `exclude` dès qu'il y a au moins deux fichiers. */
    juce::File pickRandom (const juce::File& exclude);

    static bool isSupportedFile (const juce::File& file);

private:
    void run() override;

    mutable juce::CriticalSection lock;  // protège root et files (jamais pris par le thread audio)
    juce::File root;
    juce::Array<juce::File> files;

    std::atomic<bool> scanning { false };
    std::atomic<int> numFiles { 0 };
    juce::Random random;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (SampleLibrary)
};
