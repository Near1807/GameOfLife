#pragma once

#include <juce_audio_processors/juce_audio_processors.h>
#include <juce_data_structures/juce_data_structures.h>
#include "SampleLibrary.h"
#include "SamplePlayer.h"

class SampleDiceAudioProcessor : public juce::AudioProcessor
{
public:
    SampleDiceAudioProcessor();
    ~SampleDiceAudioProcessor() override;

    //== AudioProcessor ===========================================================
    void prepareToPlay (double sampleRate, int samplesPerBlock) override;
    void releaseResources() override {}
    bool isBusesLayoutSupported (const BusesLayout& layouts) const override;
    void processBlock (juce::AudioBuffer<float>&, juce::MidiBuffer&) override;
    using AudioProcessor::processBlock;

    juce::AudioProcessorEditor* createEditor() override;
    bool hasEditor() const override                     { return true; }

    const juce::String getName() const override         { return JucePlugin_Name; }
    bool acceptsMidi() const override                   { return true; }
    bool producesMidi() const override                  { return false; }
    bool isMidiEffect() const override                  { return false; }
    double getTailLengthSeconds() const override        { return 0.0; }

    int getNumPrograms() override                       { return 1; }
    int getCurrentProgram() override                    { return 0; }
    void setCurrentProgram (int) override               {}
    const juce::String getProgramName (int) override    { return {}; }
    void changeProgramName (int, const juce::String&) override {}

    void getStateInformation (juce::MemoryBlock& destData) override;
    void setStateInformation (const void* data, int sizeInBytes) override;

    //== Actions de l'interface (thread UI) ======================================
    void setRootFolder (const juce::File& folder);
    void rollDice();
    void goBack();
    void goForward();
    bool canGoBack() const      { return historyIndex > 0; }
    bool canGoForward() const   { return historyIndex >= 0 && historyIndex < history.size() - 1; }

    juce::File getCurrentFile() const   { return currentFile; }
    /** S'incrémente à chaque changement de fichier courant (l'éditeur s'en sert pour se rafraîchir). */
    int getSelectionVersion() const     { return selectionVersion; }

    bool getAutoPlay() const;
    void setAutoPlay (bool shouldAutoPlay);

    SampleLibrary& getLibrary()                     { return library; }
    SamplePlayer& getPlayer()                       { return player; }
    juce::AudioProcessorValueTreeState& getState()  { return parameters; }

    static constexpr int maxHistory = 20;

private:
    void selectFile (const juce::File& file, bool autoPlay);

    juce::AudioProcessorValueTreeState parameters;
    std::atomic<float>* volumeParameter = nullptr;

    juce::ApplicationProperties globalSettings;
    SampleLibrary library;
    SamplePlayer player;

    juce::File currentFile;
    juce::Array<juce::File> history;
    int historyIndex = -1;
    int selectionVersion = 0;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (SampleDiceAudioProcessor)
};
