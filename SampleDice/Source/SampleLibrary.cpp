#include "SampleLibrary.h"

SampleLibrary::SampleLibrary() : juce::Thread ("SampleDice scan") {}

SampleLibrary::~SampleLibrary()
{
    stopThread (4000);
}

void SampleLibrary::setRootFolder (const juce::File& folder)
{
    {
        const juce::ScopedLock sl (lock);
        if (folder == root && (files.size() > 0 || isThreadRunning()))
            return;
    }

    stopThread (4000);

    {
        const juce::ScopedLock sl (lock);
        root = folder;
    }

    rescan();
}

juce::File SampleLibrary::getRootFolder() const
{
    const juce::ScopedLock sl (lock);
    return root;
}

void SampleLibrary::rescan()
{
    stopThread (4000);

    {
        const juce::ScopedLock sl (lock);
        files.clearQuick();
        numFiles = 0;

        if (! root.isDirectory())
            return;
    }

    scanning = true;
    startThread (juce::Thread::Priority::low);
}

bool SampleLibrary::isSupportedFile (const juce::File& file)
{
    static const juce::StringArray extensions { ".wav", ".aif", ".aiff", ".mp3", ".flac", ".ogg" };
    return extensions.contains (file.getFileExtension().toLowerCase());
}

juce::File SampleLibrary::pickRandom (const juce::File& exclude)
{
    const juce::ScopedLock sl (lock);

    if (files.isEmpty())
        return {};

    if (files.size() == 1)
        return files.getFirst();

    for (int attempt = 0; attempt < 16; ++attempt)
    {
        const auto& candidate = files.getReference (random.nextInt (files.size()));
        if (candidate != exclude)
            return candidate;
    }

    // Très improbable : on prend simplement le voisin.
    const auto index = files.indexOf (exclude);
    return files.getReference ((juce::jmax (0, index) + 1) % files.size());
}

void SampleLibrary::run()
{
    const auto folder = getRootFolder();
    juce::Array<juce::File> batch;

    auto flush = [this, &batch]
    {
        const juce::ScopedLock sl (lock);
        files.addArray (batch);
        numFiles = files.size();
        batch.clearQuick();
    };

    for (const auto& entry : juce::RangedDirectoryIterator (folder, true, "*", juce::File::findFiles,
                                                            juce::File::FollowSymlinks::noCycles))
    {
        if (threadShouldExit())
        {
            scanning = false;
            return;
        }

        const auto& file = entry.getFile();

        if (! entry.isHidden() && isSupportedFile (file))
        {
            batch.add (file);

            if (batch.size() >= 256)
                flush();
        }
    }

    flush();
    scanning = false;
}
