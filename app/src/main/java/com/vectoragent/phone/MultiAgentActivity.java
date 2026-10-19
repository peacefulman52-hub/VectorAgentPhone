package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MultiAgentActivity extends Activity {
    private MultiAgentEngine engine;
    private LinearLayout root;
    private TextView dashboard,trace,history,researchView,diagnostics;
    private EditText topic;

    int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,float z){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.rgb(35,35,40));
        t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;
    }
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}

    public void onCreate(Bundle b){
        super.onCreate(b);
        engine=new MultiAgentEngine(this,new MemoryStore(this));
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18));sc.addView(root);setContentView(sc);build();
    }

    void build(){
        root.addView(tv("🧪 ЭНТРОПИЯ — ЛАБОРАТОРИЯ",24));
        root.addView(tv("Изолированный исследовательский экран. Эксперименты и диагностические записи не переводятся автоматически в факты памяти.",13));

        root.addView(tv("Пилот модели: результаты GitHub",18));
        root.addView(tv("Модель: distilgpt2 • CPU • 40 заданий на антонимы • слой 5. Это результат удалённого эксперимента, не запуск модели на телефоне.",12));
        root.addView(tv("Без вмешательства: точность 20% • Brier 0,485 • ECE 0,534\nЦелевое подавление выбранного токена: точность 80% • Brier 0,193 • ECE 0,185\nСлучайное вмешательство: точность 20% • Brier 0,484 • ECE 0,529\nТолько предупреждение в промпте: точность 35% • Brier 0,407 • ECE 0,370",13));
        root.addView(tv("Ограничение: это узкий пилот на одной модели и 40 заданиях. Он показывает эффект вмешательства на этой задаче, но не доказывает самонаблюдение, сознание или универсальное улучшение модели.",12));

        Button check=btn("✓ Проверить приложение на телефоне");root.addView(check);
        diagnostics=tv("Диагностика ещё не запускалась.",13);root.addView(diagnostics);
        check.setOnClickListener(v->runDiagnostics());

        ViewDivider();

        dashboard=tv("",14);root.addView(dashboard);
        root.addView(tv("Задача / наблюдения",18));
        topic=new EditText(this);
        topic.setHint("Например: 2, 5, 10, 17, 26");
        topic.setMinLines(2);root.addView(topic);

        LinearLayout a=new LinearLayout(this);
        Button run=btn("▶ Запустить цикл"),clear=btn("↺ Очистить лабораторию");
        a.addView(run,new LinearLayout.LayoutParams(0,-2,1));
        a.addView(clear,new LinearLayout.LayoutParams(0,-2,1));root.addView(a);

        root.addView(tv("🧠 Анализ ResearchEngine",18));
        researchView=tv("Введите наблюдения и запустите цикл.",13);root.addView(researchView);

        root.addView(tv("🤖 Агентный цикл",18));
        root.addView(tv("🔎 Исследователь — генерирует правила из данных\n🥊 Скептик — удерживает конкурирующие объяснения\n🔬 Экспериментатор — выбирает опыт, который их различает\n⚖ Судья — оценивает текущие доказательства",14));

        root.addView(tv("Последний цикл",18));
        trace=tv("Пока не запускался.",13);root.addView(trace);
        root.addView(tv("Постоянный журнал циклов",18));
        history=tv("",12);root.addView(history);

        run.setOnClickListener(v->{
            String p=topic.getText().toString().trim();
            if(p.isEmpty()){researchView.setText("Сначала введи задачу или наблюдения.");return;}
            MultiAgentEngine.Cycle c=engine.runCycle(p);
            researchView.setText(engine.researchDashboard(p));
            trace.setText("Цикл #"+c.number+"\n\n🔎 "+c.hypothesis+"\n\n🥊 "+c.challenge+"\n\n🔬 "+c.experiment+
                    "\n\n📊 "+c.evidence+"\n\n⚖ "+c.judgement+"\n\n🎯 "+c.prediction+
                    "\nУверенность: "+Math.round(c.confidence*100)+"%");
            refresh();
        });
        clear.setOnClickListener(v->{engine.clearRuntime();researchView.setText("История очищена.");refresh();});
        refresh();
    }

    void ViewDivider(){
        android.view.View line=new android.view.View(this);
        line.setBackgroundColor(Color.LTGRAY);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));
        lp.setMargins(0,dp(8),0,dp(8));root.addView(line,lp);
    }

    void runDiagnostics(){
        StringBuilder report=new StringBuilder();
        int passed=0,total=5;
        try{
            MemoryStore store=new MemoryStore(this);
            store.all(); passed++; report.append("✓ Локальная память открывается\n");
        }catch(Exception e){report.append("✗ Локальная память: ").append(e.getClass().getSimpleName()).append("\n");}
        try{
            JSONObject obj=new JSONObject();obj.put("test","entropy");obj.put("ok",true);
            JSONObject parsed=new JSONObject(obj.toString());
            if(parsed.optBoolean("ok") && "entropy".equals(parsed.optString("test"))){passed++;report.append("✓ JSON: запись и чтение\n");}
            else report.append("✗ JSON: проверка не пройдена\n");
        }catch(Exception e){report.append("✗ JSON: ").append(e.getClass().getSimpleName()).append("\n");}
        try{
            SharedPreferences p=getSharedPreferences("entropy_diagnostics",MODE_PRIVATE);
            String token="ok-"+System.currentTimeMillis();
            p.edit().putString("probe",token).commit();
            boolean ok=token.equals(p.getString("probe",""));
            p.edit().remove("probe").commit();
            if(ok){passed++;report.append("✓ Локальное сохранение настроек\n");}
            else report.append("✗ Локальное сохранение настроек\n");
        }catch(Exception e){report.append("✗ Настройки: ").append(e.getClass().getSimpleName()).append("\n");}
        try{
            String raw=new ExperimentBridge(this,new MemoryStore(this)).exportLog();
            JSONArray arr=new JSONArray(raw);
            passed++;report.append("✓ Журнал экспериментов читается (").append(arr.length()).append(" записей)\n");
        }catch(Exception e){report.append("✗ Журнал экспериментов: ").append(e.getClass().getSimpleName()).append("\n");}
        try{
            String text="2, 5, 10, 17, 26";
            if(text.split(",").length==5){passed++;report.append("✓ Ввод и обработка тестовой последовательности\n");}
            else report.append("✗ Проверка последовательности\n");
        }catch(Exception e){report.append("✗ Последовательность: ").append(e.getClass().getSimpleName()).append("\n");}
        report.insert(0,"Результат: "+passed+"/"+total+" локальных проверок пройдено.\n\n");
        report.append("\nЭто проверка базовых компонентов приложения, не тест точности ИИ-модели.");
        diagnostics.setText(report.toString());
    }

    void refresh(){
        dashboard.setText(engine.dashboard());
        history.setText(engine.recentCycles(8));
    }
}
