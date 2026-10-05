package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

public class MultiAgentActivity extends Activity {
    private MultiAgentEngine engine;
    private LinearLayout root;
    private TextView dashboard, trace;
    private EditText topic;

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,float size){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(35,35,40)); t.setPadding(dp(8),dp(7),dp(8),dp(7)); return t;
    }
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        MemoryStore store=new MemoryStore(this);
        engine=new MultiAgentEngine(this,store);
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18));scroll.addView(root);setContentView(scroll);
        build();
    }

    void build(){
        root.addView(tv("🧪 VECTOR LAB",26));
        root.addView(tv("Коллективное обучение: 4 агента → общий мир → сообщения → экспериментальный цикл.",13));

        LinearLayout stats=new LinearLayout(this); stats.setGravity(Gravity.CENTER);
        dashboard=tv("",14); stats.addView(dashboard,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(stats);

        root.addView(tv("Текущая задача",18));
        topic=new EditText(this);
        topic.setHint("Например: найти закономерность в данных");
        topic.setMinLines(2); root.addView(topic);

        LinearLayout actions=new LinearLayout(this);
        Button run=btn("▶ Запустить цикл");
        Button pause=btn("⏸ Пауза");
        Button clear=btn("↺ Сбросить среду");
        actions.addView(run,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(pause,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(clear,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);

        root.addView(tv("Агенты",18));
        root.addView(tv(
                "🔎 Исследователь — строит гипотезы\n"+
                "🥊 Скептик — ищет контрпримеры\n"+
                "🔬 Экспериментатор — предлагает проверку\n"+
                "⚖ Судья — оценивает доказательства",14));

        root.addView(tv("Последний цикл",18));
        trace=tv("Пока не запускался.",13); root.addView(trace);

        run.setOnClickListener(v->{
            MultiAgentEngine.Cycle c=engine.runCycle(topic.getText().toString());
            trace.setText("Цикл #"+c.number+"\n\n"+c.hypothesis+"\n\n"+c.challenge+
                    "\n\n"+c.experiment+"\n\n"+c.judgement);
            refresh();
        });
        pause.setOnClickListener(v->Toast.makeText(this,"Автоматический цикл пока не запущен — текущая версия работает пошагово.",Toast.LENGTH_SHORT).show());
        clear.setOnClickListener(v->{engine.clearRuntime();trace.setText("Среда очищена.");refresh();});
        refresh();
    }

    void refresh(){
        dashboard.setText(engine.dashboard());
    }
}