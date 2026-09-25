package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainActivity extends Activity {
    MemoryStore store; LinearLayout root,chat,list; EditText input,source,prov,relation; SeekBar confidence; TextView confLabel,stats; Switch auto;
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new MemoryStore(this);build();showChat();}
    void build(){
        ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(10),dp(12),dp(16));scroll.addView(root);setContentView(scroll);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("VECTOR AGENT",24);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button memBtn=btn("Память");memBtn.setOnClickListener(v->showMemory());head.addView(memBtn);root.addView(head);
        root.addView(tv("v0.5 • auto-ingest • contradiction engine • feedback learning",12));
        chat=new LinearLayout(this);chat.setOrientation(LinearLayout.VERTICAL);root.addView(chat);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        input=new EditText(this);input.setHint("Напиши агенту… факты, наблюдения, вопросы");input.setMinLines(2);input.setGravity(48);
        source=new EditText(this);source.setHint("Источник (необязательно)");prov=new EditText(this);prov.setHint("Provenance / ограничение (необязательно)");relation=new EditText(this);relation.setHint("Связь (необязательно)");
        confLabel=tv("Уверенность новых фактов: 70%",13);confidence=new SeekBar(this);confidence.setMax(100);confidence.setProgress((int)(store.threshold()*100));
        confidence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){confLabel.setText("Уверенность новых фактов: "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){store.setThreshold(s.getProgress()/100.0);}});
        auto=new Switch(this);auto.setText("Автоматически активировать подтверждённые факты");auto.setChecked(store.auto());auto.setOnCheckedChangeListener((v,c)->store.setAuto(c));
    }
    void showChat(){
        chat.removeAllViews();chat.addView(tv("Я не языковая модель: внутри телефона сейчас локальный агент памяти. Он выделяет простые утверждения, сохраняет их, сравнивает с памятью и учится на твоём подтверждении/отклонении.",14));
        chat.addView(tv("Можно написать сразу несколько фактов — по одному предложению или строке.",13));chat.addView(input,new LinearLayout.LayoutParams(-1,dp(90)));
        Button send=btn("ОТПРАВИТЬ АГЕНТУ");send.setOnClickListener(v->sendToAgent());chat.addView(send);chat.addView(tv("Последний тест",17));
        chat.addView(tv("Для проверки: «Вода кипит при 100 C» → затем «Вода не кипит при 100 C». Агент должен сохранить оба и пометить пару CONFLICT.",13));
    }
    void sendToAgent(){
        String raw=input.getText().toString().trim();if(raw.isEmpty())return;addBubble("Ты",raw);
        MemoryStore.IngestResult r=store.ingest(raw,confidence.getProgress()/100.0);StringBuilder a=new StringBuilder();
        if(r.added==0)a.append("Я не нашёл утверждений для памяти. Если это вопрос — используй его как тест агента.");
        else{a.append("Принял утверждений: ").append(r.added).append(". Сравнение выполнено автоматически.");if(r.conflicts>0){a.append("\n\nОбнаружено конфликтов: ").append(r.conflicts);for(String m:r.messages)a.append("\n• ").append(m);}else a.append("\nКонфликтов с текущей памятью не обнаружено.");a.append("\n\nОткрой «Память» и подтверди или отклони факты. Это изменит их статус, уверенность и запишет learning signal.");}
        addBubble("Агент",a.toString());input.setText("");
    }
    void addBubble(String who,String text){TextView t=tv(who+"\n"+text,14);t.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));chat.addView(t,lp);}
    void showMemory(){
        chat.removeAllViews();chat.addView(tv("ПАМЯТЬ И ЭКСПЕРИМЕНТ",20));stats=tv("",13);chat.addView(stats);
        LinearLayout tools=new LinearLayout(this);Button snap=btn("Snapshot"),roll=btn("Rollback"),exp=btn("Export"),imp=btn("Import");for(Button b:new Button[]{snap,roll,exp,imp})tools.addView(b,new LinearLayout.LayoutParams(0,-2,1));chat.addView(tools);
        snap.setOnClickListener(v->{store.snapshot();toast("Snapshot сохранён");});roll.setOnClickListener(v->{toast(store.rollback()?"Rollback выполнен":"Snapshot отсутствует");refresh();});
        exp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"vector-memory-v0.5.json");startActivityForResult(i,10);});
        imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,11);});
        chat.addView(tv("Добавить факт вручную",16));chat.addView(input,new LinearLayout.LayoutParams(-1,dp(75)));chat.addView(source);chat.addView(prov);chat.addView(relation);chat.addView(confLabel);chat.addView(confidence);chat.addView(auto);
        Button add=btn("СОХРАНИТЬ В ПАМЯТЬ");add.setOnClickListener(v->{String s=input.getText().toString().trim();if(s.isEmpty())return;String c=store.add(s,confidence.getProgress()/100.0,auto.isChecked(),source.getText().toString().trim(),prov.getText().toString().trim(),relation.getText().toString().trim());input.setText("");source.setText("");prov.setText("");relation.setText("");toast(c.isEmpty()?"Сохранено":"Обнаружен конфликт — оба факта сохранены");refresh();});chat.addView(add);
        chat.addView(tv("TEST LAB",16));Button test=btn("ТЕСТ 6: добавить пару про воду");test.setOnClickListener(v->{store.add("Вода кипит при 100 C",.90,true,"TEST","synthetic test case","supports baseline");store.add("Вода не кипит при 100 C",.90,true,"TEST","synthetic contradiction case","contradicts baseline");refresh();toast("Тест 6 добавлен");});chat.addView(test);
        chat.addView(tv("Факты",18));chat.addView(list);refresh();
    }
    void refresh(){
        if(list==null||stats==null)return;list.removeAllViews();stats.setText("CANDIDATE: "+store.count("CANDIDATE")+"   ACTIVE: "+store.count("ACTIVE")+"   CONFLICT: "+store.count("CONFLICT")+"   REJECTED: "+store.count("REJECTED"));
        List<MemoryStore.Item> items=store.all();if(items.isEmpty()){list.addView(tv("Пока пусто.",14));return;}
        for(MemoryStore.Item x:items){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);TextView h=tv(x.status+"  •  "+Math.round(x.confidence*100)+"%  • v"+x.version,15);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(h);row.addView(tv(x.text,15));row.addView(tv("source: "+x.source,11));if(!x.provenance.isEmpty())row.addView(tv("provenance: "+x.provenance,11));if(!x.relation.isEmpty())row.addView(tv("relation: "+x.relation,11));
            LinearLayout a=new LinearLayout(this);Button yes=btn("✓ ПОДТВЕРДИТЬ"),no=btn("✕ ОТКЛОНИТЬ");a.addView(yes,new LinearLayout.LayoutParams(0,-2,1));a.addView(no,new LinearLayout.LayoutParams(0,-2,1));yes.setOnClickListener(v->{store.learn(x.id,true);refresh();});no.setOnClickListener(v->{store.learn(x.id,false);refresh();});row.addView(a);list.addView(row);View line=new View(this);line.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));list.addView(line);}
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;try{Uri u=data.getData();if(req==10){java.io.OutputStream out=getContentResolver().openOutputStream(u);out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));out.close();toast("Export готов");}else if(req==11){InputStream in=getContentResolver().openInputStream(u);java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)buf.write(b,0,n);in.close();store.importJson(new String(buf.toByteArray(),StandardCharsets.UTF_8));showMemory();toast("Import готов");}}catch(Exception e){toast("Ошибка файла");}}
}