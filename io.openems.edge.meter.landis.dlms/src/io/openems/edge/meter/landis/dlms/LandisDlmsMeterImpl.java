package io.openems.edge.meter.landis.dlms;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.GenericJsonrpcResponseSuccess;
import io.openems.common.types.MeterType;
import io.openems.common.types.OpenemsType;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.bridge.dlms.api.AbstractDlmsBridge;
import io.openems.edge.bridge.dlms.api.AbstractOpenemsDlmsComponent;
import io.openems.edge.bridge.dlms.api.BridgeDlms;
import io.openems.edge.bridge.dlms.api.DlmsComponent;
import io.openems.edge.bridge.dlms.api.DlmsProtocol;
import io.openems.edge.bridge.dlms.api.element.ObisElement;
import io.openems.edge.bridge.dlms.api.task.DlmsReadTask;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.jsonapi.JsonApiBuilder;
import io.openems.edge.common.taskmanager.Priority;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Landis.Dlms", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class LandisDlmsMeterImpl extends AbstractOpenemsDlmsComponent
		implements DlmsComponent, LandisDlmsMeter, OpenemsComponent, ComponentJsonApi {

	private MeterType meterType = MeterType.GRID;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setDlms(BridgeDlms dlms) {
		super.setDlms(dlms);
	}

	@Reference
	protected ConfigurationAdmin cm;

	public LandisDlmsMeterImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				DlmsComponent.ChannelId.values(), //
				LandisDlmsMeter.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), this.cm, "dlms", config.dlms_id())) {
			return;
		}
		this.meterType = config.type();
	}

	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsException {
		if (super.modified(context, config.id(), config.alias(), config.enabled(), this.cm, "dlms", config.dlms_id())) {
			return;
		}
		this.meterType = config.type();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public String debugLog() {
		return "Export: " + this.channel(LandisDlmsMeter.ChannelId.ACTIVE_POWER_EXPORT).value().asString();
	}

	@Override
	protected DlmsProtocol defineDlmsProtocol() {
		return new DlmsProtocol(this, //
				new DlmsReadTask(Priority.HIGH,
						this.m(LandisDlmsMeter.ChannelId.ACTIVE_POWER_EXPORT, //
								new ObisElement(OpenemsType.INTEGER, "1.1.1.8.0.255", 2))
				)
				);
	}

	/**
	 * Method name for the JSON-RPC endpoint.
	 */
	public static final String GET_DLMS_PROFILE_METHOD = "getDlmsProfile";
	public static final String GET_DLMS_BILLING_VALUE = "getBillingValues";

	@Override
	public void buildJsonApiRoutes(JsonApiBuilder builder) {
		builder.handleRequest(GET_DLMS_PROFILE_METHOD, call -> {
			var params = call.getRequest().getParams();
			String obis     = JsonUtils.getAsString(params, "obis");
			String startStr = JsonUtils.getAsString(params, "startDate"); // "yyyy-MM-dd HH:mm:ss"
			String endStr   = JsonUtils.getAsString(params, "endDate");

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date start = sdf.parse(startStr);
			Date end   = sdf.parse(endStr);


			//This stands for getting the setup configured BridgeDlmsSerialImpl component
			AbstractDlmsBridge bridge = (AbstractDlmsBridge) this.getBridgeDlms();
			if (bridge == null) {
				throw new IllegalStateException("DLMS bridge is not connected");
			}

			//The BridgeDlmsSerialImpl is the children of AbstractDlmsBridge. The first ever declaration of readProfile is from @AbstractDlms.java. However the readl implementation is from BridgeDlmsSerialImpl. This will be decided by the dlms_id in the configuration.
			Object[] rows = bridge.readProfile(obis, start, end);

			// Serialize rows → JSON
			JsonArray result = new JsonArray();
			for (Object rowObj : rows) {
				Object[] row = (Object[]) rowObj;
				JsonArray jsonRow = new JsonArray();
				for (Object cell : row) {
					jsonRow.add(cell != null ? cell.toString() : "null");
				}
				result.add(jsonRow);
			}
			JsonObject resultObj = new JsonObject();
			resultObj.add("rows", result);
			return new GenericJsonrpcResponseSuccess(call.getRequest().getId(), resultObj);
		});

		builder.handleRequest(GET_DLMS_BILLING_VALUE, call -> {
			AbstractDlmsBridge bridge = (AbstractDlmsBridge) this.getBridgeDlms();
			if (bridge == null) {
				throw new IllegalStateException("DLMS bridge is not connected");
			}
			Object[] objects = bridge.readBillingValues();
			JsonArray result = new JsonArray();
			for (Object object : objects) {
				@SuppressWarnings("unchecked")
				Map<String, Object> data = (Map<String, Object>) object;
				JsonObject json = new JsonObject();
				
				// Handle potential nulls and extract correctly from 'data' (not 'object')
				json.addProperty("obis", data.get("obis") != null ? data.get("obis").toString() : null);
				json.addProperty("description", data.get("description") != null ? data.get("description").toString() : null);
				
				Object val = data.get("value");
				if (val instanceof Number) {
					json.addProperty("value", (Number) val);
				} else {
					json.addProperty("value", val != null ? val.toString() : null);
				}
				
				// json.addProperty("unit", data.get("unit") != null ? data.get("unit").toString() : null);
				result.add(json);
			}
			JsonObject resultObj = new JsonObject();
			resultObj.add("objects", result);
			return new GenericJsonrpcResponseSuccess(call.getRequest().getId(), resultObj);
		});
	}
}
