package com.orbit.core.utils;

import com.orbit.core.kernel.Kernel;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.Date;

public class ds {
    public DataSource ds = null;
    public Connection conn = null;
    public int MaxLookUpMin=20;
    public Timestamp lookUpTime;
    public Kernel kernel;

    public ds() {}

    public ds(Kernel knl) {
        super();
        kernel=knl;
        lookUp();
    }

    public void lookUp() {
        Timestamp curTimestamp=new Timestamp(System.currentTimeMillis());
        lookUpTime=curTimestamp;
    }

    public boolean lookUpCheck() {
        long mTs = new Date().getTime() - lookUpTime.getTime();
        if(mTs>MaxLookUpMin*60*1000) return true;
        return false;
    }

    public void Init(String dsStr) throws ClassNotFoundException, SQLException {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] " + "Error when found OraDriver", ex);
            throw ex;
        }
        try {
            this.conn = DriverManager.getConnection(dsStr);
            kernel.writeLog("[DATASOURCE : INIT] " + "DataSourse init OK");
        } catch (SQLException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] " + "Error create connection to bd", ex);
            throw ex;
        }
    }

    public Map<String, String> getOperationParams(String Operation, String Class) {
        Map<String, String> Params=new HashMap<>();
        try {
            String strsql = "select PAR_NAME, PAR_VALUE from OPERATION_PARAMS " +
                    "where OPERATION='"+Operation+"' " +
                    "and CLASS='"+Class+"'";
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next()) {
                Params.put(rs.getString("PAR_NAME"), rs.getString("PAR_VALUE"));
            }
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
        } catch (SQLException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] ", ex);
            throw new RuntimeException (ex.toString());
        }
        return Params;
    }

    public void execute(String sql)
    {
        try {
            Statement st=conn.createStatement();
            st.executeUpdate(sql);
            if(st!=null) st.close();
        } catch (SQLException ex) {
            throw new RuntimeException (ex.toString());
        }
    }


    public int getClassId(String Operation)
    {
        int id=0;
        try {
            String strsql = "select ID from CLASS,OPERATION_PARAMS where CLASS.NAME=OPERATION_PARAMS.CLASS " +
                    "and OPERATION='"+Operation+"'";
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next()) {
                id=rs.getInt(1);
                break;
            }
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
        } catch (SQLException ex) {
            throw new RuntimeException (ex.toString());
        }
        return id;
    }

    public int getClassIdByName(String Class)
    {
        int id=0;
        try {
            String strsql = "select ID from CLASS where CLASS.NAME='"+Class+"'";
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next()) {
                id=rs.getInt(1);
                break;
            }
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
        } catch (SQLException ex) {
            throw new RuntimeException (ex.toString());
        }
        return id;
    }

    public String getOperationProfile(String Operation, int ClassId)
    {
        String profile=null;
        try {
            String strsql = "select PROFILE from OPERATION_PROFILE where " +
                    "OPERATION='"+Operation+"' and CLASS=(select NAME from CLASS where ID="+ClassId+")";
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next()) {
                profile=rs.getString(1);
                break;
            }
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
            if(profile == null) {
                strsql = "select PROFILE from OPERATION_PROFILE where " +
                        "OPERATION=(select distinct(TEMPLATE) from PARAMS_MAP where OPERATION='"+Operation+"') " +
                        "and CLASS=(select NAME from CLASS where ID="+ClassId+")";
                pst=conn.prepareStatement(strsql);
                rs=pst.executeQuery();
                while (rs.next()) {
                    profile=rs.getString(1);
                    break;
                }
                if(rs!=null) rs.close();
                if(pst!=null) pst.close();
            }                                         
        } catch (SQLException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] ", ex);
            throw new RuntimeException (ex.toString());
        }
        return profile;
    }

    public Map<Integer,Integer> getOperationClasses(String Operation)
    {
        Map<Integer,Integer> profile=new HashMap<>();
        try {
            String strsql = "select distinct(CLASS.ID), CLASS.PRIORITY from CLASS, OPERATION_PARAMS where " +
                    "OPERATION='"+Operation+"' and CLASS.NAME=OPERATION_PARAMS.CLASS " +
                    "and CLASS.LOCKED=0 order by CLASS.PRIORITY asc";
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next())
                profile.put(rs.getInt(1), rs.getInt(2));
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
        } catch (SQLException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] ", ex);
            throw new RuntimeException (ex.toString());
        }
        return profile;
    }

    public String getClassName(int ClassId)
    {
        String name=null;
        try {
            String strsql = "select NAME from CLASS where ID="+ClassId;
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next()) {
                name=rs.getString(1);
                break;
            }
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
        } catch (SQLException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] ", ex);
            throw new RuntimeException (ex.toString());
        }
        return name;
    }

    public String getNetworkName(int NetworkId)
    {
        String name=null;
        try {
            String strsql = "select NAME from NETWORK where ID="+NetworkId;
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next()) {
                name=rs.getString(1);
                break;
            }
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
        } catch (SQLException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] ", ex);
            throw new RuntimeException (ex.toString());
        }
        return name;
    }

    public boolean test() {
        PreparedStatement pst=null;
        ResultSet rs=null;
        try {
            if (conn != null) {
                if (conn.isClosed()) return false;
                if (!conn.isClosed()) {
                    String sql = "select VM_NAME,IP,PORT from ACTIVE_VM where ID="+kernel.currentVM.id;
                    pst=conn.prepareStatement(sql);
                    rs=pst.executeQuery();
                    while (rs.next()) {
                        kernel.writeLog("[DATASOURCE : DEBUG] "
                                +rs.getString("VM_NAME")
                                +":"+rs.getString("IP")
                                +":"+rs.getString("PORT"));
                        break;
                    }
                    if(rs!=null) rs.close();
                    if(pst!=null) pst.close();
                }
            }
        } catch (SQLException e) {
            try {
                if (rs != null) rs.close();
                if (pst != null) pst.close();
            } catch (SQLException ex) {
                kernel.writeLog("[DATASOURCE : ERROR] ", e);
            }
            kernel.writeLog("[DATASOURCE : ERROR] ", e);
            return false;
        }
        return true;
    }

    public boolean exist() {
        PreparedStatement pst=null;
        ResultSet rs=null;
        boolean flag = true;
        try {
            if (conn != null) {
                if (!conn.isClosed()) {
                    String sql = "select VM_NAME from ACTIVE_VM where ID="+kernel.currentVM.id;
                    pst=conn.prepareStatement(sql);
                    rs=pst.executeQuery();
                    flag = false;
                    while (rs.next()) {
                        kernel.writeLog("[DATASOURCE : DEBUG] "
                                + rs.getString("VM_NAME") + " exist");
                        flag = true;
                        break;
                    }
                    if(rs!=null) rs.close();
                    if(pst!=null) pst.close();
                }
            }
        } catch (SQLException e) {
            try {
                if (rs != null) rs.close();
                if (pst != null) pst.close();
            } catch (SQLException ex) {
                kernel.writeLog("[DATASOURCE : ERROR] ", e);
            }
            kernel.writeLog("[DATASOURCE : ERROR] ", e);
        }
        return flag;
    }

    public long getClassTimeout(int ClassId)
    {
        long timeout=-1;
        try {
            String strsql = "select TIMEOUT from CLASS where ID="+ClassId;
            PreparedStatement pst=conn.prepareStatement(strsql);
            ResultSet rs=pst.executeQuery();
            while (rs.next()) {
                timeout=rs.getLong(1);
                break;
            }
            if(rs!=null) rs.close();
            if(pst!=null) pst.close();
        } catch (SQLException ex) {
            kernel.writeLog("[DATASOURCE : ERROR] ", ex);
            throw new RuntimeException (ex.toString());
        }
        return timeout;
    }


    public void Close(){
        try{
            if (conn!=null)
                if(!conn.isClosed())
                    conn.close();
            kernel.writeLog("[DATASOURCE : CLOSE] " + "DataSourse close OK");
        }catch(Exception e){
            kernel.writeLog("[DATASOURCE : ERROR] ", e);
            throw new RuntimeException(e.toString());
        }
    }
}
