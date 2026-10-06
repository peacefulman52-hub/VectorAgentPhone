package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;

public class MultiAgentActivity extends Activity {
    private MultiAgentEngine engine;
    private LinearLayout root;
    private TextView dashboard,trace,history,researchView;
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
        root.addView(tv("🧪 VECTOR LAB 3.1",26));
        root.addView(tv("Исследовательская лаборатория: наблюдения → кандидаты правил → проверка → различающий эксперимент → verdict. Разные эксперименты не смешиваются.",13));
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

    void refresh(){
        dashboard.setText(engine.dashboard());
        history.setText(engine.recentCycles(8));
    }
}
