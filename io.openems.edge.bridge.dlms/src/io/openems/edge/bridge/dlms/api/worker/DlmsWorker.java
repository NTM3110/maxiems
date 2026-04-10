package io.openems.edge.bridge.dlms.api.worker;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.openems.common.worker.AbstractImmediateWorker;
import io.openems.edge.bridge.dlms.api.DlmsComponent;
import io.openems.edge.bridge.dlms.api.DlmsProtocol;
import io.openems.edge.bridge.dlms.api.task.DlmsTask;
import io.openems.edge.bridge.dlms.api.task.DlmsTask.ExecuteState;
import io.openems.edge.common.taskmanager.TasksManager;
public class DlmsWorker extends AbstractImmediateWorker {

	private final Function<DlmsTask, ExecuteState> execute;
	private final Map<String, DlmsProtocol> protocols = new HashMap<>();
	private final TasksManager<DlmsTask> taskManager = new TasksManager<>();
	private volatile int cycleDelay = 0;

	public DlmsWorker(Function<DlmsTask, ExecuteState> execute) {
		this.execute = execute;
	}

	public void setCycleDelay(int delayMs) {
		this.cycleDelay = delayMs;
	}

	@Override
	protected void forever() throws InterruptedException {
		DlmsTask task = this.taskManager.getOneTask();
		if (task == null) {
			Thread.sleep(100);
			return;
		}

		ExecuteState result = this.execute.apply(task);
		if (result instanceof ExecuteState.Ok) {
			this.markComponentAsDefective(task.getParent(), false);
		} else if (result instanceof ExecuteState.Error) {
			this.markComponentAsDefective(task.getParent(), true);
		}

		// Apply configured delay between task cycles
		if (this.cycleDelay > 0) {
			Thread.sleep(this.cycleDelay);
		}
	}

	private void markComponentAsDefective(DlmsComponent component, boolean isDefective) {
		if (component != null) {
			component._setDlmsCommunicationFailed(isDefective);
		}
	}

	public synchronized void addProtocol(String sourceId, DlmsProtocol protocol) {
		this.protocols.put(sourceId, protocol);
		this.updateTaskManager();
	}

	public synchronized void removeProtocol(String sourceId) {
		this.protocols.remove(sourceId);
		this.updateTaskManager();
	}

	private void updateTaskManager() {
		this.taskManager.clearAll();
		for (DlmsProtocol protocol : this.protocols.values()) {
			this.taskManager.addTasks(protocol.getTaskManager().getTasks());
		}
	}

	public void retryDlmsCommunication(String sourceId) {
		// For now, doing nothing special here
	}
}