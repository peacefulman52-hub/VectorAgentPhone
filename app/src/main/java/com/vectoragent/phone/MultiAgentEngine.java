package com.vectoragent.phone;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

public final class MultiAgentEngine {
    public static final class Cycle {
        public final int number;
        public final String hypothesis, challenge, experiment, judgement;
        public Cycle(int n,String h,String c,String e,String j){
            number=n; hypothesis=h; challenge=c; experiment=e; judgement=j;
        }
    }

    private final Context context;
    private final Map<String,AgentProfile> agents;
    private final AgentBus bus;
    private final WorldState world;
    private final MemoryStore memory;
    private int cycle;

    public MultiAgentEngine(Context c, MemoryStore memory){
        context=c.getApplicationContext();
        this.memory=memory;
        agents=AgentProfile.createDefault(context);
        bus=new AgentBus(context);
        world=new WorldState(context);
        cycle=context.getSharedPreferences("vector_lab_runtime",Context.MODE_PRIVATE).getInt("cycle",0);
    }

    public synchronized Cycle runCycle(String topic){
        cycle++;
        context.getSharedPreferences("vector_lab_runtime",Context.MODE_PRIVATE).edit().putInt("cycle",cycle).apply();

        String seed=(topic==null||topic.trim().isEmpty())?
                "Найди закономерность в накопленной памяти":topic.trim();

        String hypothesis="Гипотеза: "+seed;
        bus.send("researcher","skeptic","HYPOTHESIS",hypothesis);
        world.addEvent("HYPOTHESIS","researcher",hypothesis);

        String challenge="Проверить альтернативу и найти контрпример для: "+hypothesis;
        bus.send("skeptic","experimenter","CHALLENGE",challenge);
        world.addEvent("CHALLENGE","skeptic",challenge);

        String experiment="Предложить проверку гипотезы: "+hypothesis;
        bus.send("experimenter","judge","EXPERIMENT",experiment);
        world.addEvent("EXPERIMENT","experimenter",experiment);

        String judgement="Пока нет внешнего результата. Состояние: HYPOTHESIS, требуется наблюдение.";
        bus.send("judge","researcher","JUDGEMENT",judgement);
        world.addEvent("JUDGEMENT","judge",judgement);

        return new Cycle(cycle,hypothesis,challenge,experiment,judgement);
    }

    public synchronized String dashboard(){
        StringBuilder s=new StringBuilder();
        s.append("Цикл: ").append(cycle).append("\n");
        s.append("Событий World: ").append(world.size()).append("\n");
        s.append("Сообщений Bus: ").append(bus.size()).append("\n\n");
        for(AgentProfile a:agents.values()) s.append("• ").append(a.summary()).append("\n");
        return s.toString();
    }

    public synchronized String recentTrace(int max){
        JSONArray a=bus.recent(max);
        StringBuilder s=new StringBuilder();
        for(int i=0;i<a.length();i++) try{
            JSONObject o=a.getJSONObject(i);
            s.append("\n").append(o.optString("from")).append(" → ")
             .append(o.optString("to")).append(" [").append(o.optString("type")).append("]\n")
             .append(o.optString("payload")).append("\n");
        }catch(Exception ignored){}
        return s.length()==0?"Пока сообщений нет.":s.toString();
    }

    public synchronized void clearRuntime(){
        bus.clear(); world.clear();
        cycle=0;
        context.getSharedPreferences("vector_lab_runtime",Context.MODE_PRIVATE).edit().putInt("cycle",0).apply();
    }
}