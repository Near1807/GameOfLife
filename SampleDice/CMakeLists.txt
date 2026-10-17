cmake_minimum_required(VERSION 3.22)

project(SampleDice VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Copie du .vst3 dans le dossier VST3 système après chaque build
# (Windows : C:\Program Files\Common Files\VST3, demande les droits admin).
# Désactiver avec -DSAMPLEDICE_COPY_PLUGIN=OFF si le terminal n'est pas lancé en admin.
option(SAMPLEDICE_COPY_PLUGIN "Copy the VST3 to the system VST3 folder after build" ON)

# --- JUCE via FetchContent (aucune installation manuelle) ---------------------
include(FetchContent)
set(FETCHCONTENT_QUIET OFF)
FetchContent_Declare(
    JUCE
    GIT_REPOSITORY https://github.com/juce-framework/JUCE.git
    GIT_TAG        9.0.4
    GIT_SHALLOW    TRUE
    GIT_PROGRESS   TRUE)
FetchContent_MakeAvailable(JUCE)

# --- Plugin -------------------------------------------------------------------
juce_add_plugin(SampleDice
    PRODUCT_NAME                "SampleDice"
    COMPANY_NAME                "SampleDice"
    BUNDLE_ID                   "com.sampledice.sampledice"
    PLUGIN_MANUFACTURER_CODE    Sdce
    PLUGIN_CODE                 Sdc1
    FORMATS                     VST3 Standalone
    IS_SYNTH                    TRUE
    NEEDS_MIDI_INPUT            TRUE
    NEEDS_MIDI_OUTPUT           FALSE
    IS_MIDI_EFFECT              FALSE
    EDITOR_WANTS_KEYBOARD_FOCUS TRUE
    VST3_CATEGORIES             Instrument Sampler
    COPY_PLUGIN_AFTER_BUILD     ${SAMPLEDICE_COPY_PLUGIN})

target_sources(SampleDice PRIVATE
    Source/PluginProcessor.cpp
    Source/PluginEditor.cpp
    Source/SampleLibrary.cpp
    Source/SamplePlayer.cpp
    Source/SampleDiceLookAndFeel.cpp)

target_compile_definitions(SampleDice PUBLIC
    JUCE_WEB_BROWSER=0
    JUCE_USE_CURL=0
    JUCE_VST3_CAN_REPLACE_VST2=0
    JUCE_USE_MP3AUDIOFORMAT=1
    JUCE_USE_FLAC=1
    JUCE_USE_OGGVORBIS=1)

# Les sources contiennent du texte en UTF-8 (accents, "…").
target_compile_options(SampleDice PRIVATE $<$<CXX_COMPILER_ID:MSVC>:/utf-8>)

target_link_libraries(SampleDice
    PRIVATE
        juce::juce_audio_utils
        juce::juce_audio_formats
        juce::juce_audio_devices
    PUBLIC
        juce::juce_recommended_config_flags
        juce::juce_recommended_lto_flags
        juce::juce_recommended_warning_flags)
