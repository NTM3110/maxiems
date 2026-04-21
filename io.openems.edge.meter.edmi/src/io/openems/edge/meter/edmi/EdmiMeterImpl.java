package io.openems.edge.meter.edmi;

import com.atdigital.imr.objects.ReportProfileData;
import io.openems.common.jsonrpc.base.GenericJsonrpcResponseSuccess;
import io.openems.edge.bridge.edmi.api.*;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.jsonapi.JsonApiBuilder;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.types.MeterType;
import io.openems.edge.bridge.edmi.EdmiBridge;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.EDMI", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class EdmiMeterImpl extends AbstractOpenemsEdmiComponent implements ElectricityMeter, OpenemsComponent, ComponentJsonApi {

	private MeterType meterType;

	@Reference
	private EdmiBridge bridge;

	public EdmiMeterImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		this.meterType = config.type();
		super.activate(context, config.id(), config.alias(), config.enabled(), this.bridge);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	@Override
	protected EdmiProtocol defineEdmiProtocol() {
		return new EdmiProtocol(
				// 30-Second Task for Real-time values
				new ReadBillingTask(this.id(),Priority.HIGH),

				// 30-Minute Task for Profile records
				new ReadProfileTask(this.id(), Priority.LOW)
		);
	}

	public static final String GET_EDMI_PROFILE_METHOD = "getEdmiProfile";
	public static final String GET_EDMI_BILLING_VALUE = "getEdmiBillingValues";

	@Override
	public void buildJsonApiRoutes(JsonApiBuilder builder) {
		builder.handleRequest(GET_EDMI_PROFILE_METHOD, call -> {
			ReportProfileData reportProfileData = (ReportProfileData) bridge.sendRequest("profile");
			Gson gson = new Gson();
			JsonObject jsonObject = gson.toJsonTree(reportProfileData).getAsJsonObject();
			JsonObject resultObj = new JsonObject();
			resultObj.add("objects", jsonObject);
			return new GenericJsonrpcResponseSuccess(call.getRequest().getId(), resultObj);
		});
	}
}
