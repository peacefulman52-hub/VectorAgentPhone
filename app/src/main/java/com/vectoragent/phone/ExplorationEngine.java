package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates synthetic training experiences from existing ACTIVE memory.
 * Synthetic proposals are persisted separately from factual MemoryStore.
 * They can train the learner, but they can never become facts or independent evidence.
 */
public final class ExplorationEngine {
    public static final class Proposal {
        public String id, seedId, seedText, text, operator, expected, predicted, features;
        public double probability;
        public long time;
        Proposal(String id,String seedId,String seedText,String text,String operator,String expected,
                 String predicted,double probability,String features,long time){
            this.id=id; this.seedId=seedId; this.seedText=seedText; this.text=text; this.operator=operator;
            this.expected=expected; this.predicted=predicted; this.probability=probability; this.features=features; this.time=time;
        }
    }

    private static final String PREF="vector_exploration", KEY="proposals";
    private final SharedPreferences p;
    private final Random random = new Random();

    public ExplorationEngine(Context c){ p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

    private JSONArray read(){
        try{return new JSONArray(p.getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}
    }
    private void write(JSONArray a){p.edit().putString(KEY,a.toString()).apply();}

    public synchronized int runBatch(MemoryStore store, LearningEngine learner, int rounds){
        List<MemoryStore.Item> seeds=new ArrayList<>();
        for(MemoryStore.Item x:store.all()) if("ACTIVE".equals(x.status) && !x.source.startsWith("GENERATED")) seeds.add(x);
        if(seeds.isEmpty()) return 0;

        JSONArray a=read();
        int made=0;
        int limit=Math.max(1,Math.min(rounds,50));
        for(int i=0;i<limit;i++){
            MemoryStore.Item seed=seeds.get(random.nextInt(seeds.size()));
            Mutation m=mutate(seed.text);
            if(m==null) continue;

            LearningEngine.Prediction before=learner.predict(seed.text,m.text);
            LearningEngine.TrainingEvent ev=learner.observe(seed.text,m.text,"CONTRADICTS".equals(m.expected));
            String id="e-"+System.currentTimeMillis()+"-"+i+"-"+random.nextInt(100000);
            JSONObject o=new JSONObject();
            try{
                o.put("id",id);
                o.put("seedId",seed.id);
                o.put("seedText",seed.text);
                o.put("text",m.text);
                o.put("operator",m.operator);
                o.put("expected",m.expected);
                o.put("predicted",before.label);
                o.put("probability",before.probability);
                o.put("features",before.features);
                o.put("correct",ev.correct);
                o.put("synthetic",true);
                o.put("provenance","GENERATED_EXPLORATION_ONLY");
                o.put("time",System.currentTimeMillis());
                a.put(o);
                made++;
                store.appendLearningLog("EXPLORATION: "+m.operator+" | expected="+m.expected+
                        " predicted="+before.label+" | "+seed.text+" ↔ "+m.text);
            }catch(Exception ignored){}
        }
        while(a.length()>500) a.remove(0);
        write(a);
        return made;
    }

    public synchronized List<Proposal> recent(int max){
        List<Proposal> out=new ArrayList<>(); JSONArray a=read();
        int from=Math.max(0,a.length()-Math.max(1,max));
        for(int i=from;i<a.length();i++)try{
            JSONObject o=a.getJSONObject(i);
            out.add(new Proposal(o.optString("id"),o.optString("seedId"),o.optString("seedText"),
                    o.optString("text"),o.optString("operator"),o.optString("expected"),
                    o.optString("predicted"),o.optDouble("probability"),o.optString("features"),
                    o.optLong("time")));
        }catch(Exception ignored){}
        return out;
    }

    public synchronized String exportState(){return p.getString(KEY,"[]");}
    public synchronized void importState(String json){try{new JSONArray(json);p.edit().putString(KEY,json).apply();}catch(Exception ignored){}}
    public synchronized void clear(){p.edit().remove(KEY).apply();}

    private static final class Mutation{
        String text,operator,expected;
        Mutation(String t,String o,String e){text=t;operator=o;expected=e;}
    }

    private Mutation mutate(String input){
        String x=input==null?"":input.trim();
        if(x.length()<5) return null;
        int choice=random.nextInt(5);

        if(choice==0){
            String y=flipNegation(x);
            if(y!=null && !y.equals(x)) return new Mutation(y,"NEGATION_FLIP","CONTRADICTS");
        }
        if(choice==1){
            String y=numberJitter(x);
            if(y!=null && !y.equals(x)) return new Mutation(y,"NUMBER_JITTER","CONTRADICTS");
        }
        if(choice==2){
            String y=swapTwoWords(x);
            if(y!=null && !y.equals(x)) return new Mutation(y,"WORD_ORDER_NOISE","NONE");
        }
        if(choice==3){
            String y=insertUncertainty(x);
            if(y!=null && !y.equals(x)) return new Mutation(y,"UNCERTAINTY_PREFIX","NONE");
        }

        String y=syntheticPrefix(x);
        if(!y.equals(x)) return new Mutation(y,"SYNTHETIC_PREFIX","NONE");
        return null;
    }

    private static String flipNegation(String x){
        String[] forms={" не ","нет ","не работает","не доступен","неактивен","невозможно","неисправен"};
        for(String f:forms) if(x.toLowerCase(Locale.ROOT).contains(f.trim())){
            String y=x.replaceFirst("(?i)\\bне\\s+","");
            if(!y.equals(x)) return y;
        }
        Matcher m=Pattern.compile("(?i)\\b(работает|доступен|активен|исправен|включен)\\b").matcher(x);
        if(m.find()) return x.substring(0,m.start())+"не "+m.group(1)+x.substring(m.end());
        return null;
    }

    private static String numberJitter(String x){
        Matcher m=Pattern.compile("(?<!\\d)(\\d+(?:[.,]\\d+)?)(?!\\d)").matcher(x);
        if(!m.find()) return null;
        try{
            double v=Double.parseDouble(m.group(1).replace(',','.'));
            int delta=Math.max(1,(int)Math.round(Math.abs(v)*(.05+random.nextDouble()*.25)));
            if(random.nextBoolean()) delta=-delta;
            double nv=Math.max(0,v+delta);
            String rep=(Math.abs(nv-Math.rint(nv))<1e-9)?Integer.toString((int)nv):String.format(Locale.ROOT,"%.2f",nv);
            return x.substring(0,m.start())+rep+x.substring(m.end());
        }catch(Exception e){return null;}
    }

    private static String swapTwoWords(String x){
        String[] t=x.split("\\s+");
        if(t.length<4) return x+" — экспериментальный вариант";
        int a=1+random.nextInt(t.length-2);
        int b=a+1+random.nextInt(t.length-a-1);
        String z=t[a];t[a]=t[b];t[b]=z;
        StringBuilder s=new StringBuilder();
        for(int i=0;i<t.length;i++){if(i>0)s.append(' ');s.append(t[i]);}
        return s.toString();
    }

    private static String insertUncertainty(String x){
        String[] p={"Возможно, ","По одной из гипотез, ","Предварительно: "};
        return p[random.nextInt(p.length)]+x;
    }

    private static String syntheticPrefix(String x){
        String[] p={"Наблюдение: ","Тестовая версия: ","Гипотетический вариант: "};
        return p[random.nextInt(p.length)]+x;
    }
}
