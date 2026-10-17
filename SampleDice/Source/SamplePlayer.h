#pragma once

#include <juce_audio_basics/juce_audio_basics.h>
#include <juce_audio_formats/juce_audio_formats.h>
#include <atomic>
#include <memory>

/**
    Preview d'un sample, sans risque pour le thread audio.

    - Le fichier est décodé entièrement en mémoire par un thread de chargement.
    - Le buffer prêt est confié au thread audio par un simple échange de pointeur atomique ;
      l'ancien buffer revient par un autre pointeur atomique et est libéré par le thread de
      chargement. render() n'alloue rien, ne lit jamais le disque et ne prend aucun verrou.
    - Le rééchantillonnage (fréquence du fichier -> fréquence de l'hôte) est fait à la volée
      par un interpolateur de Lagrange, donc un changement de fréquence de l'hôte marche aussi.
*/
class SamplePlayer : private juce::Thread
{
public:
    SamplePlayer();
    ~SamplePlayer() override;

    //== Thread UI ===============================================================
    /** Charge un fichier en arrière-plan ; s'il faut, la lecture démarre dès qu'il est prêt. */
    void load (const juce::File& file, bool playWhenLoaded);
    void play();
    void stop();

    bool isPlaying() const noexcept               { return playing.load(); }
    double getPositionSeconds() const noexcept    { return positionSeconds.load(); }
    /** Durée de l'aperçu chargé (un fichier très long est tronqué à maxPreviewSeconds). */
    double getLoadedLengthSeconds() const noexcept { return loadedLengthSeconds.load(); }
    juce::String getLastError() const;

    static constexpr double maxPreviewSeconds = 5.0 * 60.0;

    //== Thread audio ============================================================
    void prepare (double hostSampleRate, int maxBlockSize);
    /** Écrit (en remplaçant) la preview dans `output`. `retrigger` relance du début (note MIDI). */
    void render (juce::AudioBuffer<float>& output, bool retrigger, float gain) noexcept;

private:
    struct LoadedSample
    {
        juce::AudioBuffer<float> buffer;
        double sampleRate = 44100.0;
        bool startOnArrival = false;
    };

    void run() override;
    std::unique_ptr<LoadedSample> readFile (const juce::File& file);

    // Requête de chargement (thread UI <-> thread de chargement uniquement)
    juce::CriticalSection requestLock;
    juce::File requestedFile;
    bool hasRequest = false;
    bool requestPlay = false;
    bool loadInProgress = false;
    juce::String lastError;

    juce::AudioFormatManager formatManager;

    // Échanges lock-free avec le thread audio
    std::atomic<LoadedSample*> incoming { nullptr };
    std::atomic<LoadedSample*> outgoing { nullptr };
    std::atomic<bool> startRequested { false };
    std::atomic<bool> stopRequested { false };
    std::atomic<bool> playing { false };
    std::atomic<double> positionSeconds { 0.0 };
    std::atomic<double> loadedLengthSeconds { 0.0 };

    // État propre au thread audio
    LoadedSample* current = nullptr;
    double hostRate = 44100.0;
    int readPosition = 0;
    bool audioPlaying = false;
    juce::LagrangeInterpolator interpolators[2];
    juce::SmoothedValue<float> smoothedGain;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (SamplePlayer)
};
