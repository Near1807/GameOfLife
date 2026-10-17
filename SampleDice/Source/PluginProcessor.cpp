#include "PluginProcessor.h"
#include "PluginEditor.h"

namespace
{
    const juce::Identifier rootFolderId  { "rootFolder" };
    const juce::Identifier autoPlayId    { "autoPlay" };
    const juce::Identifier currentFileId { "currentFile" };
    const char* const globalRootKey = "lastRootFolder";

    juce::AudioProcessorValueTreeState::ParameterLayout createLayout()
    {
        return { std::make_unique<juce::AudioParameterFloat> (juce::ParameterID { "volume", 1 }, "Volume",
                                                              juce::NormalisableRange<float> (0.0f, 1.0f), 0.8f) };
    }
}

SampleDiceAudioProcessor::SampleDiceAudioProcessor()
    : AudioProcessor (BusesProperties().withOutput ("Output", juce::AudioChannelSet::stereo(), true)),
      parameters (*this, nullptr, "SampleDice", createLayout())
{
    volumeParameter = parameters.getRawParameterValue ("volume");
    parameters.state.setProperty (autoPlayId, true, nullptr);

    juce::PropertiesFile::Options options;
    options.applicationName     = "SampleDice";
    options.filenameSuffix      = ".settings";
    options.folderName          = "SampleDice";
    options.osxLibrarySubFolder = "Application Support";
    globalSettings.setStorageParameters (options);

    // Une nouvelle instance retrouve le dernier dossier utilisé (l'état du projet,
    // s'il y en a un, le remplacera ensuite dans setStateInformation).
    if (auto* settings = globalSettings.getUserSettings())
    {
        const juce::File lastRoot (settings->getValue (globalRootKey));
        if (lastRoot.isDirectory())
        {
            parameters.state.setProperty (rootFolderId, lastRoot.getFullPathName(), nullptr);
            library.setRootFolder (lastRoot);
        }
    }
}

SampleDiceAudioProcessor::~SampleDiceAudioProcessor() = default;

//==============================================================================
void SampleDiceAudioProcessor::prepareToPlay (double sampleRate, int samplesPerBlock)
{
    player.prepare (sampleRate, samplesPerBlock);
}

bool SampleDiceAudioProcessor::isBusesLayoutSupported (const BusesLayout& layouts) const
{
    return layouts.getMainInputChannelSet().isDisabled()
        && layouts.getMainOutputChannelSet() == juce::AudioChannelSet::stereo();
}

void SampleDiceAudioProcessor::processBlock (juce::AudioBuffer<float>& buffer, juce::MidiBuffer& midi)
{
    juce::ScopedNoDenormals noDenormals;

    bool noteOn = false;
    for (const auto metadata : midi)
        if (metadata.getMessage().isNoteOn())
            noteOn = true;

    player.render (buffer, noteOn, volumeParameter->load());
}

juce::AudioProcessorEditor* SampleDiceAudioProcessor::createEditor()
{
    return new SampleDiceAudioProcessorEditor (*this);
}

//==============================================================================
void SampleDiceAudioProcessor::getStateInformation (juce::MemoryBlock& destData)
{
    auto state = parameters.copyState();
    state.setProperty (currentFileId, currentFile.getFullPathName(), nullptr);

    if (auto xml = state.createXml())
        copyXmlToBinary (*xml, destData);
}

void SampleDiceAudioProcessor::setStateInformation (const void* data, int sizeInBytes)
{
    auto xml = getXmlFromBinary (data, sizeInBytes);

    if (xml == nullptr || ! xml->hasTagName (parameters.state.getType()))
        return;

    auto state = juce::ValueTree::fromXml (*xml);
    parameters.replaceState (state);

    const juce::File root (state.getProperty (rootFolderId).toString());
    if (root.isDirectory())
        library.setRootFolder (root);

    const juce::File saved (state.getProperty (currentFileId).toString());
    if (saved.existsAsFile() && saved != currentFile)
    {
        history.clearQuick();
        history.add (saved);
        historyIndex = 0;
        selectFile (saved, false);
    }
}

//==============================================================================
void SampleDiceAudioProcessor::setRootFolder (const juce::File& folder)
{
    parameters.state.setProperty (rootFolderId, folder.getFullPathName(), nullptr);
    library.setRootFolder (folder);

    if (auto* settings = globalSettings.getUserSettings())
    {
        settings->setValue (globalRootKey, folder.getFullPathName());
        settings->saveIfNeeded();
    }
}

void SampleDiceAudioProcessor::rollDice()
{
    const auto file = library.pickRandom (currentFile);
    if (file == juce::File())
        return;

    // Comme un navigateur : un nouveau tirage efface les entrées "suivantes".
    if (historyIndex < history.size() - 1)
        history.removeRange (historyIndex + 1, history.size() - historyIndex - 1);

    history.add (file);
    while (history.size() > maxHistory)
        history.remove (0);

    historyIndex = history.size() - 1;
    selectFile (file, getAutoPlay());
}

void SampleDiceAudioProcessor::goBack()
{
    if (canGoBack())
        selectFile (history[--historyIndex], getAutoPlay());
}

void SampleDiceAudioProcessor::goForward()
{
    if (canGoForward())
        selectFile (history[++historyIndex], getAutoPlay());
}

void SampleDiceAudioProcessor::selectFile (const juce::File& file, bool autoPlay)
{
    currentFile = file;
    ++selectionVersion;
    player.load (file, autoPlay);
}

bool SampleDiceAudioProcessor::getAutoPlay() const
{
    return parameters.state.getProperty (autoPlayId, true);
}

void SampleDiceAudioProcessor::setAutoPlay (bool shouldAutoPlay)
{
    parameters.state.setProperty (autoPlayId, shouldAutoPlay, nullptr);
}

//==============================================================================
juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
    return new SampleDiceAudioProcessor();
}
