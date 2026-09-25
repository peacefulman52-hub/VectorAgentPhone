package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    SharedPreferences prefs; ArrayList<String> contextLog = new ArrayList<>();
    MemoryStore store; LinearLayout root,content,chat,list; EditText chatInput; SeekBar confidence; TextView confLabel,stats; Switch auto;
    void clearContent(){ if(content!=null) content.removeAllViews(); }
    void loadContext(){
        prefs=getSharedPreferences("vector_context",MODE_PRIVATE);
        String raw=prefs.getString("log","");
        if(!raw.isEmpty()) for(String s:raw.split("\\n", -1)) if(!s.trim().isEmpty()) contextLog.add(s);
    }
    void saveContext(){ StringBuilder s=new StringBuilder(); for(String x:contextLog) s.append(x).append("\\n"); prefs.edit().putString("log",s.toString()).apply(); }
    void rememberContext(String source,String text){
        String v=text==null?"":text.trim(); if(v.isEmpty()) return;
        contextLog.add(source+": "+v);
        if(contextLog.size()>300) contextLog.remove(0);
        saveContext();
    }
    String recentContext(){
        StringBuilder s=new StringBuilder(); int start=Math.max(0,contextLog.size()-12);
        for(int i=start;i<contextLog.size();i++) s.append("• ").append(contextLog.get(i)).append("\\n");
        return s.toString();
    }
    void clearInputs(EditText... fields){ for(EditText e:fields) if(e!=null) e.setText(""); }
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(35,35,40));t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    TextView chip(String s){TextView t=tv(s,13);t.setGravity(Gravity.CENTER);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(dp(12),dp(6),dp(12),dp(6));return t;}
    GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new MemoryStore(this);loadContext();build();showChat();}
    void build(){
        ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(10),dp(12),dp(18));scroll.addView(root);setContentView(scroll);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("VECTOR",25);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(Color.rgb(20,65,90));head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=chip("LOCAL AGENT");status.setTextColor(Color.rgb(20,90,65));status.setBackground(bg(Color.rgb(225,245,235),24));head.addView(status);root.addView(head);
        root.addView(tv("v0.8 • память • гипотезы • обучение • источники • общий контекст",12));
        LinearLayout modes=new LinearLayout(this);modes.setGravity(Gravity.CENTER);
        Button chatMode=btn("💬 Агент"),memMode=btn("🧠 Память"),srcMode=btn("🌐 Источники"),genMode=btn("✨ Генератор"),setMode=btn("⚙");
        modes.addView(chatMode,new LinearLayout.LayoutParams(0,dp(48),1));modes.addView(memMode,new LinearLayout.LayoutParams(0,dp(48),1));modes.addView(srcMode,new LinearLayout.LayoutParams(0,dp(48),1));modes.addView(genMode,new LinearLayout.LayoutParams(0,dp(48),1));modes.addView(setMode,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(modes);
        chatMode.setOnClickListener(v->showChat());memMode.setOnClickListener(v->showMemory());srcMode.setOnClickListener(v->showSources());genMode.setOnClickListener(v->showGenerator());setMode.setOnClickListener(v->showSettings());
        View line=new View(this);line.setBackgroundColor(Color.LTGRAY);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
    }
    void showChat(){
        content.removeAllViews();chat=new LinearLayout(this);chat.setOrientation(LinearLayout.VERTICAL);content.addView(chat);
        TextView intro=tv("Локальный агент",18);intro.setTypeface(Typeface.DEFAULT,Typeface.BOLD);chat.addView(intro);
        chat.addView(tv("Пиши как в мессенджере. Контекст общий для всех окон и сохраняется между запусками. Агент отдельно хранит факты и использует накопленные записи как основание ответа.",13));
        addBubble("Агент","Я отвечаю только по собственной локальной памяти. Если данных недостаточно — скажу об этом прямо.");
        chatInput=new EditText(this);chatInput.setHint("Сообщение…");chatInput.setMinLines(2);chatInput.setGravity(48);chat.addView(chatInput,new LinearLayout.LayoutParams(-1,dp(82)));
        LinearLayout sendRow=new LinearLayout(this);Button send=btn("➤  Отправить"),clear=btn("Очистить");send.setTextSize(16);sendRow.addView(send,new LinearLayout.LayoutParams(0,dp(52),1));sendRow.addView(clear,new LinearLayout.LayoutParams(0,dp(52),1));chat.addView(sendRow);
        send.setOnClickListener(v->sendToAgent());clear.setOnClickListener(v->chatInput.setText(""));
        chat.addView(tv("Общий контекст: "+contextLog.size()+" записей",13));
        chat.addView(tv("Быстрый эксперимент",16));Button t=btn("Тест 6: вода → конфликт");chat.addView(t);t.setOnClickListener(v->{store.add("Вода кипит при 100 C",.90,true,"TEST","synthetic test case","supports baseline");store.add("Вода не кипит при 100 C",.90,true,"TEST","synthetic contradiction case","contradicts baseline");addBubble("Агент","Тест 6 выполнен. Открой Память и посмотри конфликт.");});
    }
    void sendToAgent(){
        String raw=chatInput.getText().toString().trim();if(raw.isEmpty())return;addBubble("Ты",raw);rememberContext("Чат",raw);
        MemoryStore.IngestResult r=store.ingest(raw,.70);StringBuilder a=new StringBuilder(memoryAnswer(raw));
        if(r.conflicts>0){a.append("\nОбнаружено конфликтов: "+r.conflicts);for(String m:r.messages)a.append("\n• ").append(m);}else a.append("\nПротиворечий с текущей памятью не обнаружено.");
        a.append("\n\nФакт сохранён только если он прошёл локальную обработку. Состояния смотри в «Память».");addBubble("Агент",a.toString());chatInput.setText("");
    }
    void addBubble(String who,String text){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));
        TextView h=tv(who,12);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setTextColor(Color.rgb(30,90,120));row.addView(h);
        TextView b=tv(text,15);b.setBackground(bg(who.equals("Ты")?Color.rgb(232,242,250):Color.rgb(242,242,242),18));row.addView(b);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));chat.addView(row,lp);
    }
    String memoryAnswer(String q){
        String nq=q.toLowerCase();
        List<MemoryStore.Item> items=store.all(); StringBuilder s=new StringBuilder();
        int hits=0;
        for(MemoryStore.Item x:items){
            String[] words=nq.replaceAll("[^\\p{L}\\p{Nd} ]"," ").split("\\s+");
            int score=0; for(String w:words) if(w.length()>2 && x.text.toLowerCase().contains(w)) score++;
            if(score>0){if(hits==0)s.append("По моей памяти:\\n"); s.append("• ").append(x.text).append(" [").append(x.status).append(", ").append(Math.round(x.confidence*100)).append("%]\\n");hits++;}
        }
        if(hits==0) s.append("В моей накопленной базе нет достаточно близких фактов, чтобы ответить по существу. Я не буду додумывать ответ.");
        else s.append("\\nОснование ответа: ").append(hits).append(" совпавших записей локальной памяти.");
        if(!contextLog.isEmpty()) s.append("\\n\\nПродолжение общего контекста:\\n").append(recentContext());
        return s.toString();
    }
    void showSources(){
        content.removeAllViews(); content.addView(tv("🌐 Источники",20));
        content.addView(tv("Вставь HTTPS-ссылку. Приложение скачает доступный текст страницы, разобьёт его на утверждения и передаст их в локальную память. Внешний текст не становится автоматически истиной.",13));
        EditText url=new EditText(this);url.setHint("https://example.com/article");content.addView(url);
        EditText preview=new EditText(this);preview.setHint("Текст страницы / результат…");preview.setMinLines(8);preview.setGravity(48);content.addView(preview);
        Button load=btn("↓ Загрузить источник");content.addView(load);
        TextView result=tv("",13);content.addView(result);
        load.setOnClickListener(v->{
            String u=url.getText().toString().trim();
            if(!u.startsWith("https://")){result.setText("Нужна HTTPS-ссылка.");return;}
            result.setText("Загружаю…");
            new Thread(()->{
                try{
                    HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","VectorAgentPhone/0.8");c.setInstanceFollowRedirects(true);
                    BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));StringBuilder raw=new StringBuilder();String line;int chars=0;
                    while((line=br.readLine())!=null && chars<200000){raw.append(line).append("\\n");chars+=line.length();}
                    br.close();String text=raw.toString().replaceAll("<script[\\s\\S]*?</script>"," ").replaceAll("<style[\\s\\S]*?</style>"," ").replaceAll("<[^>]+>"," ").replaceAll("&nbsp;"," ").replaceAll("\\s+"," ").trim();
                    rememberContext("Источник "+u,text.substring(0,Math.min(text.length(),4000))); MemoryStore.IngestResult ir=store.ingest(text,.55);
                    runOnUiThread(()->{preview.setText(text.substring(0,Math.min(text.length(),12000))); url.setText(""); result.setText("Источник обработан: добавлено "+ir.added+", конфликтов "+ir.conflicts+", пропущено "+ir.ignored+". Контекст добавлен.");});
                }catch(Exception e){runOnUiThread(()->result.setText("Не удалось загрузить источник: "+e.getClass().getSimpleName()));}
            }).start();
        });
    }
    void showGenerator(){
        content.removeAllViews();content.addView(tv("✨ Генератор красивого текста",20));
        content.addView(tv("Это отдельная песочница. Её ответы НЕ считаются знаниями агента и НЕ записываются в память.",13));
        EditText topic=new EditText(this);topic.setHint("Тема / идея / настроение");topic.setMinLines(2);content.addView(topic);
        Spinner style=new Spinner(this);String[] styles={"Мини-эссе","Лирический текст","Научно-фантастический фрагмент","Притча","Пост"};style.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,styles));content.addView(style);
        Button go=btn("✨ Сгенерировать");content.addView(go);TextView out=tv("",15);out.setBackground(bg(Color.rgb(247,247,247),16));content.addView(out);
        go.setOnClickListener(v->{String t=topic.getText().toString().trim();if(t.isEmpty())return;rememberContext("Генератор",t);out.setText(generatePretty(t,style.getSelectedItem().toString()));topic.setText("");});
    }
    String generatePretty(String topic,String style){
        if(style.equals("Лирический текст")) return "Иногда "+topic+" начинается не с ответа, а с вопроса.\\n\\nМы смотрим на привычное и вдруг замечаем в нём неизвестное. И тогда маленькая мысль становится дверью: за ней уже не готовая истина, а пространство, где можно наблюдать, сомневаться и пробовать снова.\\n\\nПусть эта история останется открытой — именно поэтому она интересна.";
        if(style.equals("Притча")) return "Однажды человек спросил: «Что важнее — знать ответ или уметь его искать?»\\n\\nЕму ответили: «Если ты знаешь только один ответ, ты знаешь прошлое. Если умеешь проверять — ты умеешь встречать новое».\\n\\nТак "+topic+" перестало быть вещью и стало вопросом.";
        if(style.equals("Научно-фантастический фрагмент")) return "В журнале эксперимента появилась новая строка: «"+topic+"».\\n\\nСистема сравнила её с накопленными наблюдениями. Совпадений было мало. Поэтому она не стала придумывать вывод. Она пометила неизвестное как неизвестное — и оставила место для следующего наблюдения.\\n\\nИменно в этот момент эксперимент стал интереснее результата.";
        if(style.equals("Пост")) return "Есть темы, которые нельзя понять одним красивым ответом. "+topic+" — одна из них. Поэтому вместо уверенного вывода лучше собрать наблюдения, сравнить их, сохранить противоречия и посмотреть, какая закономерность выдержит проверку. Иногда именно так начинается настоящее исследование.";
        return "«"+topic+"» — это повод остановиться на минуту и посмотреть внимательнее.\\n\\nУ каждой идеи есть поверхность — то, что видно сразу. Но под ней находятся связи, исключения, вопросы и неожиданные последствия. Красивый текст может создать впечатление завершённости; настоящее исследование, наоборот, оставляет пространство для следующего шага.\\n\\nПоэтому пусть "+topic+" будет не точкой, а началом.";
    }
    void showMemory(){
        content.removeAllViews();content.addView(tv("🧠 Память и лаборатория",20));stats=tv("",13);stats.setBackground(bg(Color.rgb(245,247,249),14));content.addView(stats);
        LinearLayout tools=new LinearLayout(this);Button snap=btn("Snapshot"),roll=btn("Rollback"),exp=btn("Export"),imp=btn("Import");for(Button b:new Button[]{snap,roll,exp,imp})tools.addView(b,new LinearLayout.LayoutParams(0,-2,1));content.addView(tools);
        snap.setOnClickListener(v->{store.snapshot();toast("Snapshot сохранён");});roll.setOnClickListener(v->{toast(store.rollback()?"Rollback выполнен":"Snapshot отсутствует");refresh();});exp.setOnClickListener(v->exportFile());imp.setOnClickListener(v->importFile());
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(8),dp(6),dp(8),dp(6));
        EditText fact=new EditText(this);fact.setHint("Новый факт…");form.addView(fact);EditText src=new EditText(this);src.setHint("Источник");form.addView(src);EditText pv=new EditText(this);pv.setHint("Происхождение / ограничение");form.addView(pv);EditText rel=new EditText(this);rel.setHint("Связь");form.addView(rel);
        confLabel=tv("Порог уверенности: "+Math.round(store.threshold()*100)+"%",13);form.addView(confLabel);confidence=new SeekBar(this);confidence.setMax(100);confidence.setProgress((int)(store.threshold()*100));form.addView(confidence);
        confidence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){confLabel.setText("Порог уверенности: "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){store.setThreshold(s.getProgress()/100.0);}});
        auto=new Switch(this);auto.setText("Автоактивация фактов выше порога");auto.setChecked(store.auto());auto.setOnCheckedChangeListener((v,c)->store.setAuto(c));form.addView(auto);
        Button add=btn("＋ Сохранить факт");add.setTextSize(15);form.addView(add);add.setOnClickListener(v->{String s=fact.getText().toString().trim();if(s.isEmpty())return;rememberContext("Память",s); String c=store.add(s,confidence.getProgress()/100.0,auto.isChecked(),src.getText().toString().trim(),pv.getText().toString().trim(),rel.getText().toString().trim());fact.setText("");src.setText("");pv.setText("");rel.setText("");toast(c.isEmpty()?"Факт сохранён":"Конфликт обнаружен — оба факта сохранены");refresh();});content.addView(form);
        content.addView(tv("🔬 Гипотезы — закономерности, найденные системой",18));LinearLayout hyps=new LinearLayout(this);hyps.setOrientation(LinearLayout.VERTICAL);content.addView(hyps);refreshHypotheses(hyps);
        content.addView(tv("Факты",18));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);refresh();
    }
    void refreshHypotheses(LinearLayout hyps){
        hyps.removeAllViews();List<MemoryStore.Hypothesis> hs=store.hypotheses();if(hs.isEmpty()){hyps.addView(tv("Пока нет кандидатов. Дай несколько пар примеров с одинаковой структурой.",13));return;}
        for(MemoryStore.Hypothesis h:hs){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(6),dp(8),dp(6),dp(8));row.setBackground(bg(Color.rgb(247,247,242),14));row.addView(tv(h.status+" • support "+h.support+" • v"+h.version,13));row.addView(tv("Гипотеза: "+h.rule,15));row.addView(tv("Основания: "+h.evidence,11));LinearLayout a=new LinearLayout(this);Button yes=btn("✓ Принять"),no=btn("✕ Отклонить");a.addView(yes,new LinearLayout.LayoutParams(0,-2,1));a.addView(no,new LinearLayout.LayoutParams(0,-2,1));yes.setOnClickListener(v->{store.learnHypothesis(h.id,true);showMemory();});no.setOnClickListener(v->{store.learnHypothesis(h.id,false);showMemory();});row.addView(a);hyps.addView(row);}}
    void refresh(){
        if(list==null||stats==null)return;list.removeAllViews();stats.setText("CANDIDATE "+store.count("CANDIDATE")+"   •   ACTIVE "+store.count("ACTIVE")+"   •   CONFLICT "+store.count("CONFLICT")+"   •   REJECTED "+store.count("REJECTED")+"   •   HYP "+store.hypotheses().size());
        List<MemoryStore.Item> items=store.all();if(items.isEmpty()){list.addView(tv("Память пока пуста.",14));return;}
        for(MemoryStore.Item x:items){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(6),dp(8),dp(6),dp(8));TextView h=tv(x.status+"  •  "+Math.round(x.confidence*100)+"%  • v"+x.version,14);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(h);row.addView(tv(x.text,15));row.addView(tv("Источник: "+x.source,11));if(!x.provenance.isEmpty())row.addView(tv("Provenance: "+x.provenance,11));if(!x.relation.isEmpty())row.addView(tv("Связь: "+x.relation,11));LinearLayout a=new LinearLayout(this);Button yes=btn("✓ Подтвердить"),no=btn("✕ Отклонить");a.addView(yes,new LinearLayout.LayoutParams(0,-2,1));a.addView(no,new LinearLayout.LayoutParams(0,-2,1));yes.setOnClickListener(v->{store.learn(x.id,true);refresh();});no.setOnClickListener(v->{store.learn(x.id,false);refresh();});row.addView(a);list.addView(row);}}
    void showSettings(){
        content.removeAllViews();content.addView(tv("⚙ Настройки и пояснения",20));content.addView(tv("Все экспериментальные переключатели собраны здесь. Описание находится прямо под каждой функцией.",13));
        addSetting("Автоактивация","Если включена, новый факт получает ACTIVE, когда его уверенность выше порога.",autoSwitch());
        content.addView(tv("Порог уверенности",17));content.addView(tv("Минимальная уверенность для автоматического ACTIVE. Это не математическая вероятность истины.",12));SeekBar sb=new SeekBar(this);sb.setMax(100);sb.setProgress((int)(store.threshold()*100));content.addView(sb);TextView sl=tv("Сейчас: "+Math.round(store.threshold()*100)+"%",13);content.addView(sl);sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){sl.setText("Сейчас: "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){store.setThreshold(s.getProgress()/100.0);}});
        addAction("Snapshot","Сохраняет текущее состояние памяти и гипотез перед экспериментом.","Сделать Snapshot",v->{store.snapshot();toast("Snapshot сохранён");});addAction("Rollback","Возвращает последний Snapshot после неудачного эксперимента.","Выполнить Rollback",v->toast(store.rollback()?"Rollback выполнен":"Snapshot отсутствует"));
        content.addView(tv("Export / Import",17));content.addView(tv("Export выгружает память в JSON. Import загружает JSON обратно — это внешний слой продолжения эксперимента.",12));LinearLayout io=new LinearLayout(this);Button ex=btn("Export JSON"),im=btn("Import JSON");io.addView(ex,new LinearLayout.LayoutParams(0,-2,1));io.addView(im,new LinearLayout.LayoutParams(0,-2,1));content.addView(io);ex.setOnClickListener(v->exportFile());im.setOnClickListener(v->importFile());
        content.addView(tv("Как работает обучение",17));content.addView(tv("1) наблюдение → 2) фиксация → 3) сравнение → 4) конфликт/сходство → 5) кандидат-гипотеза → 6) подтверждение/отклонение → 7) изменение состояния.",13));content.addView(tv("Научный следующий шаг",17));content.addView(tv("v0.8 отделяет память от генератора. ACTIVE больше не должен появляться просто из-за добавления независимого факта. Следующий этап — применять подтверждённые гипотезы к новым наблюдениям и измерять ошибки.",13));
    }
    void addSetting(String title,String desc,View control){content.addView(tv(title,17));content.addView(tv(desc,12));content.addView(control);}
    void addAction(String title,String desc,String label,View.OnClickListener l){content.addView(tv(title,17));content.addView(tv(desc,12));Button b=btn(label);content.addView(b);b.setOnClickListener(l);}
    Switch autoSwitch(){Switch s=new Switch(this);s.setText("Включить автоактивацию");s.setChecked(store.auto());s.setOnCheckedChangeListener((v,c)->store.setAuto(c));return s;}
    void exportFile(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"vector-memory-v0.7.json");startActivityForResult(i,10);}
    void importFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,11);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;try{Uri u=data.getData();if(req==10){java.io.OutputStream out=getContentResolver().openOutputStream(u);out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));out.close();toast("Export готов");}else{InputStream in=getContentResolver().openInputStream(u);java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)buf.write(b,0,n);in.close();store.importJson(new String(buf.toByteArray(),StandardCharsets.UTF_8));showMemory();toast("Import готов");}}catch(Exception e){toast("Ошибка файла");}}
}