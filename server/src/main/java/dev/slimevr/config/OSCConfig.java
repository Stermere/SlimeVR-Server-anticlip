package dev.slimevr.config;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializers;
import dev.slimevr.config.serializers.BooleanMapDeserializer;
import dev.slimevr.tracking.trackers.TrackerRole;

import java.util.HashMap;
import java.util.Map;


public class OSCConfig {

	// Are the OSC receiver and sender enabled?
	private boolean enabled = false;

	// Port to receive OSC messages from
	private int portIn;

	// Port to send out OSC messages at
	private int portOut;

	// Address to send out OSC messages at
	private String address = "127.0.0.1";

	public OSCConfig() {
	}

	public boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		enabled = value;
	}

	public int getPortIn() {
		return portIn;
	}

	public void setPortIn(int portIn) {
		this.portIn = portIn;
	}

	public int getPortOut() {
		return portOut;
	}

	public void setPortOut(int portOut) {
		this.portOut = portOut;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}
}
