package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentBus {
    private static final String PREF="vector_agent_bus";
    private static final String KEY="messages";
    private final SharedPreferences prefs;

    public AgentBus(Context c){ prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

    public synchronized String send(String from,String to,String type,String payload){
        String id="msg-"+System.currentTimeMillis()+"-"+Math.abs(payload.hashCode());
        JSONArray a=read();
        JSONObject o=new JSONObject();
        try{
            o.put("id",id); o.put("from",from); o.put("to",to);
            o.put("type",type); o.put("payload",payload); o.put("time",System.currentTimeMillis());
            a.put(o);
            while(a.length()>2000) a.remove(0);
            prefs.edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
        return id;
    }

    public synchronized JSONArray recent(int max){
        JSONArray a=read(), out=new JSONArray();
        int from=Math.max(0,a.length()-Math.max(1,max));
        for(int i=from;i<a.length();i++) try{out.put(a.getJSONObject(i));}catch(Exception ignored){}
        return out;
    }

    public synchronized int size(){return read().length();}
    public synchronized void clear(){prefs.edit().remove(KEY).apply();}

    private JSONArray read(){
        try{return new JSONArray(prefs.getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}
    }
}