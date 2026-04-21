package io.openems.edge.meter.edmi;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.common.types.MeterType;

@ObjectClassDefinition(//
		name = "Meter EDMI", //
		description = "An EDMI meter.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID if empty")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Meter-Type", description = "What is the Type of this Meter?")
	MeterType type() default MeterType.GRID;

	@AttributeDefinition(name = "EDMI Bridge-ID", description = "ID of the EDMI Bridge")
	String bridge_id() default "bridge0";

	String webconsole_configurationFactory_nameHint() default "Meter EDMI [{id}]";
}
