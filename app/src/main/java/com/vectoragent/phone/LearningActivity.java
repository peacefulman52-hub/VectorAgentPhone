package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;

public class LearningActivity extends Activity {
    LearningEngine learner; ExplorationEngine explorer;
    LinearLayout root;
    TextView output, explorationOutput;

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.rgb(35,35,40));t.setPadding(dp(8),dp(8),dp(8),dp(8));return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        learner=new LearningEngine(this); explorer=new ExplorationEngine(this);
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18));sc.addView(root);setContentView(sc);
        build();
    }

    void build(){
        root.addView(tv("🧬 САМОСТОЯТЕЛЬНОЕ ОБУЧЕНИЕ v1.5",24));
        root.addView(tv("Это отдельный локальный обучаемый слой. Он не генерирует знания: он получает пары наблюдений, делает предсказание, сравнивает его с фактом и меняет веса при ошибке.",13));

        Button test=btn("▶ Запустить полный тест: обучение → ошибка → blind transfer");
        root.addView(test);
        test.setOnClickListener(v->{output.setText(learner.transferTest());refresh();});

        Button fresh=btn("↻ Сбросить модель");
        root.addView(fresh);
        fresh.setOnClickListener(v->{learner.reset();output.setText("Модель сброшена.");refresh();});

        root.addView(tv("🌪 Энтропийная лаборатория",18));
        root.addView(tv("Генератор берёт ACTIVE-знания как семена и создаёт синтетические вариации: отрицание, числовой сдвиг, перестановку слов и неопределённость. Эти записи сохраняются отдельно от фактов и не становятся независимыми источниками.",13));
        Button explore=btn("🎲 Создать 20 исследовательских вариаций");
        root.addView(explore);
        explorationOutput=tv("",12); explorationOutput.setBackground(bg()); root.addView(explorationOutput);
        explore.setOnClickListener(v->{int n=explorer.runBatch(new MemoryStore(this),learner,20); renderExploration(n); refresh();});

        root.addView(tv("Состояние обучаемой модели",18));
        output=tv(learner.summary(),13);output.setBackground(bg());root.addView(output);

        root.addView(tv("Что именно теперь обучается",18));
        root.addView(tv(
            "1) Prediction — модель заранее оценивает отношение двух новых наблюдений.\n"+
            "2) Error-driven update — после получения истинного отношения веса признаков меняются только при ошибке.\n"+
            "3) Persistent state — веса сохраняются между запусками приложения.\n"+
            "4) Blind transfer — структурное правило переносится на новые сущности, которых не было в обучении.\n"+
            "5) Model ≠ memory — обучаемые веса хранятся отдельно от MemoryStore.",
            13));
    }

    android.graphics.drawable.GradientDrawable bg(){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.rgb(247,247,247));g.setCornerRadius(dp(16));return g;
    }

    void renderExploration(int n){
        StringBuilder s=new StringBuilder("Создано синтетических опытов: ").append(n).append("\n\n");
        java.util.List<ExplorationEngine.Proposal> ps=explorer.recent(10);
        for(ExplorationEngine.Proposal p:ps){
            s.append("• ").append(p.operator).append(" | expected=").append(p.expected)
             .append(" | predicted=").append(p.predicted).append(" ")
             .append(Math.round(p.probability*100)).append("%\n")
             .append("  ").append(p.seedText).append("\n")
             .append("  ↳ ").append(p.text).append("\n");
        }
        explorationOutput.setText(s.toString());
    }
    void refresh(){output.setText(learner.summary());}
}
