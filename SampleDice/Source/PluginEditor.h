#pragma once

#include <juce_audio_utils/juce_audio_utils.h>
#include "PluginProcessor.h"
#include "SampleDiceLookAndFeel.h"

//==============================================================================
/** Composant qu'on peut glisser hors du plugin : lance un drag & drop de fichier vers l'hôte. */
class FileDragSource : public juce::Component
{
public:
    std::function<juce::File()> getFile;

    void mouseDown (const juce::MouseEvent&) override;
    void mouseDrag (const juce::MouseEvent&) override;

private:
    bool dragStarted = false;
};

/** Nom du fichier en grand, chemin relatif en petit ; glissable aussi. */
class FileNameView : public FileDragSource
{
public:
    void setText (const juce::String& name, const juce::String& relativePath);
    void paint (juce::Graphics&) override;

private:
    juce::String fileName, path;
};

/** Zone "Glisse-moi dans FL". */
class DragZone : public FileDragSource
{
public:
    void paint (juce::Graphics&) override;
    void mouseEnter (const juce::MouseEvent&) override  { repaint(); }
    void mouseExit (const juce::MouseEvent&) override   { repaint(); }
};

/** Forme d'onde du sample courant + tête de lecture. */
class WaveformView : public juce::Component, private juce::ChangeListener
{
public:
    explicit WaveformView (SamplePlayer& playerToWatch);
    ~WaveformView() override;

    void setFile (const juce::File& file);
    void updatePlayhead();
    void paint (juce::Graphics&) override;

private:
    void changeListenerCallback (juce::ChangeBroadcaster*) override  { repaint(); }

    SamplePlayer& player;
    juce::AudioFormatManager formatManager;
    juce::AudioThumbnailCache cache { 8 };
    juce::AudioThumbnail thumbnail { 512, formatManager, cache };
    float lastPlayheadX = -1.0f;
};

/** Gros bouton Random avec un dé dessiné. */
class DiceButton : public juce::Button
{
public:
    DiceButton() : juce::Button ("Random") {}
    void paintButton (juce::Graphics&, bool isMouseOver, bool isButtonDown) override;
};

/** Bouton ◀ / ▶ de l'historique (triangle dessiné). */
class HistoryButton : public juce::Button
{
public:
    explicit HistoryButton (bool pointsLeft) : juce::Button (pointsLeft ? "Back" : "Forward"), left (pointsLeft) {}
    void paintButton (juce::Graphics&, bool isMouseOver, bool isButtonDown) override;

private:
    bool left;
};

//==============================================================================
class SampleDiceAudioProcessorEditor : public juce::AudioProcessorEditor,
                                       public juce::DragAndDropContainer,
                                       private juce::Timer
{
public:
    explicit SampleDiceAudioProcessorEditor (SampleDiceAudioProcessor&);
    ~SampleDiceAudioProcessorEditor() override;

    void paint (juce::Graphics&) override;
    void resized() override;
    bool keyPressed (const juce::KeyPress&) override;
    void mouseDown (const juce::MouseEvent&) override;

private:
    void timerCallback() override;
    void chooseFolder();
    void roll();
    void refreshSelection();
    void refreshStatus();

    SampleDiceAudioProcessor& processor;
    SampleDiceLookAndFeel lookAndFeel;

    juce::TextButton chooseFolderButton, rescanButton, openFolderButton, playButton { "Play" }, stopButton { "Stop" };
    juce::Label statusLabel, volumeLabel, errorLabel;
    FileNameView fileNameView;
    WaveformView waveform;
    HistoryButton backButton { true }, forwardButton { false };
    juce::ToggleButton autoPlayToggle { "Auto-play" };
    juce::Slider volumeSlider;
    juce::AudioProcessorValueTreeState::SliderAttachment volumeAttachment;
    DiceButton diceButton;
    DragZone dragZone;

    std::unique_ptr<juce::FileChooser> fileChooser;
    juce::TooltipWindow tooltipWindow { this, 600 };
    int shownSelectionVersion = -1;
    bool initialFocusTaken = false;
    juce::String lastStatus;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (SampleDiceAudioProcessorEditor)
};
