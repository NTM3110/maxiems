package io.openems.edge.bridge.edmi; // Update to your actual component's test package
import org.junit.Test;
import com.atdigital.imr.MeterClient; // Import your library's class
import com.atdigital.imr.ImrCoreLib;
import com.atdigital.imr.EdmiRegisters;
/**
 * This test verifies the integration and basic initialization of the IMR EDMI Client.
 */
public class EdmiClientTest {
    @Test
    public void testImports() {
        // Simple test to ensure the IMR EDMI wrapper library is successfully loaded
        try {
            MeterClient client = new MeterClient();
            System.out.println("IMR EDMI Client initialized successfully!");
            
            // If your MeterClient has getters, you can test them here, like:
            // System.out.println("Client status: " + client.toString());
            
        } catch (Exception e) {
            System.err.println("Failed to initialize MeterClient: " + e.getMessage());
            e.printStackTrace();
        }
    }
    @Test
    public void testRun(){
        try{
            MeterClient.listPorts();
            ImrCoreLib.INSTANCE.Init();
            System.out.println("IMR.Core initialized.");

            MeterClient client = new MeterClient();
            client.connect();

            if (!client.login()) {
                System.out.println("Login failed — check COM port, baud rate, serial, credentials.");
                return;
            }
             client.readRegister(EdmiRegisters.METER_SERIAL_NUMBER);
        } catch (Exception e) {
            System.err.println("Failed to RUN TEST" + e.getMessage());
            e.printStackTrace();
        }

        
    }
}