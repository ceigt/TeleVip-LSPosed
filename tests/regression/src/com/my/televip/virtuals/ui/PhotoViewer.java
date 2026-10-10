package com.my.televip.virtuals.ui;
public final class PhotoViewer {private final Object viewer;public PhotoViewer(Object value){viewer=value;}
public android.view.View getGalleryButton(){return (android.view.View)com.my.televip.compat.XposedHelpers.getObjectField(viewer,"galleryButton");}}
