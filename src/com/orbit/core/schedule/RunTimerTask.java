package com.orbit.core.schedule;

import com.orbit.core.exec.ThreadRunner;
import com.orbit.core.exec.ThreadMessage;
import com.orbit.core.kernel.Kernel;
import com.orbit.core.utils.utils;
import javax.management.ObjectName;
import java.sql.Timestamp;
import java.util.*;

public class RunTimerTask extends TimerTask implements RunTimerTaskMBean {
    private String NAME = "";
    private String COMMAND = "";
    private String WORK = ".";
    private int FREQ = 0;
    private int TIMEOUT = -1;
    public Kernel kernel;
    public Timestamp started;
    public ThreadRunner threadExeRunner;
    public ThreadMessage threadMessage;
    Timer timer;

    public RunTimerTask() {}

    public RunTimerTask(Kernel knl, String Name, String Cmd, int Frequency, int Timeout, String Work) {
        this.NAME = Name;
        this.COMMAND = Cmd;
        this.FREQ = Frequency;
        this.TIMEOUT = Timeout;
        this.WORK = Work;
        kernel=knl;
    }

    @Override
    public void run() {
        try {
            if (this.COMMAND.isEmpty() || this.FREQ == 0) {
                kernel.writeLog("[TIMER : DEBUG] " + "Task " + this.NAME + " not set in Data Base");
            }
            kernel.writeLogWO("[TIMER : DEBUG] " + "Task " + this.NAME + " started");
            started=new Timestamp(System.currentTimeMillis());
            threadExeRunner = new ThreadRunner(kernel, this.COMMAND, this.WORK, new HashMap<>());
            threadMessage = new ThreadMessage(threadExeRunner, this.TIMEOUT, this.COMMAND);
            threadExeRunner.start();
            threadMessage.run();
            kernel.writeLogWO("[TIMER : DEBUG] " + "Task " + this.NAME + " finished");
        } catch (Exception e) {
            kernel.writeLog("[TIMER : ERROR]", e);
        }
    }

    @Override
    public int getPeriod() {
        return this.FREQ;
    }

    @Override
    public String getName() {
        return this.NAME;
    }

    @Override
    public String getState() {
        if(threadExeRunner.getProcess().isAlive()) return "RUNNING";
        return "ENDED";
    }

    @Override
    public String getStarted() {
        return utils.set_date(this.started);
    }

    @Override
    public void stop() {
        threadExeRunner.getProcess().destroy();
        timer.cancel();
        try {
            ObjectName onTimer = new ObjectName(Kernel.packageName + ":type=tasks,name="+NAME.toLowerCase());
            if(kernel.mBeanServer.isRegistered(onTimer))
                kernel.mBeanServer.unregisterMBean(onTimer);
        } catch (Exception e) {
            kernel.writeLog("[TIMER : ERROR]", e);
        }
    }

    @Override
    public String getInfo() {
        String str="Timer:" + this.getName() + " Period:" + this.getPeriod() +
                " Started:" + this.getStarted() +
                " State:" + this.getState();
        if(this.TIMEOUT>0) str+=" Timeout:" + this.TIMEOUT;
        return str;
    }
}
