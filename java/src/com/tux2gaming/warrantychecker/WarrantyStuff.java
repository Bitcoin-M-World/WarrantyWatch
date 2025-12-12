package com.tux2gaming.warrantychecker;

public class WarrantyStuff {
	
	private int warranty = 0;
	private String warrantyEndDate = "";
	private String haveWhiteList = "";
	private int code = 0;

	public WarrantyStuff() {
		// TODO Auto-generated constructor stub
	}

	public int getWarranty() {
		return warranty;
	}

	public String getWarrantyEndDate() {
		return warrantyEndDate;
	}
	
	public void setWarrantyEndDate(String date) {
		warrantyEndDate = date;
	}

	public String getHaveWhiteList() {
		return haveWhiteList;
	}

	public int getCode() {
		return code;
	}
}
