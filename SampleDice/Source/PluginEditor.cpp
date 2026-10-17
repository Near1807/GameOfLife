#include "PluginEditor.h"

using Colours = SampleDiceLookAndFeel::Colours;

namespace
{
    juce::String utf8 (const char* text)  { return juce::String::fromUTF8 (text); }
}

//==============================================================================
void FileDragSource::mouseDown (const juce::MouseEvent&)
{
    dragStarted = false;
}

void FileDragSource::mouseDrag (const juce::MouseEvent& e)
{
    if (dragStarted || e.getDistanceFromDragStart() < 6 || getFile == nullptr)
        return;

    const auto file = getFile();
    if (! file.existsAsFile())
        return;

    dragStarted = true;
    juce::DragAndDropContainer::performExternalDragDropOfFiles ({ file.getFullPathName() }, false, this);
}

//==============================================================================
void FileNameView::setText (const juce::String& name, const juce::String& relativePath)
{
    fileName = name;
    path = relativePath;
    setMouseCursor (name.isNotEmpty() ? juce::MouseCursor::DraggingHandCursor : juce::MouseCursor::NormalCursor);
    repaint();
}

void FileNameView::paint (juce::Graphics& g)
{
    auto bounds = getLocalBounds();

    if (fileName.isEmpty())
    {
        g.setColour (Colours::textDim);
        g.setFont (SampleDiceLookAndFeel::font (20.0f));
        g.drawFittedText (utf8 ("Lance le dé pour tirer un sample"), bounds, juce::Justification::centredLeft, 1);
        return;
    }

    g.setColour (Colours::text);
    g.setFont (SampleDiceLookAndFeel::font (26.0f, true));
    g.drawFittedText (fileName, bounds.removeFromTop (36), juce::Justification::bottomLeft, 1, 0.8f);

    g.setColour (Colours::textDim);
    g.setFont (SampleDiceLookAndFeel::font (13.0f));
    g.drawFittedText (path, bounds, juce::Justification::topLeft, 1, 0.8f);
}

//==============================================================================
void DragZone::paint (juce::Graphics& g)
{
    const auto active = getFile != nullptr && getFile().existsAsFile();
    auto bounds = getLocalBounds().toFloat().reduced (1.5f);

    g.setColour (isMouseOver() && active ? Colours::panelLight : Colours::panel);
    g.fillRoundedRectangle (bounds, 8.0f);

    juce::Path outline;
    outline.addRoundedRectangle (bounds, 8.0f);
    juce::Path dashed;
    const float dashes[] = { 6.0f, 4.0f };
    juce::PathStrokeType (1.5f).createDashedStroke (dashed, outline, dashes, 2);
    g.setColour (active ? Colours::waveform : Colours::outline);
    g.fillPath (dashed);

    // Petite flèche vers le bas
    auto arrowArea = bounds.removeFromTop (bounds.getHeight() * 0.5f).withSizeKeepingCentre (18.0f, 20.0f);
    juce::Path arrow;
    arrow.addArrow ({ arrowArea.getCentreX(), arrowArea.getY(), arrowArea.getCentreX(), arrowArea.getBottom() },
                    3.0f, 12.0f, 9.0f);
    g.fillPath (arrow);

    g.setColour (active ? Colours::text : Colours::textDim);
    g.setFont (SampleDiceLookAndFeel::font (14.0f));
    g.drawFittedText ("Glisse-moi dans FL", bounds.toNearestInt().reduced (6, 2), juce::Justification::centredTop, 2);
}

//==============================================================================
WaveformView::WaveformView (SamplePlayer& playerToWatch) : player (playerToWatch)
{
    formatManager.registerBasicFormats();
    thumbnail.addChangeListener (this);
}

WaveformView::~WaveformView()
{
    thumbnail.removeChangeListener (this);
}

void WaveformView::setFile (const juce::File& file)
{
    if (file.existsAsFile())
        thumbnail.setSource (new juce::FileInputSource (file));
    else
        thumbnail.clear();

    repaint();
}

void WaveformView::updatePlayhead()
{
    const auto length = thumbnail.getTotalLength();
    const auto x = (player.isPlaying() && length > 0.0)
                     ? (float) (player.getPositionSeconds() / length) * (float) getWidth()
                     : -1.0f;

    if (std::abs (x - lastPlayheadX) >= 0.5f)
    {
        lastPlayheadX = x;
        repaint();
    }
}

void WaveformView::paint (juce::Graphics& g)
{
    auto bounds = getLocalBounds().toFloat();
    g.setColour (Colours::panel);
    g.fillRoundedRectangle (bounds, 8.0f);

    auto inner = getLocalBounds().reduced (6, 8);

    if (thumbnail.getNumChannels() > 0 && thumbnail.getTotalLength() > 0.0)
    {
        // Affichage normalisé : un sample peu fort reste lisible.
        const auto peak = thumbnail.getApproximatePeak();
        const auto zoom = peak > 0.0f ? juce::jlimit (1.0f, 8.0f, 0.95f / peak) : 1.0f;
        g.setColour (Colours::waveform);
        thumbnail.drawChannels (g, inner, 0.0, thumbnail.getTotalLength(), zoom);
    }
    else
    {
        g.setColour (Colours::outline);
        g.drawHorizontalLine (inner.getCentreY(), (float) inner.getX(), (float) inner.getRight());
    }

    if (lastPlayheadX >= 0.0f)
    {
        g.setColour (Colours::accent);
        g.fillRect (juce::Rectangle<float> (lastPlayheadX - 1.0f, 0.0f, 2.0f, bounds.getHeight()));
    }
}

//==============================================================================
void DiceButton::paintButton (juce::Graphics& g, bool isMouseOver, bool isButtonDown)
{
    auto bounds = getLocalBounds().toFloat().reduced (1.0f);
    auto colour = Colours::accent;
    if (isButtonDown)      colour = colour.darker (0.15f);
    else if (isMouseOver)  colour = colour.brighter (0.12f);

    g.setColour (colour);
    g.fillRoundedRectangle (bounds, 10.0f);

    // Le dé
    const auto dieSize = juce::jmin (bounds.getHeight() * 0.55f, 52.0f);
    auto content = bounds.reduced (16.0f, 0.0f);
    const auto textWidth = 110.0f;
    auto group = content.withSizeKeepingCentre (dieSize + 14.0f + textWidth, dieSize);
    auto die = group.removeFromLeft (dieSize);
    group.removeFromLeft (14.0f);

    g.setColour (Colours::background);
    g.fillRoundedRectangle (die, dieSize * 0.2f);

    g.setColour (colour);
    const auto pip = dieSize * 0.16f;
    auto drawPip = [&] (float fx, float fy)
    {
        g.fillEllipse (die.getX() + die.getWidth() * fx - pip * 0.5f,
                       die.getY() + die.getHeight() * fy - pip * 0.5f, pip, pip);
    };
    drawPip (0.27f, 0.27f);  drawPip (0.73f, 0.27f);
    drawPip (0.5f, 0.5f);
    drawPip (0.27f, 0.73f);  drawPip (0.73f, 0.73f);

    g.setColour (Colours::background);
    g.setFont (SampleDiceLookAndFeel::font (28.0f, true));
    g.drawFittedText ("Random", group.toNearestInt(), juce::Justification::centredLeft, 1);
}

void HistoryButton::paintButton (juce::Graphics& g, bool isMouseOver, bool isButtonDown)
{
    getLookAndFeel().drawButtonBackground (g, *this, Colours::panelLight, isMouseOver, isButtonDown);

    auto area = getLocalBounds().toFloat().withSizeKeepingCentre (10.0f, 12.0f);
    juce::Path triangle;
    if (left)
        triangle.addTriangle (area.getRight(), area.getY(), area.getRight(), area.getBottom(), area.getX(), area.getCentreY());
    else
        triangle.addTriangle (area.getX(), area.getY(), area.getX(), area.getBottom(), area.getRight(), area.getCentreY());

    g.setColour (isEnabled() ? Colours::text : Colours::textDim.withAlpha (0.5f));
    g.fillPath (triangle);
}

//==============================================================================
SampleDiceAudioProcessorEditor::SampleDiceAudioProcessorEditor (SampleDiceAudioProcessor& p)
    : AudioProcessorEditor (&p),
      processor (p),
      chooseFolderButton ("Choisir dossier"),
      rescanButton ("Rescanner"),
      openFolderButton ("Ouvrir le dossier"),
      waveform (p.getPlayer()),
      volumeAttachment (p.getState(), "volume", volumeSlider)
{
    setLookAndFeel (&lookAndFeel);

    for (auto* component : std::initializer_list<juce::Component*> {
             &chooseFolderButton, &rescanButton, &statusLabel, &fileNameView, &waveform, &backButton,
             &forwardButton, &playButton, &stopButton, &autoPlayToggle, &volumeLabel, &volumeSlider,
             &diceButton, &dragZone, &openFolderButton, &errorLabel })
    {
        addAndMakeVisible (component);
        // Le clavier reste à l'éditeur, pour que Espace / R lancent toujours le dé.
        component->setWantsKeyboardFocus (false);
        component->setMouseClickGrabsKeyboardFocus (false);
    }

    statusLabel.setFont (SampleDiceLookAndFeel::font (13.0f));
    statusLabel.setColour (juce::Label::textColourId, Colours::textDim);
    statusLabel.setJustificationType (juce::Justification::centredRight);

    errorLabel.setFont (SampleDiceLookAndFeel::font (12.0f));
    errorLabel.setColour (juce::Label::textColourId, Colours::accent);
    errorLabel.setJustificationType (juce::Justification::centredRight);

    volumeLabel.setText ("Volume", juce::dontSendNotification);
    volumeLabel.setFont (SampleDiceLookAndFeel::font (14.0f));
    volumeLabel.setJustificationType (juce::Justification::centredRight);

    volumeSlider.setSliderStyle (juce::Slider::LinearHorizontal);
    volumeSlider.setTextBoxStyle (juce::Slider::NoTextBox, false, 0, 0);

    chooseFolderButton.onClick = [this] { chooseFolder(); };
    rescanButton.onClick       = [this] { processor.getLibrary().rescan(); };
    diceButton.onClick         = [this] { roll(); };
    backButton.onClick         = [this] { processor.goBack(); };
    forwardButton.onClick      = [this] { processor.goForward(); };
    playButton.onClick         = [this] { processor.getPlayer().play(); };
    stopButton.onClick         = [this] { processor.getPlayer().stop(); };
    openFolderButton.onClick   = [this]
    {
        const auto file = processor.getCurrentFile();
        if (file.existsAsFile())
            file.revealToUser();
        else if (processor.getLibrary().getRootFolder().isDirectory())
            processor.getLibrary().getRootFolder().revealToUser();
    };

    autoPlayToggle.setToggleState (processor.getAutoPlay(), juce::dontSendNotification);
    autoPlayToggle.onClick = [this] { processor.setAutoPlay (autoPlayToggle.getToggleState()); };

    diceButton.setTooltip ("Espace ou R");
    auto currentFile = [this] { return processor.getCurrentFile(); };
    fileNameView.getFile = currentFile;
    dragZone.getFile = currentFile;

    setWantsKeyboardFocus (true);
    // Tout clic dans le plugin (boutons compris) rend le clavier à l'éditeur.
    addMouseListener (this, true);
    setSize (600, 400);

    refreshSelection();
    refreshStatus();
    startTimerHz (30);
}

SampleDiceAudioProcessorEditor::~SampleDiceAudioProcessorEditor()
{
    setLookAndFeel (nullptr);
}

//==============================================================================
void SampleDiceAudioProcessorEditor::paint (juce::Graphics& g)
{
    g.fillAll (Colours::background);
}

void SampleDiceAudioProcessorEditor::resized()
{
    auto area = getLocalBounds().reduced (16, 14);

    auto top = area.removeFromTop (30);
    chooseFolderButton.setBounds (top.removeFromLeft (140));
    top.removeFromLeft (8);
    rescanButton.setBounds (top.removeFromLeft (100));
    top.removeFromLeft (12);
    statusLabel.setBounds (top);

    area.removeFromTop (10);
    fileNameView.setBounds (area.removeFromTop (60));

    area.removeFromTop (6);
    waveform.setBounds (area.removeFromTop (96));

    area.removeFromTop (12);
    auto controls = area.removeFromTop (32);
    backButton.setBounds (controls.removeFromLeft (40));
    controls.removeFromLeft (6);
    forwardButton.setBounds (controls.removeFromLeft (40));
    controls.removeFromLeft (16);
    playButton.setBounds (controls.removeFromLeft (70));
    controls.removeFromLeft (6);
    stopButton.setBounds (controls.removeFromLeft (70));
    controls.removeFromLeft (16);
    autoPlayToggle.setBounds (controls.removeFromLeft (100));
    volumeLabel.setBounds (controls.removeFromLeft (60));
    controls.removeFromLeft (6);
    volumeSlider.setBounds (controls);

    area.removeFromTop (14);
    errorLabel.setBounds (area.removeFromBottom (16));
    area.removeFromBottom (2);

    auto bottom = area;
    diceButton.setBounds (bottom.removeFromLeft (280));
    bottom.removeFromLeft (12);
    openFolderButton.setBounds (bottom.removeFromRight (130).withSizeKeepingCentre (130, 34));
    bottom.removeFromRight (12);
    dragZone.setBounds (bottom);
}

bool SampleDiceAudioProcessorEditor::keyPressed (const juce::KeyPress& key)
{
    if (key == juce::KeyPress::spaceKey || key.getTextCharacter() == 'r' || key.getTextCharacter() == 'R')
    {
        roll();
        return true;
    }

    return false;
}

void SampleDiceAudioProcessorEditor::mouseDown (const juce::MouseEvent&)
{
    if (! hasKeyboardFocus (false))
        grabKeyboardFocus();
}

//==============================================================================
void SampleDiceAudioProcessorEditor::chooseFolder()
{
    fileChooser = std::make_unique<juce::FileChooser> ("Choisis le dossier de tes samples",
                                                       processor.getLibrary().getRootFolder());

    fileChooser->launchAsync (juce::FileBrowserComponent::openMode | juce::FileBrowserComponent::canSelectDirectories,
                              [this] (const juce::FileChooser& chooser)
                              {
                                  const auto folder = chooser.getResult();
                                  if (folder.isDirectory())
                                      processor.setRootFolder (folder);

                                  refreshStatus();
                              });
}

void SampleDiceAudioProcessorEditor::roll()
{
    processor.rollDice();
    refreshSelection();
}

void SampleDiceAudioProcessorEditor::refreshSelection()
{
    shownSelectionVersion = processor.getSelectionVersion();
    const auto file = processor.getCurrentFile();
    const auto root = processor.getLibrary().getRootFolder();

    if (file == juce::File())
        fileNameView.setText ({}, {});
    else
        fileNameView.setText (file.getFileName(),
                              file.isAChildOf (root) ? file.getRelativePathFrom (root) : file.getFullPathName());

    waveform.setFile (file);
    backButton.setEnabled (processor.canGoBack());
    forwardButton.setEnabled (processor.canGoForward());
    dragZone.repaint();
}

void SampleDiceAudioProcessorEditor::refreshStatus()
{
    auto& library = processor.getLibrary();
    const auto root = library.getRootFolder();
    const auto count = library.getNumFiles();
    juce::String status;

    if (! root.isDirectory())
        status = utf8 ("Aucun dossier choisi");
    else if (library.isScanning())
        status = utf8 ("Scan… ") + juce::String (count) + " fichiers";
    else
        status = root.getFileName() + utf8 ("  ·  ") + juce::String (count) + (count > 1 ? " fichiers" : " fichier");

    if (status != lastStatus)
    {
        lastStatus = status;
        statusLabel.setText (status, juce::dontSendNotification);
        statusLabel.setTooltip (root.getFullPathName());
    }

    diceButton.setEnabled (count > 0);
    rescanButton.setEnabled (root.isDirectory());
}

void SampleDiceAudioProcessorEditor::timerCallback()
{
    if (! initialFocusTaken && isShowing())
    {
        initialFocusTaken = true;
        grabKeyboardFocus();
    }

    if (shownSelectionVersion != processor.getSelectionVersion())
        refreshSelection();

    refreshStatus();
    waveform.updatePlayhead();

    const auto error = processor.getPlayer().getLastError();
    if (error != errorLabel.getText())
        errorLabel.setText (error, juce::dontSendNotification);
}
