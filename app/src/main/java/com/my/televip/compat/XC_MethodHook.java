package com.my.televip.compat;

import java.lang.reflect.Executable;

/** Callback shape used by the existing feature implementations. */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static final class MethodHookParam {
        public Executable method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        private boolean returnEarly;

        public Object getResult() { return result; }
        public void setResult(Object value) { result = value; throwable = null; returnEarly = true; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable value) { throwable = value; returnEarly = true; }
        public boolean hasThrowable() { return throwable != null; }
        boolean isReturnEarly() { return returnEarly; }
        void setOriginalResult(Object value) { result = value; returnEarly = false; }
        void setOriginalThrowable(Throwable value) { throwable = value; returnEarly = false; }
    }
}
