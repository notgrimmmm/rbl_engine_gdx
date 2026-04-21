package io.rebelengine;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class BSPEntities {
    public static class Entity {
        public final Map<String, String> properties = new HashMap<>();

        public String get(String key) { return properties.get(key); }
        public String classname() { return properties.get("classname"); }
    }

    public final List<Entity> all = new ArrayList<>();
    public final Map<String, List<Entity>> byClass = new HashMap<>();

    public void add(Entity ent) {
        all.add(ent);
        String cls = ent.classname();
        if (cls != null) {
            byClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(ent);
        }
    }
    // Get first entity of a class or null — saves the null check + getFirst() pattern
    public Entity first(String classname) {
        List<Entity> list = byClass.get(classname);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }
}