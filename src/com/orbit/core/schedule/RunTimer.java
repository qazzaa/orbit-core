package com.orbit.core.schedule;

import com.orbit.core.kernel.Kernel;
import javax.management.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;


public class RunTimer implements RunTimerMBean {
    public Kernel kernel;
    Map<RunTimerTask,Timer> timers;

    public RunTimer() {}

    public RunTimer(Kernel knl) {
        kernel=knl;
        timers=new HashMap<>();
        try {
            Thread thread=new Thread(new Runnable() {
                @Override
                public void run() {
                    startScheduledObjects();
                }
            });
            thread.start();
        } catch (Exception e) {
            kernel.writeLog("[TIMER : ERROR]", e);
        }
    }

    @Override
    public void startByName(String Name) {
        kernel.writeLog("[TIMER : DEBUG] " + "Start timed object "+ Name);
        try {
            Statement st=kernel.ds.conn.createStatement();
            ResultSet rs = st.executeQuery("select * from TIMERS where NAME='"+Name+"'");
            while(rs.next()) {
                putTimer(rs);
                break;
            }
            rs.close();
            st.close();
        } catch (SQLException e) {
            kernel.writeLog("[TIMER : ERROR]", e);
        }
    }

    @Override
    public void stopScheduledObjects() {
        for(RunTimerTask task:timers.keySet()) {
            task.stop();
            kernel.writeLog("[TIMER : DEBUG] " + "Stop schedule " + task.getName());
        }
    }

    @Override
    public void startScheduledObjects() {
        kernel.writeLog("[TIMER : DEBUG] " + "Start timed objects");
        try {
            Statement st=kernel.ds.conn.createStatement();
            ResultSet rs = st.executeQuery("select * from TIMERS where ACTIVE=1 " +
                    "and NETWORK_ID="+kernel.currentVM.vmNetwork + " " +
                    "and (VM_ID="+kernel.currentVM.id + " " +
                    "or CLASS_ID="+kernel.currentVM.idClass+")");
            while(rs.next()) putTimer(rs);
            rs.close();
            st.close();
        } catch (SQLException e) {
            kernel.writeLog("[TIMER : ERROR]", e);
        }
    }

    public void putTimer(ResultSet rs) {
        try {
            String name = rs.getString("NAME");
            String cmd = rs.getString("COMMAND_LINE");
            String work = rs.getString("WORKING_DIR");
            int frequency = rs.getInt("FREQUENCY");
            int timeout = rs.getInt("TIMEOUT");
            RunTimerTask task = new RunTimerTask(kernel, name, cmd, frequency, timeout, work);
            Timer timer = new Timer(name, true);
//            timer.scheduleAtFixedRate(task, 0, task.getPeriod());
            timer.schedule(task, 0, task.getPeriod());
            timers.put(task, timer);
            task.timer = timer;
            kernel.writeLog("[TIMER : DEBUG] " + "Start schedule " + task.getName());


            ObjectName onTimer = new ObjectName(Kernel.packageName + ":type=tasks,name=" + name.toLowerCase());
            if (kernel.mBeanServer.isRegistered(onTimer))
                kernel.mBeanServer.unregisterMBean(onTimer);
            kernel.mBeanServer.registerMBean(task, onTimer);
        } catch (Exception e) {
            kernel.writeLog("[TIMER : ERROR]", e);
        }
    }

}
