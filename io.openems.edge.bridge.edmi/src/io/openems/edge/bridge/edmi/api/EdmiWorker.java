package io.openems.edge.bridge.edmi.api;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.influxdb.client.write.Point;

import io.openems.common.worker.AbstractWorker;
import io.openems.edge.bridge.edmi.EdmiBridge;

public class EdmiWorker extends AbstractWorker {

	private final Logger log = LoggerFactory.getLogger(EdmiWorker.class);
	private final PriorityBlockingQueue<EdmiTask> taskQueue = new PriorityBlockingQueue<>(11, (a, b) -> {
		int res = a.getPriority().compareTo(b.getPriority());
		if (res != 0) {
			return res;
		}
		return Long.compare(a.getNextRunTime(), b.getNextRunTime());
	});

	private final EdmiBridge bridge;
	private final Consumer<Point> influxWriter;

	public EdmiWorker(EdmiBridge bridge, Consumer<Point> influxWriter) {
		this.bridge = bridge;
		this.influxWriter = influxWriter;
	}

	@Override
	protected void forever() throws InterruptedException {
		EdmiTask task = this.taskQueue.take();

		long now = System.currentTimeMillis();
		long delay = task.getNextRunTime() - now;
		if (delay > 0) {
			Thread.sleep(delay);
		}

		try {
			task.execute(this.bridge);
		} catch (Exception e) {
			this.log.error("Error executing EDMI Task [" + task + "]: " + e.getMessage());
		}

		// Re-add to queue if it's a repeating task
		this.taskQueue.add(task);
	}

	@Override
	protected int getCycleTime() {
		return 0; // Handled by taskQueue.take() and Thread.sleep()
	}

	public void addTask(EdmiTask task) {
		this.taskQueue.add(task);
	}

	public void removeTask(EdmiTask task) {
		this.taskQueue.remove(task);
	}

	public void writeToInflux(Point point) {
		this.influxWriter.accept(point);
	}
}
