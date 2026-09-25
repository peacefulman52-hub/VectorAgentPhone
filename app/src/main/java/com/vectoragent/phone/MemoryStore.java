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
        public String id,text,status,source,relation,provenance,version; public double confidence; public int support; public long created,updated;
        Item(String id,String text,String status,String source,String relation,String provenance,String version,double confidence,int support,long created,long updated){this.id=id;this.text=text;this.status=status;this.source=source;this.relation=relation;this.provenance=provenance;this.version=version;this.confidence=confidence;this.support=support;this.created=created;this.updated=updated;}
    }
    public static class Hypothesis {
        public String id,rule,evidence,status,version; public int support;
        Hypothesis(String id,String rule,String evidence,String status,String version,int support){this.id=id;this.rule=rule;this.evidence=evidence;this.status=status;this.version=version;this.support=support;}
    }
    public static class IngestResult { public int added=0,conflicts=0,ignored=0,similar=0; public final List<String> messages=new ArrayList<>(); public final List<String> learning=new ArrayList<>(); }
    private static final String PREF="vector_memory",KEY="items",HKEY="hypotheses"; private final SharedPreferences p;
    public MemoryStore(Context c){p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);ensureKey();}
    private boolean ensureKey(){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias("VectorAgentKey"))return true;try{KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(256);kg.generateKey();return true;}catch(Exception ignored){}KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(128);kg.generateKey();return true;}catch(Exception ignored){return false;}}
    private String enc(String s){try{if(!ensureKey())return "PLAIN:"+Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,k);byte[] iv=c.getIV(),data=c.doFinal(s.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[iv.length+data.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(data,0,out,iv.length,data.length);return Base64.encodeToString(out,Base64.NO_WRAP);}catch(Exception e){throw new RuntimeException(e);}}
    private String dec(String s){try{if(s.startsWith("PLAIN:"))return new String(Base64.decode(s.substring(6),Base64.NO_WRAP),StandardCharsets.UTF_8);byte[] all=Base64.decode(s,Base64.NO_WRAP);byte[] iv=new byte[12],data=new byte[all.length-12];System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,data,0,data.length);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,iv));return new String(c.doFinal(data),StandardCharsets.UTF_8);}catch(Exception e){return s;}}
    private JSONArray read(){try{return new JSONArray(dec(p.getString(KEY,"[]")));}catch(Exception e){return new JSONArray();}}
    private void write(JSONArray a){p.edit().putString(KEY,enc(a.toString())).apply();}
    public List<Item> all(){List<Item> r=new ArrayList<>();JSONArray a=read();for(int i=0;i<a.length();i++)try{r.add(item(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
    private Item item(JSONObject o){return new Item(o.optString("id"),o.optString("text"),o.optString("status"),o.optString("source"),o.optString("relation"),o.optString("provenance"),o.optString("version","1"),o.optDouble("confidence"),o.optInt("support",1),o.optLong("created"),o.optLong("updated",o.optLong("created")));}
    private String norm(String s){return s.toLowerCase().replaceAll("\\s+"," ").trim();}
    private boolean has(String s,String... xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private boolean contradicts(String a,String b){
        String x=norm(a),y=norm(b);
        if(has(x,"недоступен","недоступна","не работал","не работает","выключен","выключена")&&has(y,"доступен","доступна","работал без","работает","включен","включена"))return true;
        if(has(y,"недоступен","недоступна","не работал","не работает","выключен","выключена")&&has(x,"доступен","доступна","работал без","работает","включен","включена"))return true;
        String nx=x.replace("не ","").replaceAll("[.,!?;:]$","").trim(),ny=y.replace("не ","").replaceAll("[.,!?;:]$","").trim();
        if(!nx.equals(x)&&nx.equals(y.replaceAll("[.,!?;:]$","").trim()))return true;
        if(!ny.equals(y)&&ny.equals(x.replaceAll("[.,!?;:]$","").trim()))return true;
        if((has(x,"есть ","имеется ","существует ")&&has(y,"нет ","отсутствует ","не существует "))||(has(y,"есть ","имеется ","существует ")&&has(x,"нет ","отсутствует ","не существует ")))return true;
        if((x.contains("кипит")&&y.contains("не кипит"))||(y.contains("кипит")&&x.contains("не кипит")))return true;
        return false;
    }
    private double similarity(String a,String b){String[] x=norm(a).replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9 ]"," ").split(" +");String[] y=norm(b).replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9 ]"," ").split(" +");int common=0;for(String w:x){if(w.length()<3)continue;for(String z:y)if(w.equals(z)){common++;break;}}return (double)common/Math.max(1,Math.max(x.length,y.length));}
    private void addHistory(JSONObject o)throws Exception{JSONArray h=o.optJSONArray("history");if(h==null)h=new JSONArray();JSONObject v=new JSONObject();v.put("version",o.optString("version","1"));v.put("status",o.optString("status"));v.put("confidence",o.optDouble("confidence"));v.put("updated",o.optLong("updated",o.optLong("created")));h.put(v);o.put("history",h);}
    public String add(String text,double conf,boolean auto,String source,String provenance,String relation){
        JSONArray a=read();String id="m-"+System.currentTimeMillis()+"-"+a.length();long now=System.currentTimeMillis();String conflictWith="";
        try{
            JSONObject o=new JSONObject();o.put("id",id);o.put("text",text);o.put("confidence",conf);o.put("created",now);o.put("updated",now);o.put("source",source);o.put("provenance",provenance);o.put("relation",relation);o.put("version","1");o.put("history",new JSONArray());o.put("learningState","UNTESTED");o.put("support",1);
            for(int i=0;i<a.length();i++){JSONObject old=a.optJSONObject(i);if(old==null)continue;String oldText=old.optString("text");if(oldText.isEmpty())continue;
                if(contradicts(text,oldText)){conflictWith=old.optString("id");o.put("status","CONFLICT");o.put("relation",(relation.isEmpty()?"":relation+"; ")+"CONTRADICTS "+conflictWith);old.put("status","CONFLICT");String oldRel=old.optString("relation");old.put("relation",(oldRel.isEmpty()?"":oldRel+"; ")+"CONTRADICTS "+id);old.put("updated",now);break;}
            }
            if(conflictWith.isEmpty())o.put("status",auto&&conf>=threshold()?"ACTIVE":"CANDIDATE");o.put("conflictWith",conflictWith);a.put(o);write(a);discoverHypotheses();return conflictWith;
        }catch(Exception ignored){return "";}
    }
    public IngestResult ingest(String raw,double conf){
        IngestResult r=new IngestResult();String[] parts=raw.split("[\n.!?;]+");
        for(String part:parts){String s=part.trim();if(s.length()<4||isNonFact(s)){r.ignored++;continue;}
            List<Item> before=all();boolean sim=false;String bestId="";double bestScore=0;for(Item x:before)if(!"REJECTED".equals(x.status)){double sc=similarity(s,x.text);if(sc>=0.55){sim=true;if(sc>bestScore){bestScore=sc;bestId=x.id;}}}
            String c=add(s,conf,auto(), "CHAT","user message → local claim extraction","auto-ingested");r.added++;if(sim)r.similar++;
            if(!c.isEmpty()){r.conflicts++;r.messages.add("Конфликт: «"+s+"» ↔ существующий факт");r.learning.add("Система изменила состояние пары: оба утверждения помечены CONFLICT.");}
            else if(sim){r.learning.add("Найдено сходство с накопленным знанием.");
                if(reinforce(bestId,s,conf)) r.learning.add("🧠 Повторное подтверждение накопленного знания: support достиг порога, состояние изменено на ACTIVE.");
                else r.learning.add("Повторное свидетельство записано; до автоматического закрепления нужны дополнительные независимые подтверждения.");
            }
            else r.learning.add("Новое наблюдение не имеет достаточного сходства; оно осталось отдельным кандидатом.");
        }
        return r;
    }
    private boolean reinforce(String id,String text,double conf){
        if(id==null||id.isEmpty())return false;
        JSONArray a=read();boolean promoted=false;
        try{for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(!id.equals(o.optString("id")))continue;if("CONFLICT".equals(o.optString("status"))||"REJECTED".equals(o.optString("status")))return false;
            int support=o.optInt("support",1)+1;o.put("support",support);addHistory(o);o.put("updated",System.currentTimeMillis());o.put("version",Integer.toString(o.optInt("version",1)+1));
            double old=o.optDouble("confidence",conf);o.put("confidence",Math.min(1.0,Math.max(old,conf)+0.05));
            if(support>=3 && !"ACTIVE".equals(o.optString("status"))){o.put("status","ACTIVE");o.put("learningState","AUTO_CONFIRMED_BY_REPETITION");o.put("learningSignal","THREE_SUPPORTING_OBSERVATIONS");promoted=true;}
            break;}}
        catch(Exception ignored){return false;} write(a);return promoted;
    }
    private boolean isNonFact(String s){String x=norm(s);return x.matches("^(привет|здравствуйте|хай|добрый день|что|как|почему|зачем|кто|где|когда|помоги)\b.*");}
    public void learn(String id,boolean accepted){
        JSONArray a=read();String acceptedText="";
        for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id"))){acceptedText=o.optString("text");addHistory(o);double c=o.optDouble("confidence",.5);c=accepted?Math.min(1,c+.10):Math.max(0,c-.15);o.put("confidence",c);o.put("status",accepted?"ACTIVE":"REJECTED");o.put("learned",true);o.put("learningSignal",accepted?"USER_CONFIRMED":"USER_REJECTED");o.put("learningState",accepted?"CONFIRMED":"REJECTED");o.put("updated",System.currentTimeMillis());o.put("version",Integer.toString(o.optInt("version",1)+1));if(accepted){String other=o.optString("conflictWith");if(!other.isEmpty())for(int j=0;j<a.length();j++)try{JSONObject q=a.getJSONObject(j);if(other.equals(q.optString("id"))){addHistory(q);q.put("status","REJECTED");q.put("learningState","REJECTED_BY_CONFLICT_RESOLUTION");q.put("learningSignal","OTHER_FACT_CONFIRMED");q.put("confidence",Math.max(0,q.optDouble("confidence",.5)-.20));q.put("updated",System.currentTimeMillis());q.put("version",Integer.toString(q.optInt("version",1)+1));}}catch(Exception ignored){}}break;}}catch(Exception ignored){}
        write(a);if(!acceptedText.isEmpty())learnFromResolution(acceptedText,accepted);
    }
    private void learnFromResolution(String text,boolean accepted){if(!accepted)return;JSONArray h=readHyp();String rule="Подтверждённое утверждение получает ACTIVE; прямой конфликт с ним переводится в REJECTED до появления нового свидетельства.";String id="h-resolution-v1";try{boolean exists=false;for(int i=0;i<h.length();i++)if(id.equals(h.optJSONObject(i).optString("id"))){exists=true;break;}if(!exists){JSONObject o=new JSONObject();o.put("id",id);o.put("rule",rule);o.put("evidence","Пользователь подтвердил: "+text);o.put("support",1);o.put("status","ACTIVE");o.put("version","1");o.put("created",System.currentTimeMillis());h.put(o);writeHyp(h);}}catch(Exception ignored){}}
    public double threshold(){return Double.longBitsToDouble(p.getLong("thresholdBits",Double.doubleToLongBits(.70)));}
    public void setThreshold(double v){p.edit().putLong("thresholdBits",Double.doubleToLongBits(v)).apply();}
    public boolean auto(){return p.getBoolean("auto",false);} public void setAuto(boolean v){p.edit().putBoolean("auto",v).apply();}
    public List<Hypothesis> hypotheses(){List<Hypothesis> r=new ArrayList<>();try{JSONArray a=new JSONArray(p.getString(HKEY,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);r.add(new Hypothesis(o.optString("id"),o.optString("rule"),o.optString("evidence"),o.optString("status","CANDIDATE"),o.optString("version","1"),o.optInt("support")));}}catch(Exception ignored){}return r;}
    private JSONArray readHyp(){try{return new JSONArray(p.getString(HKEY,"[]"));}catch(Exception e){return new JSONArray();}}
    private void writeHyp(JSONArray a){p.edit().putString(HKEY,a.toString()).apply();}
    public void learnHypothesis(String id,boolean accepted){JSONArray a=readHyp();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id"))){o.put("status",accepted?"ACTIVE":"REJECTED");o.put("version",Integer.toString(o.optInt("version",1)+1));o.put("learningSignal",accepted?"USER_CONFIRMED":"USER_REJECTED");o.put("updated",System.currentTimeMillis());break;}}catch(Exception ignored){}writeHyp(a);}
    public int discoverHypotheses(){List<Item> items=all();java.util.HashMap<String,java.util.HashMap<String,java.util.HashSet<String>>> groups=new java.util.HashMap<>();
        for(int i=0;i<items.size();i++)for(int j=i+1;j<items.size();j++){String[] a=norm(items.get(i).text).split(" "),b=norm(items.get(j).text).split(" ");if(a.length!=b.length||a.length<2)continue;int diff=-1,n=0;for(int k=0;k<a.length;k++)if(!a[k].equals(b[k])){diff=k;n++;}if(n!=1)continue;String key="POS"+diff+"|"+signature(a,diff);java.util.HashMap<String,java.util.HashSet<String>> m=groups.get(key);if(m==null){m=new java.util.HashMap<>();groups.put(key,m);}String pair=a[diff]+" ↔ "+b[diff];m.computeIfAbsent("PAIR",z->new java.util.HashSet<>()).add(pair);}
        JSONArray h=readHyp();int created=0;for(String key:groups.keySet()){java.util.HashSet<String> pairs=groups.get(key).get("PAIR");if(pairs==null||pairs.size()<2)continue;String rule="повторяющееся противопоставление в шаблоне "+key.substring(key.indexOf("|")+1)+": "+pairs;String hid="h-"+Integer.toHexString(rule.hashCode());boolean exists=false;for(int i=0;i<h.length();i++)if(h.optJSONObject(i)!=null&&hid.equals(h.optJSONObject(i).optString("id"))){exists=true;break;}if(!exists){try{JSONObject o=new JSONObject();o.put("id",hid);o.put("rule",rule);o.put("evidence",pairs.toString());o.put("support",pairs.size());o.put("status","CANDIDATE");o.put("version","1");o.put("created",System.currentTimeMillis());h.put(o);created++;}catch(Exception ignored){}}}if(created>0)writeHyp(h);return created;}
    private String signature(String[] a,int diff){StringBuilder s=new StringBuilder();for(int i=0;i<a.length;i++)if(i!=diff)s.append(a[i]).append(" ");return s.toString().trim();}
    public String exportJson(){return read().toString()+"\nHYPOTHESES\n"+readHyp().toString();}
    public void importJson(String json){try{write(new JSONArray(json));}catch(Exception ignored){}}
    public void snapshot(){p.edit().putString("snapshot",p.getString(KEY,"[]")).putString("snapshotHyp",p.getString(HKEY,"[]")).apply();}
    public boolean rollback(){String s=p.getString("snapshot",null);if(s==null)return false;p.edit().putString(KEY,s).putString(HKEY,p.getString("snapshotHyp","[]")).apply();return true;}
    public void saveApiKey(String key){if(key==null)key="";p.edit().putString("apiKey",enc(key)).apply();} public boolean hasApiKey(){return !p.getString("apiKey","").isEmpty();}
    public int count(String status){int n=0;for(Item x:all())if(x.status.equals(status))n++;return n;}
}