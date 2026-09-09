package com.orbit.core.exec;

import com.orbit.core.kernel.Kernel;
import java.io.File;
import java.io.IOException;

public class Task_Kill {
    Process pr;
    private Kernel kernel;

    public Task_Kill() {}

    public Task_Kill(Kernel knl) {
        super();
        kernel = knl;
    }

    public int kill_by_pid(long _pid) throws IOException, InterruptedException {
        String command = "taskkill /F /T /PID " + String.valueOf(_pid);
        kernel.writeLog("[EXECUTE : DEBUG] " + "Kill command - " + command);
        String expr[] = new String[]{"cmd.exe", "/c",  command};
        pr = Runtime.getRuntime().exec(expr);
        return(pr.waitFor());
    }

    public int kill_by_name(String _run_string) throws IOException, InterruptedException {
        String[] lex_arr = _run_string.split(" ");
        String[] task_name = lex_arr[0].split(File.separator + File.separator);
        if(task_name[task_name.length - 1].trim().equalsIgnoreCase("java.exe")) {
            kernel.writeLog("[EXECUTE : DEBUG] " + "Java is not to be killed...");
        }
        else {
            String command = "taskkill /F /T /IM " + task_name[task_name.length - 1];
            kernel.writeLog("[EXECUTE : DEBUG] " + "Kill command - " + command);
            String expr[] = new String[]{"cmd.exe", "/c",  command};
            pr = Runtime.getRuntime().exec(expr);
            return(pr.waitFor());
        }
        return(0);
    }

    public static void main(String[] args) {
    }
}
