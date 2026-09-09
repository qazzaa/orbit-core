package com.orbit.core.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class log {

    private File logfile = null;
    private FileWriter fr = null;
    private Timestamp ts = new Timestamp(System.currentTimeMillis());
    private String dir_name = null;
    private String file_name = null;
    private int files_num = 10;
    private int current_file = 1;
    private int file_max_len = 100000000;
    private boolean initiated = false;
    public boolean add_timestamp = true;


    public log() {}

    public log(String dir) {
//      this.dir_name = System.getProperty("user.dir");
        this.dir_name = dir;
        this.file_name = "log";
// Define current file number
        File dir_File = new File(this.dir_name);
        String s[] = dir_File.list();
        if(s == null) {
            System.out.println("No such directory - '" + this.dir_name + "'...");
        }
        File f_log = null;
        long max_time = 0;
        for (int k = 0; k < s.length; k++) {
            String[] parts = s[k].split("[.]");
            if(parts.length != 2) {
                continue;
            }
            if(parts[1].equalsIgnoreCase(this.file_name)) {
                try {
                    int num = Integer.parseInt(parts[0]);
                    if(num <= this.files_num) {
                        f_log = new File(this.dir_name + File.separator + s[k]);
                        if(max_time <= f_log.lastModified()) {
                            max_time = f_log.lastModified();
                            this.current_file = num;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void append(String _mes) {
        boolean append = false;
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        this.logfile = new File(this.dir_name + File.separator + String.valueOf(this.current_file) +
                "." + this.file_name);
        if((this.logfile.length() + _mes.length()) > this.file_max_len) {
            this.current_file++;
            if(this.current_file > this.files_num) {
                this.current_file = 1;
            }
            this.logfile = new File(this.dir_name + File.separator + String.valueOf(this.current_file) +
                    "." + this.file_name);
        }
        else {
            append = true;
        }
        try {
            this.fr = new FileWriter(this.logfile, append);
            if(add_timestamp)
                this.fr.write(sdf.format(new Date(System.currentTimeMillis())) + " - " + new String(_mes.getBytes(), "CP1251"));
            else
                this.fr.write(new String(_mes.getBytes(), "CP1251"));
            this.fr.write(0x0a);
            this.fr.close();
//          System.out.println(sdf.format(new Date(System.currentTimeMillis())) + " - " + new String(_mes.getBytes(), "CP1251"));
        }
        catch (IOException ex) {
            System.out.println("MyLog: Write error... ["+_mes+"]");
            System.out.println(ex);
        }
    }
}
