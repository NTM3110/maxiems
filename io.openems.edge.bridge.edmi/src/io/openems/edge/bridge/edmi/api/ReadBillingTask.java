package io.openems.edge.bridge.edmi.api;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import io.openems.edge.bridge.edmi.EdmiBridge;
import io.openems.edge.common.taskmanager.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A concrete Task for reading real-time registers from an EDMI meter.
 */
public class ReadBillingTask extends AbstractEdmiTask {

    private final String meterId;

    private final Logger log = LoggerFactory.getLogger(ReadBillingTask.class);

    public ReadBillingTask(String meterId, Priority priority) {
        super(priority);
        this.meterId = meterId;
    }

    @Override
    public void execute(EdmiBridge bridge) throws Exception {
        this.log.warn("-------------- Excecuting reading billing task --------------");
        // Schedule next run in 30 seconds
        List<Object> objects = this.readBillingValuesFromHardware(bridge);
        String date = objects.get(2).toString();
        String time = objects.get(3).toString();
        LocalDateTime ldt = LocalDateTime.parse(date + "T" + time);
        this.log.info("Local time is: {}", ldt.toString());
        Point point = Point.measurement("edmi_billing_values") //
                .addTag("meter_id", this.meterId) //
                .time(ldt.toInstant(ZoneOffset.ofHours(7)), WritePrecision.MS) //
                .addField("meter_serial_number", objects.get(0).toString()) //
                .addField("error_code", (Number) objects.get(1)) //
                .addField("rate1_imp_wh", (Number) objects.get(4))
                .addField("rate2_imp_kwh",(Number) objects.get(5))
                .addField("rate3_imp_kwh",(Number) objects.get(6))
                .addField("total_imp_kwh",(Number) objects.get(7))
                .addField("total_imp_kvar",(Number) objects.get(8))
                .addField("rate1_exp_kwh",(Number) objects.get(9))
                .addField("rate2_exp_kwh",(Number) objects.get(10))
                .addField("rate3_exp_kwh",(Number) objects.get(11))
                .addField("total_exp_kwh",(Number) objects.get(12))
                .addField("total_exp_kvar",(Number) objects.get(13));
        bridge.writeToInflux(point);
        this.log.info("Write data to InfluxDB");
        this.setNextRunTime(System.currentTimeMillis() + 30 * 1000);
    }

    public List<Object> readBillingValuesFromHardware(EdmiBridge bridge) throws Exception {
        return Collections.singletonList(bridge.sendRequest("billing"));
    }
}
