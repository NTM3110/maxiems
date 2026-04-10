package io.openems.edge.bridge.dlms;

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gurux.common.GXCommon;
import gurux.common.ReceiveParameters;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.ErrorCode;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.Security;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSObject;
import gurux.io.BaudRate;
import gurux.io.Parity;
import gurux.io.StopBits;
import gurux.serial.GXSerial;
import gurux.dlms.GXDLMSException;
import gurux.dlms.objects.GXDLMSProfileGeneric;

import io.openems.edge.bridge.dlms.api.AbstractDlmsBridge;
import io.openems.edge.bridge.dlms.api.BridgeDlms;
import io.openems.edge.common.component.OpenemsComponent;

import io.openems.edge.bridge.dlms.GXDLMSReader;
import io.openems.edge.bridge.dlms.GXDLMSSecureClient2;
import gurux.dlms.objects.GXDLMSObjectCollection;
import gurux.dlms.objects.GXXmlWriterSettings;
import gurux.dlms.enums.ObjectType;
import java.io.File;
import java.util.Date;
import java.lang.reflect.Field;


import gurux.common.enums.TraceLevel;

@Component(//
		name = "Bridge.Dlms.Serial", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@Designate(ocd = ConfigSerial.class, factory = true)
public class BridgeDlmsSerialImpl extends AbstractDlmsBridge implements BridgeDlms, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(BridgeDlmsSerialImpl.class);

	private String portName = "";
	private int baudRate;
	private int clientAddress;
	private int serverAddress;
	private int logicalAddress;
	private boolean useLogicalNameReferencing;
	private Parity parity = Parity.NONE;
	private StopBits stopBits = StopBits.ONE;
	private int dataBits = 8;
	private Authentication authentication = Authentication.NONE;
	private String password = "";
	private Security security = Security.NONE;
	private String outputFile = "";

	private GXSerial serial = null;
	private GXDLMSSecureClient2 client = null;
	private GXDLMSReader reader = null;


	public BridgeDlmsSerialImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				BridgeDlms.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, ConfigSerial config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.applyConfig(config);
		this.initClient();
	}

	@Modified
	private void modified(ComponentContext context, ConfigSerial config) {
		super.modified(context, config.id(), config.alias(), config.enabled());
		this.applyConfig(config);
		this.closeConnection();
		this.initClient();
	}

	private void applyConfig(ConfigSerial config) {
		this.portName = config.portName();
		this.baudRate = config.baudRate();
		this.clientAddress = config.clientAddress();
		this.serverAddress = config.serverAddress();
		this.logicalAddress = config.logicalAddress();
		this.useLogicalNameReferencing = config.useLogicalNameReferencing();
		this.parity = Parity.valueOf(config.parity().toUpperCase());
		this.stopBits = StopBits.values()[config.stopBits() - 1];
		this.dataBits = config.dataBits();
		this.authentication = Authentication.valueOf(config.authentication().toUpperCase());
		this.password = config.password();
		this.security = Security.valueOf(config.security().toUpperCase());
		this.outputFile = config.outputFile();
		this._setCycleDelay(config.delay());
		this.worker.setCycleDelay(config.delay());
	}

	private void initClient() {
		this.client = new GXDLMSSecureClient2(this.useLogicalNameReferencing);
		this.client.setClientAddress(this.clientAddress);
		// Combine Logical and Physical addresses into one DLMS-compliant integer
		int combinedAddress = GXDLMSClient.getServerAddress(this.logicalAddress, this.serverAddress);

		// Apply the combined value to the client
		this.client.setServerAddress(combinedAddress);
		this.client.setInterfaceType(InterfaceType.HDLC);
		this.client.setAuthentication(this.authentication);
		this.client.setPassword(this.password.getBytes());
		this.client.getCiphering().setSecurity(this.security);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
		this.closeConnection();
	}

	private synchronized void closeConnection() {
		if (this.serial != null) {
			try {
				if (this.serial.isOpen()) {
					this.serial.close();
				}
			} catch (Exception e) {
				this.log.error("Error closing serial port: " + e.getMessage());
			}
			this.serial = null;
		}
		this.reader = null;
		this.associationViewLoaded = false; // reset so next connection re-fetches
	}

	private synchronized void ensureConnection() throws Exception {
		if (this.serial == null) {
			this.serial = new GXSerial();
			this.serial.setPortName(this.portName);
			this.serial.setBaudRate(BaudRate.forValue(this.baudRate));
			this.serial.setParity(this.parity);
			this.serial.setStopBits(this.stopBits);
			this.serial.setDataBits(this.dataBits);
			this.serial.open();

			// Stabilization delay
			Thread.sleep(1000);

			this.reader = new GXDLMSReader(this.client, this.serial, TraceLevel.VERBOSE, null);
			this.log.info("Initializing Connection (Handshake)...");
			try {
				this.reader.initializeConnection();
				this.log.info("Handshake successful! Connection established.");
			} catch (Exception e) {
				this.log.error("Handshake failed, resetting connection: {}", e.getMessage(), e);
				this.closeConnection();
				this.initClient();
				throw e;
			}
		} else if (!this.serial.isOpen()) {
			this.serial.open();

			// Stabilization delay
			Thread.sleep(1000);

			this.reader = new GXDLMSReader(this.client, this.serial, TraceLevel.INFO, null);
			this.log.info("Initializing Connection (Handshake)...");
			try {
				this.reader.initializeConnection();
				this.log.info("Handshake successful! Connection established.");
			} catch (Exception e) {
				this.log.error("Handshake failed, resetting connection: {}", e.getMessage(), e);
				this.closeConnection();
				this.initClient();
				throw e;
			}
		}
	}

	private boolean associationViewLoaded = false;

	private void ensureAssociationView(){
		if (!this.associationViewLoaded) {
			boolean readFromFile = false;
			String cacheFile = (this.outputFile != null && !this.outputFile.isEmpty())
					? this.outputFile
					: null;
			if (cacheFile != null && new File(cacheFile).exists()) {
				this.log.info("File of association view does exist already!!!!");
				try {
					GXDLMSObjectCollection c = GXDLMSObjectCollection.load(cacheFile);
					if (!c.isEmpty()) {
						this.client.getObjects().addAll(c);
						readFromFile = true;
						this.associationViewLoaded = true;
						this.log.info("Loaded Association View from file (" + c.size() + " objects).");
					}
				} catch (Exception ex) {
					this.log.error("Failed to load objects from file: " + ex.getMessage());
				}
			}

			if (!readFromFile) {
				this.log.info("FILE OF ASSOCATION VIEW  DOES NOT EXIST -> need to read the association view again!!!!");
				try {
					this.log.info("Reading Association View (Object List)...");
					this.reader.getAssociationView();
					this.associationViewLoaded = true;
					this.log.info("Association View loaded (" + this.client.getObjects().size() + " objects).");
					if (this.outputFile != null) {
						this.client.getObjects().save(this.outputFile, new GXXmlWriterSettings());
					}
				} catch (Exception ex) {
					// Meter may not support getAssociationView (e.g. AARQ rejected).
					// Mark as "attempted" to avoid hammering the meter every cycle.
//				 	this.associationViewLoaded = true;
					this.log.warn("Could not read Association View (will use direct OBIS reads): " + ex.getMessage());
					// The DLMS session may be in bad state - reset the connection
					this.closeConnection();
					this.initClient();
				}
			}
		}
	}

	public synchronized Object read(String obis, int attributeIndex) throws Exception {
		this.ensureConnection();
		Object val = null;

		// Only attempt to load association view if we haven't loaded it yet
		this.ensureAssociationView();

		if(associationViewLoaded) {
			// Try looking up object by OBIS in the loaded collection
			GXDLMSObject obj = this.client.getObjects().findByLN(ObjectType.NONE, obis);
			try {
				if (obj != null) {
					val = reader.read(obj, attributeIndex);
				} else {
					// Fallback: read directly by constructing a register object with the OBIS code.
					// This works even when getAssociationView is unavailable.
					gurux.dlms.objects.GXDLMSRegister reg = new gurux.dlms.objects.GXDLMSRegister();
					reg.setLogicalName(obis);
					val = reader.read(reg, attributeIndex);
				}
			} catch (Exception e) {
				this.log.error("DLMS read failed for OBIS [{}] attr [{}]: {}", obis, attributeIndex, e.getMessage(), e);
				// Reset the connection so the next cycle triggers a fresh reconnect + handshake
				this.closeConnection();
				this.initClient();
				throw e;
			}
		}

		return val;
	}

	@Override
	public synchronized Object[] readProfile(String obis, Date start, Date end) throws Exception {
		this.ensureConnection();

		// Load association view if needed (same logic as read())
		if (!this.associationViewLoaded) {
			this.reader.getAssociationView();
			this.associationViewLoaded = true;
		}

		// Find the ProfileGeneric object by OBIS
		GXDLMSObject obj = this.client.getObjects()
				.findByLN(ObjectType.PROFILE_GENERIC, obis);

		if (obj == null) {
			this.log.warn("ProfileGeneric object not found for OBIS [{}]", obis);
			return new Object[0];
		}

		GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) obj;

		// Ensure capture columns are loaded (attribute 3)
		if (pg.getCaptureObjects().isEmpty()) {
			this.reader.read(pg, 3);
		}

		try {
			return this.reader.readRowsByRange(pg, start, end);
		} catch (Exception e) {
			this.log.error("readProfile failed [{}]: {}", obis, e.getMessage(), e);
			this.closeConnection();
			this.initClient();
			throw e;
		}
	}



	public static void printObjectFields(Object obj) {
		if (obj == null) {
			System.out.println("Object is null");
			return;
		}
		System.out.println("Class: " + obj.getClass().getName());
		if (obj.getClass().getName().startsWith("java.lang.")) {
			System.out.println("Value: " + obj);
			return;
		}
		java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();

		for (java.lang.reflect.Field field : fields) {
			try {
				field.setAccessible(true);
				String name = field.getName();
				Object value = field.get(obj);
				System.out.println(name + ": " + value);
			} catch (java.lang.reflect.InaccessibleObjectException | IllegalAccessException e) {
				System.out.println(field.getName() + ": [Inaccessible]");
			}
		}
	}
	@Override
	public synchronized Object[] readBillingValues() throws Exception {
		this.ensureConnection();

		// Load association view if needed (same logic as read())
		if (!this.associationViewLoaded) {
			this.reader.getAssociationView();
			this.associationViewLoaded = true;
		}
		String[] reference_codes = {
				"1-1:1.8.0"
//				"0-0:42.0.0", "0-0:97.97.0", "0-0:1.0.0", "1-0:0.0.0",
//				"1-0:0.0.1", "1-0:0.0.2", "1-0:0.0.3", "0-0:96.1.0",
//				"0-0:96.1.1", "1-1:2.8.0", "1-1:3.8.0", "1-1:4.8.0",
//				"1-1:1.8.0", "1-1:9.8.0", "1-1:1.8.1", "1-1:1.8.2",
//				"1-1:1.8.3", "1-1:2.8.1", "1-1:2.8.2", "1-1:2.8.3",
//				"1-1:1.9.0", "1-1:9.9.0", "1-1:2.5.0", "1-1:3.5.0",
//				"1-1:4.5.0", "1-1:1.5.0", "1-1:9.5.0", "1-1:1.6.0",
//				"1-1:2.6.0", "1-1:1.6.1", "1-1:2.6.1", "1-1:1.6.2",
//				"1-1:2.6.2", "1-1:1.6.3", "1-1:2.6.3", "1-1:1.6.4",
//				"1-1:2.6.4", "1-1:1.2.0", "1-1:2.2.0", "1-1:1.2.1",
//				"1-1:2.2.1", "1-1:1.2.2", "1-1:2.2.2", "1-1:1.2.3",
//				"1-1:2.2.3", "1-1:1.2.4", "1-1:2.2.4", "1-1:32.7.0",
//				"1-1:52.7.0", "1-1:72.7.0", "1-4:32.7.0", "1-4:52.7.0",
//				"1-4:72.7.0", "1-1:31.7.0", "1-1:51.7.0", "1-1:71.7.0",
//				"1-4:31.7.0", "1-4:51.7.0", "1-4:71.7.0", "1-1:91.7.0",
//				"1-1:14.7.0", "1-4:16.7.0", "1-4:36.7.0", "1-4:56.7.0",
//				"1-4:76.7.0", "1-4:131.7.0", "1-4:151.7.0", "1-4:171.7.0",
//				"1-4:191.7.0", "1-1:13.7.0", "1-1:33.7.0", "1-1:53.7.0",
//				"1-1:73.7.0", "1-1:81.7.0", "1-1:81.7.1", "1-1:81.7.2",
//				"1-1:81.7.4", "1-1:81.7.5", "1-1:81.7.6", "0-0:96.7.1",
//				"0-0:96.7.2", "0-0:96.7.3", "1-0:0.1.0", "1-0:0.1.2",
//				"0-0:96.8.0", "0-0:96.2.0", "0-0:96.2.1", "0-1:96.2.5",
//				"0-0:96.2.2", "0-0:96.2.7", "0-0:96.3.1", "0-0:96.3.2",
//				"0-0:96.4.0", "0-0:96.5.0", "0-0:96.6.0", "0-0:96.6.3",
//				"1-0:0.2.0", "1-0:0.2.1", "1-0:0.2.2", "1-0:0.2.7",
//				"0-0:96.90", "1-0:0.2.4", "0-0:96.99.8", "0-0:96.90.2",
//				"0-0:96.90.1", "1-1:0.3.0", "1-1:0.3.1", "1-1:0.4.0",
//				"1-1:0.4.1", "1-1:0.4.2", "1-1:0.4.3"
		};

		int objectsLength = reference_codes.length;
		Object[] objects = new Object[objectsLength];

		for (int i = 0; i < reference_codes.length; i++) {
			String standard_obis = reference_codes[i].replace('-', '.').replace(':', '.');
			long dotCount = standard_obis.chars().filter(ch -> ch == '.').count();
			if (dotCount == 4) {
				standard_obis += ".255";
			}
			this.log.info("Standard obis: {}", standard_obis);
			GXDLMSObject obj = this.client.getObjects()
					.findByLN(ObjectType.NONE, standard_obis);
			Object unit= this.reader.read(obj, 3);
			printObjectFields(unit);
			Object value = this.reader.read(obj, 2);


			Map<String, Object> data = new HashMap<>();
			data.put("obis", standard_obis);
			data.put("description", obj.getDescription() != null ? obj.getDescription() : "Unknown");
			data.put("value", value);
//			data.put("unit", unit.name);
			objects[i] = data;
		}
		return objects;
	}
}

