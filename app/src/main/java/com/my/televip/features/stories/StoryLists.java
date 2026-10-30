package com.my.televip.features.stories;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BooleanSupplier;

/** Filter the two transient story lists; leave controller maps, uploads and storage intact. */
public final class StoryLists {
    private StoryLists() {}

    public static void validate(Field visible, Field hidden) {
        if (visible == null || hidden == null || visible.equals(hidden)) throw new IllegalArgumentException("Distinct story lists required");
        for (Field field : new Field[]{visible, hidden}) {
            if (Modifier.isStatic(field.getModifiers())
                    || field.getType() != ArrayList.class) throw new IllegalArgumentException("Unsupported story list: " + field);
            field.setAccessible(true);
        }
    }

    public static void install(Object controller, Field visible, Field hidden, Class<?> peerStories,
            BooleanSupplier disabled) throws IllegalAccessException {
        validate(visible, hidden);
        if (peerStories == null || disabled == null) throw new IllegalArgumentException("Missing story filter dependency");
        Field[] fields = {visible, hidden};
        Object[] replacements = new Object[2];
        for (int i = 0; i < fields.length; i++) {
            Object value = fields[i].get(controller);
            if (value instanceof FilteredList) replacements[i] = value;
            else if (value != null && value.getClass() == ArrayList.class)
                replacements[i] = new FilteredList(peerStories, disabled, (ArrayList<?>) value);
            else throw new IllegalArgumentException("Unexpected story list implementation");
        }
        // Validate both lists before replacing either, including retry after a partial hook setup.
        for (int i = 0; i < fields.length; i++) fields[i].set(controller, replacements[i]);
    }

    private static final class FilteredList extends ArrayList<Object> {
        private final Class<?> storyType;
        private final BooleanSupplier disabled;
        FilteredList(Class<?> storyType, BooleanSupplier disabled, Collection<?> initial) {
            this.storyType = storyType; this.disabled = disabled;
            addAll(initial);
        }
        private boolean drops(Object value) { return disabled.getAsBoolean() && storyType.isInstance(value); }
        @Override public boolean add(Object value) { return !drops(value) && super.add(value); }
        @Override public void add(int index, Object value) {
            if (index < 0 || index > size()) throw new IndexOutOfBoundsException();
            if (!drops(value)) super.add(index, value);
        }
        @Override public boolean addAll(Collection<?> values) { return addAll(size(), values); }
        @Override public boolean addAll(int index, Collection<?> values) {
            if (index < 0 || index > size()) throw new IndexOutOfBoundsException();
            int at = index;
            for (Object value : new ArrayList<>(values)) if (!drops(value)) super.add(at++, value);
            return at != index;
        }
        @Override public Object set(int index, Object value) {
            return drops(value) ? get(index) : super.set(index, value);
        }
    }
}
