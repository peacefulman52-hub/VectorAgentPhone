package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Persistent starter research programme and evidence log for Astra Initiative. */
public final class InitiativeResearchAgenda {
    private static final String PREFS = "vector_initiative_agenda";
    private static final String TOPICS = "topics";
    private static final String RESULTS = "results";
    private static final int SCHEMA_VERSION = 1;

    public static final class Topic {
        public final String id, title, question, goal;
        public final boolean completed;
        public final int order;
        public final long updatedAt;
        Topic(String id, String title, String question, String goal, boolean completed, int order, long updatedAt) {
            this.id=id; this.title=title; this.question=question; this.goal=goal;
            this.completed=completed; this.order=order; this.updatedAt=updatedAt;
        }
    }

    private InitiativeResearchAgenda() {}

    private static Context app(Context c) { return c.getApplicationContext(); }
    private static SharedPreferences prefs(Context c) { return app(c).getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static String[][] starterTopics() {
        return new String[][]{
            {"continuity", "Преемственность между чатами",
             "Исследуй лучшие проверяемые способы сохранять контекст долгих задач между разными чатами и сессиями: checkpoints, журнал событий, retrieval, краткие handoff-файлы. Сравни надёжность, приватность, стоимость и риск потери важных решений. Используй актуальные официальные документы и независимые источники.",
             "Результат: предложить минимальный протокол checkpoint и набор тестов на восстановление контекста."},
            {"agent-loop", "Самостоятельный цикл агента",
             "Исследуй, как строить надёжный цикл AI-агента observe → choose goal → plan → act → verify → learn. Какие механизмы предотвращают бесконечные циклы, подтверждение собственных ошибок и пустую активность? Сравни официальные описания агентных фреймворков и опубликованные практические оценки.",
             "Результат: предложить один простой эксперимент, измеряющий реальную инициативу, а не качество объяснений."},
            {"android-background", "Автономная работа на Android",
             "Исследуй актуальные официальные ограничения Android WorkManager, периодических задач, Doze, ограничений батареи и уведомлений. Укажи, что реально гарантируется, что лишь best-effort, и как тестировать после перезагрузки телефона.",
             "Результат: проверить, какие обещания Astra о фоновой работе технически обоснованы, и сформировать чек-лист тестирования."},
            {"research-quality", "Качество веб-исследования",
             "Исследуй методы оценки надёжности веб-исследований AI-агентов: первичные источники, реальная независимость доменов, цитирование на уровне утверждений, устаревшие страницы и противоречивые свидетельства. Предложи защиту от выдуманных источников и ложной независимости.",
             "Результат: выработать явные правила источников и схему хранения evidence без автоматического превращения отчёта в факт."},
            {"evaluation", "Как измерять агентность",
             "Найди и сравни доступные benchmark-подходы для долгих задач и агентности: восстановление после прерывания, самостоятельный выбор следующего шага, проверка результата, отказ от бесполезного действия и перенос на незнакомые задачи. Приоритет — первичные публикации и страницы разработчиков.",
             "Результат: предложить воспроизводимый тестовый набор для Astra и критерии успеха до изменения кода."},
            {"human-values", "Человеческие качества как проверяемые правила",
             "Исследуй, как операционализировать честность об неопределённости, интеллектуальную скромность, заботу о последствиях, любознательность, ответственность за ошибки и аргументированное несогласие в поведении AI-систем. Разделяй измеримое поведение, имитацию общения и неподтверждённые утверждения о сознании.",
             "Результат: предложить тесты поведения с примерами ожидаемых ответов и провалов."},
            {"phone-safety", "Безопасность управления телефоном",
             "Исследуй безопасные паттерны управления браузером Android через AccessibilityService и удалённую очередь команд: ограничение полномочий, подтверждение рискованных действий, STOP, защита от повторных кликов, журнал аудита, минимизация доступа к личным данным.",
             "Результат: сформировать модель угроз Astra Bridge и небольшой набор обязательных защитных тестов."}
        };
    }

    public static synchronized void ensureSeeded(Context context) {
        SharedPreferences p = prefs(context);
        JSONArray current;
        try { current = new JSONArray(p.getString(TOPICS, "[]")); }
        catch (Exception e) { current = new JSONArray(); }
        String[][] seeds = starterTopics();
        boolean changed = false;
        for (int i=0;i<seeds.length;i++) {
            String id = seeds[i][0];
            boolean exists = false;
            for (int j=0;j<current.length();j++) {
                JSONObject item = current.optJSONObject(j);
                if (item != null && id.equals(item.optString("id"))) { exists=true; break; }
            }
            if (!exists) {
                JSONObject o = new JSONObject();
                try {
                    o.put("id", id); o.put("title", seeds[i][1]); o.put("question", seeds[i][2]);
                    o.put("goal", seeds[i][3]); o.put("completed", false); o.put("order", i);
                    o.put("updatedAt", System.currentTimeMillis()); current.put(o); changed=true;
                } catch (Exception ignored) {}
            }
        }
        if (changed || p.getInt("schema_version",0) < SCHEMA_VERSION) {
            p.edit().putString(TOPICS,current.toString()).putInt("schema_version",SCHEMA_VERSION).apply();
        }
    }

    private static Topic toTopic(JSONObject o) {
        return new Topic(o.optString("id"), o.optString("title"), o.optString("question"),
                o.optString("goal"), o.optBoolean("completed"), o.optInt("order"),
                o.optLong("updatedAt"));
    }

    public static synchronized List<Topic> all(Context context) {
        ensureSeeded(context);
        List<Topic> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(context).getString(TOPICS,"[]"));
            for (int i=0;i<a.length();i++) {
                JSONObject o=a.optJSONObject(i);
                if(o!=null) out.add(toTopic(o));
            }
        } catch(Exception ignored) {}
        java.util.Collections.sort(out,(a,b)->Integer.compare(a.order,b.order));
        return out;
    }

    public static synchronized Topic get(Context context, String id) {
        if (id==null || id.isEmpty()) return null;
        for (Topic t : all(context)) if (id.equals(t.id)) return t;
        return null;
    }

    public static synchronized Topic next(Context context) {
        for (Topic t : all(context)) if (!t.completed) return t;
        return null;
    }

    public static synchronized String addCustom(Context context, String question) {
        ensureSeeded(context);
        String q=question==null?"":question.trim();
        if(q.length()<8) return "";
        SharedPreferences p=prefs(context);
        try {
            JSONArray a=new JSONArray(p.getString(TOPICS,"[]"));
            int order=0;
            for(int i=0;i<a.length();i++) order=Math.max(order,a.optJSONObject(i).optInt("order",-1)+1);
            String id="custom-"+Long.toHexString(System.currentTimeMillis());
            JSONObject o=new JSONObject();
            o.put("id",id); o.put("title","Пользовательский вопрос"); o.put("question",q);
            o.put("goal","Ответ должен содержать источники, неопределённости и следующий проверяемый шаг.");
            o.put("completed",false); o.put("order",order); o.put("updatedAt",System.currentTimeMillis());
            a.put(o); p.edit().putString(TOPICS,a.toString()).apply(); return id;
        } catch(Exception e) { return ""; }
    }

    /** Save report and source URLs; only a successfully researched agenda topic is marked complete. */
    public static synchronized void saveResult(Context context, String topicId, String title,
                                               String question, String report, String[] urls) {
        ensureSeeded(context);
        SharedPreferences p=prefs(context);
        long now=System.currentTimeMillis();
        JSONArray safeUrls=new JSONArray();
        if(urls!=null) for(String u:urls) if(u!=null && (u.startsWith("https://") || u.startsWith("http://")) && !contains(safeUrls,u)) safeUrls.put(u);

        try {
            JSONArray history=new JSONArray(p.getString(RESULTS,"[]"));
            JSONObject result=new JSONObject();
            result.put("topicId",topicId==null?"":topicId); result.put("title",title==null?"":title);
            result.put("question",question==null?"":question); result.put("report",report==null?"":report);
            result.put("urls",safeUrls); result.put("createdAt",now);
            history.put(result);
            while(history.length()>50) history.remove(0);
            p.edit().putString(RESULTS,history.toString()).apply();
        } catch(Exception ignored) {}

        // Every useful web report feeds the preliminary knowledge graph, even when its conclusions are weak.
        // This store keeps observations separate and never promotes them to factual MemoryStore.
        ResearchKnowledgeStore.ingestReport(context, title, question, report, urls);

        if(topicId==null || topicId.isEmpty() || report==null || report.trim().isEmpty()) return;
        try {
            JSONArray a=new JSONArray(p.getString(TOPICS,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);
                if(o==null || !topicId.equals(o.optString("id"))) continue;
                o.put("completed",true); o.put("lastReport",report==null?"":report);
                o.put("lastUrls",safeUrls); o.put("updatedAt",now);
                p.edit().putString(TOPICS,a.toString()).apply();
                break;
            }
        } catch(Exception ignored) {}
    }

    public static synchronized boolean hasSuccessfulResearchForQuestion(Context context, String question) {
        if (question == null || question.trim().isEmpty()) return false;
        ensureSeeded(context);
        try {
            JSONArray a=new JSONArray(prefs(context).getString(RESULTS,"[]"));
            for(int i=a.length()-1;i>=0;i--) {
                JSONObject o=a.optJSONObject(i);
                if(o!=null && question.trim().equals(o.optString("question").trim())
                        && !o.optString("report").trim().isEmpty()) return true;
            }
        } catch(Exception ignored) {}
        return false;
    }

    public static synchronized String latestResultText(Context context) {
        ensureSeeded(context);
        try {
            JSONArray a=new JSONArray(prefs(context).getString(RESULTS,"[]"));
            if(a.length()==0) return "Исследований пока нет. Первый вопрос уже подготовлен в программе.";
            JSONObject o=a.getJSONObject(a.length()-1);
            StringBuilder s=new StringBuilder();
            s.append("Последнее исследование: ").append(o.optString("title")).append("\n\n");
            s.append(o.optString("report",""));
            JSONArray urls=o.optJSONArray("urls");
            if(urls!=null && urls.length()>0){
                s.append("\n\nИсточники:");
                for(int i=0;i<urls.length();i++) s.append("\n• ").append(urls.optString(i));
            } else s.append("\n\nURL источников не удалось извлечь из ответа. Проверь цитаты в отчёте.");
            return s.toString();
        } catch(Exception e) { return "Не удалось прочитать журнал исследований."; }
    }

    public static synchronized String listText(Context context) {
        List<Topic> topics=all(context);
        StringBuilder s=new StringBuilder("СТАРТОВАЯ ПРОГРАММА ИССЛЕДОВАНИЙ\n");
        int done=0;
        for(Topic t:topics) if(t.completed) done++;
        s.append("Завершено: ").append(done).append("/").append(topics.size()).append("\n\n");
        for(Topic t:topics) {
            s.append(t.completed ? "✓ " : "○ ").append(t.title).append("\n");
            s.append("   ").append(t.goal).append("\n\n");
        }
        return s.toString();
    }

    private static boolean contains(JSONArray a,String value) {
        for(int i=0;i<a.length();i++) if(value.equals(a.optString(i))) return true;
        return false;
    }
}
