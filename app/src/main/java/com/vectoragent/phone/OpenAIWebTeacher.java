package com.vectoragent.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class OpenAIWebTeacher {
    public static final class Result {
        public String report; public String[] urls;
        Result(String r,String[] u){report=r;urls=u;}
    }
    private OpenAIWebTeacher(){}

    public static Result research(String apiKey,String claim,MemoryStore store) throws Exception {
        StringBuilder memory=new StringBuilder();
        for(MemoryStore.Item x:store.all()){
            if("REJECTED".equals(x.status)) continue;
            memory.append("- [").append(x.status).append("] ").append(x.text)
                  .append(" | confidence=").append(Math.round(x.confidence*100))
                  .append("% | support=").append(x.support).append("\n");
            if(memory.length()>9000) break;
        }
        String instructions="Ты — внешний ИИ-учитель для экспериментального локального агента. "+
                "Твоя задача не переписать его память и не объявлять истину на основании собственного авторитета. "+
                "Исследуй утверждение через web search. Ищи минимум 2 независимых домена, предпочитай первичные/официальные/научные источники. "+
                "Если источники расходятся, явно скажи об этом. Не считай две страницы одного издателя независимыми. "+
                "Верни краткий отчёт: (1) что утверждают источники, (2) есть ли согласие, (3) что остаётся неопределённым. "+
                "Не придумывай URL. Текущая локальная память приведена ниже только для сравнения.\n\nПроверяемое утверждение:\n"+claim+
                "\n\nЛокальная память:\n"+memory;
        JSONObject body=new JSONObject();
        body.put("model","gpt-5.6-luna");
        body.put("tools",new JSONArray().put(new JSONObject().put("type","web_search")));
        body.put("input",new JSONArray()
                .put(new JSONObject().put("role","system").put("content",instructions))
                .put(new JSONObject().put("role","user").put("content","Проведи веб-исследование утверждения: "+claim)));
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
        c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(60000);
        c.setRequestProperty("Authorization","Bearer "+apiKey);c.setRequestProperty("Content-Type","application/json");
        c.setDoOutput(true);
        try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        StringBuilder raw=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)raw.append(line);}
        if(code<200||code>=300) throw new IOException("HTTP "+code+": "+raw);
        JSONObject root=new JSONObject(raw.toString());
        String report=root.optString("output_text","");
        if(report.isEmpty()) report=extractText(root);
        List<String> urls=new ArrayList<>();
        collectUrls(root,urls);
        return new Result(report,urls.toArray(new String[0]));
    }
    private static String extractText(JSONObject root){
        StringBuilder s=new StringBuilder();JSONArray out=root.optJSONArray("output");if(out==null)return "";
        for(int i=0;i<out.length();i++){JSONObject item=out.optJSONObject(i);if(item==null)continue;JSONArray c=item.optJSONArray("content");if(c==null)continue;
            for(int j=0;j<c.length();j++){JSONObject p=c.optJSONObject(j);if(p!=null&&"output_text".equals(p.optString("type")))s.append(p.optString("text",""));}}
        return s.toString();
    }
    private static void collectUrls(JSONObject obj,List<String> urls){
        if(obj==null)return;
        JSONArray names=obj.names();if(names==null)return;
        for(int i=0;i<names.length();i++){String k=names.optString(i);Object v=obj.opt(k);
            if(v instanceof JSONObject)collectUrls((JSONObject)v,urls);
            else if(v instanceof JSONArray){JSONArray a=(JSONArray)v;for(int j=0;j<a.length();j++){Object z=a.opt(j);if(z instanceof JSONObject)collectUrls((JSONObject)z,urls);}}
            else if("url".equals(k)&&v instanceof String){String u=(String)v;if(u.startsWith("http")){if(!urls.contains(u))urls.add(u);}}
        }
    }
}
