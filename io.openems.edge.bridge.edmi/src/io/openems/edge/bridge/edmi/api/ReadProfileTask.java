package io.openems.edge.bridge.edmi.api;

import java.time.ZoneOffset;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

import io.openems.edge.bridge.edmi.EdmiBridge;
import io.openems.edge.common.taskmanager.Priority;

import com.atdigital.imr.objects.ReportProfileData;

/**
 * A concrete Task for reading profile load records from an EDMI meter.
 */
public class ReadProfileTask extends AbstractEdmiTask {

	private final String meterId;

	public ReadProfileTask(String meterId, Priority priority) {
		super(priority);
		this.meterId = meterId;
	}

	@Override
	public void execute(EdmiBridge bridge) throws Exception {
		// 1. Read records from hardware via the bridge
		// The bridge should return a list of rows (maps)
		ReportProfileData recordProfile = this.readProfileFromHardware(bridge);

		for (List<Object> row : recordProfile.getData()) {
			// 2. Convert to InfluxDB Point
			LocalDateTime timestamp = LocalDateTime.parse((String) row.get(1));

            Point point = Point.measurement("edmi_profile") //
					.addTag("meter_id", this.meterId) //
					.time(timestamp.toInstant(ZoneOffset.ofHours(7)), WritePrecision.MS) //
					.addField("record_status", row.get(0).toString()) //
					.addField("total_energy_tot_imp_wh", (Number) row.get(2)) //
					.addField("total_energy_tot_exp_wh", (Number) row.get(3));

			// 3. Write to InfluxDB via the bridge
			bridge.writeToInflux(point);
		}

		// Schedule next run in 30 minutes
		this.setNextRunTime(System.currentTimeMillis() + 30 * 60 * 1000);
	}

	private ReportProfileData readProfileFromHardware(EdmiBridge bridge) throws Exception {
		// TODO: Implement the profile block reading logic here using bridge.sendRequest()
		// Example: bridge.sendRequest("READ_PROFILE_BLOCK")
		return (ReportProfileData) bridge.sendRequest("profile");
	}
}
