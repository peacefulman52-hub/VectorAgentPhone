package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Research core: discovers rules from observations instead of selecting fixed templates.
 * Each experiment is isolated by a stable key; observations from different keys are never mixed.
 */
public final class ResearchEngine {
    public static final class Candidate {
        public final String id, rule, prediction;
        public final double score, confidence;
        public final int complexity, errors;
        Candidate(String id,String rule,String prediction,double score,double confidence,int complexity,int errors){
            this.id=id;this.rule=rule;this.prediction=prediction;this.score=score;this.confidence=confidence;
            this.complexity=complexity;this.errors=errors;
        }
    }
    public static final class Result {
        public final String experimentId, observations, bestRule, prediction, nextExperiment;
        public final double confidence;
        public final boolean determined;
        public final List<Candidate> candidates;
        Result(String id,String obs,String best,String pred,String next,double conf,boolean det,List<Candidate> cs){
            experimentId=id;observations=obs;bestRule=best;prediction=pred;nextExperiment=next;confidence=conf;
            determined=det;candidates=cs;
        }
    }

    private static final String PREF="vector_research_engine";
    private static final String SESSIONS="sessions";
    private final SharedPreferences prefs;

    public ResearchEngine(Context c){ prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

    public synchronized Result analyze(String input){
        double[] y=parseNumbers(input);
        String id=experimentId(input);
        List<Candidate> cs=generate(y);
        rank(cs);
        Candidate best=cs.isEmpty()?null:cs.get(0);
        String pred=best==null?"Недостаточно данных":best.prediction;
        double conf=best==null?0.0:best.confidence;
        boolean determined=best!=null && best.errors==0 && y.length>=4 && confidenceMargin(cs)>=0.12;
        String next=designNext(cs,y);
        persist(id,input,y,cs);
        return new Result(id,format(y),best==null?"Нет проверенного правила":best.rule,pred,next,conf,determined,cs);
    }

    public synchronized String dashboard(String input){
        Result r=analyze(input);
        StringBuilder s=new StringBuilder();
        s.append("Эксперимент: ").append(r.experimentId).append("\n");
        s.append("Наблюдения: ").append(r.observations).append("\n\n");
        s.append("Лучшее правило: ").append(r.bestRule).append("\n");
        s.append("Прогноз: ").append(r.prediction).append("\n");
        s.append("Уверенность: ").append(Math.round(r.confidence*100)).append("%").append("\n");
        s.append(r.determined?"Статус: рабочая гипотеза подтверждается данными.":"Статус: однозначного закона пока нет.").append("\n\n");
        s.append("Различающий эксперимент: ").append(r.nextExperiment).append("\n\n");
        int n=Math.min(6,r.candidates.size());
        for(int i=0;i<n;i++){
            Candidate c=r.candidates.get(i);
            s.append("#").append(i+1).append(" ").append(c.rule)
             .append(" | следующее=").append(c.prediction)
             .append(" | fit=").append(Math.round(c.confidence*100)).append("%")
             .append(" | сложность=").append(c.complexity).append("\n");
        }
        return s.toString();
    }

    private String experimentId(String input){
        return "exp-"+Integer.toHexString(normalize(input).hashCode());
    }

    private String normalize(String s){
        return s==null?"":s.toLowerCase(Locale.ROOT).replace(',','.').replaceAll("\\s+"," ").trim();
    }

    private double[] parseNumbers(String s){
        Matcher m=Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?").matcher(s==null?"":s);
        ArrayList<Double> a=new ArrayList<>();
        while(m.find()) try{a.add(Double.parseDouble(m.group().replace(',','.')));}catch(Exception ignored){}
        double[] out=new double[a.size()];
        for(int i=0;i<out.length;i++) out[i]=a.get(i);
        return out;
    }

    private List<Candidate> generate(double[] y){
        ArrayList<Candidate> out=new ArrayList<>();
        if(y.length<2) return out;

        // Linear and polynomial models are generated from the observed points.
        int maxDegree=Math.min(3,y.length-1);
        for(int d=1;d<=maxDegree;d++){
            double[] coef=interpolate(y,d);
            if(coef==null) continue;
            int errors=0;
            double maxErr=0;
            for(int i=0;i<y.length;i++){
                double e=Math.abs(eval(coef,i+1)-y[i]);
                maxErr=Math.max(maxErr,e);
                if(e>1e-7) errors++;
            }
            double next=eval(coef,y.length+1);
            String rule=polynomialRule(coef,d);
            out.add(new Candidate("poly"+d,rule,fmt(next),fitScore(errors,maxErr,d,y.length),0,d,errors));
        }

        // Geometric rule.
        if(y.length>=3 && Math.abs(y[0])>1e-12){
            double r=y[1]/y[0];
            boolean ok=true; double maxErr=0;
            for(int i=1;i<y.length;i++){
                double e=Math.abs(y[i-1]*r-y[i]);
                maxErr=Math.max(maxErr,e);
                if(e>1e-7) ok=false;
            }
            if(ok) out.add(new Candidate("ratio","Умножение на постоянный коэффициент "+fmt(r),fmt(y[y.length-1]*r),
                    fitScore(0,maxErr,2,y.length),0,2,0));
        }

        // Constant second/third differences are represented naturally by polynomial fits,
        // but these human-readable candidates help the agents explain the discovery.
        if(y.length>=3){
            double d1=y[1]-y[0], d2=y[2]-y[1];
            if(Math.abs(d1-d2)<1e-7)
                out.add(new Candidate("arith","Постоянная разность "+fmt(d1),fmt(y[y.length-1]+d1),
                        fitScore(0,0,1,y.length),0,1,0));
        }
        return out;
    }

    private double[] interpolate(double[] y,int degree){
        int n=degree+1;
        if(y.length<n) return null;
        // Newton divided differences on the first degree+1 observations.
        double[] a=new double[n];
        for(int i=0;i<n;i++) a[i]=y[i];
        double[] coef=new double[n];
        coef[0]=a[0];
        for(int level=1;level<n;level++){
            for(int i=0;i<n-level;i++) a[i]=(a[i+1]-a[i])/level;
            coef[level]=a[0];
        }
        // Convert Newton coefficients into an evaluable coefficient array in x powers.
        double[] poly=new double[n]; poly[0]=coef[0];
        double[] basis=new double[]{1};
        for(int k=1;k<n;k++){
            double[] nb=new double[basis.length+1];
            for(int i=0;i<basis.length;i++){ nb[i]+= -k*basis[i]; nb[i+1]+=basis[i]; }
            basis=nb;
            for(int i=0;i<basis.length;i++) poly[i]+=coef[k]*basis[i];
        }
        return poly;
    }

    private double eval(double[] c,double x){
        double s=0;
        for(int i=c.length-1;i>=0;i--) s=s*x+c[i];
        return s;
    }

    private String polynomialRule(double[] c,int d){
        if(d==1){
            return "Линейное правило: a(n) = "+fmt(c[1])+"·n "+signed(c[0]);
        }
        if(d==2){
            return "Квадратичное правило: a(n) = "+fmt(c[2])+"·n² "+signed(c[1])+"·n "+signed(c[0]);
        }
        return "Полиномиальное правило степени "+d;
    }

    private String signed(double x){ return x>=0?"+ "+fmt(x):"- "+fmt(-x); }

    private double fitScore(int errors,double maxErr,int complexity,int n){
        if(errors>0) return Math.max(0.0,0.15-0.03*errors);
        // Exact fit, with a mild Occam penalty. More observations increase evidence.
        return Math.min(0.99,0.52+0.10*Math.min(n,5)-0.08*(complexity-1));
    }

    private void rank(List<Candidate> cs){
        Collections.sort(cs,(a,b)->Double.compare(b.score,a.score));
        if(cs.isEmpty()) return;
        double total=0;
        for(Candidate c:cs) total+=Math.exp(c.score*6);
        for(int i=0;i<cs.size();i++){
            Candidate c=cs.get(i);
            double p=Math.exp(c.score*6)/Math.max(1e-12,total);
            cs.set(i,new Candidate(c.id,c.rule,c.prediction,c.score,p,c.complexity,c.errors));
        }
    }

    private double confidenceMargin(List<Candidate> cs){
        if(cs.size()<2) return cs.isEmpty()?0:cs.get(0).confidence;
        return cs.get(0).confidence-cs.get(1).confidence;
    }

    private String designNext(List<Candidate> cs,double[] y){
        if(cs.isEmpty()) return "Нужно минимум 2 числовых наблюдения.";
        LinkedHashMap<String,Integer> groups=new LinkedHashMap<>();
        for(Candidate c:cs) groups.put(c.prediction,groups.containsKey(c.prediction)?groups.get(c.prediction)+1:1);
        if(groups.size()==1) return "Текущие кандидаты дают один и тот же прогноз; нужен новый независимый эксперимент.";
        String best=cs.get(0).prediction;
        StringBuilder b=new StringBuilder("Измерить следующий элемент n=").append(y.length+1)
                .append(". Кандидаты расходятся: ");
        int shown=0;
        for(Map.Entry<String,Integer> e:groups.entrySet()){
            if(shown++>0)b.append("; ");
            b.append(e.getKey());
            if(shown>=5)break;
        }
        b.append(". Это различит конкурирующие правила.");
        return b.toString();
    }

    private String fmt(double x){
        if(Math.abs(x-Math.rint(x))<1e-9) return Long.toString(Math.round(x));
        return String.format(Locale.US,"%.6f",x).replaceAll("0+$","").replaceAll("\\.$","");
    }

    private String format(double[] y){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<y.length;i++){if(i>0)b.append(", ");b.append(fmt(y[i]));}
        return b.toString();
    }

    private void persist(String id,String input,double[] y,List<Candidate> cs){
        try{
            JSONArray sessions=new JSONArray(prefs.getString(SESSIONS,"[]"));
            JSONObject o=new JSONObject();
            o.put("id",id);o.put("input",input);o.put("observations",format(y));o.put("time",System.currentTimeMillis());
            JSONArray ca=new JSONArray();
            for(Candidate c:cs){
                JSONObject x=new JSONObject();x.put("id",c.id);x.put("rule",c.rule);x.put("prediction",c.prediction);
                x.put("confidence",c.confidence);x.put("complexity",c.complexity);x.put("errors",c.errors);ca.put(x);
            }
            o.put("candidates",ca);sessions.put(o);
            while(sessions.length()>200)sessions.remove(0);
            prefs.edit().putString(SESSIONS,sessions.toString()).apply();
        }catch(Exception ignored){}
    }

    public synchronized String history(){
        return prefs.getString(SESSIONS,"[]");
    }
    public synchronized void clearHistory(){prefs.edit().remove(SESSIONS).apply();}
}
