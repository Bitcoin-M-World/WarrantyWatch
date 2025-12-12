package com.tux2gaming.warrantychecker;

public class WarrantyDetails {
	
	private MinerType type = null;
	private String warranty = "";
	private String serial = "";

	public WarrantyDetails(MinerType type, String warranty, String serial) {
		this.type = type;
		this.warranty = warranty;
		this.serial = serial;
	}

	public MinerType getType() {
		return type;
	}

	public String getWarranty() {
		return warranty;
	}

	public String getSerial() {
		return serial;
	}
	
	

}
