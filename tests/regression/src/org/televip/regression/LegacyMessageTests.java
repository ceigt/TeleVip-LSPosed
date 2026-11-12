package org.televip.regression;
import com.my.televip.Class.*;
import com.my.televip.messages.MessageStorage;
import com.my.televip.virtuals.tgnet.*;
import java.lang.reflect.*;
import java.util.*;
final class LegacyMessageTests {
    public static class Modern extends TLRPC.MessageData {
        public Modern(){super(0,0,0,"");}
        public int getObjectSize(){return 16;}
        public void serializeToStream(NativeByteBuffer buffer){buffer.previous=this;buffer.flags=flags;}
    }
    static int run()throws Exception{
        Map<String,Class<?>> prior=new HashMap<>();String[] names={ClassNames.NATIVE_BYTE_BUFFER,"org.telegram.tgnet.TLRPC$TL_message","org.telegram.tgnet.TLRPC$TL_messageService","org.telegram.tgnet.TLRPC$TL_messageEmpty"};
        for(String n:names)prior.put(n,ClassLoad.classes.put(n,n.equals(ClassNames.NATIVE_BYTE_BUFFER)?NativeByteBuffer.class:Modern.class));
        Method normalize=MessageStorage.class.getDeclaredMethod("normalizeLegacy",NativeByteBuffer.class,int.class,long.class,int.class);normalize.setAccessible(true);int checks=0;
        try{for(int constructor:new int[]{0x22eb6aba,0x9f8d60bb,0x05f46804,0x83e5de54}){
            NativeByteBuffer old=new NativeByteBuffer();old.previous=new TLRPC.MessageData(7|Integer.MIN_VALUE,202,0,"legacy text");old.previous.date=1001;
            NativeByteBuffer normalized=(NativeByteBuffer)normalize.invoke(null,old,constructor,101L,7);
            if(normalized.previous.id!=7)throw new AssertionError("legacy ID was not repaired from trusted SQL identity");checks++;
            if(normalized.previous.date!=1001||!normalized.previous.text.equals("legacy text"))throw new AssertionError("legacy content was lost during normalization");checks++;
            if(normalized.previous==old.previous||!(normalized.previous instanceof Modern))throw new AssertionError("legacy serializer was reused");checks++;
        }}finally{for(String n:names){Class<?> old=prior.get(n);if(old==null)ClassLoad.classes.remove(n);else ClassLoad.classes.put(n,old);}}
        return checks;
    }
}
