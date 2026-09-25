package com.vectoragent.phone;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.util.List;

public class TeacherActivity extends Activity {
    MemoryStore store;
    LinearLayout root;
    TextView state, log;
    int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    TextView tv(String s,int z){ TextView t=new TextView(this); t.setText(s); t.setTextSize(z); t.setTextColor(Color.rgb(35,35,40)); t.setPadding(dp(8),dp(8),dp(8),dp(8)); return t; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b); store=new MemoryStore(this);
        ScrollView sc=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18)); sc.addView(root); setContentView(sc); build();
    }

    void build(){
        root.addView(tv("🎓 УЧИТЕЛЬ / МОДЕЛЬ v1",24));
        root.addView(tv("Это не копия модели ChatGPT. Здесь заложен упрощённый принцип: сначала извлечь утверждение, затем сопоставить его с памятью, отдельно учитывать источник, конфликт и степень подтверждения, и только потом формировать ответ.",13));
        root.addView(tv("1. Базовое знание",18));
        addFact("Вода при нормальном атмосферном давлении кипит примерно при 100 °C.","Учебный источник A");
        addFact("Земля обращается вокруг Солнца.","Учебный источник A");
        addFact("Солнце — звезда.","Учебный источник A");
        root.addView(tv("2. Проверка независимым источником",18));
        addFact("Вода при нормальном атмосферном давлении кипит примерно при 100 °C.","Учебный источник B — независимое свидетельство");
        root.addView(tv("3. Конфликт",18));
        addFact("Экспериментальный объект X доступен.","Учебная гипотеза A");
        addFact("Экспериментальный объект X недоступен.","Учебная гипотеза B");
        root.addView(tv("После обучения открой «Память» и смотри не только на текст, но и на статус, источник и историю. Это принципиально: повторение одним источником не должно автоматически считаться новым знанием.",13));
        state=tv("",13); root.addView(state); log=tv("",12); root.addView(log); refresh();
    }

    void addFact(String text,String source){
        Button b=btn("➤ "+text+"  ["+source+"]"); root.addView(b);
        b.setOnClickListener(v->{String c=store.add(text,.85,false,"TEACHER",source,"curriculum");log.setText(c.isEmpty()?"Утверждение принято как новое наблюдение.":"Обнаружен конфликт с: "+c);refresh();});
    }
    void refresh(){state.setText("ACTIVE="+store.count("ACTIVE")+"   CANDIDATE="+store.count("CANDIDATE")+"   CONFLICT="+store.count("CONFLICT")+"   REJECTED="+store.count("REJECTED")+"   HYP="+store.hypotheses().size());}
}