package com.vectoragent.phone;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public final class MultiAgentEngine {
    public static final class Cycle {
        public final int number;
        public final String hypothesis,challenge,experiment,evidence,judgement,prediction;
        public final double confidence;
        public Cycle(int n,String h,String c,String e,String ev,String j,String p,double x){
            number=n;hypothesis=h;challenge=c;experiment=e;evidence=ev;judgement=j;prediction=p;confidence=x;
        }
    }

    private final Context context;
    private final Map<String,AgentProfile> agents;
    private final AgentBus bus;
    private final WorldState world;
    private final CycleStore cycles;
    private final ResearchEngine research;
    private int cycle;

    public MultiAgentEngine(Context c,MemoryStore m){
        context=c.getApplicationContext();
        agents=AgentProfile.createDefault(context);
        bus=new AgentBus(context);
        world=new WorldState(context);
        cycles=new CycleStore(context);
        research=new ResearchEngine(context);
        cycle=context.getSharedPreferences("vector_lab_runtime",0).getInt("cycle",0);
    }

    public synchronized Cycle runCycle(String topic){
        cycle++;
        context.getSharedPreferences("vector_lab_runtime",0).edit().putInt("cycle",cycle).apply();

        String p=topic==null||topic.trim().isEmpty()?"Найди закономерность в накопленной памяти":topic.trim();
        ResearchEngine.Result r=research.analyze(p);

        String h="Гипотеза: "+r.bestRule+" | прогноз: "+r.prediction;
        bus.send("researcher","skeptic","HYPOTHESIS",h);
        world.addEvent("HYPOTHESIS","researcher",h);

        String c;
        if(r.candidates.size()>1){
            c="Скептик: конкурирующих правил "+r.candidates.size()+". Лучшая гипотеза не принимается окончательно; проверяем различающий эксперимент.";
        }else if(r.candidates.isEmpty()){
            c="Скептик: недостаточно числовых наблюдений для построения проверяемого правила.";
        }else{
            c="Скептик: найденное правило согласуется с наблюдениями, но требует проверки на новом наблюдении.";
        }
        bus.send("skeptic","experimenter","CHALLENGE",c);
        world.addEvent("CHALLENGE","skeptic",c);

        String e="Экспериментатор: "+r.nextExperiment;
        bus.send("experimenter","judge","EXPERIMENT",e);
        world.addEvent("EXPERIMENT","experimenter",e);

        String ev="Наблюдения: "+r.observations+". Независимый эксперимент не подмешан к другим наборам данных.";
        world.addEvent("EVIDENCE","experimenter",ev);

        String j=r.determined
                ?"ПОДТВЕРЖДЕНО: правило полностью согласуется с текущими данными; это рабочая гипотеза, не доказанный универсальный закон."
                :"НЕ ОПРЕДЕЛЕНО: данные допускают несколько объяснений или их недостаточно. Следующий опыт выбран для их различения.";
        bus.send("judge","researcher","JUDGEMENT",j);
        world.addEvent("JUDGEMENT","judge",j);

        double conf=r.confidence;
        cycles.save(cycle,p,h,c,e,ev,j,r.prediction,conf);
        return new Cycle(cycle,h,c,e,ev,j,r.prediction,conf);
    }

    public synchronized String dashboard(){
        StringBuilder s=new StringBuilder("Цикл: ").append(cycle)
                .append("\nСохранено циклов: ").append(cycles.count())
                .append("\nЭксперименты ResearchEngine: ").append(experimentCount())
                .append("\nСобытий World: ").append(world.size())
                .append("\nСообщений Bus: ").append(bus.size()).append("\n\n");
        for(AgentProfile a:agents.values())s.append("• ").append(a.summary()).append("\n");
        return s.toString();
    }

    private int experimentCount(){
        try{
            JSONArray a=new JSONArray(research.history());
            return a.length();
        }catch(Exception e){return 0;}
    }

    public synchronized String recentTrace(int n){
        JSONArray a=bus.recent(n);
        StringBuilder s=new StringBuilder();
        for(int i=0;i<a.length();i++)try{
            JSONObject o=a.getJSONObject(i);
            s.append("\n").append(o.optString("from")).append(" → ").append(o.optString("to"))
             .append(" [").append(o.optString("type")).append("]\n")
             .append(o.optString("payload")).append("\n");
        }catch(Exception ignored){}
        return s.length()==0?"Пока сообщений нет.":s.toString();
    }

    public synchronized String recentCycles(int n){
        StringBuilder s=new StringBuilder();
        for(String x:cycles.recent(n))s.append("\n").append(x).append("\n");
        return s.length()==0?"История циклов пуста.":s.toString();
    }

    public synchronized String researchDashboard(String topic){
        return research.dashboard(topic);
    }

    public synchronized void clearRuntime(){
        bus.clear();
        world.clear();
        research.clearHistory();
        cycle=0;
        context.getSharedPreferences("vector_lab_runtime",0).edit().putInt("cycle",0).apply();
    }
}
