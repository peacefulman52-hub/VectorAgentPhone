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
        public String id,text,status,source,relation,provenance,version;
        public double confidence;
        public long created,updated;
        Item(String id,String text,String status,String source,String relation,String provenance,String version,double confidence,long created,long updated){
            this.id=id;this.text=text;this.status=status;this.source=source;this.relation=relation;this.provenance=provenance;this.version=version;this.confidence=confidence;this.created=created;this.updated=updated;
        }
    }
    private static final String PREF="vector_memory",KEY="items";
    private final SharedPreferences p;
    public MemoryStore(Context c){p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);ensureKey();}
    private boolean ensureKey(){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias("VectorAgentKey"))return true;try{KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(256);kg.generateKey();return true;}catch(Exception ignored){}KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(128);kg.generateKey();return true;}catch(Exception ignored){return false;}}
    private String enc(String s){try{if(!ensureKey())return "PLAIN:"+Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,k);byte[] iv=c.getIV(),data=c.doFinal(s.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[iv.length+data.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(data,0,out,iv.length,data.length);return Base64.encodeToString(out,Base64.NO_WRAP);}catch(Exception e){throw new RuntimeException(e);}}
    private String dec(String s){try{if(s.startsWith("PLAIN:"))return new String(Base64.decode(s.substring(6),Base64.NO_WRAP),StandardCharsets.UTF_8);byte[] all=Base64.decode(s,Base64.NO_WRAP);byte[] iv=new byte[12],data=new byte[all.length-12];System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,data,0,data.length);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,iv));return new String(c.doFinal(data),StandardCharsets.UTF_8);}catch(Exception e){return s;}}
    private JSONArray read(){try{return new JSONArray(dec(p.getString(KEY,"[]")));}catch(Exception e){return new JSONArray();}}
    private void write(JSONArray a){p.edit().putString(KEY,enc(a.toString())).apply();}
    public List<Item> all(){List<Item> r=new ArrayList<>();JSONArray a=read();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);r.add(item(o));}catch(Exception ignored){}return r;}
    private Item item(JSONObject o){return new Item(o.optString("id"),o.optString("text"),o.optString("status"),o.optString("source"),o.optString("relation"),o.optString("provenance"),o.optString("version","1"),o.optDouble("confidence"),o.optLong("created"),o.optLong("updated",o.optLong("created")));}
    private String norm(String s){return s.toLowerCase().replace("не ","").replace("  "," ").trim();}
    private String conflictId(JSONArray a,String text){String n=norm(text);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String old=o.optString("text");if(!old.isEmpty()&&!old.equalsIgnoreCase(text)&&norm(old).equals(n))return o.optString("id");}return "";}
    public String add(String text,double conf,boolean auto,String source,String provenance,String relation){
        JSONArray a=read();String id="m-"+System.currentTimeMillis();String conflict=conflictId(a,text);long now=System.currentTimeMillis();
        try{JSONObject o=new JSONObject();o.put("id",id);o.put("text",text);o.put("confidence",conf);o.put("created",now);o.put("updated",now);o.put("source",source);o.put("provenance",provenance);o.put("relation",relation);o.put("version","1");o.put("status",conflict.isEmpty()?(auto&&conf>=threshold()?"ACTIVE":"CANDIDATE"):"CONFLICT");o.put("conflictWith",conflict);o.put("history",new JSONArray());a.put(o);write(a);}catch(Exception ignored){}return conflict;}
    public void setStatus(String id,String status){JSONArray a=read();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id"))){history(o);o.put("status",status);o.put("updated",System.currentTimeMillis());o.put("version",Integer.toString(o.optInt("version",1)+1));break;}}catch(Exception ignored){}write(a);}
    private void history(JSONObject o)throws Exception{JSONArray h=o.optJSONArray("history");if(h==null)h=new JSONArray();JSONObject v=new JSONObject();v.put("version",o.optString("version","1"));v.put("status",o.optString("status"));v.put("confidence",o.optDouble("confidence"));v.put("updated",o.optLong("updated",o.optLong("created")));h.put(v);o.put("history",h);}
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
    public int count(String status){int n=0;for(Item x:all())if(x.status.equals(status))n++;return n;}
}