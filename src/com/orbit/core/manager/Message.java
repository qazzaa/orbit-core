package com.orbit.core.manager;

import com.orbit.core.exec.Run;
import com.orbit.core.kernel.Kernel;
import com.orbit.core.kernel.VM;
import com.orbit.core.utils.ds;

import java.sql.*;
import java.util.*;
import java.util.Date;

public class Message {
    public final static int FAILED  = -1;
    public final static int INPUT = 0;
    public final static int ROLLBACK  = 1;
    public final static int RECEIVE  = 2;
    public final static int WAITING = 3;
    public final static int REMOVE  = 4;
    public final static int SEND  = 5;
    public final static int IN_PROGRESS  = 6;
    public final static int PREPARE  = 7;


    public String getState() {
        return getState(STATE);
    }
    public static String getState(int iState) {
        String state=null;
        switch (iState) {
            case -1 : state="FAILED"; break;
            case 0 : state="INPUT"; break;
            case 1 : state="ROLLBACK"; break;
            case 2 : state="RECEIVE"; break;
            case 3 : state="WAITING"; break;
            case 4 : state="REMOVE"; break;
            case 5 : state="SEND"; break;
            case 6 : state="IN_PROGRESS"; break;
            case 7 : state="PREPARE"; break;
            default : state=null;
        } 
        return state;
    }

    public String GUID;
    public Timestamp DATE_DEF;
    public Timestamp DATE_CNG;
    public String INPUT_DIR;
    public String OPERATION;
    public int STATE;
    public int CLASS_ID;
    public int MACHINE_ID;
    public int PRIORITY;
    public Map<String,String> msg;

    public Run Run;

    public Message() {}

    public Message(ResultSet rs) throws SQLException {
        fit(rs);
    }

    public Message(Map<String, String> msgMap) {
        fit(msgMap);
    }

    public void fit(ResultSet rs) throws SQLException {
        GUID = rs.getString("GUID");
        DATE_DEF = rs.getTimestamp("DATE_DEF");
        DATE_CNG = rs.getTimestamp("DATE_CNG");
        INPUT_DIR = rs.getString("INPUT_DIR");
        OPERATION = rs.getString("OPERATION");
        STATE = rs.getInt("STATE");
        CLASS_ID = rs.getInt("CLASS_ID");
        MACHINE_ID = rs.getInt("MACHINE_ID");
        PRIORITY = rs.getInt("PRIORITY");
        msgSet();
    }

    public void fit(Map<String, String> msgMap) {
        msg=msgMap;
        GUID = msg.get("KEY_GUID");
        DATE_DEF = new Timestamp(Long.parseLong(msg.get("KEY_DATE_DEF")));
        DATE_CNG = new Timestamp(Long.parseLong(msg.get("KEY_DATE_CNG")));
        INPUT_DIR = msg.get("KEY_INPUT_DIR");
        OPERATION = msg.get("KEY_OPERATION");
        STATE = Integer.parseInt(msg.get("KEY_STATE"));
        CLASS_ID = Integer.parseInt(msg.get("KEY_CLASS_ID"));
        MACHINE_ID = Integer.parseInt(msg.get("KEY_MACHINE_ID"));
        PRIORITY = Integer.parseInt(msg.get("KEY_PRIORITY"));
    }

    public void msgSet() {
        msg=new HashMap<>();
        msg.put("KEY_GUID", GUID);
        msg.put("KEY_DATE_DEF", String.valueOf(DATE_DEF.getTime()));
        msg.put("KEY_DATE_CNG", String.valueOf(DATE_CNG.getTime()));
        msg.put("KEY_INPUT_DIR", INPUT_DIR);
        msg.put("KEY_OPERATION", OPERATION);
        msg.put("KEY_STATE", String.valueOf(STATE));
        msg.put("KEY_CLASS_ID", String.valueOf(CLASS_ID));
        msg.put("KEY_MACHINE_ID", String.valueOf(MACHINE_ID));
        msg.put("KEY_PRIORITY", String.valueOf(PRIORITY));
    }

    public void msgSet(Map<String, String> key) {
        for (Object e : key.keySet())
            msg.put("KEY_"+(String) e, key.get((String) e));
    }

    public void change() {
        DATE_CNG=new Timestamp(System.currentTimeMillis());
    }

    public void update(ds ds) throws SQLException {
        change();
        PreparedStatement pst=ds.conn.prepareStatement("update QUEUE set "+
                "STATE="+STATE+
                ",CLASS_ID="+CLASS_ID+
                ",MACHINE_ID="+MACHINE_ID+
                ",DATE_CNG=? "+
                "where GUID='"+GUID+"'");
        pst.setTimestamp(1, DATE_CNG);
        pst.executeUpdate();
        pst.close();
        msgSet();
    }

    public void delete(ds ds) throws SQLException {
        PreparedStatement pst=ds.conn.prepareStatement("delete from QUEUE " +
                "where GUID='"+GUID+"'");
        pst.executeUpdate();
        pst.close();
    }

    public void insert(ds ds) throws SQLException {
        DATE_DEF=new Timestamp(new Date().getTime());
        DATE_CNG=new Timestamp(new Date().getTime());
        STATE=Message.INPUT;
        GUID=UUID.randomUUID().toString().replace("-","").toUpperCase();
        msgSet();
        String strsql = "insert into QUEUE(INPUT_DIR,OPERATION,CLASS_ID,PRIORITY,MACHINE_ID,GUID) " +
                "values(?,?,?,?,?,?,?)";
        PreparedStatement pst = ds.conn.prepareStatement(strsql);
        pst.setString(1, INPUT_DIR);
        pst.setString(2, OPERATION);
        pst.setInt(3, CLASS_ID);
        pst.setInt(4, PRIORITY);
        pst.setInt(5, MACHINE_ID);
        pst.setString(6, GUID);
        pst.executeUpdate();
        pst.close();
    }

    public void load(ds ds) throws SQLException {
        PreparedStatement pst=ds.conn.prepareStatement("select * from QUEUE where GUID='"+GUID+"'");
        ResultSet rs=pst.executeQuery();
        while (rs.next()) {
            fit(rs);
            break;
        }
        pst.close();
        rs.close();
    }

    public boolean inQueue(List<Message> queue) {
        synchronized (queue) {
            for (Message mes : queue)
                if (mes.GUID.equals(GUID)) return true;
            return false;
        }
    }

    public int ping(Kernel kernel) {
        try {
            long mTs = new Date().getTime() - DATE_CNG.getTime();
            if(kernel.manager.class_timeout.get(CLASS_ID) > 0)
                if (mTs > kernel.manager.class_timeout.get(CLASS_ID))
                    if (STATE == Message.IN_PROGRESS) {
                        if(Run != null)
                            Run.destroy_();
                        STATE = Message.ROLLBACK;
                        kernel.writeLog("[MANAGER : ROLLBACK] " +
                                "Rollback message " + GUID + ". Timeout in " + mTs);
                    }
        } catch (NullPointerException e) {
            STATE = Message.ROLLBACK;
            kernel.writeLog("[MANAGER : ERROR] "+
                    "Rollback message due to error " + GUID, e);
        }                
        return STATE;
    }


}
