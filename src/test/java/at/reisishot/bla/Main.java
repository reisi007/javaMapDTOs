package at.reisishot.bla;

import at.reisishot.bla.commons.ComplexType;
import at.reisishot.bla.json.JSON;
import at.reisishot.bla.xml.XML;
import at.reisishot.mapping.MappingCommand;
import at.reisishot.mapping.MappingUtils;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Main {

    @Test
    public void test() {
        List<MappingCommand> mappings = loadFile();

        XML xml = createXml();

        JSON mapped = MappingUtils.map(xml, JSON.class, mappings);

        assertEquals(1, mapped.getA());
        assertEquals("bla", mapped.getJt().getValue());
    }

    private XML createXml() {
        XML xml = new XML();
        xml.setB("1");
        ComplexType xt = new ComplexType();
        xml.setXt(xt);
        xt.setValue("bla");
        return xml;
    }

    private List<MappingCommand> loadFile() {
        List<MappingCommand> commands = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(Main.class.getClassLoader().getResourceAsStream("mapping.txt"), "File not found"), StandardCharsets.UTF_8))) {
            String curLine;
            do {
                curLine = reader.readLine();
                if (curLine != null) {
                    String[] split = curLine.split("=", 2);
                    if (split.length == 2)
                        commands.add(new MappingCommand(split[0], split[1]));
                }
            } while (curLine != null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return commands;
    }
}
