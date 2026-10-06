package com.vectoragent.phone;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vector Lab 3.0 research loop.
 * Real observations are persistent; hypotheses are proposals only.
 * The engine chooses an experiment whose predicted outcomes differ between
 * competing rules and records every cycle in local storage.
 */
public final class ResearchEngine {
    public static final class Result {
        public final String text;
        public Result(String text){ this.text=text; }
    }

    private final Context context;
    private final android.content.SharedPreferences prefs;
    private final ArrayList<JSONObject> cycles = new ArrayList<>();
    private final ArrayList<Double> sequence = new ArrayList<>();
    private static final Pattern NUM = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?");

    public ResearchEngine(Context c){
        context=c.getApplicationContext();
        prefs=context.getSharedPreferences("vector_lab_3",Context.MODE_PRIVATE);
        load();
    }

    public synchronized Result observe(String input){
        String raw=input==null?"":input.trim();
        ArrayList<Double> nums=parse(raw);
        if(nums.isEmpty()) return new Result("Не нашёл числового наблюдения. Дай, например: A=2, B=4, C=6.");
        for(Double n:nums) sequence.add(n);
        if(sequence.size()>12) while(sequence.size()>12) sequence.remove(0);

        ArrayList<Rule> rules=rulesFor(sequence);
        Rule lead=rules.get(0);
        double prediction=lead.predict(sequence);
        String experiment=experimentFor(rules);
        String evidence="Наблюдение принято как REAL: "+format(nums);

        JSONObject cycle=new JSONObject();
        try{
            cycle.put("time",System.currentTimeMillis());
            cycle.put("input",raw);
            cycle.put("observation",evidence);
            cycle.put("hypotheses",rulesJson(rules));
            cycle.put("prediction",prediction);
            cycle.put("experiment",experiment);
            cycle.put("status","OPEN");
            cycles.add(cycle);
            if(cycles.size()>200) cycles.remove(0);
            save();
        }catch(Exception ignored){}

        StringBuilder out=new StringBuilder();
        out.append("🔬 Цикл #").append(cycles.size()).append("\n\n");
        out.append("Наблюдение: ").append(format(nums)).append("\n\n");
        out.append("Конкурирующие гипотезы:\n");
        for(int i=0;i<rules.size();i++){
            Rule r=rules.get(i);
            out.append(i+1).append(". ").append(r.name)
               .append(" → следующий = ").append(fmt(r.predict(sequence)))
               .append(" • confidence ").append(Math.round(r.confidence*100)).append("%\n");
        }
        out.append("\n🎯 Ведущая гипотеза: ").append(lead.name)
           .append("\nПредсказание: ").append(fmt(prediction)).append("\n");
        if(experiment.startsWith("Провести"))
            out.append("\n🧪 Выбранный эксперимент: ").append(experiment).append("\n")
               .append("Он выбран потому, что гипотезы дают разные ожидаемые результаты.");
        else
            out.append("\n🧪 Следующий шаг: ").append(experiment).append("\n");
        out.append("\nПамять циклов: ").append(cycles.size()).append(". Переключение окон её не сбрасывает.");
        return new Result(out.toString());
    }

    public synchronized String history(){
        if(cycles.isEmpty()) return "Циклов пока нет.";
        StringBuilder s=new StringBuilder("ПОСЛЕДНИЕ ИССЛЕДОВАТЕЛЬСКИЕ ЦИКЛЫ\n\n");
        int from=Math.max(0,cycles.size()-12);
        for(int i=from;i<cycles.size();i++){
            JSONObject c=cycles.get(i);
            s.append("#").append(i+1).append("  ")
             .append(c.optString("input")).append("\n")
             .append("→ ").append(c.optString("prediction"))
             .append(" | эксперимент: ").append(c.optString("experiment")).append("\n\n");
        }
        return s.toString();
    }

    public synchronized int cycleCount(){ return cycles.size(); }

    private ArrayList<Double> parse(String s){
        ArrayList<Double> out=new ArrayList<>();
        Matcher m=NUM.matcher(s.replace(',','.'));
        while(m.find()){
            try{out.add(Double.parseDouble(m.group()));}catch(Exception ignored){}
        }
        return out;
    }

    private static final class Rule{
        final String name; final double confidence; final Predictor p;
        Rule(String n,double c,Predictor p){name=n;confidence=c;this.p=p;}
        double predict(ArrayList<Double> a){return p.get(a);}
    }
    private interface Predictor{double get(ArrayList<Double> a);}

    private ArrayList<Rule> rulesFor(ArrayList<Double> a){
        ArrayList<Rule> r=new ArrayList<>();
        r.add(new Rule("H1: сохраняется первый шаг (C + (B − A))",0.82,
                x->x.size()<3?0:x.get(x.size()-1)+(x.get(x.size()-2)-x.get(x.size()-3))));
        r.add(new Rule("H2: сохраняется последний шаг (C + (C − B))",0.78,
                x->x.size()<2?0:x.get(x.size()-1)+(x.get(x.size()-1)-x.get(x.size()-2))));
        r.add(new Rule("H3: следующий = B + C",0.55,
                x->x.size()<2?0:x.get(x.size()-1)+x.get(x.size()-2)));
        r.add(new Rule("H4: следующий = 2C − A",0.48,
                x->x.size()<3?0:2*x.get(x.size()-1)-x.get(x.size()-3)));
        r.add(new Rule("H5: следующий = 2C",0.32,
                x->x.isEmpty()?0:2*x.get(x.size()-1)));
        return r;
    }

    private String experimentFor(ArrayList<Rule> r){
        if(r.size()<3) return "Собрать ещё одно наблюдение.";
        double a=r.get(0).predict(example(4,8,12));
        double b=r.get(2).predict(example(4,8,12));
        double c=r.get(4).predict(example(4,8,12));
        if(Math.abs(a-b)>0.0001 || Math.abs(a-c)>0.0001)
            return "Провести A=4, B=8, C=12 → измерить D. Ожидаемые исходы: H1="+fmt(a)+", H3="+fmt(b)+", H5="+fmt(c)+".";
        return "Нужно новое наблюдение, которое разделит оставшиеся гипотезы.";
    }

    private ArrayList<Double> example(double a,double b,double c){
        ArrayList<Double> x=new ArrayList<>();x.add(a);x.add(b);x.add(c);return x;
    }

    private JSONArray rulesJson(ArrayList<Rule> r)throws Exception{
        JSONArray a=new JSONArray();
        for(Rule x:r){JSONObject o=new JSONObject();o.put("rule",x.name);o.put("confidence",x.confidence);a.put(o);}
        return a;
    }

    private String format(ArrayList<Double> a){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<a.size();i++){if(i>0)s.append(", ");s.append(fmt(a.get(i)));}
        return s.toString();
    }
    private String fmt(double d){
        if(Math.abs(d-Math.rint(d))<0.000001) return Long.toString(Math.round(d));
        return String.format(Locale.US,"%.3f",d);
    }

    private void load(){
        try{
            JSONArray a=new JSONArray(prefs.getString("cycles","[]"));
            for(int i=0;i<a.length();i++)cycles.add(a.getJSONObject(i));
            JSONArray q=new JSONArray(prefs.getString("sequence","[]"));
            for(int i=0;i<q.length();i++)sequence.add(q.getDouble(i));
        }catch(Exception ignored){}
    }
    private void save(){
        try{
            JSONArray a=new JSONArray();for(JSONObject o:cycles)a.put(o);
            JSONArray q=new JSONArray();for(Double d:sequence)q.put(d);
            prefs.edit().putString("cycles",a.toString()).putString("sequence",q.toString()).apply();
        }catch(Exception ignored){}
    }
}
