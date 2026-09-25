package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainActivity extends Activity {
    MemoryStore store; LinearLayout root,list; EditText input,source,prov,relation; SeekBar confidence; TextView confLabel,stats; Switch auto;
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new MemoryStore(this);build();refresh();}
    void build(){
        ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(16));scroll.addView(root);setContentView(scroll);
        TextView title=tv("VECTOR AGENT PHONE",24);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
        root.addView(tv("v0.4 • provenance • relations • versioned memory",13));
        input=new EditText(this);input.setHint("RAW INPUT — наблюдение или утверждение");input.setMinLines(3);input.setGravity(48);root.addView(input,new LinearLayout.LayoutParams(-1,dp(95)));
        source=new EditText(this);source.setHint("Источник / откуда получено");root.addView(source);
        prov=new EditText(this);prov.setHint("Provenance — как подтверждено / ограничение");root.addView(prov);
        relation=new EditText(this);relation.setHint("Связь: поддерживает / уточняет / опровергает / ...");root.addView(relation);
        confLabel=tv("Уверенность: 70%",14);root.addView(confLabel);confidence=new SeekBar(this);confidence.setMax(100);confidence.setProgress((int)(store.threshold()*100));confidence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){confLabel.setText("Уверенность: "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){store.setThreshold(s.getProgress()/100.0);}});root.addView(confidence);
        auto=new Switch(this);auto.setText("Автопереход CANDIDATE → ACTIVE при пороге");auto.setChecked(store.auto());auto.setOnCheckedChangeListener((v,c)->store.setAuto(c));root.addView(auto);
        Button add=btn("СОХРАНИТЬ В ПАМЯТЬ");add.setOnClickListener(v->{String s=input.getText().toString().trim();if(s.isEmpty())return;String conflict=store.add(s,confidence.getProgress()/100.0,auto.isChecked(),source.getText().toString().trim(),prov.getText().toString().trim(),relation.getText().toString().trim());input.setText("");source.setText("");prov.setText("");relation.setText("");toast(conflict.isEmpty()?"Сохранено":"Обнаружено возможное противоречие — ничего не перезаписано");refresh();});root.addView(add);
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);Button snap=btn("Snapshot"),roll=btn("Rollback"),exp=btn("Export"),imp=btn("Import");for(Button b:new Button[]{snap,roll,exp,imp})tools.addView(b,new LinearLayout.LayoutParams(0,-2,1));root.addView(tools);
        snap.setOnClickListener(v->{store.snapshot();toast("Snapshot сохранён");});roll.setOnClickListener(v->{toast(store.rollback()?"Rollback выполнен":"Snapshot отсутствует");refresh();});
        exp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"vector-memory-v0.3.json");startActivityForResult(i,10);});
        imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,11);});
        root.addView(tv("TEST LAB",18));Button test=btn("ТЕСТ: добавить два потенциально противоречивых факта");test.setOnClickListener(v->{store.add("Вода кипит при 100 C",.90,true,"TEST","synthetic test case","supports baseline");store.add("Вода не кипит при 100 C",.90,true,"TEST","synthetic contradiction case","contradicts baseline");refresh();toast("Тест добавлен: проверь CONFLICT без перезаписи");});root.addView(test);
        stats=tv("",13);root.addView(stats);root.addView(tv("ПАМЯТЬ",18));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
    }
    void refresh(){if(list==null)return;list.removeAllViews();stats.setText("CANDIDATE: "+store.count("CANDIDATE")+"  ACTIVE: "+store.count("ACTIVE")+"  CONFLICT: "+store.count("CONFLICT")+"  REJECTED: "+store.count("REJECTED"));List<MemoryStore.Item> items=store.all();if(items.isEmpty()){list.addView(tv("Пока пусто.",14));return;}for(MemoryStore.Item x:items){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);TextView h=tv(x.status+"  •  "+Math.round(x.confidence*100)+"%  • v"+x.version,15);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(h);row.addView(tv(x.text,15));row.addView(tv("source: "+x.source,11));if(!x.provenance.isEmpty())row.addView(tv("provenance: "+x.provenance,11));if(!x.relation.isEmpty())row.addView(tv("relation: "+x.relation,11));if(x.status.equals("CANDIDATE")||x.status.equals("CONFLICT")){LinearLayout a=new LinearLayout(this);Button yes=btn("ACTIVE"),no=btn("REJECTED");a.addView(yes,new LinearLayout.LayoutParams(0,-2,1));a.addView(no,new LinearLayout.LayoutParams(0,-2,1));yes.setOnClickListener(v->{store.setStatus(x.id,"ACTIVE");refresh();});no.setOnClickListener(v->{store.setStatus(x.id,"REJECTED");refresh();});row.addView(a);}list.addView(row);View line=new View(this);line.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));list.addView(line);}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;try{Uri u=data.getData();if(req==10){java.io.OutputStream out=getContentResolver().openOutputStream(u);out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));out.close();toast("Export готов");}else if(req==11){InputStream in=getContentResolver().openInputStream(u);java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)buf.write(b,0,n);in.close();store.importJson(new String(buf.toByteArray(),StandardCharsets.UTF_8));refresh();toast("Import готов");}}catch(Exception e){toast("Ошибка файла");}}
}