package com.my.televip.virtuals.SQLite;
import java.util.ArrayList;
import java.util.List;
public final class SQLiteDatabase {
    public final List<SQLiteCursor> cursors = new ArrayList<>();
    public final List<SQLitePreparedStatement> statements = new ArrayList<>();
    public boolean empty, failStep;
    public java.util.Map<String,com.my.televip.virtuals.tgnet.TLRPC.MessageData> history;
    public final List<String> queries = new ArrayList<>();
    public SQLiteCursor queryFinalized(String sql, Object[] args) {
        queries.add(sql);
        com.my.televip.virtuals.tgnet.TLRPC.MessageData old=null;
        if(history!=null) {
            java.util.regex.Matcher match=java.util.regex.Pattern.compile("mid=(-?\\d+) AND uid=(-?\\d+)").matcher(sql);
            if(!match.find()) throw new IllegalArgumentException("Unexpected history query");
            old=history.get(match.group(1)+":"+match.group(2));
        }
        SQLiteCursor cursor = new SQLiteCursor(empty || history!=null && old==null);
        cursor.data.previous=old;
        cursors.add(cursor);
        return cursor;
    }
    public SQLitePreparedStatement executeFast(String sql) {
        SQLitePreparedStatement statement = new SQLitePreparedStatement(failStep);
        statements.add(statement);
        return statement;
    }
}
