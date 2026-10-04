package com.combatreplay;

import java.nio.file.Path;
import net.runelite.client.util.Filepath;

final class TestFilepaths
{
    private TestFilepaths()
    {
    }

    static Filepath filepath(Path path)
    {
        Path absolute = path.toAbsolutePath();
        return Filepath.Unchecked.getRooted(absolute.getParent())
            .joinSegment(absolute.getFileName().toString());
    }
}
