package io.openems.edge.bridge.edmi;

import io.openems.edge.bridge.edmi.api.EdmiTask;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

import com.influxdb.client.write.Point;

public interface EdmiBridge extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Adds an EDMI Task.
	 * 
	 * @param task the {@link EdmiTask}
	 */
	void addTask(EdmiTask task);

	/**
	 * Removes an EDMI Task.
	 * 
	 * @param task the {@link EdmiTask}
	 */
	void removeTask(EdmiTask task);

	/**
	 * Sends a request to the hardware and returns the response.
	 * 
	 * @param command the EDMI command/address
	 * @return the response value
	 * @throws Exception on error
	 */
	Object sendRequest(String command) throws Exception;

	/**
	 * Writes a Point to InfluxDB.
	 * 
	 * @param point the InfluxDB Point
	 */
	void writeToInflux(Point point);
}
