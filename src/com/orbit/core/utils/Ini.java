package com.orbit.core.utils;

import java.io.*;
import java.util.*;

public class Ini {
    public Ini() {    }

    public Ini(BufferedReader reader) throws IOException {
        load(reader);
    }

    private Map<String,  Map<String, String>> sections = null;
    
    public void load(BufferedReader reader) throws IOException {
        sections = new HashMap<>();
        String line=null;
        Map<String, String> section = null;
        while((line=reader.readLine())!=null) {
            if(line.trim().isEmpty()) continue;
            if(line.startsWith("[") && line.endsWith("]"))
                sections.put(line.substring(1, line.length() - 1),
                        section = new HashMap<>());
            else
//                section.put(line.split("=")[0], line.split("=")[1]);
                section.put(line.substring(0,line.indexOf("=")), line.substring(line.indexOf("=")+1));
        }
    }

    public void save(Writer writer) throws IOException {
        for(String section : sections.keySet()) {
            writer.append("[" + section + "]");
            writer.append("\n");
            List<String> keys = new ArrayList<String>();
            for (String key : sections.get(section).keySet()) keys.add(key);
            Collections.sort(keys);
            for (Object key : keys) {
                writer.append(key + "=" + sections.get(section).get(key));
                writer.append("\n");
            }
            writer.append("\n");
        }
    }

    public static Map<String, String> loadMap(String path) throws IOException {
        FileReader fr=new FileReader(path);
        BufferedReader reader = new BufferedReader(fr);
        Map<String, String> map = new HashMap<>();
        String line=null;
        while((line=reader.readLine())!=null) {
            if(line.trim().isEmpty()) continue;
            if(line.split("=").length>1) {
//                String val=line.split("=")[1].replace("\\\\", "\\");
//                val=val.replace("\\:",":");
//                map.put(line.split("=")[0], val);
                String val=line.substring(line.indexOf("=")+1).replace("\\\\", "\\");
                val=val.replace("\\:",":");
                val=val.replace("\\=","=");
                val=val.replace("\\#","#");
                map.put(line.substring(0, line.indexOf("=")), val);
            } else if(line.endsWith("="))
                map.put(line.substring(0, line.indexOf("=")), "");
        }
        reader.close();
        fr.close();
        return map;
    }

    public static void saveMap(String path, String tag, Map<String, String> map) throws IOException {
        Writer writer = new FileWriter(path);
        writer.append("#"+tag+"\n");
        writer.append("#"+new Date().toString()+"\n");
        writer.append("["+tag+"]\n");
        List<String> keys = new ArrayList<String>();
        for (String key : map.keySet()) keys.add(key);
        Collections.sort(keys);
        for (Object key : keys) {
            String val=map.get(key);
            if(val!=null) {
                val = val.replace("\\", "\\\\");
                val = val.replace(":", "\\:");
                val = val.replace("\\\\u", "\\u");
            }
            writer.append(key + "=" + val);
            writer.append("\n");
        }
        writer.append("\n");
        writer.close();
    }

    public static Map<String, String> loadMap(File f) throws IOException {
        return loadMap(f.getCanonicalPath());
    }

    public static void saveMap(File f, String tag, Map<String, String> map) throws IOException {
        saveMap(f.getCanonicalPath(), tag, map);
    }

    public Map<String, String> value(String section) {
        return  sections.get(section);
    }

    public Map<String, Map<String, String>> get() {
        return sections;
    }

    public void set(String section, Map<String, String> values) {
        if(sections!=null) {
            sections.put(section, values);
        } else {
            sections = new HashMap<>();
            sections.put(section, values);
        }
    }
}