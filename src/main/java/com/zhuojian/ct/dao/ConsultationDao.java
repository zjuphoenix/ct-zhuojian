package com.zhuojian.ct.dao;

import com.zhuojian.ct.model.Consultation;
import com.zhuojian.ct.model.HttpCode;
import com.zhuojian.ct.model.ResponseMsg;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by wuhaitao on 2016/3/8.
 */
public class ConsultationDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsultationDao.class);

    protected JDBCClient sqlite = null;

    public ConsultationDao(Vertx vertx) {
        JsonObject sqliteConfig = new JsonObject()
                .put("url", "jdbc:sqlite:E:/毕业论文/JavaProject/ct-zhuojian/src/main/resources/webroot/db/zhuojian")
                .put("driver_class", "org.sqlite.JDBC");
        sqlite = JDBCClient.createShared(vertx, sqliteConfig, "consultation");
    }

    public void getConsultations(Handler<List<Consultation>> consultationsHandler){
        sqlite.getConnection(connection -> {
            if (connection.failed()){
                LOGGER.error("connection sqlite failed!");
                consultationsHandler.handle(null);
            }
            else{
                SQLConnection conn = connection.result();
                conn.query("select * from consultation", result -> {
                    if (result.succeeded()){
                        List<JsonObject> objs = result.result().getRows();
                        List<Consultation> consultations = null;
                        Consultation consultation = null;
                        if (objs != null && !objs.isEmpty()) {
                            consultations = new ArrayList<>();
                            for (JsonObject obj : objs) {
                                consultation = new Consultation();
                                consultation.setId(obj.getString("id"));
                                consultation.setCreated(obj.getString("created"));
                                consultation.setCtfile(obj.getString("ctfile"));
                                consultation.setRecord(obj.getString("record"));
                                consultation.setUpdated(obj.getString("updated"));
                                consultations.add(consultation);
                            }
                            consultationsHandler.handle(consultations);
                        }
                        else{
                            consultationsHandler.handle(null);
                        }
                    }
                    else{
                        LOGGER.error("insert data failed!");
                        consultationsHandler.handle(null);
                    }
                });
            }
        });
    }

    public void addConsultation(Consultation consultation, Handler<ResponseMsg> responseMsgHandler){
        sqlite.getConnection(connection -> {
            if (connection.failed()){
               /*System.out.println("connection sqlite failed!");*/
               LOGGER.error("connection sqlite failed!");
               responseMsgHandler.handle(new ResponseMsg(HttpCode.INTERNAL_SERVER_ERROR, "sqlite connected failed!"));
               return;
            }
            SQLConnection conn = connection.result();
            JsonArray params = new JsonArray().add(consultation.getId()).add(consultation.getCreated()).add(consultation.getCtfile()).add(consultation.getRecord()).add(consultation.getUpdated());
            String sql = "insert into consultation(id,created,ctfile,record,updated) values(?,?,?,?,?)";
            conn.updateWithParams(sql, params, insertResult -> {
                if (insertResult.succeeded()){
                    /*System.out.println("insert data success!");*/
                    LOGGER.info("insert data success!");
                    responseMsgHandler.handle(new ResponseMsg(HttpCode.OK, "insert consultation success!"));
                }
                else{
                    /*System.out.println("insert data failed!");*/
                    LOGGER.error("insert data failed!");
                    responseMsgHandler.handle(new ResponseMsg(HttpCode.INTERNAL_SERVER_ERROR, "sqlite insert data failed!"));
                }
            });
        });
    }

    public void updateConsultation(Consultation consultation, Handler<ResponseMsg> responseMsgHandler){
        sqlite.getConnection(connection -> {
            if (connection.failed()){
               /*System.out.println("connection sqlite failed!");*/
                LOGGER.error("connection sqlite failed!");
                responseMsgHandler.handle(new ResponseMsg(HttpCode.INTERNAL_SERVER_ERROR, "sqlite connected failed!"));
                return;
            }
            SQLConnection conn = connection.result();
            JsonArray params = new JsonArray();
            StringBuilder sql = new StringBuilder("update consultation set updated = ?");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String updated = sdf.format(new Date());
            params.add(updated);
            if (StringUtils.isNotEmpty(consultation.getCtfile())){
                params.add(consultation.getCtfile());
                sql.append(",ctfile = ?");
            }
            if (StringUtils.isNotEmpty(consultation.getRecord())){
                params.add(consultation.getRecord());
                sql.append(",record = ?");
            }
            params.add(consultation.getId());
            sql.append(" where id = ?");
            conn.updateWithParams(sql.toString(), params, insertResult -> {
                if (insertResult.succeeded()) {
                    LOGGER.info("update consultation success!");
                    responseMsgHandler.handle(new ResponseMsg(HttpCode.OK, "update consultation success!"));
                } else {
                    LOGGER.error("update consultation failed!");
                    responseMsgHandler.handle(new ResponseMsg(HttpCode.INTERNAL_SERVER_ERROR, "sqlite update consultation failed!"));
                }
            });
        });
    }
}