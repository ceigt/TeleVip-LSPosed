package com.my.televip.features.media;

import java.io.File;
import java.lang.ref.WeakReference;

/** Bind a local path to its exact host message, never just a possibly reused message ID. */
public final class MediaPathOverride {
    private volatile Binding binding;
    private static final class Binding {
        final WeakReference<Object> message;
        final File path;
        Binding(Object message, File path) {this.message=new WeakReference<>(message);this.path=path;}
    }
    public void remember(Object message, File path) {
        binding=message==null || path==null ? null : new Binding(message,path);
    }
    public File find(Object message) {
        Binding current=binding;
        return message!=null && current!=null && current.message.get()==message ? current.path : null;
    }
}
