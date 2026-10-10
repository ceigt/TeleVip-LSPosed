package org.telegram.tgnet;
public class ConnectionsManager {
public interface Normal {void run(TLObject result,TLRPC.TL_error error);} public interface Timestamp {void run(TLObject result,TLRPC.TL_error error,long time);}
public interface Ack {} public interface Write {} public final java.util.concurrent.atomic.AtomicInteger delivered=new java.util.concurrent.atomic.AtomicInteger();
public void sendRequestInternal(TLObject request,Normal callback,Timestamp stamp,Ack ack,Write write,int a,int b,int c,boolean d,int token){delivered.incrementAndGet();}
public void cancelRequest(int token,boolean server){}
}
