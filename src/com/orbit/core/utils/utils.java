package com.orbit.core.utils;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;

import java.io.*;
import java.net.MalformedURLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;


public class utils {

    public static String get_file_number(long _number){
        String DECIMAL_FORMAT = "000";
        DecimalFormat format = new DecimalFormat(DECIMAL_FORMAT);
        return (format.format(_number));
    }

    public static String set_date(Date date) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd' 'HH:mm:ss");
        return (format.format(date));
    }

    public static String set_date(Timestamp date) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd' 'HH:mm:ss");
        return (format.format(date));
    }

    public static String set_date_oracle(Date date) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd' 'HH:mm:ss");
        return dateToOracleFormat(format.format(date));
    }

    public static String set_date_oracle(Timestamp date) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd' 'HH:mm:ss");
        return dateToOracleFormat(format.format(date));
    }

    public static String dateToOracleFormat(String dateStr) {
        return "TO_TIMESTAMP('"+dateStr+"','YYYY-MM-DD HH24\\:MI\\:SS.FF')";
    }

    public static String set_date() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
        return format.format(new Date());
    }

    public static String ReadFile(String filename)
            throws IOException
    {
        String line = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "CP1251"));
        StringBuilder stringBuilder = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        String filestring = stringBuilder.toString();
        reader.close();
        return filestring;
    }
    public static ArrayList<String> StringToArrayList(String string)
            throws IOException
    {
        ArrayList<String> array;
        String str=(string.replaceAll(" ", ",")).replaceAll("\n", ",");
        String new_str="";
        if(str.length()==(str.lastIndexOf(",")+1)){
            new_str =str.substring(0,str.length()-1);
        }else{
            new_str =str;
        }
        //System.out.println("new_str: "+new_str);
        array= new ArrayList<String>(Arrays.asList(new_str.split(",")));
        return array;
    }
    public static void delete(File file)
            throws IOException
    {
        if(!file.exists()){
            return;
        }
        if(file.isDirectory()){
            for(File f:file.listFiles()){
                delete(f);
                file.delete();
            }
        }else{
            file.delete();
        }
    }
    public static File CopyFile(String source, String target)
            throws IOException
    {
        File f1 = new File(source);
        File f2 = new File(target);
        RandomAccessFile input = new RandomAccessFile(f1, "r");
        RandomAccessFile output = new RandomAccessFile(f2, "rw");
        try
        {
            byte[] buf = new byte[65536];
            long len = input.length();
            output.setLength(len);
            int bytesRead;
            while ((bytesRead = input.read(buf, 0, buf.length)) > 0) {
                output.write(buf, 0, bytesRead);
            }
        }
        catch (IOException e)
        {
            long len;
            e.printStackTrace();
            return null;
        }
        finally
        {
            input.close();
            output.close();
        }
        return f2;
    }

    public static SmbFile[] getSmbFiles(SmbFile dir) throws SmbException {
        SmbFileFilter filter = new SmbFileFilter() {
            @Override
            public boolean accept(SmbFile pathname) throws SmbException {
                return pathname.isFile();  //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        SmbFile[] files=dir.listFiles(filter);
        Arrays.sort(files, new Comparator<SmbFile>() {
            @Override
            public int compare(SmbFile o1, SmbFile o2) {
                try {
                    return o1.lastModified()>o2.lastModified() ? 1 : 0;  //To change body of implemented methods use File | Settings | File Templates.
                } catch (SmbException e) {
                    e.printStackTrace();
                }
                return 0;
            }
        });
        return files;
    }

    public static Timestamp timeFirstModifiedSmb(String box) throws MalformedURLException, SmbException {
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, "tko", "tko");
        SmbFile smbDir = null;
        String filepath=box;
        filepath=filepath.replace("\\","/");
        if(!filepath.endsWith("/")) filepath+="/";
        smbDir = new SmbFile("smb:"+filepath, auth);
        if(!smbDir.exists() || !smbDir.isDirectory()) return null;
        SmbFile[] files=getSmbFiles(smbDir);
        if(files.length == 0) return null;
        return new Timestamp(files[0].lastModified());
    }

    public static Timestamp timeLastModifiedSmb(String box) throws SmbException, MalformedURLException {
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, "tko", "tko");
        SmbFile smbDir = null;
        String filepath=box;
        filepath=filepath.replace("\\","/");
        if(!filepath.endsWith("/")) filepath+="/";
        smbDir = new SmbFile("smb:"+filepath, auth);
        if(!smbDir.exists() || !smbDir.isDirectory()) return null;
        SmbFile[] files=getSmbFiles(smbDir);
        if(files.length == 0) return null;
        return new Timestamp(files[files.length-1].lastModified());
    }

    public static Timestamp timeFirstModified(String box) {
        File fBoxOut=new File(box);
        if(!fBoxOut.exists() || !fBoxOut.isDirectory()) return null;
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile();  //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        File[] files=fBoxOut.listFiles(filter);
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.lastModified()>o2.lastModified() ? 1 : 0;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        if(files.length == 0) return null;
        return new Timestamp(files[0].lastModified());
    }

    public static Timestamp timeLastModified(String box) {
        File fBoxOut=new File(box);
        if(!fBoxOut.exists() || !fBoxOut.isDirectory()) return null;
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile();  //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        File[] files=fBoxOut.listFiles(filter);
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.lastModified()>o2.lastModified() ? 1 : 0;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        if(files.length == 0) return null;
        return new Timestamp(files[files.length-1].lastModified());
    }

    public static int countBoxIE(String box) {
        int cnt=0;
        try {
            File fBox=new File(box);
            if(!fBox.exists() || !fBox.isDirectory()) return -1;
            FileFilter filter = new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isFile();  //To change body of implemented methods use File | Settings | File Templates.
                }
            };
            File[] files=fBox.listFiles(filter);
            cnt=files.length;
            return cnt;
        } catch (Exception ex) {
            ex.printStackTrace();
            return -1;
        }
    }

    public static void mail(String text, int code, Map<String, String> ini) throws IOException {
        String[] sRecipient=ini.get("MAIL_OPERATOR").split(",");
        String sender=ini.get("MAIL_SENDER");
        String server=ini.get("MAIL_SERVER");
        String subject="Supervisor System. Status: "+code;
        for(String recipient : sRecipient)
            email.send(recipient,sender,server,subject,text);
    }

    public static String toStr(String str,int maxLen){
        if(str.length()>maxLen) return str;
        int cnt=maxLen-str.length();
        for (int i=0;i<cnt;i++) str+=" ";
        return  str;
    }

    public static String getId_work(){
        String DECIMAL_FORMAT = "0000000000";
        DecimalFormat format = new DecimalFormat(DECIMAL_FORMAT);
        String id_work_str = (
                new Long(System.currentTimeMillis()).toString()).
                hashCode() + "";
        long uis_l = Long.parseLong(id_work_str);
        String id_work = format.format(uis_l);
        if (id_work.charAt(0) == '-') {
            id_work = id_work.replace('-', '1');
        } else {
            id_work = "2" + id_work;
        }
        return id_work;
    }

    public static String getId_work(long id){
        String DECIMAL_FORMAT = "0000000000";
        DecimalFormat format = new DecimalFormat(DECIMAL_FORMAT);
        String id_work_str = (new Long(id).toString() +
                new Long(System.currentTimeMillis()).toString()).hashCode() + "";
        long uis_l = Long.parseLong(id_work_str);
        String id_work = format.format(uis_l);
        if (id_work.charAt(0) == '-') {
            id_work = id_work.replace('-', '1');
        }
        else {
            id_work = "2" + id_work;
        }
        return id_work;
    }

    public static String getHexString(byte[] b)
    {
        String result = "";
        for (int i=0; i < b.length; i++)
        {
            result +=
                    Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring(1);
        }
        return result;
    }

    public static int ExecNoThread(String[] cmd, String workdir, boolean bWriteOut)
            throws IOException, InterruptedException {
        if (cmd==null) { System.out.println("cmd is NULL"); return -1; }
        String prog_name=cmd[0];

        System.out.println("execute "+prog_name);

        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        String envp[] = null;
        File dir= new File(workdir);
        process = runtime.exec(cmd, envp, dir);

        //Run();
        InputStream is= process.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line=null;
        while ( (line = br.readLine()) != null) {
            if (bWriteOut) { System.out.println(line); }
        }

        process.waitFor();
        System.out.println("process exit value : "+process.exitValue());
        return process.exitValue();
    }

    public static int ExecNoThread(String[] cmd, String workdir, String nName)
            throws IOException, InterruptedException {
        if (cmd==null) { System.out.println("cmd is NULL"); return -1; }
        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        String envp[] = null;
        File dir= new File(workdir);
        process = runtime.exec(cmd, envp, dir);
        InputStreamReader isr = new InputStreamReader(process.getInputStream());
        BufferedReader output = new BufferedReader(isr);
        new File("./nm_log/"+nName).mkdirs();
        log _log = new log("./nm_log/"+nName);
        _log.add_timestamp = false;
        String line;
        while((line = output.readLine()) != null)
            _log.append(line);

        return process.exitValue();
    }
}
