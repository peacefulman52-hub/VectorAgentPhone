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
    MemoryStore store; LinearLayout root, list; EditText input; SeekBar confidence; TextView confLabel; Switch auto;
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(dp(4),dp(6),dp(4),dp(6));return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new MemoryStore(this);build();refresh();}
    void build(){
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(16));scroll.addView(root);setContentView(scroll);
        TextView title=tv("VECTOR AGENT PHONE",24);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
        root.addView(tv("v0.2 • controlled memory • offline by default",13));
        input=new EditText(this);input.setHint("RAW INPUT — наблюдение или утверждение");input.setMinLines(3);input.setGravity(48);root.addView(input,new LinearLayout.LayoutParams(-1,dp(100)));
        confLabel=tv("Уверенность: 70%",14);root.addView(confLabel);confidence=new SeekBar(this);confidence.setMax(100);confidence.setProgress((int)(store.threshold()*100));confidence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){confLabel.setText("Уверенность: "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});root.addView(confidence);
        auto=new Switch(this);auto.setText("Автопереход CANDIDATE → ACTIVE при пороге");auto.setChecked(store.auto());auto.setOnCheckedChangeListener((v,c)->store.setAuto(c));root.addView(auto);
        Button add=btn("СОХРАНИТЬ RAW / CANDIDATE");add.setOnClickListener(v->{String s=input.getText().toString().trim();if(s.isEmpty())return;store.add(s,confidence.getProgress()/100.0,auto.isChecked());input.setText("");refresh();});root.addView(add);
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);Button snap=btn("Snapshot"),roll=btn("Rollback"),exp=btn("Export"),imp=btn("Import");tools.addView(snap,new LinearLayout.LayoutParams(0,-2,1));tools.addView(roll,new LinearLayout.LayoutParams(0,-2,1));tools.addView(exp,new LinearLayout.LayoutParams(0,-2,1));tools.addView(imp,new LinearLayout.LayoutParams(0,-2,1));root.addView(tools);
        snap.setOnClickListener(v->{store.snapshot();toast("Snapshot сохранён");});roll.setOnClickListener(v->{toast(store.rollback()?"Rollback выполнен":"Snapshot отсутствует");refresh();});exp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"vector-memory.json");startActivityForResult(i,10);});imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,11);});
        root.addView(tv("ПАМЯТЬ",18));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
    }
    void refresh(){if(list==null)return;list.removeAllViews();List<MemoryStore.Item> items=store.all();if(items.isEmpty()){list.addView(tv("Пока пусто. Сначала сохрани RAW INPUT.",14));return;}for(MemoryStore.Item x:items){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);TextView h=tv(x.status+"  •  "+Math.round(x.confidence*100)+"%",15);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(h);row.addView(tv(x.text,15));TextView src=tv("source: "+x.source,11);row.addView(src);if(x.status.equals("CANDIDATE")){LinearLayout a=new LinearLayout(this);Button yes=btn("ACTIVE"),no=btn("REJECTED");a.addView(yes,new LinearLayout.LayoutParams(0,-2,1));a.addView(no,new LinearLayout.LayoutParams(0,-2,1));yes.setOnClickListener(v->{store.setStatus(x.id,"ACTIVE");refresh();});no.setOnClickListener(v->{store.setStatus(x.id,"REJECTED");refresh();});row.addView(a);}list.addView(row);View line=new View(this);line.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));list.addView(line);}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;try{Uri u=data.getData();if(req==10){String json=store.exportJson();getContentResolver().openOutputStream(u).write(json.getBytes(StandardCharsets.UTF_8));toast("Export готов");}else if(req==11){InputStream in=getContentResolver().openInputStream(u);byte[] bytes=new byte[in.available()];in.read(bytes);in.close();store.importJson(new String(bytes,StandardCharsets.UTF_8));refresh();toast("Import готов");}}catch(Exception e){toast("Ошибка файла");}}
}
