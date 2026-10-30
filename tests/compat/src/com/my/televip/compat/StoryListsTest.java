package com.my.televip.compat;

import com.my.televip.features.stories.StoryLists;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StoryListsTest {
    public static class Story {}
    public static class Controller {
        public ArrayList<Object> visible = new ArrayList<>(), hidden = new ArrayList<>(), uploads = new ArrayList<>();
        public static ArrayList<Object> shared = new ArrayList<>();
    }
    public static class FinalController {
        public final ArrayList<Object> visible = new ArrayList<>(), hidden = new ArrayList<>();
    }
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Story list " + checks); }
    public static void main(String[] args) throws Exception { System.out.println("STORY_LISTS_PASS: " + run() + " assertions"); }
    public static int run() throws Exception {
        checks = 0;
        Controller c = new Controller();
        Story story = new Story(); Object upload = new Object();
        c.visible.add(story); c.visible.add("keep"); c.hidden.add(story); c.uploads.add(upload);
        ArrayList<Object> untouched = c.uploads;
        AtomicBoolean disabled = new AtomicBoolean(true);
        java.lang.reflect.Field visible=Controller.class.getField("visible"), hidden=Controller.class.getField("hidden");
        StoryLists.install(c,visible,hidden,Story.class,disabled::get);
        check(c.visible.equals(Collections.singletonList("keep")) && c.hidden.isEmpty());
        check(c.uploads==untouched && c.uploads.get(0)==upload);
        check(!c.visible.add(story));
        c.visible.add(0,story); check(c.visible.size()==1);
        check(!c.hidden.addAll(Arrays.asList(story,story)));
        check(c.hidden.addAll(0,Arrays.asList(story,"other",story)) && c.hidden.equals(Collections.singletonList("other")));
        c.visible.set(0,story); check(c.visible.get(0).equals("keep"));
        c.visible.add(null); check(c.visible.size()==2);
        ArrayList<Object> first = c.visible;
        StoryLists.install(c,visible,hidden,Story.class,disabled::get); check(c.visible==first);
        disabled.set(false); check(c.visible.add(story));
        disabled.set(true); check(c.visible.remove(story));
        c.hidden.addAll(c.hidden); check(c.hidden.equals(Arrays.asList("other","other")));
        boolean rejected=false;
        try { c.visible.add(-1,story); } catch (IndexOutOfBoundsException expected) { rejected=true; } check(rejected);
        rejected=false;
        try { StoryLists.validate(visible,visible); } catch (IllegalArgumentException expected) { rejected=true; } check(rejected);
        rejected=false;
        try { StoryLists.validate(visible,Controller.class.getField("shared")); } catch (IllegalArgumentException expected) { rejected=true; } check(rejected);
        Controller invalid = new Controller(); invalid.hidden=null; first=invalid.visible;
        rejected=false;
        try { StoryLists.install(invalid,visible,hidden,Story.class,disabled::get); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected && invalid.visible==first);
        FinalController finals = new FinalController(); finals.visible.add(story);
        StoryLists.install(finals,FinalController.class.getField("visible"),FinalController.class.getField("hidden"),Story.class,disabled::get);
        check(finals.visible.isEmpty() && !finals.visible.add(story));
        return checks;
    }
}
