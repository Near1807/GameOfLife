#include "SampleDiceLookAndFeel.h"

SampleDiceLookAndFeel::SampleDiceLookAndFeel()
{
    setColour (juce::ResizableWindow::backgroundColourId, Colours::background);
    setColour (juce::TextButton::buttonColourId, Colours::panelLight);
    setColour (juce::TextButton::buttonOnColourId, Colours::accent);
    setColour (juce::TextButton::textColourOffId, Colours::text);
    setColour (juce::TextButton::textColourOnId, Colours::background);
    setColour (juce::ComboBox::outlineColourId, Colours::outline);
    setColour (juce::Label::textColourId, Colours::text);
    setColour (juce::ToggleButton::textColourId, Colours::text);
    setColour (juce::ToggleButton::tickColourId, Colours::accent);
    setColour (juce::Slider::backgroundColourId, Colours::panelLight);
    setColour (juce::Slider::trackColourId, Colours::waveform);
    setColour (juce::Slider::thumbColourId, Colours::text);
}

juce::Font SampleDiceLookAndFeel::font (float height, bool bold)
{
    return juce::Font (juce::FontOptions (height, bold ? juce::Font::bold : juce::Font::plain));
}

juce::Font SampleDiceLookAndFeel::getTextButtonFont (juce::TextButton&, int buttonHeight)
{
    return font (juce::jmin (15.0f, (float) buttonHeight * 0.55f));
}

juce::Font SampleDiceLookAndFeel::getLabelFont (juce::Label& label)
{
    return label.getFont();
}

void SampleDiceLookAndFeel::drawButtonBackground (juce::Graphics& g, juce::Button& button,
                                                  const juce::Colour& backgroundColour,
                                                  bool isMouseOverButton, bool isButtonDown)
{
    auto bounds = button.getLocalBounds().toFloat().reduced (0.5f);
    auto colour = backgroundColour;

    if (! button.isEnabled())
        colour = colour.withMultipliedAlpha (0.4f);
    else if (isButtonDown)
        colour = colour.brighter (0.25f);
    else if (isMouseOverButton)
        colour = colour.brighter (0.1f);

    g.setColour (colour);
    g.fillRoundedRectangle (bounds, 6.0f);
    g.setColour (Colours::outline);
    g.drawRoundedRectangle (bounds, 6.0f, 1.0f);
}

void SampleDiceLookAndFeel::drawToggleButton (juce::Graphics& g, juce::ToggleButton& button,
                                              bool isMouseOverButton, bool)
{
    auto bounds = button.getLocalBounds().toFloat();
    const auto boxSize = juce::jmin (18.0f, bounds.getHeight() - 4.0f);
    auto box = bounds.removeFromLeft (boxSize + 4.0f).withSizeKeepingCentre (boxSize, boxSize);

    g.setColour (isMouseOverButton ? Colours::panelLight.brighter (0.1f) : Colours::panelLight);
    g.fillRoundedRectangle (box, 4.0f);
    g.setColour (Colours::outline);
    g.drawRoundedRectangle (box, 4.0f, 1.0f);

    if (button.getToggleState())
    {
        juce::Path tick;
        tick.startNewSubPath (box.getX() + boxSize * 0.25f, box.getCentreY());
        tick.lineTo (box.getX() + boxSize * 0.43f, box.getBottom() - boxSize * 0.28f);
        tick.lineTo (box.getRight() - boxSize * 0.22f, box.getY() + boxSize * 0.27f);
        g.setColour (Colours::accent);
        g.strokePath (tick, juce::PathStrokeType (2.2f, juce::PathStrokeType::curved, juce::PathStrokeType::rounded));
    }

    g.setColour (button.findColour (juce::ToggleButton::textColourId));
    g.setFont (font (14.0f));
    g.drawFittedText (button.getButtonText(), bounds.reduced (4.0f, 0.0f).toNearestInt(),
                      juce::Justification::centredLeft, 1);
}
