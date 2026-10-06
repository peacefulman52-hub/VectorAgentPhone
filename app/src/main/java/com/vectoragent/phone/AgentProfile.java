package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public final class AgentProfile {
    public final String id, role, instruction;
    private final SharedPreferences prefs;

    public AgentProfile(Context c, String id, String role, String instruction) {
        this.id=id; this.role=role; this.instruction=instruction;
        prefs=c.getSharedPreferences("vector_agent_profiles", Context.MODE_PRIVATE);
    }

    public synchronized void recordPrediction(boolean correct) {
        int trials=prefs.getInt(id+"_trials",0)+1;
        int correctCount=prefs.getInt(id+"_correct",0)+(correct?1:0);
        prefs.edit().putInt(id+"_trials",trials).putInt(id+"_correct",correctCount).apply();
    }

    public synchronized double accuracy() {
        int t=prefs.getInt(id+"_trials",0);
        return t==0?0.5:(double)prefs.getInt(id+"_correct",0)/t;
    }

    public synchronized void trust(String other, boolean useful) {
        String k=id+"_trust_"+other;
        int n=prefs.getInt(k+"_n",0)+1;
        int good=prefs.getInt(k+"_good",0)+(useful?1:0);
        prefs.edit().putInt(k+"_n",n).putInt(k+"_good",good).apply();
    }

    public synchronized double trustOf(String other) {
        int n=prefs.getInt(id+"_trust_"+other+"_n",0);
        return n==0?0.5:(double)prefs.getInt(id+"_trust_"+other+"_good",0)/n;
    }

    public synchronized String summary() {
        return role+" | trials="+prefs.getInt(id+"_trials",0)+
                " | accuracy="+Math.round(accuracy()*100)+"%";
    }

    public static Map<String,AgentProfile> createDefault(Context c) {
        Map<String,AgentProfile> m=new HashMap<>();
        m.put("researcher",new AgentProfile(c,"researcher","Исследователь",
                "Ищи закономерности и формируй проверяемые гипотезы. Никогда не выдавай гипотезу за факт."));
        m.put("skeptic",new AgentProfile(c,"skeptic","Скептик",
                "Ищи контрпримеры, альтернативные объяснения и слабые места. Не соглашайся без причины."));
        m.put("experimenter",new AgentProfile(c,"experimenter","Экспериментатор",
                "Превращай спор в проверяемое предсказание или эксперимент. Не голосуй за правоту без проверки."));
        m.put("judge",new AgentProfile(c,"judge","Судья",
                "Оценивай доказательства, результаты и историю точности агентов. Учитывай неопределённость."));
        return m;
    }
}