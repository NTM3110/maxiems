package io.openems.edge.bridge.edmi;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Bridge EDMI Serial", description = "Provides a service for connecting to a EDMI Device via Serial")
@interface ConfigSerial {
    @AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
    String id() default "bridgeEdmiSerial0";

    @AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

    @AttributeDefinition(name = "Port-Name", description = "The name of the serial port (e.g. /dev/ttyUSB0 or COM1)")
	String portName() default "/dev/ttyUSB0";

	@AttributeDefinition(name = "Baudrate", description = "The baudrate (e.g. 9600)")
	int baudRate() default 9600;
}