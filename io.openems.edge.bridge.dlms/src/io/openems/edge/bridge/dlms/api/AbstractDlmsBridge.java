package io.openems.edge.bridge.dlms.api;


import org.osgi.service.component.ComponentContext;

import io.openems.edge.bridge.dlms.api.worker.DlmsWorker;
import io.openems.edge.common.component.AbstractOpenemsComponent;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import io.openems.edge.common.event.EdgeEventConstants;


public abstract class AbstractDlmsBridge extends AbstractOpenemsComponent 
				implements BridgeDlms, EventHandler {

	protected final DlmsWorker worker = new DlmsWorker(task -> task.execute(this));

	protected AbstractDlmsBridge(io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
			io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
		super(firstInitialChannelIds, furtherInitialChannelIds);
	}

	protected void activate(ComponentContext context, String id, String alias, boolean enabled) {
		super.activate(context, id, alias, enabled);
		if (enabled) {
			this.worker.activate(id);
		}
	}


	@Override
	public void handleEvent(Event event) {
		// DLMS worker is driven by its own background thread, not by OpenEMS cycles.
		// This handler is kept here in case future cycle-aware logic is needed.
	}

	@Override
	protected void deactivate() {
		super.deactivate();
		this.worker.deactivate();
	}

	@Override
	public void addProtocol(String sourceId, DlmsProtocol protocol) {
		this.worker.addProtocol(sourceId, protocol);
	}

	@Override
	public void removeProtocol(String sourceId) {
		this.worker.removeProtocol(sourceId);
	}

	@Override
	public void retryDlmsCommunication(String sourceId) {
		this.worker.retryDlmsCommunication(sourceId);
	}

	/**
	 * Reads a DLMS object value.
	 *
	 * @param obis           the OBIS code
	 * @param attributeIndex the attribute index
	 * @return the value
	 * @throws Exception on error
	 */
	public abstract Object read(String obis, int attributeIndex) throws Exception;

}
