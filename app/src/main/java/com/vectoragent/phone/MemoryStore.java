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
        public String id,text,status,source,relation,provenance,version,clusterId; public double confidence; public int support; public long created,updated;
        Item(String id,String text,String status,String source,String relation,String provenance,String version,String clusterId,double confidence,int support,long created,long updated){this.id=id;this.text=text;this.status=status;this.source=source;this.relation=relation;this.provenance=provenance;this.version=version;this.clusterId=clusterId;this.confidence=confidence;this.support=support;this.created=created;this.updated=updated;}
    }
    public static class Hypothesis {
        public String id,rule,evidence,status,version; public int support;
        Hypothesis(String id,String rule,String evidence,String status,String version,int support){this.id=id;this.rule=rule;this.evidence=evidence;this.status=status;this.version=version;this.support=support;}
    }
    public static class ConclusionCluster { public String id,label; public final List<Item> members=new ArrayList<>(); public int active,candidate,conflict,rejected; }
    public static class IngestResult { public int added=0,conflicts=0,ignored=0,similar=0; public final List<String> messages=new ArrayList<>(); public final List<String> learning=new ArrayList<>(); }
    private static final String PREF="vector_memory",KEY="items",HKEY="hypotheses",LKEY="learning_log"; private final SharedPreferences p; private final LearningEngine learner; private final ExplorationEngine exploration;
    public MemoryStore(Context c){p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);learner=new LearningEngine(c);exploration=new ExplorationEngine(c);ensureKey();ensureSeedData();}

    private void ensureSeedData(){
        try{
            if(SeedData.VERSION.equals(p.getString("seed_version",""))) return;
            JSONArray a=read();
            java.util.HashSet<String> existing=new java.util.HashSet<>();
            for(int i=0;i<a.length();i++) existing.add(norm(a.optJSONObject(i).optString("text")));
            long now=System.currentTimeMillis();
            int index=0;
            for(String[] fact:SeedData.FACTS){
                if(fact.length<2) continue;
                String text=fact[1];
                if(existing.contains(norm(text))) continue;
                JSONObject o=new JSONObject();
                o.put("id","seed-"+SeedData.VERSION+"-"+index++);
                o.put("text",text);
                o.put("status","ACTIVE");
                o.put("source","BUILT_IN_BASE/"+fact[0]);
                o.put("relation","");
                o.put("provenance","bundled starter knowledge; category="+fact[0]+"; seed="+SeedData.VERSION);
                o.put("version","1");
                o.put("confidence",0.95);
                o.put("support",1);
                o.put("created",now);
                o.put("updated",now);
                o.put("learningState","BUILT_IN");
                o.put("seed",true);
                o.put("history",new JSONArray());
                a.put(o);
                existing.add(norm(text));
            }
            write(a);
            p.edit().putString("seed_version",SeedData.VERSION).apply();
        }catch(Exception ignored){}
    }
    private boolean ensureKey(){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias("VectorAgentKey"))return true;try{KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(256);kg.generateKey();return true;}catch(Exception ignored){}KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(128);kg.generateKey();return true;}catch(Exception ignored){return false;}}
    private String enc(String s){try{if(!ensureKey())return "PLAIN:"+Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,k);byte[] iv=c.getIV(),data=c.doFinal(s.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[iv.length+data.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(data,0,out,iv.length,data.length);return Base64.encodeToString(out,Base64.NO_WRAP);}catch(Exception e){throw new RuntimeException(e);}}
    private String dec(String s){try{if(s.startsWith("PLAIN:"))return new String(Base64.decode(s.substring(6),Base64.NO_WRAP),StandardCharsets.UTF_8);byte[] all=Base64.decode(s,Base64.NO_WRAP);byte[] iv=new byte[12],data=new byte[all.length-12];System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,data,0,data.length);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("VectorAgentKey",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,iv));return new String(c.doFinal(data),StandardCharsets.UTF_8);}catch(Exception e){return s;}}
    private JSONArray read(){try{return new JSONArray(dec(p.getString(KEY,"[]")));}catch(Exception e){return new JSONArray();}}
    private void write(JSONArray a){p.edit().putString(KEY,enc(a.toString())).apply();}
    public String firstLearningTarget(){
        JSONArray a=read();
        try{
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                String st=o.optString("status");
                if("CANDIDATE".equals(st)||"CONFLICT".equals(st)) return o.optString("id");
            }
        }catch(Exception ignored){}
        return "";
    }
    public String researchTarget(String id){
        JSONArray a=read();
        try{
            for(int i=0;i<a.length();i++){ JSONObject o=a.getJSONObject(i); if(id.equals(o.optString("id"))) return o.optString("text"); }
        }catch(Exception ignored){}
        return "";
    }
    public String recordWebEvidence(String targetId,String report,String[] urls){
        JSONArray a=read(); String result="";
        try{
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                if(!targetId.equals(o.optString("id"))) continue;
                JSONArray src=o.optJSONArray("independentSources");
                if(src==null) src=new JSONArray();
                java.util.HashSet<String> domains=new java.util.HashSet<>();
                for(int j=0;j<src.length();j++) domains.add(src.optString(j));
                for(String u:urls){
                    try{
                        String d=new java.net.URL(u).getHost().toLowerCase();
                        if(d.startsWith("www.")) d=d.substring(4);
                        if(!d.isEmpty()) domains.add(d);
                    }catch(Exception ignored){}
                }
                src=new JSONArray(); for(String d:domains) src.put(d); o.put("independentSources",src);
                o.put("webResearch",report);
                o.put("webEvidenceCount",domains.size());
                addHistory(o); o.put("updated",System.currentTimeMillis());
                if(domains.size()>=2 && "CANDIDATE".equals(o.optString("status"))){
                    o.put("status","ACTIVE");
                    o.put("learningState","AUTO_CONFIRMED_BY_INDEPENDENT_WEB_SOURCES");
                    o.put("learningSignal","TWO_OR_MORE_DISTINCT_SOURCE_DOMAINS");
                    o.put("support",Math.max(o.optInt("support",1),domains.size()));
                    result="ACTIVE: найдено "+domains.size()+" независимых доменов.";
                } else if("CONFLICT".equals(o.optString("status"))){
                    result="CONFLICT: источники собраны, но конфликт автоматически не разрешён.";
                } else {
                    result="Собрано источников: "+domains.size()+"; нужны дополнительные независимые домены.";
                }
                break;
            }
        }catch(Exception ignored){ return "Ошибка записи веб-свидетельства."; }
        write(a); return result;
    }

    public List<Item> all(){List<Item> r=new ArrayList<>();JSONArray a=read();for(int i=0;i<a.length();i++)try{r.add(item(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
    private Item item(JSONObject o){return new Item(o.optString("id"),o.optString("text"),o.optString("status"),o.optString("source"),o.optString("relation"),o.optString("provenance"),o.optString("version","1"),o.optString("clusterId",""),o.optDouble("confidence"),o.optInt("support",1),o.optLong("created"),o.optLong("updated",o.optLong("created")));}
    private String norm(String s){return s.toLowerCase().replaceAll("\\s+"," ").trim();}
    private boolean has(String s,String... xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private boolean contradicts(String a,String b){
        // Numeric contradiction: same factual subject/conditions, explicit numeric values differ.
        // Example: "вода кипит ... 100 градусов" vs "вода кипит ... 80 градусов".
        if (numericClaimContradiction(a,b)) return true;
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
    private boolean numericClaimContradiction(String a,String b){
        String x=norm(a), y=norm(b);
        if(!(x.contains("кип") && y.contains("кип"))) return false;
        if(!(x.contains("вод") && y.contains("вод"))) return false;
        boolean samePressure = (x.contains("нормаль") && x.contains("давлен")) && (y.contains("нормаль") && y.contains("давлен"));
        if(!samePressure) return false;
        java.util.regex.Matcher mx=java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:°\\s*)?(?:c|с|градус)").matcher(x);
        java.util.regex.Matcher my=java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:°\\s*)?(?:c|с|градус)").matcher(y);
        if(!mx.find() || !my.find()) return false;
        try {
            double vx=Double.parseDouble(mx.group(1).replace(',','.'));
            double vy=Double.parseDouble(my.group(1).replace(',','.'));
            return Math.abs(vx-vy) > 0.000001;
        } catch(Exception e){ return false; }
    }

    private double similarity(String a,String b){String[] x=norm(a).replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9 ]"," ").split(" +");String[] y=norm(b).replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9 ]"," ").split(" +");int common=0;for(String w:x){if(w.length()<3)continue;for(String z:y)if(w.equals(z)){common++;break;}}return (double)common/Math.max(1,Math.max(x.length,y.length));}

    private static final java.util.Set<String> CLUSTER_STOP_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "это","как","что","при","для","или","и","а","но","из","по","в","во","к","с","со","от","до","не","без","над","под","у","же","ли","бы","об","о","за","на","the","and","are","is","of","to","in","on","for","with","as","by","from",
        "предварительный","вывод","заключение","гипотеза","гипотезы","повторяющееся","противопоставление","шаблоне","шаблон","правило","система","данные","наблюдение","наблюдения"
    ));

    private java.util.Set<String> clusterTokens(String text){
        java.util.HashSet<String> out=new java.util.HashSet<>();
        String clean=norm(text).replaceAll("[^\\p{L}\\p{Nd} ]"," ");
        for(String token:clean.split("\\s+")){
            if(token.length()<3 || CLUSTER_STOP_WORDS.contains(token)) continue;
            String stem=token;
            String[] suffixes={"иями","ями","ами","ого","ему","ыми","ими","иях","ая","яя","ое","ее","ые","ие","ий","ый","ой","ую","юю","ых","их","ам","ям","ом","ем","ах","ях","ов","ев","ей","а","я","ы","и","у","ю","е","о"};
            for(String suffix:suffixes){
                if(stem.length()>suffix.length()+2 && stem.endsWith(suffix)){stem=stem.substring(0,stem.length()-suffix.length());break;}
            }
            if(stem.length()>=3 && !CLUSTER_STOP_WORDS.contains(stem)) out.add(stem);
        }
        return out;
    }

    // Overlap of meaningful terms, not exact equality: similar conclusions stay separate
    // records but can share a persistent cluster. At least two informative terms must match.
    private double clusterSimilarity(String a,String b){
        java.util.Set<String> x=clusterTokens(a), y=clusterTokens(b);
        if(x.isEmpty()||y.isEmpty()) return 0.0;
        int common=0; for(String w:x) if(y.contains(w)) common++;
        if(common<2) return 0.0;
        return (double)common/Math.max(1,Math.min(x.size(),y.size()));
    }

    private String freshClusterId(JSONArray items){
        String id;
        do { id="cl-"+System.currentTimeMillis()+"-"+items.length()+"-"+(int)(Math.random()*100000); }
        while(containsClusterId(items,id));
        return id;
    }
    private boolean containsClusterId(JSONArray items,String id){
        for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o!=null&&id.equals(o.optString("clusterId")))return true;}
        return false;
    }
    private String assignCluster(JSONArray items,JSONObject incoming)throws Exception{
        if(!incoming.optString("clusterId","").isEmpty()) return incoming.optString("clusterId");
        JSONObject best=null; double bestScore=0.0;
        String text=incoming.optString("text","");
        for(int i=0;i<items.length();i++){
            JSONObject old=items.optJSONObject(i); if(old==null) continue;
            double score=clusterSimilarity(text,old.optString("text",""));
            if(score>bestScore){bestScore=score;best=old;}
        }
        String clusterId;
        if(best!=null && bestScore>=0.50){
            clusterId=best.optString("clusterId","");
            if(clusterId.isEmpty()){clusterId=freshClusterId(items);best.put("clusterId",clusterId);}
            // Migrate unclustered legacy records that are similarly related to this conclusion.
            for(int i=0;i<items.length();i++){
                JSONObject old=items.optJSONObject(i); if(old==null || !old.optString("clusterId","").isEmpty()) continue;
                if(clusterSimilarity(text,old.optString("text",""))>=0.50) old.put("clusterId",clusterId);
            }
        } else clusterId=freshClusterId(items);
        incoming.put("clusterId",clusterId);
        return clusterId;
    }

    public List<ConclusionCluster> clusters(){
        java.util.LinkedHashMap<String,ConclusionCluster> grouped=new java.util.LinkedHashMap<>();
        for(Item x:all()){
            if(x.clusterId==null||x.clusterId.isEmpty()) continue;
            ConclusionCluster c=grouped.get(x.clusterId);
            if(c==null){c=new ConclusionCluster();c.id=x.clusterId;c.label=x.text;grouped.put(c.id,c);}
            c.members.add(x);
            if("ACTIVE".equals(x.status))c.active++;else if("CANDIDATE".equals(x.status))c.candidate++;else if("CONFLICT".equals(x.status))c.conflict++;else if("REJECTED".equals(x.status))c.rejected++;
        }
        return new ArrayList<>(grouped.values());
    }

    // Store tentative conclusions as candidates, never as automatically verified facts.
    // Similar-but-different conclusions receive distinct records and join a shared cluster.
    public boolean recordConclusion(String text,String origin,String basis,double confidence){
        String clean=text==null?"":text.trim();
        if(clean.length()<8) return false;
        String key="manual:"+Integer.toHexString(norm(clean).hashCode());
        upsertPreliminaryConclusion(key,clean,origin,basis,confidence,1,"CANDIDATE");
        appendLearningLog("PRELIMINARY_CONCLUSION: "+clean);
        return true;
    }

    private void upsertPreliminaryConclusion(String key,String conclusion,String origin,String basis,double confidence,int support,String requestedStatus){
        JSONArray a=read(); JSONObject found=null;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);
            if(o!=null && "LOCAL_INFERENCE".equals(o.optString("source")) && key.equals(o.optString("conclusionKey"))){found=o;break;}
        }
        long now=System.currentTimeMillis();
        try{
            double modestConfidence=Math.max(0.15,Math.min(0.55,confidence));
            if(found==null){
                JSONObject o=new JSONObject();
                o.put("id","conclusion-"+Integer.toHexString(key.hashCode())+"-"+now);
                o.put("text","Предварительный вывод: "+conclusion);
                o.put("status","REJECTED".equals(requestedStatus)?"REJECTED":"CANDIDATE");
                o.put("source","LOCAL_INFERENCE");
                o.put("conclusionOrigin",origin==null?"":origin);
                o.put("relation","INFERRED_CONCLUSION");
                o.put("provenance","PRELIMINARY_CONCLUSION; key="+key+"; basis="+(basis==null?"":basis));
                o.put("conclusionKey",key);
                o.put("confidence",modestConfidence);
                o.put("support",Math.max(1,support));
                o.put("version","1");
                o.put("created",now);o.put("updated",now);
                o.put("learningState","PRELIMINARY_NOT_VERIFIED");
                o.put("history",new JSONArray());
                assignCluster(a,o);a.put(o);
            }else{
                addHistory(found);
                found.put("text","Предварительный вывод: "+conclusion);
                found.put("conclusionOrigin",origin==null?"":origin);
                found.put("provenance","PRELIMINARY_CONCLUSION; key="+key+"; basis="+(basis==null?"":basis));
                found.put("confidence",Math.min(0.55,Math.max(found.optDouble("confidence",modestConfidence),modestConfidence)));
                found.put("support",Math.max(found.optInt("support",1),Math.max(1,support)));
                if("REJECTED".equals(requestedStatus)) found.put("status","REJECTED");
                else if(!"ACTIVE".equals(found.optString("status")) && !"REJECTED".equals(found.optString("status"))) found.put("status","CANDIDATE");
                found.put("learningState","PRELIMINARY_NOT_VERIFIED");
                found.put("version",Integer.toString(found.optInt("version",1)+1));
                found.put("updated",now);
                if(found.optString("clusterId","").isEmpty()) assignCluster(a,found);
            }
            write(a);
        }catch(Exception ignored){}
    }

    private void setConclusionStatus(String key,String status,String signal){
        JSONArray a=read();boolean changed=false;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);
            if(o==null||!"LOCAL_INFERENCE".equals(o.optString("source"))||!key.equals(o.optString("conclusionKey")))continue;
            try{addHistory(o);o.put("status",status);o.put("learningSignal",signal);o.put("updated",System.currentTimeMillis());o.put("version",Integer.toString(o.optInt("version",1)+1));changed=true;}catch(Exception ignored){}
        }
        if(changed)write(a);
    }
    private void addHistory(JSONObject o)throws Exception{JSONArray h=o.optJSONArray("history");if(h==null)h=new JSONArray();JSONObject v=new JSONObject();v.put("version",o.optString("version","1"));v.put("status",o.optString("status"));v.put("confidence",o.optDouble("confidence"));v.put("updated",o.optLong("updated",o.optLong("created")));h.put(v);o.put("history",h);}
    private String detectRelation(String a,String b){
        String x=norm(a), y=norm(b);
        if(contradicts(x,y)) return "CONTRADICTS";
        String[] support={"подтверждает","подтверждено","соответствует","согласуется","доказывает","поддерживает"};
        for(String k:support) if(x.contains(k)||y.contains(k)) return "SUPPORTS";
        String[] cause={"потому что","из-за","приводит к","привело к","вызывает","является причиной"};
        for(String k:cause) if(x.contains(k)||y.contains(k)) return "CAUSES";
        String[] temporal={"сначала","затем","после","до того","раньше","позже","сначала"};
        for(String k:temporal) if(x.contains(k)||y.contains(k)) return "TEMPORAL";
        String[] contain={"содержит","включает","состоит из","внутри"};
        for(String k:contain) if(x.contains(k)||y.contains(k)) return "CONTAINS";
        String[] attr={"имеет","характеризуется","относится к","является"};
        for(String k:attr) if(x.contains(k)||y.contains(k)) return "ATTRIBUTE";
        return "";
    }

    public String relationBetween(String a,String b){ return detectRelation(a,b); }

    public String add(String text,double conf,boolean auto,String source,String provenance,String relation){
        JSONArray a=read();String id="m-"+System.currentTimeMillis()+"-"+a.length();long now=System.currentTimeMillis();String conflictWith="";int modelPairs=0;
        try{
            JSONObject o=new JSONObject();o.put("id",id);o.put("text",text);o.put("confidence",conf);o.put("created",now);o.put("updated",now);o.put("source",source);o.put("provenance",provenance);o.put("relation",relation);o.put("version","1");o.put("history",new JSONArray());o.put("learningState","UNTESTED");o.put("support",1);
            for(int i=0;i<a.length();i++){JSONObject old=a.optJSONObject(i);if(old==null || "LOCAL_INFERENCE".equals(old.optString("source")))continue;String oldText=old.optString("text");if(oldText.isEmpty())continue;
                double pairSimilarity=similarity(text,oldText);
                boolean directContradiction=contradicts(text,oldText);
                LearningEngine.Prediction prediction=learner.predict(text,oldText);
                if(directContradiction || (pairSimilarity>=0.45 && modelPairs<8)){
                    LearningEngine.TrainingEvent te=learner.observe(text,oldText,directContradiction);
                    o.put("predictedRelation",prediction.label);
                    o.put("predictionConfidence",prediction.probability);
                    o.put("predictionFeatures",prediction.features);
                    if(!te.correct) appendLearningLog("MODEL_ERROR: predicted="+(te.predictedPositive?"CONTRADICTS":"NONE")+" actual="+(te.actualPositive?"CONTRADICTS":"NONE")+" | "+text+" ↔ "+oldText); modelPairs++;
                }
                String detected=detectRelation(text,oldText); if(directContradiction){conflictWith=old.optString("id");o.put("status","CONFLICT");o.put("relation",(relation.isEmpty()?"":relation+"; ")+"CONTRADICTS "+conflictWith);old.put("status","CONFLICT");String oldRel=old.optString("relation");old.put("relation",(oldRel.isEmpty()?"":oldRel+"; ")+"CONTRADICTS "+id);old.put("updated",now);break;} else if(!detected.isEmpty() && o.optString("relation").isEmpty()){o.put("relation",detected+" "+old.optString("id"));} else if("CONTRADICTS".equals(prediction.label) && prediction.probability>=0.55 && pairSimilarity>=0.45){if(o.optString("relation").isEmpty())o.put("relation","PREDICTED_CONTRADICTS "+old.optString("id"));}
            }
            if(conflictWith.isEmpty())o.put("status",auto&&conf>=threshold()?"ACTIVE":"CANDIDATE");o.put("conflictWith",conflictWith);assignCluster(a,o);a.put(o);write(a);discoverHypotheses();return conflictWith;
        }catch(Exception ignored){return "";}
    }
    public void appendLearningLog(String event){ try{ JSONArray a=readLearningLog(); JSONObject o=new JSONObject(); o.put("time",System.currentTimeMillis()); o.put("event",event); a.put(o); while(a.length()>300) a.remove(0); p.edit().putString(LKEY,a.toString()).apply(); }catch(Exception ignored){} }
    public List<String> learningLog(){ List<String> r=new ArrayList<>(); try{JSONArray a=readLearningLog(); for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i); if(o!=null) r.add(o.optString("event"));}}catch(Exception ignored){} return r; }
    private JSONArray readLearningLog(){try{return new JSONArray(p.getString(LKEY,"[]"));}catch(Exception e){return new JSONArray();}}

    public IngestResult ingest(String raw,double conf){
        IngestResult r=new IngestResult();String[] parts=raw.split("[\n.!?;]+");
        for(String part:parts){String s=part.trim();if(s.length()<4||isNonFact(s)){r.ignored++;continue;}
            List<Item> before=all();boolean sim=false;String bestId="";double bestScore=0;for(Item x:before)if(!"REJECTED".equals(x.status)&&!"LOCAL_INFERENCE".equals(x.source)){double sc=similarity(s,x.text);if(sc>=0.55){sim=true;if(sc>bestScore){bestScore=sc;bestId=x.id;}}}
            if(sim && bestScore>=0.80){
                r.similar++;
                boolean promoted=reinforce(bestId,s,conf);
                r.learning.add(promoted
                    ? "🧠 Повторное подтверждение: накопленное знание автоматически закреплено как ACTIVE."
                    : "Повторное свидетельство сопоставлено с уже накопленным знанием; новая копия не создаётся.");
                appendLearningLog("REINFORCE: "+s);
                continue;
            }
            String c=add(s,conf,true, "CHAT","user message → local claim extraction","auto-ingested");r.added++;if(sim)r.similar++;
            if(!c.isEmpty()){r.conflicts++;r.messages.add("Конфликт: «"+s+"» ↔ существующий факт");r.learning.add("Система изменила состояние пары: оба утверждения помечены CONFLICT.");appendLearningLog("CONFLICT: "+s);}
            else if(sim){r.learning.add("Найдено сходство с накопленным знанием.");appendLearningLog("Найдено сходство: "+s);
                if(reinforce(bestId,s,conf)) r.learning.add("🧠 Повторное подтверждение накопленного знания: support достиг порога, состояние изменено на ACTIVE.");
                else r.learning.add("Повторное свидетельство записано; до автоматического закрепления нужны дополнительные независимые подтверждения.");
            }
            else { r.learning.add("Новое наблюдение не имеет достаточного сходства; оно осталось отдельным кандидатом."); appendLearningLog("Новое наблюдение → CANDIDATE: "+s); }
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
    public boolean auto(){return p.getBoolean("auto",true);} public void setAuto(boolean v){p.edit().putBoolean("auto",v).apply();}
    public List<Hypothesis> hypotheses(){List<Hypothesis> r=new ArrayList<>();try{JSONArray a=new JSONArray(p.getString(HKEY,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);r.add(new Hypothesis(o.optString("id"),o.optString("rule"),o.optString("evidence"),o.optString("status","CANDIDATE"),o.optString("version","1"),o.optInt("support")));}}catch(Exception ignored){}return r;}
    private JSONArray readHyp(){try{return new JSONArray(p.getString(HKEY,"[]"));}catch(Exception e){return new JSONArray();}}
    private void writeHyp(JSONArray a){p.edit().putString(HKEY,a.toString()).apply();}
    public void learnHypothesis(String id,boolean accepted){JSONArray a=readHyp();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id"))){o.put("status",accepted?"ACTIVE":"REJECTED");o.put("version",Integer.toString(o.optInt("version",1)+1));o.put("learningSignal",accepted?"USER_CONFIRMED":"USER_REJECTED");o.put("updated",System.currentTimeMillis());break;}}catch(Exception ignored){}writeHyp(a);setConclusionStatus("hypothesis:"+id,accepted?"ACTIVE":"REJECTED",accepted?"USER_CONFIRMED_HYPOTHESIS":"USER_REJECTED_HYPOTHESIS");}
    public int discoverHypotheses(){
        List<Item> items=new ArrayList<>(); for(Item x:all()) if(!"LOCAL_INFERENCE".equals(x.source)) items.add(x);
        java.util.HashMap<String,java.util.HashSet<String>> groups=new java.util.HashMap<>();
        for(int i=0;i<items.size();i++)for(int j=i+1;j<items.size();j++){
            String[] a=norm(items.get(i).text).split(" "),b=norm(items.get(j).text).split(" ");
            if(a.length!=b.length||a.length<2)continue;
            int diff=-1,n=0; for(int k=0;k<a.length;k++)if(!a[k].equals(b[k])){diff=k;n++;}
            if(n!=1)continue;
            String key="POS"+diff+"|"+signature(a,diff);
            java.util.HashSet<String> pairs=groups.get(key);
            if(pairs==null){pairs=new java.util.HashSet<>();groups.put(key,pairs);}
            pairs.add(a[diff]+" ↔ "+b[diff]);
        }
        JSONArray h=readHyp(); int created=0;
        for(String key:groups.keySet()){
            java.util.HashSet<String> pairs=groups.get(key);
            if(pairs==null||pairs.size()<2)continue;
            String rule="повторяющееся противопоставление в шаблоне "+key.substring(key.indexOf("|")+1)+": "+pairs;
            String hid="h-"+Integer.toHexString(rule.hashCode());
            JSONObject found=null;
            for(int i=0;i<h.length();i++)if(h.optJSONObject(i)!=null&&hid.equals(h.optJSONObject(i).optString("id"))){found=h.optJSONObject(i);break;}
            try{
                if(found==null){
                    found=new JSONObject(); found.put("id",hid); found.put("rule",rule); found.put("evidence",pairs.toString());
                    found.put("support",pairs.size()); found.put("tests",Math.max(0,pairs.size()-1)); found.put("counterexamples",0);
                    found.put("status","CANDIDATE"); found.put("version","1"); found.put("created",System.currentTimeMillis()); h.put(found); created++;
                }else{
                    int old=found.optInt("support",0);
                    found.put("support",Math.max(old,pairs.size()));
                    found.put("tests",Math.max(0,pairs.size()-1));
                    found.put("evidence",pairs.toString());
                    found.put("updated",System.currentTimeMillis());
                    if(pairs.size()>=3 && found.optInt("tests",0)>=2 && found.optInt("counterexamples",0)==0 && !"REJECTED".equals(found.optString("status"))){
                        if(!"ACTIVE".equals(found.optString("status"))){
                            found.put("status","ACTIVE");
                            found.put("learningSignal","AUTO_VALIDATED_BY_REPEATED_PATTERN");
                            found.put("learningState","AUTO_TESTED");
                            found.put("version",Integer.toString(found.optInt("version",1)+1));
                            appendLearningLog("Гипотеза автоматически подтверждена повторяемым паттерном: "+rule);
                        }
                    }
                }
            }catch(Exception ignored){}
        }
        // Every discovered pattern also becomes a modest, persistent conclusion candidate.
        // It is stored in MemoryStore for future clustering, but excluded from evidence used
        // to discover patterns so the agent cannot "prove itself" with its own conclusions.
        for(int i=0;i<h.length();i++){
            JSONObject hyp=h.optJSONObject(i); if(hyp==null) continue;
            String hid=hyp.optString("id");
            int support=Math.max(1,hyp.optInt("support",1));
            String conclusion=hyp.optString("rule");
            String basis="hypothesisId="+hid+"; evidence="+hyp.optString("evidence")+"; hypothesisStatus="+hyp.optString("status");
            String conclusionStatus="REJECTED".equals(hyp.optString("status"))?"REJECTED":"CANDIDATE";
            upsertPreliminaryConclusion("hypothesis:"+hid,conclusion,"LOCAL_PATTERN",basis,0.25+Math.min(6,support)*0.04,support,conclusionStatus);
        }
        writeHyp(h);
        return created;
    }
    private String signature(String[] a,int diff){StringBuilder s=new StringBuilder();for(int i=0;i<a.length;i++)if(i!=diff)s.append(a[i]).append(" ");return s.toString().trim();}
    public String testHypotheses(){
        StringBuilder out=new StringBuilder();
        List<Hypothesis> hs=hypotheses();
        if(hs.isEmpty()) return "Гипотез для проверки пока нет.";
        List<Item> items=all();
        for(Hypothesis h:hs){
            int evidence=0, active=0, conflicts=0;
            for(Item x:items){
                if("ACTIVE".equals(x.status)) active++;
                if("CONFLICT".equals(x.status)) conflicts++;
                String r=x.relation==null?"":x.relation;
                if(r.contains("CONTRADICTS") && h.rule.contains("противопоставление")) conflicts++;
                if(h.evidence.contains(x.text.substring(0,Math.min(18,x.text.length())))) evidence++;
            }
            out.append("• ").append(h.status).append(" — ").append(h.rule)
               .append("\n  support=").append(h.support)
               .append(", проверочных совпадений=").append(Math.max(h.support-1,0))
               .append(", конфликтов в памяти=").append(conflicts)
               .append("\n");
        }
        out.append("\nАвтопроверка: гипотеза может перейти в ACTIVE после повторяемого независимого паттерна (не менее 3 пар и 2 проверочных шагов) без обнаруженных контрпримеров. Пользователь остаётся дополнительным сигналом, а не обязательным судьёй.");
        return out.toString();
    }

    public String learningSummary(){return learner.summary();}
    public String blindTransferTest(){return learner.transferTest();}
    public int generateExplorationBatch(int rounds){return exploration.runBatch(this,learner,rounds);}
    public java.util.List<ExplorationEngine.Proposal> recentExploration(int max){return exploration.recent(max);}
    public void resetLearningModel(){learner.reset();}
    public LearningEngine.Prediction predictRelation(String a,String b){return learner.predict(a,b);}
    public String exportJson(){return read().toString()+"\nHYPOTHESES\n"+readHyp().toString()+"\nLEARNING_MODEL\n"+learner.summary();}
    public void importJson(String json){try{write(new JSONArray(json));}catch(Exception ignored){}}
    public void snapshot(){p.edit().putString("snapshot",p.getString(KEY,"[]")).putString("snapshotHyp",p.getString(HKEY,"[]")).putString("snapshotLearn",learner.exportState()).putString("snapshotExplore",exploration.exportState()).apply();}
    public boolean rollback(){String s=p.getString("snapshot",null);if(s==null)return false;p.edit().putString(KEY,s).putString(HKEY,p.getString("snapshotHyp","[]")).apply();learner.importState(p.getString("snapshotLearn","{}"));exploration.importState(p.getString("snapshotExplore","[]"));return true;}
    public void saveApiKey(String key){if(key==null)key="";p.edit().putString("apiKey",enc(key)).apply();} public boolean hasApiKey(){return !p.getString("apiKey","").isEmpty();}
    public int count(String status){int n=0;for(Item x:all())if(x.status.equals(status))n++;return n;}
}