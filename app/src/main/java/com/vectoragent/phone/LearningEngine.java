package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small trainable local model.
 *
 * It is intentionally not a language model. It learns a binary relation:
 * "these two observations are likely contradictory" from structural features.
 *
 * The model persists on the phone and updates by an error-driven perceptron step.
 * Features are entity-independent, so a learned pattern can transfer to unseen
 * words/entities ("blind transfer").
 */
public final class LearningEngine {
    public static final class Prediction {
        public final String label;
        public final double probability;
        public final String features;
        Prediction(String label,double probability,String features){
            this.label=label; this.probability=probability; this.features=features;
        }
    }

    public static final class TrainingEvent {
        public final boolean predictedPositive;
        public final boolean actualPositive;
        public final boolean correct;
        public final double probability;
        public final double delta;
        TrainingEvent(boolean predictedPositive,boolean actualPositive,boolean correct,double probability,double delta){
            this.predictedPositive=predictedPositive;
            this.actualPositive=actualPositive;
            this.correct=correct;
            this.probability=probability;
            this.delta=delta;
        }
    }

    private static final String PREF="vector_learning_model";
    private static final String MODEL_KEY="model";
    private static final double LR=0.35;
    private final SharedPreferences p;

    public LearningEngine(Context c){ p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

    private JSONObject read(){
        try{return new JSONObject(p.getString(MODEL_KEY,"{}"));}catch(Exception e){return new JSONObject();}
    }
    private void write(JSONObject o){p.edit().putString(MODEL_KEY,o.toString()).apply();}

    public synchronized Prediction predict(String a,String b){
        List<String> features=features(a,b);
        JSONObject m=read();
        double score=m.optDouble("bias",0.0);
        for(String f:features){
            score += m.optDouble("w_"+safe(f),0.0);
        }
        double prob=1.0/(1.0+Math.exp(-score));
        String label=prob>=0.55?"CONTRADICTS":"NONE";
        return new Prediction(label,prob,String.join(", ",features));
    }

    public synchronized TrainingEvent observe(String a,String b,boolean actualContradiction){
        Prediction before=predict(a,b);
        boolean predicted=before.probability>=0.50;
        boolean actual=actualContradiction;
        double delta=LR*((actual?1.0:0.0)-(predicted?1.0:0.0));
        JSONObject m=read();
        m.put("trials",m.optInt("trials",0)+1);
        if(predicted==actual)m.put("correct",m.optInt("correct",0)+1);
        else m.put("errors",m.optInt("errors",0)+1);
        if(actual)m.put("positive",m.optInt("positive",0)+1);
        else m.put("negative",m.optInt("negative",0)+1);
        for(String f:features(a,b)){
            String key="w_"+safe(f);
            double old=m.optDouble(key,0.0);
            m.put(key,Math.max(-4.0,Math.min(4.0,old+delta)));
        }
        write(m);
        Prediction after=predict(a,b);
        return new TrainingEvent(predicted,actual,predicted==actual,before.probability,delta);
    }

    public synchronized String summary(){
        JSONObject m=read();
        int trials=m.optInt("trials",0), correct=m.optInt("correct",0), errors=m.optInt("errors",0);
        double acc=trials==0?0.0:(double)correct/trials;
        StringBuilder s=new StringBuilder();
        s.append("Модель локального обучения\n");
        s.append("Тип: error-driven binary relation learner\n");
        s.append("Испытаний: ").append(trials).append("\n");
        s.append("Правильных предсказаний: ").append(correct).append("\n");
        s.append("Ошибок: ").append(errors).append("\n");
        s.append("Текущая точность на обучающих парах: ").append(Math.round(acc*100)).append("%\n");
        s.append("Положительных примеров: ").append(m.optInt("positive",0)).append("\n");
        s.append("Отрицательных примеров: ").append(m.optInt("negative",0)).append("\n");
        s.append("Bias: ").append(String.format(Locale.ROOT,"%.3f",m.optDouble("bias",0.0))).append("\n");
        String[] known={"NEGATION_FLIP","NEGATION_PREFIX","NUMBER_MISMATCH","WEAK_CONTEXT"};
        s.append("\nВес признаков:\n");
        for(String f:known)s.append("• ").append(f).append(" = ").append(String.format(Locale.ROOT,"%.3f",m.optDouble("w_"+safe(f),0.0))).append("\n");
        return s.toString();
    }

    public synchronized String transferTest(){
        String[][] train={
            {"датчик активен","датчик неактивен"},
            {"экран яркий","экран неяркий"},
            {"мотор готов","мотор неготов"}
        };
        String[][] negative={
            {"датчик активен","датчик спокоен"},
            {"экран яркий","экран большой"}
        };
        StringBuilder out=new StringBuilder();
        out.append("Фаза 1 — обучение на известных примерах\n");
        for(String[] pair:train){
            TrainingEvent ev=observe(pair[0],pair[1],true);
            out.append("✓ ").append(pair[0]).append(" ↔ ").append(pair[1])
               .append(" | ошибка=").append(!ev.correct).append("\n");
        }
        for(String[] pair:negative){
            TrainingEvent ev=observe(pair[0],pair[1],false);
            out.append("✓ отрицательный пример: ").append(pair[0]).append(" ↔ ").append(pair[1])
               .append(" | ошибка=").append(!ev.correct).append("\n");
        }
        out.append("\nФаза 2 — blind transfer на новых сущностях\n");
        String[][] unseen={
            {"робот активен","робот неактивен"},
            {"сервер стабилен","сервер нестабилен"},
            {"кабель исправен","кабель не исправен"}
        };
        for(String[] pair:unseen){
            Prediction p=predict(pair[0],pair[1]);
            out.append("\n").append(pair[0]).append(" ↔ ").append(pair[1])
               .append("\n→ prediction=").append(p.label)
               .append(", confidence=").append(Math.round(p.probability*100)).append("%")
               .append("\n→ features=").append(p.features);
        }
        return out.toString();
    }

    public synchronized String exportState(){return p.getString(MODEL_KEY,"{}");}
    public synchronized void importState(String json){try{new JSONObject(json);p.edit().putString(MODEL_KEY,json).apply();}catch(Exception ignored){}}
    public synchronized int trials(){return read().optInt("trials",0);}
    public synchronized void reset(){p.edit().remove(MODEL_KEY).apply();}

    private static String safe(String s){return s.replaceAll("[^A-Z0-9_]","_");}

    private static List<String> features(String a,String b){
        String x=norm(a),y=norm(b);
        Set<String> f=new HashSet<>();
        String[] ax=tokens(x), by=tokens(y);

        boolean nx=hasNegation(ax), ny=hasNegation(by);
        if(nx!=ny)f.add("NEGATION_FLIP");
        if((negatedBase(ax,by))||(negatedBase(by,ax)))f.add("NEGATION_PREFIX");

        double numsX=numberValue(x), numsY=numberValue(y);
        if(!Double.isNaN(numsX)&&!Double.isNaN(numsY)&&Math.abs(numsX-numsY)>1e-9&&sharedContext(ax,by)>=0.45)
            f.add("NUMBER_MISMATCH");

        if(f.isEmpty())f.add("WEAK_CONTEXT");
        return new ArrayList<>(f);
    }

    private static String norm(String s){
        return (s==null?"":s).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+"," ").trim();
    }

    private static String[] tokens(String s){return s.isEmpty()?new String[0]:s.split(" ");}

    private static boolean hasNegation(String[] ts){
        for(String t:ts){
            if(t.equals("не")||t.equals("нет")||t.startsWith("не")&&t.length()>3||
               t.startsWith("недоступ")||t.startsWith("невозмож")||t.startsWith("неисправ"))
                return true;
        }
        return false;
    }

    private static boolean negatedBase(String[] a,String[] b){
        Set<String> A=new HashSet<>();
        for(String t:a)if(t.startsWith("не")&&t.length()>3)A.add(t.substring(2));
        for(String t:b)if(A.contains(t))return true;
        return false;
    }

    private static double numberValue(String s){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?<!\\d)(\\d+(?:[.,]\\d+)?)(?!\\d)").matcher(s);
        if(!m.find())return Double.NaN;
        try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return Double.NaN;}
    }

    private static double sharedContext(String[] a,String[] b){
        if(a.length==0||b.length==0)return 0;
        int common=0;
        for(String x:a){
            if(x.length()<3)continue;
            for(String y:b)if(x.equals(y)){common++;break;}
        }
        return (double)common/Math.max(1,Math.max(a.length,b.length));
    }
}
