package com.orbit.core.exec;

import com.orbit.core.kernel.Kernel;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

// EXE_WORK_DIR – рабочая директория

public class ThreadRunner extends Thread {
    private String EXE_WORK_DIR = null; // Путь к рабочей директории
    private String RUN_STRING = null;
    private Map<String, String> env_props = null;
    private Integer exit = null;
    private Process proc;
    public Kernel kernel;

    public ThreadRunner() {}

    public ThreadRunner(Kernel knl, String _run_str,String _work_dir, Map<String, String> _env_props) {
        super();
        kernel=knl;
        EXE_WORK_DIR = _work_dir;
        RUN_STRING = _run_str;
        kernel.writeLogWO("[EXECUTE : DEBUG] " +"Work directory - '" + EXE_WORK_DIR + "'...");
        env_props = _env_props;
    }

    public void run() {
        try{
            // Определяем класс запуска процесса
            ProcessBuilder launcher = new ProcessBuilder();
            // Получаем текущие переменные окружения
            Map<String,String> environment = launcher.environment();
            // Добавляем переменные окружения
            environment.putAll(env_props);
            // Перенаправляем поток с ошибочными сообщениями в стандартный поток
            launcher.redirectErrorStream(true);
            // Указываем рабочую директорию
            launcher.directory(new File(EXE_WORK_DIR));
            // Прокачка, продувка
            String[] run_string = RUN_STRING.split(" ");
            int args = run_string.length;
            List<String> commandList = new ArrayList<String>(args);
            int jar=0;
            int Xmx=0;
            for(int i=0;i<args;i++) {
                commandList.add(run_string[i]);
                if(run_string[i].contains("-jar")) jar=i;
                if(run_string[i].contains("-Xmx")) Xmx=i;
            }
            if(jar>0 && Xmx==0) {
                commandList.add(jar, "-Xmx" + kernel.manager.Xmx + "m");
                commandList.add(jar, "-Xms" + kernel.manager.Xmx + "m");
            }
            launcher.command(commandList);
            // Поехали!
            proc = launcher.start();
            InputStreamReader isr = new InputStreamReader(proc.getInputStream());
            BufferedReader output = new BufferedReader(isr);
            String line;
            while((line = output.readLine()) != null)
                kernel.writeLogWO("[EXECUTE : DEBUG] " + line);
            // Ждем окончание процесса
            exit = proc.waitFor();
            // Процесс завершился
            kernel.writeLogWO("[EXECUTE : DEBUG] " + "exit = " + exit);
            isr.close();
            output.close();
        } catch(NullPointerException ex) {
            kernel.writeLog("[EXECUTE : ERROR] ", ex);
        } catch(IndexOutOfBoundsException ex) {
            kernel.writeLog("[EXECUTE : ERROR] ", ex);
        } catch(SecurityException ex) {
            kernel.writeLog("[EXECUTE : ERROR] ", ex);
        } catch(IOException ex) {
            kernel.writeLog("[EXECUTE : ERROR] ", ex);
        } catch(InterruptedException ex) {
            kernel.writeLog("[EXECUTE : ERROR] ", ex);
        } catch(Exception ex) {
            kernel.writeLog("[EXECUTE : ERROR] ", ex);
        }
    }

    public Integer getExitValue() {
        return exit;
    }

    public Process getProcess() {
        return proc;
    }

}
