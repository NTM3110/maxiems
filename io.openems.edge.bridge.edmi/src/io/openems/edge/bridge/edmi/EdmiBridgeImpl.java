package io.openems.edge.bridge.edmi;

import com.atdigital.imr.EdmiRegisters;
import com.atdigital.imr.ImrCoreLib;
import com.fazecast.jSerialComm.SerialPort;
import io.openems.common.oem.OpenemsEdgeOem;
import io.openems.shared.influxdb.InfluxConnector;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.influxdb.client.write.Point;

import io.openems.edge.bridge.edmi.api.EdmiTask;
import io.openems.edge.bridge.edmi.api.EdmiWorker;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import com.atdigital.imr.MeterClient;
import com.atdigital.imr.EdmiDateTime;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.influxdb.TimedataInfluxDb;

import java.net.URI;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Bridge.EDMI", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class EdmiBridgeImpl extends AbstractOpenemsComponent implements EdmiBridge, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(EdmiBridgeImpl.class);

	private EdmiWorker worker;

	private InfluxConnector influxConnector;

	@Reference
	private OpenemsEdgeOem oem;

	public EdmiBridgeImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				EdmiBridge.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.influxConnector = new InfluxConnector(config.id(), config.queryLanguage(), URI.create(config.url()),
				config.org(), config.apiKey(), config.bucket(), this.oem.getInfluxdbTag(), config.isReadOnly(), 5,
				config.maxQueueSize(), //
				(e) -> {
					// ignore
				});

		this.worker = new EdmiWorker(this, point -> {
			influxConnector.write(point);
		});
		this.worker.activate(config.id());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		if (this.worker != null) {
			this.worker.deactivate();
		}
		super.deactivate();
	}

	@Override
	public void addTask(EdmiTask task) {
		this.worker.addTask(task);
	}

	@Override
	public void removeTask(EdmiTask task) {
		this.worker.removeTask(task);
	}

	@Override
	public void writeToInflux(Point point) {
		this.worker.writeToInflux(point);
	}

	@Override
	public Object sendRequest(String command) throws Exception {
		// TODO: THIS IS WHERE THE PHYSICAL COMMUNICATION HAPPENS
		this.log.debug("Sending EDMI Request for [" + command + "]");
		MeterClient.listPorts();
		ImrCoreLib.INSTANCE.Init();
		MeterClient client = new MeterClient("COM2",9600,8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
		client.connect();
		try {
			if (!client.login()) {
				System.out.println("Login failed — check COM port, baud rate, serial, credentials.");
				return null;
			}
			switch (command) {
				case "profile":
					short survey = 0x0325;
					EdmiDateTime.ByValue from = new EdmiDateTime.ByValue(26, 4, 15, 0, 0, 0);
					EdmiDateTime.ByValue to = new EdmiDateTime.ByValue(26, 4, 16, 0, 0, 0);
					return client.readProfile(survey, from, to);

				case "billing":
					return client.readBillingValues();
				default:
					System.out.println("NOT IN THE OPTION OF COMMAND!!!");
					return null;
			}
		} finally {
			client.logout();
			client.disconnect();
		}
	}
}
