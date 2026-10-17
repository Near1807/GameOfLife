#pragma once

#include <juce_gui_basics/juce_gui_basics.h>

/** Thème sombre de SampleDice : tout est dessiné, aucune image. */
class SampleDiceLookAndFeel : public juce::LookAndFeel_V4
{
public:
    struct Colours
    {
        static inline const juce::Colour background { 0xff15171c };
        static inline const juce::Colour panel      { 0xff1f2229 };
        static inline const juce::Colour panelLight { 0xff2a2e37 };
        static inline const juce::Colour outline    { 0xff353a45 };
        static inline const juce::Colour text       { 0xffe8eaf0 };
        static inline const juce::Colour textDim    { 0xff8b91a0 };
        static inline const juce::Colour accent     { 0xffff8a3d };
        static inline const juce::Colour waveform   { 0xff8fa3c0 };
    };

    SampleDiceLookAndFeel();

    static juce::Font font (float height, bool bold = false);

    juce::Font getTextButtonFont (juce::TextButton&, int buttonHeight) override;
    juce::Font getLabelFont (juce::Label&) override;

    void drawButtonBackground (juce::Graphics&, juce::Button&, const juce::Colour& backgroundColour,
                               bool isMouseOverButton, bool isButtonDown) override;

    void drawToggleButton (juce::Graphics&, juce::ToggleButton&,
                           bool isMouseOverButton, bool isButtonDown) override;
};
