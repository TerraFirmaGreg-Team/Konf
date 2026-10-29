package team.terrafirmagreg.konf.format;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import java.nio.file.Path;
import java.util.List;

public interface ConfigFormat {

    String id();

    boolean commented();

    List<String> extensions();

    GenericBuilder<?, ? extends FileConfig> builder(Path file);

    Config inMemory();
}
