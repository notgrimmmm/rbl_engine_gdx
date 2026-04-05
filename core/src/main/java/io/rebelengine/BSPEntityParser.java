package io.rebelengine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BSPEntityParser {
    // Regex to find "key" "value" pairs inside { }
    private static final Pattern ENTITY_BLOCK = Pattern.compile("\\{([^}]*)\\}");
    private static final Pattern KEY_VALUE    = Pattern.compile("\"([^\"]*)\"\\s+\"([^\"]*)\"");

    public static BSPEntities parse(String entityString) {
        BSPEntities result = new BSPEntities();
        if (entityString == null) return result;

        Matcher blockMatcher = ENTITY_BLOCK.matcher(entityString);
        while (blockMatcher.find()) {
            String blockContent = blockMatcher.group(1);
            BSPEntities.Entity ent = new BSPEntities.Entity();
            
            Matcher kvMatcher = KEY_VALUE.matcher(blockContent);
            while (kvMatcher.find()) {
                ent.properties.put(kvMatcher.group(1), kvMatcher.group(2));
            }
            
            result.add(ent);
        }
        return result;
    }
}