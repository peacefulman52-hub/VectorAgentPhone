package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public class LabActivity extends Activity {
    MemoryStore store;
    ExperimentBridge bridge;
    LinearLayout root;
    TextView stateView, logView;
    EditText input;
    SeekBar confidence;

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
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18)); scroll.addView(root); setContentView(scroll);
        build();
    }

    void build(){
        root.addView(tv("🧪 ЛАБОРАТОРИЯ v0.9.1",24));
        root.addView(tv("Экспериментальный контур: ввод → MemoryStore → изменение состояния → журнал BEFORE/AFTER.\n\nBridge не пишет знания напрямую: он вызывает только публичные операции агента.",13));

        root.addView(tv("1. Подать наблюдение",18));
        input=new EditText(this); input.setHint("Например: Вода кипит при 100 °C"); input.setMinLines(2);
        root.addView(input);

        LinearLayout row=new LinearLayout(this);
        confidence=new SeekBar(this); confidence.setMax(100); confidence.setProgress(70);
        row.addView(confidence,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView cl=tv("70%",14); row.addView(cl,new LinearLayout.LayoutParams(dp(55),-2));
        confidence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean f){cl.setText(p+"%");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        root.addView(row);

        Button ingest=btn("➤ Передать агенту");
        root.addView(ingest);
        ingest.setOnClickListener(v->{
            String x=input.getText().toString().trim(); if(x.isEmpty()) return;
            String id=bridge.runIngest(x,confidence.getProgress()/100.0);
            input.setText("");
            refresh("INGEST "+id);
        });

        root.addView(tv("2. Состояние памяти",18));
        stateView=tv("",12); root.addView(stateView);
        Button snap=btn("📸 Снять Snapshot"); root.addView(snap);
        snap.setOnClickListener(v->{store.snapshot(); refresh("SNAPSHOT");});

        root.addView(tv("3. Сигнал обучения",18));
        root.addView(tv("После конфликта выбери ACTIVE для подтверждаемого утверждения. Второе конфликтующее утверждение перейдёт в REJECTED.",12));
        LinearLayout actions=new LinearLayout(this);
        Button refresh=btn("Обновить"); Button exp=btn("Экспорт журнала"); Button clear=btn("Очистить журнал");
        actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(exp,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(clear,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);
        refresh.setOnClickListener(v->refresh("REFRESH"));
        exp.setOnClickListener(v->exportLog());
        clear.setOnClickListener(v->{bridge.clearLog(); refresh("LOG CLEARED");});

        root.addView(tv("Записи памяти — нажми ACTIVE или REJECTED",16));
        renderItems();

        root.addView(tv("4. Журнал эксперимента",18));
        logView=tv("",11); root.addView(logView);
        refresh("READY");
    }

    void renderItems(){
        for(MemoryStore.Item x:store.all()){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.addView(tv(x.status+"  "+Math.round(x.confidence*100)+"%\n"+x.text+"\nID: "+x.id,12));
            LinearLayout buttons=new LinearLayout(this);
            Button yes=btn("✓ ACTIVE"); Button no=btn("✕ REJECTED");
            buttons.addView(yes,new LinearLayout.LayoutParams(0,-2,1));
            buttons.addView(no,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(buttons); root.addView(row);
            yes.setOnClickListener(v->{bridge.runLearn(x.id,true); rebuild();});
            no.setOnClickListener(v->{bridge.runLearn(x.id,false); rebuild();});
        }
    }

    void rebuild(){ recreate(); }

    void refresh(String event){
        if(stateView!=null){
            stateView.setText("ACTIVE="+store.count("ACTIVE")+"   CANDIDATE="+store.count("CANDIDATE")+
                    "   CONFLICT="+store.count("CONFLICT")+"   REJECTED="+store.count("REJECTED")+
                    "\n\n"+store.exportJson());
        }
        if(logView!=null){
            String raw=bridge.exportLog();
            try{
                JSONArray a=new JSONArray(raw);
                StringBuilder s=new StringBuilder("events="+a.length()+"\n");
                int start=Math.max(0,a.length()-5);
                for(int i=start;i<a.length();i++){
                    JSONObject o=a.getJSONObject(i);
                    s.append("\n#").append(i+1).append(" ").append(o.optString("type"))
                     .append(" ").append(o.optString("id")).append("\n");
                    if("INGEST".equals(o.optString("type"))){
                        s.append("input: ").append(o.optString("input")).append("\n")
                         .append("added=").append(o.optInt("added")).append(", conflicts=").append(o.optInt("conflicts"))
                         .append(", similar=").append(o.optInt("similar")).append("\n");
                    } else {
                        s.append("memoryId=").append(o.optString("memoryId"))
                         .append(", accepted=").append(o.optBoolean("accepted")).append("\n");
                    }
                }
                logView.setText(s.toString());
            }catch(Exception e){logView.setText(raw);}
        }
    }

    void exportLog(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE,"vector-experiment-log-v0.9.1.json");
        startActivityForResult(i,20);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==20 && result==RESULT_OK && data!=null){
            try{
                java.io.OutputStream out=getContentResolver().openOutputStream(data.getData());
                out.write(bridge.exportLog().getBytes(StandardCharsets.UTF_8)); out.close();
                Toast.makeText(this,"Журнал экспортирован",Toast.LENGTH_SHORT).show();
            }catch(Exception e){ Toast.makeText(this,"Ошибка экспорта",Toast.LENGTH_SHORT).show(); }
        }
    }
}