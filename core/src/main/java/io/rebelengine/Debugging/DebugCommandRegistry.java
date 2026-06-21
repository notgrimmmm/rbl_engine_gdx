package io.rebelengine.Debugging;

import io.rebelengine.Debugging.DebugCommand;
import com.badlogic.gdx.utils.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DebugCommandRegistry {
    private final Map<String, DebugCommand> commands = new HashMap<>();

    public void register(DebugCommand command) {
        commands.put(command.key().toLowerCase(Locale.ROOT), command);
    }

    public String execute(String rawInput, DebugContext context) {
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String[] parts = trimmed.split("\\s+");
        String commandKey = parts[0].toLowerCase(Locale.ROOT);
        DebugCommand command = commands.get(commandKey);
        if (command == null) {
            return "unknown command: " + commandKey;
        }

        String[] args = new String[Math.max(0, parts.length - 1)];
        if (parts.length > 1) {
            System.arraycopy(parts, 1, args, 0, args.length);
        }
        return command.run(args, context);
    }

    public void render(DebugRenderContext renderContext, DebugContext context) {
        for (DebugCommand command : commands.values()) {
            command.render(renderContext, context);
        }
    }
    
    public String[] getCommandNames() {
        String[] names = new String[commands.size()];
        int i = 0;
        for (DebugCommand value : commands.values()) {
            names[i++] = value.key();
        }
        return names;
    }
}
