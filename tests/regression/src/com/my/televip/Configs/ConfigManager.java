package com.my.televip.Configs;
public final class ConfigManager {
    public static ConfigItem showDeletedMessages = new ConfigItem(ConfigItem.SWITCH, "ShowDeletedMessages", true, null);
    public static ConfigItem customCalendar;
    public static ConfigItem hideSeenPrivateChat = item("HideSeenPrivateChat"), hideSeenChannel = item("HideSeenChannel");
    public static ConfigItem hideSeen = new ConfigItem(ConfigItem.EXPANDABLE_SWITCH,"HideSeen",java.util.Arrays.asList(hideSeenPrivateChat,hideSeenChannel));
    public static ConfigItem hideTyping=item("HideTyping"),hideOnline=item("HideOnline"),hideStoryView=item("HideStoryView");
    public static ConfigItem secretMediaSave=item("SecretMediaSave"),removesContentSaving=item("RemovesContentSaving");
    private static ConfigItem item(String key){return new ConfigItem(ConfigItem.SWITCH,key,true,null);}
    public static boolean isGhostMode(){return hideSeen.isEnable()||hideTyping.isEnable()||hideOnline.isEnable()||hideStoryView.isEnable();}
    public static ConfigItem disableChannelSwipeBack = new ConfigItem(ConfigItem.SWITCH, "DisableChannelSwipeBack", true, null);
}
