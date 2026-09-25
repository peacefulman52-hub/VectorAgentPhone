package com.vectoragent.phone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AgentEngine {
    private final MemoryStore store;

    public AgentEngine(MemoryStore store){ this.store=store; }

    public String respond(String input){
        String q=input==null?"":input.trim();
        if(q.isEmpty()) return "Скажи, что проверить или чему меня научить.";
        String n=q.toLowerCase(Locale.ROOT);
        List<MemoryStore.Item> hits=new ArrayList<>();
        for(MemoryStore.Item x:store.all()){
            if("REJECTED".equals(x.status)) continue;
            int score=score(n,x.text.toLowerCase(Locale.ROOT));
            if(score>0) hits.add(x);
        }

        if(n.matches("^(привет|здравствуйте|хай|добрый день|добрый вечер).*"))
            return "Привет. Я локальный экспериментальный агент. Могу запоминать утверждения, сравнивать их с накопленным знанием, фиксировать конфликты и менять состояние памяти. Попробуй дать мне факт или два противоречащих факта.";

        if(n.contains("что ты знаешь") || n.contains("что ты помнишь"))
            return summary();

        if(hits.isEmpty())
            return "В моей накопленной памяти сейчас нет достаточно близкого знания по этому вопросу. Я не буду выдавать догадку за факт. Если дашь наблюдение или источник, я сохраню его как кандидата и сравню с уже накопленным.";

        boolean conflict=false;
        StringBuilder out=new StringBuilder();
        out.append("Я сверил вопрос с накопленной памятью.\n\n");
        for(MemoryStore.Item x:hits){
            if("CONFLICT".equals(x.status)) conflict=true;
            out.append(marker(x.status)).append(" ").append(x.text)
               .append(" — ").append(x.status)
               .append(", confidence=").append(Math.round(x.confidence*100)).append("%")
               .append(", support=").append(x.support).append("\n");
        }
        if(conflict)
            out.append("\n⚠️ По этому вопросу в памяти есть конфликт. Я не выбираю одну версию автоматически: сначала нужно дополнительное свидетельство или подтверждение пользователя.");
        else if(hits.get(0).status.equals("ACTIVE"))
            out.append("\nВывод по памяти: выше есть ACTIVE-знание. Это состояние накопленной базы, а не гарантия абсолютной истины.");
        else
            out.append("\nВывод по памяти: сведения пока находятся на уровне кандидатов; уверенность недостаточна для автоматического утверждения.");
        return out.toString();
    }

    private int score(String q,String t){
        String[] ws=q.replaceAll("[^\\p{L}\\p{Nd} ]"," ").split("\\s+");
        int s=0;
        for(String w:ws){
            if(w.length()<3) continue;
            if(t.contains(w)) s++;
        }
        return s;
    }

    private String marker(String status){
        if("ACTIVE".equals(status)) return "✓";
        if("CONFLICT".equals(status)) return "⚠";
        return "?";
    }

    private String summary(){
        int a=store.count("ACTIVE"), c=store.count("CANDIDATE"), x=store.count("CONFLICT"), r=store.count("REJECTED");
        return "Сейчас моя локальная база содержит:\n✓ ACTIVE: "+a+
               "\n? CANDIDATE: "+c+
               "\n⚠ CONFLICT: "+x+
               "\n✕ REJECTED: "+r+
               "\n🧩 Гипотез: "+store.hypotheses().size()+
               "\n\nЯ отвечаю из этого состояния, а не из отдельного генератора красивого текста.";
    }
}