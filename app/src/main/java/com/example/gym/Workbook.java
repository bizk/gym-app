package com.example.gym;

import android.util.Xml;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.regex.*;

/** Reads the trainer's workbook layout without evaluating formulas. */
final class Workbook {
    static XmlPullParser xml(byte[] data) throws Exception { XmlPullParser p = Xml.newPullParser(); p.setInput(new ByteArrayInputStream(data), "UTF-8"); return p; }
    static String attr(XmlPullParser p, String name) { for (int i=0;i<p.getAttributeCount();i++) if (name.equals(p.getAttributeName(i))) return p.getAttributeValue(i); return ""; }
    static String col(int n) { String s=""; for (n++;n>0;n=(n-1)/26) s=(char)('A'+(n-1)%26)+s; return s; }
    static JSONArray parse(InputStream input) throws Exception {
        Map<String,byte[]> files = new HashMap<>(); ZipInputStream zip = new ZipInputStream(input); ZipEntry entry;
        while ((entry=zip.getNextEntry())!=null) { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=zip.read(b))!=-1) { out.write(b,0,n); if(out.size()>16_000_000) throw new IOException("Archivo demasiado grande"); } files.put(entry.getName(),out.toByteArray()); }
        List<String> strings=new ArrayList<>();
        if(files.containsKey("xl/sharedStrings.xml")) { XmlPullParser p=xml(files.get("xl/sharedStrings.xml")); StringBuilder s=null; for(int e=p.getEventType();e!=XmlPullParser.END_DOCUMENT;e=p.next()) { if(e==XmlPullParser.START_TAG && p.getName().equals("si")) s=new StringBuilder(); else if(e==XmlPullParser.START_TAG && p.getName().equals("t") && s!=null) s.append(p.nextText()); else if(e==XmlPullParser.END_TAG && p.getName().equals("si")) { strings.add(s.toString()); s=null; } } }
        Map<String,String> rels=new HashMap<>(); XmlPullParser rp=xml(files.get("xl/_rels/workbook.xml.rels")); for(int e=rp.getEventType();e!=XmlPullParser.END_DOCUMENT;e=rp.next()) if(e==XmlPullParser.START_TAG && rp.getName().equals("Relationship")) rels.put(attr(rp,"Id"),attr(rp,"Target"));
        JSONArray plans=new JSONArray(); XmlPullParser wp=xml(files.get("xl/workbook.xml"));
        for(int e=wp.getEventType();e!=XmlPullParser.END_DOCUMENT;e=wp.next()) if(e==XmlPullParser.START_TAG && wp.getName().equals("sheet") && attr(wp,"name").toLowerCase(Locale.ROOT).contains("plan") && !"hidden".equals(attr(wp,"state"))) {
            String target=rels.get(attr(wp,"id")); if(target==null) continue; if(target.startsWith("/")) target=target.substring(1); if(!target.startsWith("xl/")) target="xl/"+target; if(!files.containsKey(target)) continue;
            Map<String,String> cells=new HashMap<>(); List<String> merges=new ArrayList<>(); XmlPullParser p=xml(files.get(target)); String ref="",type="",value="";
            for(int x=p.getEventType();x!=XmlPullParser.END_DOCUMENT;x=p.next()) {
                if(x==XmlPullParser.START_TAG && p.getName().equals("c")) { ref=attr(p,"r"); type=attr(p,"t"); value=""; }
                else if(x==XmlPullParser.START_TAG && (p.getName().equals("v") || p.getName().equals("t"))) value+=p.nextText();
                else if(x==XmlPullParser.END_TAG && p.getName().equals("c")) { if(type.equals("s") && !value.isEmpty()) value=strings.get(Integer.parseInt(value)); cells.put(ref,value); }
                else if(x==XmlPullParser.START_TAG && p.getName().equals("mergeCell")) merges.add(attr(p,"ref"));
            }
            for(String m:merges) { Matcher mm=Pattern.compile("([A-Z]+)(\\d+):\\1(\\d+)").matcher(m); if(mm.matches()) for(int row=Integer.parseInt(mm.group(2))+1;row<=Integer.parseInt(mm.group(3));row++) cells.putIfAbsent(mm.group(1)+row,cells.getOrDefault(mm.group(1)+mm.group(2),"")); }
            JSONArray days=new JSONArray(); JSONObject day=null; String lastLabel=""; int max=0; for(String key:cells.keySet()) { Matcher m=Pattern.compile("[A-Z]+(\\d+)").matcher(key); if(m.matches()) max=Math.max(max,Integer.parseInt(m.group(1))); }
            for(int row=15;row<=max;row++) { String label=cells.getOrDefault("A"+row,""); if(label.matches("(?i)^D[IÍ]A\\s*\\d+.*") && !label.equals(lastLabel)) { lastLabel=label; day=new JSONObject().put("name",label.trim()).put("exercises",new JSONArray()); days.put(day); }
                String name=cells.getOrDefault("I"+row,"").trim(),code=cells.getOrDefault("H"+row,"").trim(); if(day==null || name.isEmpty() || !code.matches("(?i)^[A-Z]\\d+\\.?$")) continue;
                JSONArray weeks=new JSONArray(); for(int w=0;w<5;w++) { JSONObject week=new JSONObject(); String[] keys={"sets","reps","load","effort"}; for(int j=0;j<4;j++) week.put(keys[j],cells.getOrDefault(col(10+4*w+j)+row,"")); weeks.put(week); }
                day.getJSONArray("exercises").put(new JSONObject().put("code",code).put("name",name).put("weeks",weeks).put("video",""));
            }
            plans.put(new JSONObject().put("name",attr(wp,"name")).put("days",days));
        }
        if(plans.length()==0) throw new IOException("No se encontraron hojas de plan"); return plans;
    }
}
