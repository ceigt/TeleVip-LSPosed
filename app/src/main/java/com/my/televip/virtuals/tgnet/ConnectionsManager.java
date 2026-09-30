package com.my.televip.virtuals.tgnet;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.UserConfig;

import com.my.televip.compat.XposedHelpers;

public class ConnectionsManager {
    final Object connectionsManager;

    public ConnectionsManager(Object instance)
    {
        this.connectionsManager = instance;
    }

    public void sendRequest(Object object, Object completionBlock) {
        XposedHelpers.callMethod(connectionsManager, Obfuscate.getMethodName("ConnectionsManager", "sendRequest"), object, completionBlock);
    }

    public Object getInstanceObject() { return connectionsManager; }

    public int getCurrentTime(){
        return (int)XposedHelpers.callMethod(connectionsManager, Obfuscate.getMethodName("ConnectionsManager", "getCurrentTime"));
    }

    public static ConnectionsManager getInstance(int num){
        return new ConnectionsManager(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER), Obfuscate.getMethodName("ConnectionsManager", "getInstance"), num));
    }

    public static ConnectionsManager getConnectionsManager() {
        return getInstance(UserConfig.getSelectedAccount());
    }

}
