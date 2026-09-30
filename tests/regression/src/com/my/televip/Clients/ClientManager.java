package com.my.televip.Clients;
public final class ClientManager {
    public enum Client { Telegram, Nagram }
    public static boolean is(Client client) { return client == Client.Telegram; }
}
