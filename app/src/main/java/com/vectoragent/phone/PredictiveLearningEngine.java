package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * VectorAgent 2.0 predictive core.
 *
 * Cycle:
 *   PREDICT -> OBSERVE -> ERROR -> UPDATE -> REBUILD MODEL
 *
 * It is deliberately local and deterministic: no API, no text generator,
 * and no manual accept/reject is required for the predictive state.
 *
 * A rule is a repeated one-slot template. Example:
 *   "вода кипит при 100 градусах"
 *   "вода кипит при 100 градусах"
 * gives a rule whose variable slot expects "100".
 *
 * A later observation with the same template and another value produces an
 * explicit prediction error. The observed value is then added to the rule's
 * empirical distribution, so repeated counterexamples can change the model.
 */
public final class PredictiveLearningEngine {
    private static final String PREF = "vector_predictive_v2";
    private static final String RULES = "rules";
    private static final String PENDING = "pending";
    private static final String LOG = "cycle_log";
    private static final String VERSION = "2.0";

    private PredictiveLearningEngine(){}

    public static final class Cycle {
        public String prediction = "";
        public String error = "";
        public String update = "";
        public String rule = "";
    }

    /*
     * MemoryStore intentionally keeps its Android context private. The
     * predictive engine therefore receives a context through the lightweight
     * bridge below. This field is initialized once from MemoryStore's context
     * by attach().
     */
    private static SharedPreferences sp;
    private static boolean attached = false;

    public static synchronized void attach(Context context) {
        if (context == null) return;
        sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        attached = true;
    }

    private static boolean ready() { return attached && sp != null; }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String[] tokens(String s) {
        String n = norm(s);
        return n.isEmpty() ? new String[0] : n.split(" ");
    }

    private static String template(String[] t, int slot) {
        StringBuilder b = new StringBuilder();
        for (int i=0;i<t.length;i++) {
            if (i>0) b.append(' ');
            b.append(i==slot ? "*" : t[i]);
        }
        return b.toString();
    }

    private static String ruleId(String template, int slot) {
        return Integer.toHexString((template + "|" + slot).hashCode());
    }

    private static JSONArray readRules() {
        try { return new JSONArray(sp.getString(RULES, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static void writeRules(JSONArray a) {
        sp.edit().putString(RULES, a.toString()).apply();
    }

    private static JSONArray readLog() {
        try { return new JSONArray(sp.getString(LOG, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static void log(String event) {
        if (!ready()) return;
        try {
            JSONArray a = readLog();
            JSONObject o = new JSONObject();
            o.put("time", System.currentTimeMillis());
            o.put("event", event);
            a.put(o);
            while (a.length() > 200) a.remove(0);
            sp.edit().putString(LOG, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<String> recentLog() {
        ArrayList<String> out = new ArrayList<>();
        if (!ready()) return out;
        try {
            JSONArray a = readLog();
            int from = Math.max(0, a.length()-30);
            for (int i=from;i<a.length();i++) out.add(a.getJSONObject(i).optString("event"));
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Prediction phase. Called before the observation enters MemoryStore.
     */
    public static synchronized Cycle predict(String observation) {
        Cycle c = new Cycle();
        if (!ready()) return c;

        String[] obs = tokens(observation);
        JSONArray rules = readRules();
        JSONObject best = null;
        int bestMatches = -1;
        int bestTotal = -1;

        for (int i=0;i<rules.length();i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r == null) continue;
            String[] pattern = r.optString("template").split(" ", -1);
            int slot = r.optInt("slot", -1);
            if (slot < 0 || pattern.length != obs.length) continue;

            int matches = 0;
            boolean ok = true;
            for (int k=0;k<obs.length;k++) {
                if (k == slot) continue;
                if (!pattern[k].equals(obs[k])) { ok = false; break; }
                matches++;
            }
            if (!ok) continue;

            int total = 0;
            JSONObject counts = r.optJSONObject("counts");
            if (counts != null) {
                java.util.Iterator<String> it = counts.keys();
                while (it.hasNext()) total += counts.optInt(it.next(),0);
            }
            if (total < 2) continue;
            if (matches > bestMatches || (matches == bestMatches && total > bestTotal)) {
                best = r; bestMatches = matches; bestTotal = total;
            }
        }

        if (best == null) return c;

        String expected = dominant(best.optJSONObject("counts"));
        if (expected.isEmpty()) return c;

        int slot = best.optInt("slot",-1);
        c.rule = best.optString("id");
        c.prediction = "slot=" + slot + " expected=" + expected +
                " confidence=" + Math.round(best.optDouble("confidence",0)*100) + "%";
        try {
            JSONObject p = new JSONObject();
            p.put("ruleId", best.optString("id"));
            p.put("slot", slot);
            p.put("expected", expected);
            p.put("observation", observation);
            p.put("time", System.currentTimeMillis());
            sp.edit().putString(PENDING, p.toString()).apply();
            log("PREDICT: " + c.prediction + " for «" + observation + "»");
        } catch (Exception ignored) {}
        return c;
    }

    /**
     * Observation/error/update phase. The actual token is compared with the
     * previous prediction, then the rule is updated from all non-rejected
     * memory items.
     */
    public static synchronized Cycle observeAndUpdate(MemoryStore store, String observation) {
        Cycle c = new Cycle();
        if (!ready() || store == null) return c;

        try {
            String rawPending = sp.getString(PENDING, "");
            if (!rawPending.isEmpty()) {
                JSONObject p = new JSONObject(rawPending);
                String[] actual = tokens(observation);
                int slot = p.optInt("slot",-1);
                String expected = p.optString("expected");
                String ruleId = p.optString("ruleId");
                if (slot >= 0 && slot < actual.length && !expected.isEmpty()) {
                    String got = actual[slot];
                    boolean correct = expected.equals(got);
                    c.rule = ruleId;
                    if (correct) {
                        c.error = "ERROR=0: observation matched prediction (" + got + ")";
                        log("OBSERVE: CORRECT rule=" + ruleId + " value=" + got);
                    } else {
                        c.error = "PREDICTION_ERROR: expected=" + expected + " observed=" + got;
                        log("OBSERVE: ERROR rule=" + ruleId + " expected=" + expected + " observed=" + got);
                    }
                    updateRule(ruleId, got, correct);
                    c.update = correct
                            ? "UPDATE: confidence/support reinforced"
                            : "UPDATE: counterexample incorporated; model distribution changed";
                    sp.edit().remove(PENDING).apply();
                }
            }

            rebuildFromMemory(store);
        } catch (Exception e) {
            log("CYCLE_ERROR: " + e.getClass().getSimpleName());
        }
        return c;
    }

    private static String dominant(JSONObject counts) {
        if (counts == null) return "";
        String best = "";
        int n = -1;
        try {
            java.util.Iterator<String> it = counts.keys();
            while (it.hasNext()) {
                String k = it.next();
                int v = counts.optInt(k,0);
                if (v > n) { n=v; best=k; }
            }
        } catch (Exception ignored) {}
        return best;
    }

    private static void updateRule(String id, String observed, boolean correct) {
        JSONArray a = readRules();
        try {
            for (int i=0;i<a.length();i++) {
                JSONObject r=a.getJSONObject(i);
                if (!id.equals(r.optString("id"))) continue;
                JSONObject counts=r.optJSONObject("counts");
                if(counts==null){counts=new JSONObject();r.put("counts",counts);}
                counts.put(observed,counts.optInt(observed,0)+1);
                int support=r.optInt("support",0)+1;
                int errors=r.optInt("errors",0)+(correct?0:1);
                int correctN=r.optInt("correct",0)+(correct?1:0);
                r.put("support",support);
                r.put("errors",errors);
                r.put("correct",correctN);
                r.put("confidence", (double)Math.max(1, Math.max(correctN, maxCount(counts))) /
                        Math.max(1, support + 1));
                r.put("updated",System.currentTimeMillis());
                break;
            }
            writeRules(a);
        } catch (Exception ignored) {}
    }

    private static int maxCount(JSONObject counts) {
        int m=0;
        try {
            java.util.Iterator<String> it=counts.keys();
            while(it.hasNext())m=Math.max(m,counts.optInt(it.next(),0));
        } catch(Exception ignored){}
        return m;
    }

    /**
     * Rebuilds structural rules from accumulated experience. This is the
     * mutable relational model: rules are not hard-coded and survive launches.
     */
    private static void rebuildFromMemory(MemoryStore store) {
        try {
            List<MemoryStore.Item> items=store.all();
            HashMap<String,JSONObject> fresh=new HashMap<>();

            for(int i=0;i<items.size();i++) {
                MemoryStore.Item a=items.get(i);
                if("REJECTED".equals(a.status)) continue;
                String[] ta=tokens(a.text);
                if(ta.length<3 || ta.length>40) continue;

                for(int j=i+1;j<items.size();j++) {
                    MemoryStore.Item b=items.get(j);
                    if("REJECTED".equals(b.status)) continue;
                    String[] tb=tokens(b.text);
                    if(tb.length!=ta.length) continue;

                    int diff=-1,diffs=0;
                    for(int k=0;k<ta.length;k++) {
                        if(!ta[k].equals(tb[k])) { diff=k; diffs++; }
                    }
                    // Two repetitions are also evidence: choose a meaningful
                    // slot (prefer a numeric/value token) so the model can
                    // predict repetition and later register a counterexample.
                    if(diffs==0) {
                        diff=chooseValueSlot(ta);
                        if(diff<0) continue;
                    } else if(diffs!=1) continue;

                    String tpl=template(ta,diff);
                    String id=ruleId(tpl,diff);
                    JSONObject r=fresh.get(id);
                    if(r==null) {
                        r=findExisting(readRules(),id);
                        if(r==null) {
                            r=new JSONObject();
                            r.put("id",id);
                            r.put("template",tpl);
                            r.put("slot",diff);
                            r.put("counts",new JSONObject());
                            r.put("support",0);
                            r.put("errors",0);
                            r.put("correct",0);
                            r.put("confidence",0.0);
                            r.put("created",System.currentTimeMillis());
                        }
                        fresh.put(id,r);
                    }
                    JSONObject counts=r.optJSONObject("counts");
                    if(counts==null){counts=new JSONObject();r.put("counts",counts);}
                    counts.put(ta[diff],counts.optInt(ta[diff],0)+1);
                    counts.put(tb[diff],counts.optInt(tb[diff],0)+1);
                }
            }

            JSONArray out=new JSONArray();
            for(JSONObject r:fresh.values()) {
                JSONObject counts=r.optJSONObject("counts");
                int total=0,max=0;
                if(counts!=null){
                    java.util.Iterator<String> it=counts.keys();
                    while(it.hasNext()){int v=counts.optInt(it.next(),0);total+=v;max=Math.max(max,v);}
                }
                if(total>=2) {
                    r.put("support",Math.max(r.optInt("support",0),total));
                    r.put("confidence",total==0?0:(double)max/total);
                    r.put("updated",System.currentTimeMillis());
                    out.put(r);
                }
            }
            writeRules(out);
        } catch(Exception e) {
            log("MODEL_REBUILD_ERROR: "+e.getClass().getSimpleName());
        }
    }

    private static int chooseValueSlot(String[] t) {
        for(int i=0;i<t.length;i++) if(t[i].matches("[-+]?\\d+(?:[.,]\\d+)?")) return i;
        for(int i=t.length-1;i>=0;i--) if(t[i].length()>=3) return i;
        return t.length>0 ? t.length-1 : -1;
    }

    private static JSONObject findExisting(JSONArray a,String id) {
        for(int i=0;i<a.length();i++){
            JSONObject r=a.optJSONObject(i);
            if(r!=null && id.equals(r.optString("id"))) return r;
        }
        return null;
    }

    public static String summary() {
        if(!ready()) return "Predictive Core v2.0: not attached";
        try {
            JSONArray a=readRules();
            int predictions=0, errors=0;
            String last="";
            for(String s:recentLog()){
                if(s.startsWith("PREDICT:")) predictions++;
                if(s.startsWith("OBSERVE: ERROR")) errors++;
                last=s;
            }
            return "Predictive Core v2.0\n"+
                    "Rules: "+a.length()+
                    " • Predictions: "+predictions+
                    " • Prediction errors: "+errors+
                    "\nLast cycle: "+(last.isEmpty()?"—":last);
        } catch(Exception e) { return "Predictive Core v2.0: "+e.getClass().getSimpleName(); }
    }

    public static void clear() {
        if(!ready()) return;
        sp.edit().remove(RULES).remove(PENDING).remove(LOG).apply();
    }
}
