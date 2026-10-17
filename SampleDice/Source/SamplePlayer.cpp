#include "SamplePlayer.h"

SamplePlayer::SamplePlayer() : juce::Thread ("SampleDice loader")
{
    formatManager.registerBasicFormats();
    startThread (juce::Thread::Priority::normal);
}

SamplePlayer::~SamplePlayer()
{
    stopThread (4000);

    // Plus de thread audio à ce stade : on libère tout.
    delete incoming.exchange (nullptr);
    delete outgoing.exchange (nullptr);
    delete current;
}

//==============================================================================
void SamplePlayer::load (const juce::File& file, bool playWhenLoaded)
{
    // Le son en cours s'arrête tout de suite, le nouveau prendra sa place une fois chargé.
    // (Avant la requête : l'arrivée du nouveau sample annule cet arrêt, jamais l'inverse.)
    startRequested = false;
    stopRequested = true;

    {
        const juce::ScopedLock sl (requestLock);
        requestedFile = file;
        requestPlay = playWhenLoaded;
        hasRequest = true;
    }

    notify();
}

void SamplePlayer::play()
{
    {
        const juce::ScopedLock sl (requestLock);

        if (hasRequest || loadInProgress)
        {
            // Le fichier affiché n'est pas encore en mémoire : on jouera à l'arrivée.
            requestPlay = true;
            return;
        }
    }

    stopRequested = false;
    startRequested = true;
}

void SamplePlayer::stop()
{
    {
        const juce::ScopedLock sl (requestLock);
        requestPlay = false;
    }

    startRequested = false;
    stopRequested = true;
}

juce::String SamplePlayer::getLastError() const
{
    const juce::ScopedLock sl (requestLock);
    return lastError;
}

//==============================================================================
void SamplePlayer::run()
{
    while (! threadShouldExit())
    {
        // Libère le buffer rendu par le thread audio (jamais libéré côté audio).
        delete outgoing.exchange (nullptr);

        juce::File file;
        {
            const juce::ScopedLock sl (requestLock);

            if (hasRequest)
            {
                file = requestedFile;
                hasRequest = false;
                loadInProgress = true;
            }
        }

        if (file == juce::File())
        {
            wait (50);
            continue;
        }

        auto sample = readFile (file);

        bool superseded = false;
        {
            const juce::ScopedLock sl (requestLock);
            loadInProgress = false;
            superseded = hasRequest;   // l'utilisateur a déjà retiré un autre fichier

            if (sample != nullptr)
            {
                sample->startOnArrival = requestPlay && ! superseded;
                lastError.clear();
            }
            else
            {
                lastError = "Impossible de lire " + file.getFileName();
            }
        }

        if (sample != nullptr && ! superseded)
        {
            loadedLengthSeconds = sample->buffer.getNumSamples() / sample->sampleRate;

            // Si le thread audio n'a pas encore pris le précédent, c'est nous qui le libérons.
            delete incoming.exchange (sample.release());
        }
    }
}

std::unique_ptr<SamplePlayer::LoadedSample> SamplePlayer::readFile (const juce::File& file)
{
    std::unique_ptr<juce::AudioFormatReader> reader (formatManager.createReaderFor (file));

    if (reader == nullptr || reader->sampleRate <= 0.0 || reader->lengthInSamples <= 0)
        return nullptr;

    const auto maxSamples = (juce::int64) (maxPreviewSeconds * reader->sampleRate);
    const auto numSamples = (int) juce::jmin (reader->lengthInSamples, maxSamples);
    const auto numChannels = (int) juce::jlimit (1u, 2u, reader->numChannels);

    auto sample = std::make_unique<LoadedSample>();
    sample->sampleRate = reader->sampleRate;
    sample->buffer.setSize (numChannels, numSamples);

    if (! reader->read (&sample->buffer, 0, numSamples, 0, true, numChannels > 1))
        return nullptr;

    return sample;
}

//==============================================================================
void SamplePlayer::prepare (double hostSampleRate, int)
{
    hostRate = hostSampleRate > 0.0 ? hostSampleRate : 44100.0;
    smoothedGain.reset (hostRate, 0.03);
    for (auto& interpolator : interpolators)
        interpolator.reset();
}

void SamplePlayer::render (juce::AudioBuffer<float>& output, bool retrigger, float gain) noexcept
{
    const auto numSamples = output.getNumSamples();
    const auto numOutChannels = juce::jmin (2, output.getNumChannels());
    output.clear();

    auto restart = [this]
    {
        readPosition = 0;
        for (auto& interpolator : interpolators)
            interpolator.reset();
        audioPlaying = current != nullptr;
    };

    // Nouveau sample prêt ? On ne le prend que si l'emplacement de retour est libre,
    // pour ne jamais avoir à libérer de mémoire ici.
    if (incoming.load() != nullptr && outgoing.load() == nullptr)
    {
        if (auto* fresh = incoming.exchange (nullptr))
        {
            outgoing.store (current);
            current = fresh;
            restart();
            audioPlaying = current->startOnArrival;
            stopRequested = false;
        }
    }

    bool fadeOut = false;

    if (stopRequested.exchange (false) && audioPlaying)
        fadeOut = true;

    if (startRequested.exchange (false) || retrigger)
    {
        restart();
        fadeOut = false;
    }

    smoothedGain.setTargetValue (gain);

    if (audioPlaying && current != nullptr && numOutChannels > 0)
    {
        const auto& source = current->buffer;
        const auto sourceLength = source.getNumSamples();
        const auto ratio = current->sampleRate / hostRate;
        const auto available = sourceLength - readPosition;
        int used = 0;

        for (int ch = 0; ch < numOutChannels; ++ch)
        {
            const auto* in = source.getReadPointer (juce::jmin (ch, source.getNumChannels() - 1), readPosition);
            used = interpolators[ch].process (ratio, in, output.getWritePointer (ch), numSamples, available, 0);
        }

        readPosition += used;

        if (fadeOut)
        {
            output.applyGainRamp (0, numSamples, 1.0f, 0.0f);
            audioPlaying = false;
        }
        else if (readPosition >= sourceLength)
        {
            audioPlaying = false;
        }

        smoothedGain.applyGain (output, numSamples);

        // Sortie mono éventuelle : rien à faire, le canal 0 contient déjà le son.
    }
    else
    {
        audioPlaying = false;
        smoothedGain.skip (numSamples);
    }

    if (! audioPlaying && fadeOut)
        readPosition = 0;

    playing = audioPlaying;
    positionSeconds = current != nullptr ? readPosition / current->sampleRate : 0.0;
}
