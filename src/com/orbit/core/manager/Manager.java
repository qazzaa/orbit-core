package com.orbit.core.manager;

import com.orbit.core.exec.Run;
import com.orbit.core.kernel.Kernel;
import com.orbit.core.kernel.VM;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.Predicate;

public class Manager extends TimerTask implements ManagerMBean {
    public boolean FORCE_STOP;
    public int DELAY=1000;
    public int Xmx=128;
    public Kernel kernel;
    private Vector<Message> input_queue;
    private Vector<Run> Runs;
    public Map<Integer,Integer> class_timeout;
    public Timer timer;

    public enum  Type {
        DEFAULT,
        MAXLOAD
    }
    public Type type = Type.DEFAULT;

    public Manager(Kernel knl) {
        FORCE_STOP=false;
        kernel=knl;
        timer = new Timer("Manager", false);
    }

    @Override
    public Map<String, String> callMessage(String GUID) {
        for (Message mes:input_queue)
            if(mes.GUID.equals(GUID))
                return mes.msg;
        return null;
    }

    @Override
    public void rollbackMessage(String GUID) {
        for (Message mes:input_queue)
            if(mes.GUID.equals(GUID)) {
                kernel.writeLog("[MANAGER : ROLLBACK] " + "Message "+mes.GUID+" rollback");
                mes.STATE=Message.ROLLBACK;
                mes.Run.destroy_();
                break;
            }
    }

    @Override
    public int messageState(String GUID) {
        for (Message mes:input_queue)
            if(mes.GUID.equals(GUID))
                return mes.STATE;
        return 1;
    }

    @Override
    public void inputMessage(long reg_num, String input, String operation, int class_id, int priority) {
        Message msg = new Message();
        msg.REG_NUM = reg_num;
        msg.INPUT_DIR = input;
        msg.OPERATION = operation;
        msg.CLASS_ID = class_id;
        msg.PRIORITY = priority;
        msg.MACHINE_ID = 0;
        try {
            msg.insert(kernel.ds);
        } catch (SQLException e) {
            kernel.writeLog("[MANAGER : ERROR] ", e);
        }
    }

    @Override
    public String listInputQueue() throws InterruptedException {
        return showQueue(input_queue);
    }

    public void input() {
            try {
                   if (input_queue.isEmpty()) {
                    Statement st = kernel.ds.conn.createStatement();
                    ResultSet rs = st.executeQuery("select * from " +
                            "(select a.*,row_number() over (order by a.PRIORITY desc, a.DATE_DEF asc) rn " +
                            "from QUEUE a where " +
                            "a.MACHINE_ID=" + kernel.currentVM.id +
                            " and " +
                            "a.CLASS_ID=" + kernel.currentVM.idClass +
                            " and " +
                            "a.STATE=" + Message.INPUT +
                            ") where rn<=1");
                    synchronized (input_queue) {
                        while (rs.next()) {
                            Message msg = new Message(rs);
                            msg.STATE = Message.RECEIVE;
                            input_queue.add(msg);
                        }
                    }
                    rs.close();
                    st.close();
                    if (input_queue.isEmpty()) {
                        st = kernel.ds.conn.createStatement();
                        st.executeUpdate("update QUEUE set " +
                                "MACHINE_ID=" + kernel.currentVM.id + " " +
                                "where GUID in (select GUID from (select a.*,row_number() over (order by a.PRIORITY desc, a.DATE_DEF asc) rn " +
                                "from QUEUE a where " +
                                "a.MACHINE_ID=0" +
                                " and " +
                                "a.CLASS_ID=" + kernel.currentVM.idClass +
                                " and " +
                                "a.STATE=" + Message.INPUT +
                                ") where rn<=1)");
                        st.close();
                    }
                }
                kernel.writeLog("[MANAGER : DEBUG] " +
                        " message input " + kernel.currentVM.vmName);
            } catch (Exception e) {
                kernel.writeLog("[MANAGER : ERROR] ", e);
            }
    }

    public void load() {
        try {
            input_queue = new Vector<>();
            try {
                Statement st = kernel.ds.conn.createStatement();
                ResultSet rs = st.executeQuery("select * from QUEUE where " +
                        "STATE not in (" + Message.FAILED + "," + Message.ROLLBACK + ") " +
                        "and MACHINE_ID=" + kernel.currentVM.id + " " +
                        "order by PRIORITY desc, DATE_DEF asc");
                while (rs.next()) {
                    Message msg = new Message(rs);
                    if(!msg.inQueue(input_queue)) {
                        msg.STATE = Message.RECEIVE;
                        input_queue.add(msg);
                    }
                }
                rs.close();
                st.close();
            } catch (Exception e) {
                kernel.writeLog("[MANAGER : ERROR] ", e);
            }
        } catch (ConcurrentModificationException e) {
            kernel.writeLog("[MANAGER : WARNING] " + "Concurrent modification", e);
        }
    }

    @Override
    public void classTimeout() {
        try {
            synchronized (class_timeout) {
                class_timeout.clear();
                try {
                    Statement st = kernel.ds.conn.createStatement();
                    ResultSet rs = st.executeQuery("select * from CLASS");
                    while (rs.next())
                        if(rs.getInt("TIMEOUT")>0)
                            class_timeout.put(rs.getInt("ID"), rs.getInt("TIMEOUT") * 1000 + DELAY * 2);
                        else
                            class_timeout.put(rs.getInt("ID"), -1);
                    rs.close();
                    st.close();
                } catch (Exception e) {
                    kernel.writeLog("[MANAGER : ERROR] ", e);
                }
            }
        } catch (ConcurrentModificationException e) {
            kernel.writeLog("[MANAGER : WARNING] " + "Concurrent modification", e);
        }
    }

    @Override
    public int getInputQueueCount() {
        return input_queue.size();
    }

    @Override
    public int getFreeQueueCount() {
        return kernel.getMaxMsgCount() - (input_queue.size());
    }

    @Override
    public void setdelaymsec(int delay) {
        DELAY=delay;
    }

    @Override
    public int getdelaymsec() {
        return DELAY;
    }

    public String showQueue(Vector<Message> queue) {
        String res="";
        try {
            synchronized (queue) {
                for (Message mes : queue) {
                    res += "[Message:" + mes.GUID;
                    res += "|State:" + mes.getState();
                    res += "|Class:" + mes.CLASS_ID;
                    res += "|Machine:" + mes.MACHINE_ID;
                    res += "|Operation:" + mes.OPERATION;
                    res += "|Priority:" + mes.PRIORITY;
                    res += "|Time:" + mes.DATE_DEF.toLocalDateTime().toString();
                    res += "]\n";
                }
            }
        } catch (ConcurrentModificationException e) {
            kernel.writeLog("[MANAGER : WARNING] " + "Concurrent modification", e);
        }
        return res;
    }

    @Override
    public void setXmx(int Xmx) {
        Xmx=Xmx;
    }

    @Override
    public int getXmx() {
        return Xmx;
    }

    @Override
    public int getRunsCount() {
        return Runs.size();
    }

    @Override
    public int getType() {
        switch (type) {
            case DEFAULT: return 0;
            case MAXLOAD: return 1;
            default: return 0;
        }
    }

    @Override
    public void setType(int t) {
        switch (t) {
            case 0: type=Type.DEFAULT; break;
            case 1: type=Type.MAXLOAD; break;
            default: type=Type.DEFAULT; break;
        }
    }

    @Override
    public String getTypeValue() {
        switch (type) {
            case DEFAULT: return "DEFAULT";
            case MAXLOAD: return "MAXLOAD";
            default: return "DEFAULT";
        }
    }

    public void Processing() {
        for(Run run : Runs)
            run.ping();
        Runs.removeIf(not_in_progress);
        if(Runs.size() < kernel.getMaxRunsCount())
            synchronized (input_queue) {
                for (Message mes : input_queue)
                    if (mes.STATE == Message.WAITING
                            && mes.CLASS_ID == kernel.currentVM.idClass
                            && Runs.size() < kernel.getMaxRunsCount()) {
                        try {
                            mes.Run = new Run(kernel, mes);
                            Runs.add(mes.Run);
                        } catch (Exception e) {
                            kernel.writeLog("[MANAGER : ERROR] ", e);
                            mes.STATE = Message.ROLLBACK;
                        }
                        if (Runs.size() >= kernel.getMaxRunsCount()) break;
                    }
            }
    }

    public void Suspending() {
        for(Run run : Runs)
            run.ping();
        Runs.removeIf(not_in_progress);
    }

    public Predicate<? super Message> rollback=new Predicate<Message>() {
        @Override
        public boolean test(Message message) {
            if(message.STATE==Message.ROLLBACK) return true;
            return false;
        }
    };

    public Predicate<? super Message> input=new Predicate<Message>() {
        @Override
        public boolean test(Message message) {
            if(message.STATE==Message.INPUT) return true;
            return false;
        }
    };

    public Predicate<? super Message> receive=new Predicate<Message>() {
        @Override
        public boolean test(Message message) {
            if(message.STATE==Message.RECEIVE) return true;
            return false;
        }
    };

    public Predicate<? super Message> waiting=new Predicate<Message>() {
        @Override
        public boolean test(Message message) {
            if(message.STATE==Message.WAITING) return true;
            return false;
        }
    };

    public Predicate<? super Message> send=new Predicate<Message>() {
        @Override
        public boolean test(Message message) {
            if(message.STATE==Message.SEND) return true;
            return false;
        }
    };

    public Predicate<? super Message> remove=new Predicate<Message>() {
        @Override
        public boolean test(Message message) {
            if(message.STATE==Message.REMOVE) return true;
            return false;
        }
    };

    Predicate<? super Run> not_in_progress=new Predicate<Run>() {
        @Override
        public boolean test(Run run) {
            if(run.message.STATE!=Message.IN_PROGRESS) return true;
            return false;
        }
    };

    public Comparator<Message> priority = new Comparator<Message>() {
        @Override
        public int compare(Message o1, Message o2) {
            if(o1.PRIORITY>o2.PRIORITY) return -1;
            if(o1.PRIORITY==o2.PRIORITY) return 0;
            return 1;
        }
    };

    public void start() {
        stop();
        Runs=new Vector<>();
        class_timeout = new HashMap<>();
        classTimeout();
        load();
        timer = new Timer("Manager", false);
        timer.schedule(this, 0, DELAY);
        kernel.writeLog("[MANAGER : DEBUG] " + "Start queue manager");
    }

    public void stop() {
        if(timer!=null)
            timer.cancel();
        if(input_queue != null)
            try {
                synchronized (input_queue) {
                    for (Message mes : input_queue) {
                        if (mes.Run != null)
                            mes.Run.destroy_();
                        mes.STATE = Message.ROLLBACK;
                        mes.update(kernel.ds);
                        kernel.writeLog("[MANAGER : ROLLBACK] " +
                                "Rollback message due to stop " + mes.GUID);
                    }
                }
            } catch (Exception e) {
                kernel.writeLog("[MANAGER : ERROR] ", e);
            }
        kernel.writeLog("[MANAGER : DEBUG] " + "Stop queue manager");
    }


    @Override
    public void run() {
        kernel.writeLog("[MANAGER : DEBUG] " + "Queue manager health normal");
        if(!kernel.currentVM.isControl()) {
            if (kernel.currentVM.vmHealth() == VM.RUN) {
                try {
                    Processing();
                    input();
                } catch (Exception e) {
                    kernel.writeLog("[MANAGER : ERROR] ", e);
                }
                synchronized (input_queue) {
                    for (Message mes : input_queue) {
                        if (mes.STATE == Message.IN_PROGRESS)
                            mes.ping(kernel);
                        if (mes.STATE == Message.RECEIVE)
                            if (mes.CLASS_ID == kernel.currentVM.idClass) {
                                try {
                                    mes.STATE = Message.WAITING;
                                    mes.update(kernel.ds);
                                } catch (Exception e) {
                                    kernel.writeLog("[MANAGER : ERROR] ", e);
                                }
                            } else {
                                mes.STATE = Message.ROLLBACK;
                            }
                        if (mes.STATE == Message.ROLLBACK) {
                            try {
                                mes.update(kernel.ds);
                            } catch (Exception e) {
                                kernel.writeLog("[MANAGER : ERROR] ", e);
                            }
                            kernel.writeLog("[MANAGER : ROLLBACK] " +
                                    "Rollback message from engine " + mes.GUID);
                        }
                        if (mes.STATE == Message.REMOVE)
                            try {
                                mes.delete(kernel.ds);
                                kernel.writeLog("[MANAGER : REMOVE] " +
                                        "Remove message " + mes.GUID);
                            } catch (Exception e) {
                                kernel.writeLog("[MANAGER : ERROR] ", e);
                            }
                    }
                    input_queue.removeIf(send);
                    input_queue.removeIf(rollback);
                    input_queue.removeIf(remove);
                }
            }
            if (kernel.currentVM.vmHealth() == VM.SUSPENDING) {
                try {
                    Suspending();
                    synchronized (input_queue) {
                        for (Message mes : input_queue) {
                            if (mes.STATE == Message.IN_PROGRESS)
                                mes.ping(kernel);
                            if (mes.STATE == Message.ROLLBACK) {
                                try {
                                    mes.update(kernel.ds);
                                } catch (Exception e) {
                                    kernel.writeLog("[MANAGER : ERROR] ", e);
                                }
                                kernel.writeLog("[MANAGER : ROLLBACK] " +
                                        "Rollback message from engine " + mes.GUID);
                            }
                            if (mes.STATE == Message.REMOVE)
                                try {
                                    mes.delete(kernel.ds);
                                    kernel.writeLog("[MANAGER : REMOVE] " +
                                            "Remove message " + mes.GUID);
                                } catch (Exception e) {
                                    kernel.writeLog("[MANAGER : ERROR] ", e);
                                }
                            if (mes.STATE == Message.RECEIVE
                                    || mes.STATE == Message.WAITING
                                    || mes.STATE == Message.PREPARE) {
                                mes.STATE = Message.ROLLBACK;
                            }
                        }
                        input_queue.removeIf(send);
                        input_queue.removeIf(rollback);
                        input_queue.removeIf(remove);
                    }
                } catch (Exception e) {
                    kernel.writeLog("[MANAGER : ERROR] ", e);
                }
                if (input_queue.size() == 0) FORCE_STOP = true;
            }
        }
    }
}