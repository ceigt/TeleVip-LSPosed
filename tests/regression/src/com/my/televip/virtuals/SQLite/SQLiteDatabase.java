package com.my.televip.virtuals.SQLite;
import java.util.ArrayList;
import java.util.List;
public final class SQLiteDatabase {
    public final List<SQLiteCursor> cursors = new ArrayList<>();
    public final List<SQLitePreparedStatement> statements = new ArrayList<>();
    public boolean empty, failStep;
    public SQLiteCursor queryFinalized(String sql, Object[] args) {
        SQLiteCursor cursor = new SQLiteCursor(empty);
        cursors.add(cursor);
        return cursor;
    }
    public SQLitePreparedStatement executeFast(String sql) {
        SQLitePreparedStatement statement = new SQLitePreparedStatement(failStep);
        statements.add(statement);
        return statement;
    }
}
