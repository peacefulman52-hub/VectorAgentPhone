package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MemoryStore {
    public static class Item {
        public String id, text, status, source;
        public double confidence;
        public long created;
        Item(String id,String text,String status,String source,double confidence,long created){this.id=id;this.text=text;this.status=status;this.source=source;this.confidence=confidence;this.created=created;}
    }
    private static final String PREF="vector_memory"; private static final String KEY="items"; private static final String SETTINGS="settings";
    private final SharedPreferences p;
    public MemoryStore(Context c){p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);ensureKey();}
    private void ensureKey(){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("VectorAgentKey")){KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(256);kg.generateKey();}}catch(Exception ignored){}}
    private String enc(String s){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,k);byte[] iv=c.getIV(), data=c.doFinal(s.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[iv.length+data.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(data,0,out,iv.length,data.length);return Base64.encodeToString(out,Base64.NO_WRAP);}catch(Exception e){throw new RuntimeException(e);}}
    private String dec(String s){try{byte[] all=Base64.decode(s,Base64.NO_WRAP);byte[] iv=new byte[12],data=new byte[all.length-12];System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,data,0,data.length);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,iv));return new String(c.doFinal(data),StandardCharsets.UTF_8);}catch(Exception e){return s;}}
    private JSONArray read(){try{return new JSONArray(dec(p.getString(KEY,"[]")));}catch(Exception e){return new JSONArray();}}
    private void write(JSONArray a){p.edit().putString(KEY,enc(a.toString())).apply();}
    public List<Item> all(){List<Item> r=new ArrayList<>();JSONArray a=read();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);r.add(new Item(o.optString("id"),o.optString("text"),o.optString("status"),o.optString("source"),o.optDouble("confidence"),o.optLong("created")));}catch(Exception ignored){}return r;}
    public void add(String text,double conf,boolean auto){JSONArray a=read();try{JSONObject o=new JSONObject();o.put("id","m-"+System.currentTimeMillis());o.put("text",text);o.put("confidence",conf);o.put("created",System.currentTimeMillis());o.put("source","RAW");o.put("status",auto&&conf>=threshold()?"ACTIVE":"CANDIDATE");a.put(o);write(a);}catch(Exception ignored){}}
    public void setStatus(String id,String status){JSONArray a=read();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id"))){o.put("status",status);break;}}catch(Exception ignored){}write(a);}
    public double threshold(){return Double.longBitsToDouble(p.getLong("thresholdBits",Double.doubleToLongBits(.70)));}
    public void setThreshold(double v){p.edit().putLong("thresholdBits",Double.doubleToLongBits(v)).apply();}
    public boolean auto(){return p.getBoolean("auto",false);}
    public void setAuto(boolean v){p.edit().putBoolean("auto",v).apply();}
    public String exportJson(){return read().toString();}
    public void importJson(String json){try{JSONArray a=new JSONArray(json);write(a);}catch(Exception ignored){}}
    public void snapshot(){p.edit().putString("snapshot",p.getString(KEY,"[]")).apply();}
    public boolean rollback(){String s=p.getString("snapshot",null);if(s==null)return false;p.edit().putString(KEY,s).apply();return true;}
    public void saveApiKey(String key){if(key==null)key="";p.edit().putString("apiKey",enc(key)).apply();}
    public boolean hasApiKey(){return !p.getString("apiKey","").isEmpty();}
}
