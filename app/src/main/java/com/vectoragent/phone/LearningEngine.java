package com.vectoragent.phone;

import java.util.List;

/**
 * Автономный цикл обучения.
 *
 * Важно: генератор ответа не является источником знания.
 * Этот слой работает только с MemoryStore и его уже сохранёнными
 * наблюдениями/гипотезами.
 */
public final class LearningEngine {
    private LearningEngine(){}

    public static String run(MemoryStore store){
        if(store==null) return "LearningEngine: store=null";

        int discovered = store.discoverHypotheses();
        List<MemoryStore.Hypothesis> hypotheses = store.hypotheses();
        int active = store.count("ACTIVE");
        int conflicts = store.count("CONFLICT");

        int promoted = 0;
        StringBuilder events = new StringBuilder();

        for(MemoryStore.Hypothesis h : hypotheses){
            if(!"CANDIDATE".equals(h.status)) continue;

            /*
             * Автоматическое закрепление допускается только при наличии
             * повторяемого паттерна. Наличие CONFLICT в базе само по себе
             * не доказывает контрпример именно этой гипотезы, поэтому
             * глобальный conflict-count здесь НЕ используется как причина
             * отклонения. Гипотеза остаётся кандидатом, пока не выполнены
             * её собственные пороги support/tests.
             */
            if(h.support >= 3){
                store.learnHypothesis(h.id,true);
                promoted++;
                events.append("AUTO-HYPOTHESIS-ACTIVE: ")
                      .append(h.id)
                      .append(" support=").append(h.support)
                      .append(" tests=").append(Math.max(0, h.support - 1))
                      .append("\n");
            }
        }

        if(discovered>0)
            events.append("DISCOVERED-HYPOTHESES: ").append(discovered).append("\\n");
        if(promoted>0)
            events.append("PROMOTED-HYPOTHESES: ").append(promoted).append("\\n");

        if(events.length()==0)
            events.append("LEARNING-CYCLE: новых переходов состояния нет; ")
                  .append("active=").append(active)
                  .append(", conflicts=").append(conflicts)
                  .append(", hypotheses=").append(hypotheses.size());

        store.appendLearningLog(events.toString().trim());
        return events.toString().trim();
    }
}
