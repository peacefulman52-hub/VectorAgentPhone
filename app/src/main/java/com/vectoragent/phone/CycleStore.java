package com.vectoragent.phone;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

public final class CycleStore extends SQLiteOpenHelper {
    private static final String DB="vector_lab_cycles.db";
    public CycleStore(Context c){super(c,DB,null,1);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cycles(id INTEGER PRIMARY KEY AUTOINCREMENT, number INTEGER, problem TEXT, hypothesis TEXT, critique TEXT, experiment TEXT, evidence TEXT, judgement TEXT, prediction TEXT, confidence REAL, time INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}
    public synchronized long save(int number,String problem,String hypothesis,String critique,String experiment,String evidence,String judgement,String prediction,double confidence){
        ContentValues v=new ContentValues();
        v.put("number",number);v.put("problem",problem);v.put("hypothesis",hypothesis);v.put("critique",critique);
        v.put("experiment",experiment);v.put("evidence",evidence);v.put("judgement",judgement);v.put("prediction",prediction);
        v.put("confidence",confidence);v.put("time",System.currentTimeMillis());
        return getWritableDatabase().insert("cycles",null,v);
    }
    public synchronized int count(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cycles",null);try{c.moveToFirst();return c.getInt(0);}finally{c.close();}}
    public synchronized List<String> recent(int limit){
        ArrayList<String> out=new ArrayList<>();
        Cursor c=getReadableDatabase().rawQuery("SELECT number,problem,hypothesis,judgement,prediction FROM cycles ORDER BY id DESC LIMIT "+Math.max(1,limit),null);
        try{while(c.moveToNext()) out.add("Цикл #"+c.getInt(0)+"\nЗадача: "+c.getString(1)+"\n"+c.getString(2)+"\n"+c.getString(3)+"\nПредсказание: "+c.getString(4));}finally{c.close();}
        return out;
    }
}
