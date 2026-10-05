package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.content.Intent;
import android.view.View;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class LabActivity extends Activity {
    MemoryStore store;
    ExperimentBridge bridge;
    ResearchEngine research;
    LinearLayout root;
    TextView stateView, resultView, historyView;
    EditText input;

    int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    TextView tv(String s,int size){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(35,35,40)); t.setPadding(dp(8),dp(7),dp(8),dp(7)); return t;
    }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        store=new MemoryStore(this);
        bridge=new ExperimentBridge(this,store);
        research=new ResearchEngine(this);
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18)); scroll.addView(root); setContentView(scroll);
        build();
    }

    void build(){
        root.addView(tv("🔬 VECTOR LAB 3.0",25));
        root.addView(tv("Постоянный исследовательский контур: наблюдение → конкурирующие гипотезы → выбор информативного эксперимента → новое свидетельство → следующий цикл. Циклы сохраняются на устройстве.",13));

        root.addView(tv("1. Новое наблюдение",18));
        input=new EditText(this);
        input.setHint("Например: A=2, B=4, C=6");
        input.setMinLines(2);
        root.addView(input);

        Button observe=btn("▶ Запустить исследовательский цикл");
        root.addView(observe);
        observe.setOnClickListener(v->{
            String x=input.getText().toString().trim();
            if(x.isEmpty()) return;
            ResearchEngine.Result r=research.observe(x);
            resultView.setText(r.text);
            input.setText("");
            refresh();
        });

        resultView=tv("",14);
        resultView.setBackgroundColor(Color.rgb(247,248,250));
        root.addView(resultView);

        root.addView(tv("2. Память исследователя",18));
        stateView=tv("",13);
        root.addView(stateView);

        Button memory=btn("🧠 Открыть полную память");
        root.addView(memory);
        memory.setOnClickListener(v->showMemory());

        root.addView(tv("3. История циклов",18));
        historyView=tv("",12);
        root.addView(historyView);

        LinearLayout actions=new LinearLayout(this);
        Button refresh=btn("Обновить");
        Button export=btn("Экспорт журнала");
        actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(export,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);
        refresh.setOnClickListener(v->refresh());
        export.setOnClickListener(v->exportLog());

        root.addView(tv("4. Принцип безопасности",18));
        root.addView(tv("Гипотеза и внутреннее предсказание не становятся фактом автоматически. Реальное наблюдение имеет отдельную provenance. Поэтому агент может ошибиться, но не должен сам себе превращать воображение в доказательство.",12));
        refresh();
    }

    void refresh(){
        if(stateView!=null)
            stateView.setText("Циклов: "+research.cycleCount()+
                    "\nACTIVE="+store.count("ACTIVE")+
                    " • CANDIDATE="+store.count("CANDIDATE")+
                    " • CONFLICT="+store.count("CONFLICT")+
                    " • REJECTED="+store.count("REJECTED")+
                    " • HYP="+store.hypotheses().size());
        if(historyView!=null) historyView.setText(research.history());
    }

    void showMemory(){
        root.removeAllViews();
        root.addView(tv("🧠 Память / результаты экспериментов",22));
        root.addView(tv("Эта память не сбрасывается при переключении экранов. Подтверждение и отклонение остаются ручным контуром для фактов.",13));
        for(MemoryStore.Item x:store.all()){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(tv(x.status+" • "+Math.round(x.confidence*100)+"% • v"+x.version+"\n"+x.text,13));
            LinearLayout a=new LinearLayout(this);
            Button yes=btn("✓ Подтвердить"), no=btn("✕ Отклонить");
            a.addView(yes,new LinearLayout.LayoutParams(0,-2,1));
            a.addView(no,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(a); root.addView(row);
            yes.setOnClickListener(v->{store.learn(x.id,true);showMemory();});
            no.setOnClickListener(v->{store.learn(x.id,false);showMemory();});
        }
        Button back=btn("← Назад в исследование");
        root.addView(back);
        back.setOnClickListener(v->recreate());
    }

    void exportLog(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"vector-lab-3-cycles.json");
        startActivityForResult(i,20);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==20 && result==RESULT_OK && data!=null){
            try{
                String raw=bridge.exportLog();
                java.io.OutputStream out=getContentResolver().openOutputStream(data.getData());
                out.write(raw.getBytes(StandardCharsets.UTF_8)); out.close();
                Toast.makeText(this,"Журнал экспортирован",Toast.LENGTH_SHORT).show();
            }catch(Exception e){ Toast.makeText(this,"Ошибка экспорта",Toast.LENGTH_SHORT).show(); }
        }
    }
}
