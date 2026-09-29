package com.my.televip.virtuals.ui;

import android.app.Activity;
import android.view.View;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.ImageReceiver;
import com.my.televip.virtuals.messenger.MessageObject;

import com.my.televip.compat.XposedHelpers;

public class PhotoViewer {

    Object photoViewer;

    public PhotoViewer(Object photoViewer){
        this.photoViewer = photoViewer;
    }

    public static PhotoViewer getInstance(){
        return new PhotoViewer(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.PHOTO_VIEWER), Obfuscate.getMethodName("PhotoViewer", "getInstance")));
    }

    public void setParentActivity(Activity activity){
        XposedHelpers.callMethod(photoViewer,  Obfuscate.getMethodName("PhotoViewer", "setParentActivity"), activity);
    }

    public void openPhoto(MessageObject messageObject, long l, long l2, long l3, PhotoViewerProvider provider, boolean b){
        XposedHelpers.callMethod(photoViewer,  Obfuscate.getMethodName("PhotoViewer", "openPhoto"), messageObject.getMessageObject(),l, l2, l3, provider.getPhotoViewerProvider(), b);
    }

    public View getGalleryButton(){
        return (View) XposedHelpers.getObjectField(photoViewer,  Obfuscate.getFieldName("PhotoViewer", "galleryButton"));
    }

    public static class PhotoViewerProvider {
        Object photoViewerProvider;

        public PhotoViewerProvider(Object photoViewer) {
            photoViewerProvider = photoViewer;
        }

        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, Object fileLocation, int index, boolean needPreview, boolean closing) {
            return new PlaceProviderObject(XposedHelpers.callMethod(photoViewerProvider,  Obfuscate.getMethodName("PhotoViewer$PhotoViewerProvider", "getPlaceForPhoto"), messageObject.getMessageObject(), fileLocation, index, needPreview, closing));
        }

        public Object getPhotoViewerProvider() {
            return photoViewerProvider;
        }

    }

    public static class PlaceProviderObject {
        Object placeProviderObject;

        public PlaceProviderObject(Object placeProvider) {
            placeProviderObject = placeProvider;
        }

        public ImageReceiver getImageReceiver() {
            return new ImageReceiver(XposedHelpers.getObjectField(placeProviderObject,  Obfuscate.getFieldName("PhotoViewer$PlaceProviderObject", "imageReceiver")));
        }

    }

}
