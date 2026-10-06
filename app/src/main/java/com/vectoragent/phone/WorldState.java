package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public final class WorldState {
    private static final String PREF="vector_world_state";
    private static final String KEY="events";
    private final SharedPreferences prefs;

    public WorldState(Context c){ prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

    public synchronized String addEvent(String type,String actor,String payload){
        String id="w-"+System.currentTimeMillis()+"-"+Math.abs(payload.hashCode());
        JSONArray a=read();
        JSONObject o=new JSONObject();
        try {
            o.put("id",id); o.put("type",type); o.put("actor",actor);
            o.put("payload",payload); o.put("time",System.currentTimeMillis());
            a.put(o);
            while(a.length()>1000) a.remove(0);
            prefs.edit().putString(KEY,a.toString()).apply();
        } catch(Exception ignored) {}
        return id;
    }

    public synchronized JSONArray events(){ return read(); }
    public synchronized int size(){ return read().length(); }
    public synchronized void clear(){ prefs.edit().remove(KEY).apply(); }

    private JSONArray read(){
        try{return new JSONArray(prefs.getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}
    }
}